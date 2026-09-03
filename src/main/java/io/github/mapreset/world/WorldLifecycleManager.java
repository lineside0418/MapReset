package io.github.mapreset.world;

import io.github.mapreset.MapResetPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Executes Paper world lifecycle calls only on the primary thread outside world ticking. */
public final class WorldLifecycleManager {
    private final MapResetPlugin plugin;
    private final NmsCustomDimensionLoader customLoader = new NmsCustomDimensionLoader();
    public WorldLifecycleManager(MapResetPlugin plugin) { this.plugin = plugin; }
    public CompletableFuture<Boolean> unload(World world, boolean save, int timeoutSeconds) {
        return afterWorldTicks(timeoutSeconds, () -> Bukkit.unloadWorld(world, save));
    }
    public CompletableFuture<World> reload(WorldProfile profile, int timeoutSeconds) {
        return afterWorldTicks(timeoutSeconds, () -> {
            if (profile.environment() == World.Environment.CUSTOM) {
                if (!NmsCustomDimensionLoader.supported(profile)) {
                    throw new IllegalStateException("NMS custom-dimension loader supports Paper 1.21.11 only");
                }
                try {
                    return customLoader.reload(profile);
                } catch (java.io.IOException ex) {
                    throw new java.io.UncheckedIOException(ex);
                }
            }
            return Bukkit.createWorld(profile.creator());
        });
    }
    private <T> CompletableFuture<T> afterWorldTicks(int timeoutSeconds, Supplier<T> operation) {
        CompletableFuture<T> result = new CompletableFuture<>();
        long deadline = System.nanoTime() + timeoutSeconds * 1_000_000_000L;
        Runnable[] attempt = new Runnable[1];
        attempt[0] = () -> {
            if (!plugin.isEnabled()) { result.completeExceptionally(new IllegalStateException("Plugin disabled")); return; }
            if (Bukkit.isTickingWorlds()) {
                if (System.nanoTime() > deadline) result.completeExceptionally(new IllegalStateException("Timed out waiting for a safe world lifecycle point"));
                else Bukkit.getScheduler().runTaskLater(plugin, attempt[0], 1L);
                return;
            }
            try { result.complete(operation.get()); } catch (Throwable ex) { result.completeExceptionally(ex); }
        };
        Bukkit.getScheduler().runTask(plugin, attempt[0]);
        return result;
    }
}
