package io.github.mapreset.world;

import io.papermc.paper.world.PaperWorldLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.event.world.WorldLoadEvent;

import java.io.IOException;
import java.util.Objects;

/**
 * Version-locked loader for Paper 1.21.11 custom dimensions.
 *
 * <p>The public Bukkit {@code WorldCreator} route rejects {@link World.Environment#CUSTOM}.
 * Paper itself has a custom-dimension bootstrap path, however: it resolves a registered
 * {@link LevelStem}, creates a {@link ServerLevel}, and then publishes its CraftWorld. This
 * class invokes that same internal path for exactly one already-registered dimension. It never
 * registers a new dimension, changes the data-pack registry, or changes level.dat.</p>
 *
 * <p>All calls must execute on Paper's primary thread while {@code Bukkit.isTickingWorlds()} is
 * false. It is deliberately isolated because it is not a stable Paper API.</p>
 */
public final class NmsCustomDimensionLoader {
    public World reload(WorldProfile profile) throws IOException {
        if (profile.environment() != World.Environment.CUSTOM) {
            throw new IllegalArgumentException("NMS loader only accepts World.Environment.CUSTOM");
        }
        if (!profile.minecraftVersion().equals(Bukkit.getMinecraftVersion())) {
            throw new IllegalStateException("Minecraft version changed during reload");
        }
        if (Bukkit.getWorld(profile.name()) != null) {
            throw new IllegalStateException("Target world is already loaded: " + profile.name());
        }

        CraftServer craftServer = (CraftServer) Bukkit.getServer();
        MinecraftServer server = craftServer.getServer();
        if (profile.nmsStorageLevelId().isBlank() || profile.nmsStemKey().isBlank()) {
            throw new IllegalStateException("Missing captured NMS storage profile");
        }
        Identifier identifier = Identifier.parse(profile.nmsStemKey());
        ResourceKey<LevelStem> stemKey = ResourceKey.create(Registries.LEVEL_STEM, identifier);
        ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, identifier);
        Registry<LevelStem> stems = server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM);
        LevelStem stem = stems.getValue(stemKey);
        if (stem == null) {
            throw new IllegalStateException("Data-pack LevelStem is not registered: " + profile.key());
        }

        LevelStorageSource.LevelStorageAccess storage = server.storageSource.parent().createAccess(profile.nmsStorageLevelId(), stemKey);
        ServerLevel created = null;
        boolean ownershipTransferred = false;
        try {
            java.nio.file.Path resolvedDimensionPath = storage.getDimensionPath(dimensionKey).toAbsolutePath().normalize();
            if (!resolvedDimensionPath.equals(profile.nmsDimensionPath())) {
                throw new IOException("NMS storage path mismatch; captured=" + profile.nmsDimensionPath()
                        + ", resolved=" + resolvedDimensionPath + ", storage-level-id=" + profile.nmsStorageLevelId()
                        + ", stem=" + profile.nmsStemKey());
            }
            PaperWorldLoader.LevelDataResult levelDataResult = PaperWorldLoader.getLevelData(storage);
            if (levelDataResult.fatalError() || levelDataResult.dataTag() == null) {
                throw new IOException("Could not read compatible level.dat for " + profile.name());
            }
            PrimaryLevelData levelData = (PrimaryLevelData) LevelStorageSource.getLevelDataAndDimensions(
                    levelDataResult.dataTag(),
                    server.worldLoaderContext.dataConfiguration(),
                    server.worldLoaderContext.datapackDimensions().lookupOrThrow(Registries.LEVEL_STEM),
                    server.worldLoaderContext.datapackWorldgen()
            ).worldData();
            levelData.checkName(profile.name());

            PaperWorldLoader.WorldLoadingInfo loadingInfo = new PaperWorldLoader.WorldLoadingInfo(
                    -999, profile.name(), identifier.getNamespace() + "_" + identifier.getPath(), stemKey, true
            );
            server.createLevel(stem, loadingInfo, storage, levelData);
            created = server.getLevel(dimensionKey);
            if (created == null) {
                throw new IllegalStateException("Paper did not register reloaded dimension " + profile.key());
            }
            World world = created.getWorld();
            World existingByName = Bukkit.getWorld(profile.name());
            if (existingByName == null) {
                craftServer.addWorld(world);
            } else if (existingByName != world) {
                throw new IOException("Another Bukkit world with the target name appeared during NMS reload: "
                        + existingByName.getName() + " (" + existingByName.getWorldPath() + ")");
            }
            World registered = Bukkit.getWorld(profile.name());
            if (registered != world) {
                String duplicates = Bukkit.getWorlds().stream()
                        .filter(candidate -> candidate.getUID().equals(world.getUID()))
                        .map(candidate -> candidate.getName() + " (" + candidate.getWorldPath() + ")")
                        .reduce((left, right) -> left + ", " + right).orElse("unknown");
                throw new IOException("Bukkit rejected the custom world because uid.dat duplicates " + duplicates
                        + "; generated world=" + world.getWorldPath() + ", uid=" + world.getUID());
            }
            ownershipTransferred = true;

            // Match the visible lifecycle of Bukkit.createWorld. WorldInitEvent is emitted by
            // MinecraftServer.initWorld; listeners expecting WorldLoadEvent still receive it.
            Bukkit.getPluginManager().callEvent(new WorldLoadEvent(world));
            return world;
        } catch (Throwable failure) {
            if (created != null) {
                try {
                    server.removeLevel(created);
                    created.close();
                } catch (Throwable cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            if (!ownershipTransferred) {
                try {
                    storage.close();
                } catch (Throwable cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            if (failure instanceof IOException ioException) throw ioException;
            if (failure instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IllegalStateException("NMS custom-dimension reload failed", failure);
        }
    }

    public static boolean supported(WorldProfile profile) {
        return Objects.equals("1.21.11", profile.minecraftVersion());
    }
}
