package io.github.mapreset.restore;

import io.github.mapreset.MapResetPlugin;
import io.github.mapreset.config.PluginSettings;
import io.github.mapreset.io.AtomicFileReplacer;
import io.github.mapreset.io.FileHasher;
import io.github.mapreset.io.FileInfo;
import io.github.mapreset.io.RegionFileScanner;
import io.github.mapreset.map.GameMap;
import io.github.mapreset.map.MapState;
import io.github.mapreset.map.RestoreMetrics;
import io.github.mapreset.template.TemplateCreator;
import io.github.mapreset.template.TemplateFileEntry;
import io.github.mapreset.template.TemplateManifest;
import io.github.mapreset.template.TemplateStorage;
import io.github.mapreset.world.WorldLifecycleManager;
import io.github.mapreset.world.WorldProfile;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.event.world.WorldLoadEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RestoreManager {
    private final MapResetPlugin plugin;
    private final Executor io;
    private final TemplateStorage templates;
    private final TemplateCreator templateCreator;
    private final RegionFileScanner scanner;
    private final FileHasher hasher;
    private final AtomicFileReplacer replacer;
    private final TransactionStore transactions;
    private final WorldLifecycleManager lifecycle;
    private final AtomicBoolean globalBusy = new AtomicBoolean();
    private final java.util.concurrent.ConcurrentHashMap<String, Session> sessions = new java.util.concurrent.ConcurrentHashMap<>();

    public RestoreManager(MapResetPlugin plugin, Executor io, TemplateStorage templates, TemplateCreator templateCreator,
                          RegionFileScanner scanner, FileHasher hasher, AtomicFileReplacer replacer,
                          TransactionStore transactions, WorldLifecycleManager lifecycle) {
        this.plugin = plugin; this.io = io; this.templates = templates; this.templateCreator = templateCreator;
        this.scanner = scanner; this.hasher = hasher; this.replacer = replacer; this.transactions = transactions; this.lifecycle = lifecycle;
    }

    public boolean startRestore(GameMap map, CommandSender requester) {
        if (!acquire(map)) { reply(requester, "map-busy", Map.of("map", map.name(), "state", map.state())); return false; }
        PluginSettings settings = plugin.settings();
        World world = Bukkit.getWorld(map.worldName());
        if (world == null) return failImmediately(map, requester, "Target world is not loaded. Load " + map.worldName() + " before restore.");
        if (world.getEnvironment() == World.Environment.CUSTOM && !settings.customDimensionEnabled()) return failImmediately(map, requester, "Custom dimensions are disabled by custom-dimension.enabled");
        long players = Bukkit.getOnlinePlayers().stream().filter(p -> p.getWorld().equals(world)).count();
        if (players != 0) {
            Map<String, Object> values = Map.of("map", map.name(), "world", map.worldName(), "players", players);
            reply(requester, "aborted-players", values); plugin.notifications().send("aborted-players", values, requester);
            release(map); return false;
        }
        WorldProfile profile = WorldProfile.capture(world);
        if (world.getEnvironment() == World.Environment.CUSTOM && !settings.customDimensionNmsReload())
            return failCustomNmsDisabled(map, requester);
        Session session = new Session(map, profile, requester); sessions.put(map.name(), session); map.state(MapState.RESTORING); plugin.persistMaps(); startProgress(session);
        session.detail("Captured world profile: name=" + profile.name() + ", key=" + profile.key() + ", path=" + profile.path()
                + ", artifact-path=" + profile.artifactPath() + ", environment=" + profile.environment() + (profile.environment() == World.Environment.CUSTOM
                ? ", nms-storage-level-id=" + profile.nmsStorageLevelId() + ", nms-stem=" + profile.nmsStemKey()
                + ", nms-dimension-path=" + profile.nmsDimensionPath() : ""));
        session.feedback("queued", "Queued: validating template and transaction before save/unload.");
        plugin.notifications().send("start", Map.of("map", map.name(), "world", map.worldName()));
        if (world.getEnvironment() == World.Environment.CUSTOM) plugin.notifications().send("custom-warning", Map.of("map", map.name(), "world", map.worldName()));
        CompletableFuture.supplyAsync(() -> {
            try {
                TemplateManifest manifest = templates.load(map.name());
                return new RestorePreparation(manifest, transactions.create(map.name(), map.worldName(), List.of()));
            }
            catch (IOException ex) { throw new java.util.concurrent.CompletionException(ex); }
        }, io).whenCompleteAsync((preparation, preparationFailure) -> {
            if (preparationFailure != null) { finishError(session, "Restore preflight failed; no world files were changed: " + rootMessage(preparationFailure)); return; }
            if (!profile.name().equals(preparation.manifest().world()) || !profile.key().toString().equals(preparation.manifest().worldKey())) {
                finishError(session, "Template belongs to another world or dimension key; no world files were changed"); return;
            }
            session.templateManifest = preparation.manifest();
            session.transaction = preparation.transaction();
            World current = Bukkit.getWorld(map.worldName());
            if (current != world || Bukkit.getOnlinePlayers().stream().anyMatch(p -> p.getWorld().equals(world))) { finishError(session, "Target world changed or gained players before unload"); return; }
            session.feedback("phase", "Saving world to disk.", "phase", "SAVING");
            try { world.save(true); }
            catch (Throwable ex) { finishError(session, "World save failed: " + ex.getMessage()); return; }
            continueRestore(session, world, settings);
        }, plugin.mainExecutor());
        return true;
    }

    public boolean startRestore(GameMap map) { return startRestore(map, Bukkit.getConsoleSender()); }

    private void continueRestore(Session session, World world, PluginSettings settings) {
        GameMap map = session.map;
        WorldProfile profile = session.profile;
        session.feedback("phase", "Unloading world; file changes have not started.", "phase", "UNLOADING");
        lifecycle.unload(world, false, settings.lifecycleTimeoutSeconds()).thenCompose(unloaded -> {
            if (!unloaded) return CompletableFuture.failedFuture(new IllegalStateException("World unload was refused or cancelled"));
            return CompletableFuture.supplyAsync(() -> restoreFiles(session), io);
        }).thenCompose(result -> {
            session.expectedReload.set(true);
            session.feedback("phase", "File restore finished; reloading world.", "phase", "RELOADING");
            return lifecycle.reload(profile, settings.lifecycleTimeoutSeconds()).thenApply(reloaded -> new ReloadResult(result, reloaded));
        }).whenCompleteAsync((result, failure) -> {
            if (failure != null) { logFailure(session, "Restore/reload failed", failure); finishError(session, rootMessage(failure)); return; }
            if (session.abort.get()) { finishError(session, "Target world was loaded unexpectedly during restore"); return; }
            if (!acceptReloadResult(session, result.world(), settings)) return;
            CompletableFuture.runAsync(() -> {
                try { transactions.success(map.name(), result.restore().transaction()); transactions.remove(map.name()); }
                catch (IOException ex) { throw new java.util.concurrent.CompletionException(ex); }
            }, io).whenCompleteAsync((ignored, txFailure) -> {
                if (txFailure != null) { finishError(session, rootMessage(txFailure)); return; }
                RestoreMetrics metrics = result.restore().statistics().snapshot(session.reloadWarning == null ? "SUCCESS" : "SUCCESS_WITH_RELOAD_WARNING");
                map.lastRestore(metrics); map.ready(); plugin.persistMaps();
                Map<String, Object> values = Map.of("map", map.name(), "world", map.worldName(), "changed", metrics.filesChanged(), "restored", metrics.filesCopied(), "deleted", metrics.filesDeleted(), "copied", humanBytes(metrics.bytesCopied()), "duration", metrics.durationMillis() + "ms", "result", metrics.result());
                String message = session.reloadWarning == null ? "completed" : "completed-warning";
                session.feedback(message, "Restore completed: " + metrics.result(), values);
                plugin.notifications().send(message, values, session.requester);
                release(map);
            }, plugin.mainExecutor());
        }, plugin.mainExecutor());
    }

    public boolean startTemplate(GameMap map, CommandSender requester) {
        if (!acquire(map)) { reply(requester, "map-busy", Map.of("map", map.name(), "state", map.state())); return false; }
        World world = Bukkit.getWorld(map.worldName());
        if (world == null) return failImmediately(map, requester, "Target world is not loaded. Load " + map.worldName() + " before template creation.");
        if (world.getEnvironment() == World.Environment.CUSTOM && !plugin.settings().customDimensionEnabled()) return failImmediately(map, requester, "Custom dimensions are disabled by custom-dimension.enabled");
        if (Bukkit.getOnlinePlayers().stream().anyMatch(p -> p.getWorld().equals(world))) return failImmediately(map, requester, "Players are still inside " + map.worldName() + "; move them out before creating a template.");
        WorldProfile profile = WorldProfile.capture(world);
        if (world.getEnvironment() == World.Environment.CUSTOM && !plugin.settings().customDimensionNmsReload())
            return failCustomNmsDisabled(map, requester);
        Session session = new Session(map, profile, requester); sessions.put(map.name(), session); map.state(MapState.CREATING_TEMPLATE); plugin.persistMaps();
        session.detail("Captured world profile: name=" + profile.name() + ", key=" + profile.key() + ", path=" + profile.path()
                + ", artifact-path=" + profile.artifactPath() + ", environment=" + profile.environment() + (profile.environment() == World.Environment.CUSTOM
                ? ", nms-storage-level-id=" + profile.nmsStorageLevelId() + ", nms-stem=" + profile.nmsStemKey()
                + ", nms-dimension-path=" + profile.nmsDimensionPath() : ""));
        session.feedback("queued", "Queued: preparing template snapshot.");
        plugin.notifications().send("template-start", Map.of("map", map.name(), "world", map.worldName()));
        session.feedback("phase", "Saving world to disk.", "phase", "SAVING");
        try { world.save(true); } catch (Throwable ex) { finishError(session, "World save failed: " + ex.getMessage()); return false; }
        session.feedback("phase", "Unloading world; template copy has not started.", "phase", "UNLOADING");
        lifecycle.unload(world, false, plugin.settings().lifecycleTimeoutSeconds()).whenCompleteAsync((unloaded, unloadFailure) -> {
            if (unloadFailure != null || !Boolean.TRUE.equals(unloaded)) { finishError(session, unloadFailure == null ? "World unload was refused or cancelled" : rootMessage(unloadFailure)); return; }
            CompletableFuture.supplyAsync(() -> {
                try { return templateCreator.create(map, profile, enabledDirectories()); }
                catch (IOException ex) { throw new java.util.concurrent.CompletionException(ex); }
            }, io).handle((manifest, createFailure) -> new TemplateResult(manifest, createFailure)).thenCompose(result -> {
                if (result.failure() == null) session.detail("Template snapshot copied " + result.manifest().files().size() + " managed artifacts from " + profile.artifactPath() + ".");
                session.expectedReload.set(true); session.feedback("phase", "Template copy finished; reloading world.", "phase", "RELOADING");
                return lifecycle.reload(profile, plugin.settings().lifecycleTimeoutSeconds()).thenApply(reloaded -> new TemplateReloadResult(result, reloaded));
            }).whenCompleteAsync((result, reloadFailure) -> {
                if (reloadFailure != null) { logFailure(session, "Template reload failed", reloadFailure); finishError(session, rootMessage(reloadFailure)); }
                else if (result.template().failure() != null) finishError(session, rootMessage(result.template().failure()));
                else if (!acceptReloadResult(session, result.world(), plugin.settings())) { return; }
                else {
                    map.ready(); plugin.persistMaps();
                    Map<String, Object> values = Map.of("map", map.name(), "world", map.worldName(), "files", result.template().manifest().files().size());
                    session.feedback("template-complete", "Template creation completed: " + result.template().manifest().files().size() + " artifacts.", values);
                    plugin.notifications().send("template-complete", values, session.requester); release(map);
                }
            }, plugin.mainExecutor());
        }, plugin.mainExecutor());
        return true;
    }
    public boolean startTemplate(GameMap map) { return startTemplate(map, Bukkit.getConsoleSender()); }

    private RestoreResult restoreFiles(Session session) {
        try {
            PluginSettings settings = plugin.settings();
            TemplateManifest loaded = session.templateManifest;
            if (loaded == null) throw new IOException("Validated template manifest is unavailable");
            if (!session.profile.name().equals(loaded.world()) || !session.profile.key().toString().equals(loaded.worldKey())) throw new IOException("Template belongs to another world or dimension key");
            TemplateManifest manifest = filterManifest(loaded, enabledDirectories());
            session.detail("Scanning managed artifacts under " + session.profile.artifactPath() + " for " + manifest.files().size() + " template artifacts.");
            Map<String, FileInfo> current = scanner.scan(session.profile.artifactPath(), enabledDirectories());
            RestorePlan plan = new RestorePlanner(hasher).plan(manifest, current);
            session.detail("Comparison finished: current=" + current.size() + ", scanned=" + plan.scanned() + ", hashed=" + plan.hashed()
                    + ", changed=" + plan.operations().size() + ", unchanged=" + plan.unchanged());
            RestoreStatistics stats = new RestoreStatistics(); stats.plan(plan); session.phase.set("COMPARING"); session.total.set(plan.operations().size());
            List<String> pending = plan.operations().stream().map(op -> op.type() + ":" + op.path()).toList();
            RestoreTransaction transaction = session.transaction;
            if (transaction == null) throw new IOException("Restore transaction was not prepared");
            transaction.filesPending.clear(); transaction.filesPending.addAll(pending);
            transactions.phase(session.map.name(), transaction, "COMPARING");
            boolean backup = settings.backupsRequired(session.profile.environment() == World.Environment.CUSTOM);
            if (backup) {
                transactions.phase(session.map.name(), transaction, "BACKUP"); session.phase.set("BACKUP");
                Path backupRoot = safeBackupRoot(session.map.name());
                for (RestoreOperation op : plan.operations()) if (op.current() != null) replacer.copy(op.current().path(), backupRoot.resolve(op.path()));
            }
            transactions.phase(session.map.name(), transaction, "COPYING"); session.phase.set("RESTORING");
            Path templateRoot = templates.directory(session.map.name());
            for (RestoreOperation op : plan.operations()) {
                if (session.abort.get() || !plugin.isEnabled()) throw new IOException("Restore interrupted before file operation");
                if (op.type() == RestoreOperation.Type.COPY) {
                    Path source = templateRoot.resolve(op.path()).normalize();
                    Path destination = session.profile.artifactPath().resolve(op.path()).normalize();
                    if (!source.startsWith(templateRoot) || !destination.startsWith(session.profile.artifactPath())) throw new IOException("Unsafe restore path");
                    long bytes = replacer.replace(source, destination);
                    if (!op.template().hash().equals(hasher.sha256(destination))) throw new IOException("Copied artifact hash mismatch: " + op.path());
                    stats.copied(bytes);
                } else {
                    Files.deleteIfExists(op.current().path()); stats.deleted();
                }
                if (settings.verbose()) session.detail("Restored artifact: " + op.type() + " " + op.path());
                transactions.complete(session.map.name(), transaction, op.type() + ":" + op.path());
                session.current.incrementAndGet();
            }
            transactions.phase(session.map.name(), transaction, "RELOAD"); session.phase.set("RELOAD");
            return new RestoreResult(stats, transaction);
        } catch (IOException ex) { throw new java.util.concurrent.CompletionException(ex); }
    }
    private TemplateManifest filterManifest(TemplateManifest source, Set<String> dirs) {
        List<TemplateFileEntry> filtered = source.files().stream().filter(e -> dirs.contains(e.path().substring(0, e.path().indexOf('/')))).toList();
        return new TemplateManifest(source.version(), source.minecraft(), source.world(), source.worldKey(), source.createdAt(), filtered);
    }
    private Set<String> enabledDirectories() {
        PluginSettings s = plugin.settings(); Set<String> result = new LinkedHashSet<>();
        if (s.restoreRegion()) result.add("region"); if (s.restoreEntities()) result.add("entities"); if (s.restorePoi()) result.add("poi"); return result;
    }
    private Path safeBackupRoot(String map) throws IOException {
        Path base = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        Path configured = base.resolve(plugin.settings().backupDirectory()).normalize();
        if (!configured.startsWith(base) || configured.equals(base)) throw new IOException("restore.backup-directory must remain inside the plugin data folder");
        Path target = configured.resolve(map).resolve(Instant.now().toString().replace(':', '-')).normalize();
        if (!target.startsWith(configured)) throw new IOException("Unsafe backup path"); Files.createDirectories(target); return target;
    }
    public void onWorldLoad(WorldLoadEvent event) {
        for (Session session : sessions.values()) if (session.profile.name().equals(event.getWorld().getName()) && !session.expectedReload.get()) session.abort.set(true);
    }
    public void recoverIncomplete() {
        CompletableFuture.runAsync(() -> {
            try {
                for (Path path : transactions.incomplete()) {
                    String name = path.getFileName().toString().replaceFirst("\\.json$", "");
                    plugin.maps().get(name).ifPresent(map -> plugin.mainExecutor().execute(() -> {
                        map.error("Incomplete restore transaction detected"); plugin.persistMaps();
                        plugin.notifications().send("recovery", Map.of("map", map.name(), "world", map.worldName()));
                        if (plugin.settings().autoResume()) startRestore(map);
                    }));
                }
            } catch (IOException ex) { plugin.getLogger().warning("Could not scan transactions: " + ex.getMessage()); }
        }, io);
    }
    public void shutdown() { for (Session session : sessions.values()) session.abort.set(true); }
    public boolean isBusy() { return !sessions.isEmpty(); }
    public Optional<SessionProgress> progress(String mapName) {
        Session session = sessions.get(mapName);
        return session == null ? Optional.empty() : Optional.of(new SessionProgress(session.phase.get(), session.current.get(), session.total.get()));
    }
    private boolean acquire(GameMap map) {
        if (map.state() == MapState.RESTORING || map.state() == MapState.CREATING_TEMPLATE || sessions.containsKey(map.name())) return false;
        if (!plugin.settings().allowConcurrentMaps() && !globalBusy.compareAndSet(false, true)) return false;
        return true;
    }
    private boolean failImmediately(GameMap map, CommandSender requester, String message) {
        map.error(message); plugin.persistMaps(); Map<String, Object> values = Map.of("map", map.name(), "world", map.worldName(), "reason", message);
        reply(requester, "error", values); plugin.notifications().send("error", values, requester); release(map); return false;
    }
    private boolean failCustomNmsDisabled(GameMap map, CommandSender requester) {
        String reason = "custom-dimension.nms-reload is disabled; no files were changed";
        reply(requester, "nms-custom-disabled", Map.of("map", map.name(), "world", map.worldName()));
        map.error(reason); plugin.persistMaps(); plugin.notifications().send("error", Map.of("map", map.name(), "world", map.worldName(), "reason", reason), requester); release(map); return false;
    }
    private void finishError(Session session, String message) {
        session.map.error(message); plugin.persistMaps(); session.feedback("error", "ERROR: " + message, "reason", message);
        plugin.notifications().send("error", Map.of("map", session.map.name(), "world", session.map.worldName(), "reason", message), session.requester); release(session.map);
    }
    private void logFailure(Session session, String title, Throwable failure) {
        if (plugin.settings().consoleDetail()) plugin.getLogger().log(java.util.logging.Level.SEVERE, "[" + session.map.name() + "] " + title, failure);
    }
    private void startProgress(Session session) {
        PluginSettings settings = plugin.settings();
        if (!settings.progress()) return;
        session.progressTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (session.total.get() > 0) plugin.notifications().send("progress", Map.of("map", session.map.name(), "world", session.map.worldName(), "phase", session.phase.get(), "current", session.current.get(), "total", session.total.get(), "changed", session.statisticsChanged()));
        }, settings.progressIntervalSeconds() * 20L, settings.progressIntervalSeconds() * 20L);
    }
    private void release(GameMap map) { Session session = sessions.remove(map.name()); if (session != null && session.progressTask != null) session.progressTask.cancel(); globalBusy.set(false); }
    private boolean acceptReloadResult(Session session, World world, PluginSettings settings) {
        boolean shouldVerify = session.profile.environment() != World.Environment.CUSTOM || settings.customDimensionVerifyReload();
        if (!shouldVerify || session.profile.matches(world)) return true;
        String reason = "reload verification failed: " + session.profile.mismatch(world);
        if (session.profile.environment() != World.Environment.CUSTOM || settings.errorOnVerificationFailure()) {
            finishError(session, reason); return false;
        }
        session.reloadWarning = reason;
        plugin.getLogger().warning("[" + session.map.name() + "] " + reason + " (allowed by custom-dimension.error-on-verification-failure: false)");
        Map<String, Object> values = Map.of("map", session.map.name(), "world", session.map.worldName(), "reason", reason);
        session.feedback("reload-warning", reason, values);
        plugin.notifications().send("reload-warning", values, session.requester);
        return true;
    }
    private void reply(CommandSender requester, String key, Map<String, ?> values) { if (requester != null) plugin.notifications().reply(requester, key, values); }
    private static String rootMessage(Throwable ex) { Throwable current = ex; while (current.getCause() != null) current = current.getCause(); return current.getClass().getSimpleName() + ": " + current.getMessage(); }
    private static String humanBytes(long bytes) { return String.format(java.util.Locale.ROOT, "%.1f MiB", bytes / 1024d / 1024d); }
    private final class Session {
        final GameMap map; final WorldProfile profile; final AtomicBoolean abort = new AtomicBoolean(); final AtomicBoolean expectedReload = new AtomicBoolean();
        final java.util.concurrent.atomic.AtomicReference<String> phase = new java.util.concurrent.atomic.AtomicReference<>("PREPARING");
        final java.util.concurrent.atomic.AtomicLong current = new java.util.concurrent.atomic.AtomicLong(); final java.util.concurrent.atomic.AtomicLong total = new java.util.concurrent.atomic.AtomicLong();
        volatile org.bukkit.scheduler.BukkitTask progressTask;
        volatile RestoreTransaction transaction;
        volatile TemplateManifest templateManifest;
        volatile String reloadWarning;
        final CommandSender requester;
        Session(GameMap map, WorldProfile profile, CommandSender requester) { this.map = map; this.profile = profile; this.requester = requester; }
        void feedback(String key, String fallback, Object... values) {
            detail(fallback);
            if (requester == null) return;
            java.util.Map<String, Object> fields = new java.util.HashMap<>(); fields.put("map", map.name()); fields.put("world", map.worldName());
            for (int i = 0; i + 1 < values.length; i += 2) fields.put(String.valueOf(values[i]), values[i + 1]);
            plugin.notifications().reply(requester, key, fields);
        }
        void feedback(String key, String fallback, Map<String, ?> values) {
            detail(fallback);
            if (requester == null) return;
            java.util.Map<String, Object> fields = new java.util.HashMap<>(); fields.put("map", map.name()); fields.put("world", map.worldName()); fields.putAll(values);
            plugin.notifications().reply(requester, key, fields);
        }
        void detail(String message) {
            if (plugin.settings().consoleDetail()) plugin.getLogger().info("[" + map.name() + "] " + message);
        }
        long statisticsChanged() { return total.get(); }
    }
    private record RestoreResult(RestoreStatistics statistics, RestoreTransaction transaction) { }
    private record RestorePreparation(TemplateManifest manifest, RestoreTransaction transaction) { }
    private record ReloadResult(RestoreResult restore, World world) { }
    private record TemplateResult(TemplateManifest manifest, Throwable failure) { }
    private record TemplateReloadResult(TemplateResult template, World world) { }
    public record SessionProgress(String phase, long current, long total) { }
}
