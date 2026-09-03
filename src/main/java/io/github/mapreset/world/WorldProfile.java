package io.github.mapreset.world;

import net.minecraft.server.level.ServerLevel;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.craftbukkit.CraftWorld;

import java.nio.file.Path;
import java.util.Objects;

/** Captures only API-visible attributes while the original world is still loaded. */
public final class WorldProfile {
    private final String name;
    private final NamespacedKey key;
    /** Bukkit API world root, used only for post-reload identity validation. */
    private final Path path;
    /** Actual location containing region/, entities/, and poi/. */
    private final Path artifactPath;
    private final long seed;
    private final World.Environment environment;
    private final int minHeight;
    private final int maxHeight;
    private final String generator;
    private final String biomeProvider;
    private final WorldCreator creator;
    private final String minecraftVersion;
    private final String nmsStorageLevelId;
    private final String nmsStemKey;
    private final Path nmsDimensionPath;

    private WorldProfile(World world) {
        name = world.getName(); key = world.getKey(); path = world.getWorldPath().toAbsolutePath().normalize();
        seed = world.getSeed(); environment = world.getEnvironment(); minHeight = world.getMinHeight(); maxHeight = world.getMaxHeight();
        generator = typeName(world.getGenerator()); biomeProvider = typeName(world.getBiomeProvider());
        creator = WorldCreator.ofNameAndKey(name, key).copy(world);
        minecraftVersion = Bukkit.getMinecraftVersion();
        if (environment == World.Environment.CUSTOM) {
            ServerLevel handle = ((CraftWorld) world).getHandle();
            nmsStorageLevelId = handle.levelStorageAccess.getLevelId();
            nmsStemKey = handle.getTypeKey().identifier().toString();
            nmsDimensionPath = handle.levelStorageAccess.getDimensionPath(handle.dimension()).toAbsolutePath().normalize();
            // CraftWorld#getWorldPath is the level root for this custom world;
            // region/ entities/ poi actually live under the dimension path.
            artifactPath = nmsDimensionPath;
        } else {
            nmsStorageLevelId = "";
            nmsStemKey = "";
            nmsDimensionPath = path;
            artifactPath = path;
        }
    }
    public static WorldProfile capture(World world) { return new WorldProfile(Objects.requireNonNull(world)); }
    private static String typeName(Object value) { return value == null ? "" : value.getClass().getName(); }
    public String name() { return name; }
    public NamespacedKey key() { return key; }
    public Path path() { return path; }
    public Path artifactPath() { return artifactPath; }
    public String minecraftVersion() { return minecraftVersion; }
    public WorldCreator creator() { return creator; }
    public World.Environment environment() { return environment; }
    public String nmsStorageLevelId() { return nmsStorageLevelId; }
    public String nmsStemKey() { return nmsStemKey; }
    public Path nmsDimensionPath() { return nmsDimensionPath; }
    public boolean matches(World loaded) {
        return loaded != null && name.equals(loaded.getName()) && key.equals(loaded.getKey())
                && path.equals(loaded.getWorldPath().toAbsolutePath().normalize()) && seed == loaded.getSeed()
                && environment == loaded.getEnvironment() && minHeight == loaded.getMinHeight() && maxHeight == loaded.getMaxHeight()
                && generator.equals(typeName(loaded.getGenerator())) && biomeProvider.equals(typeName(loaded.getBiomeProvider()));
    }
    public String mismatch(World loaded) {
        if (loaded == null) return "WorldCreator returned null";
        if (!name.equals(loaded.getName())) return "world name changed";
        if (!key.equals(loaded.getKey())) return "world key changed";
        if (!path.equals(loaded.getWorldPath().toAbsolutePath().normalize())) return "world path changed";
        if (seed != loaded.getSeed()) return "seed changed";
        if (environment != loaded.getEnvironment()) return "environment changed";
        if (minHeight != loaded.getMinHeight() || maxHeight != loaded.getMaxHeight()) return "world height changed";
        if (!generator.equals(typeName(loaded.getGenerator()))) return "generator changed";
        if (!biomeProvider.equals(typeName(loaded.getBiomeProvider()))) return "biome provider changed";
        return "unknown mismatch";
    }
}
