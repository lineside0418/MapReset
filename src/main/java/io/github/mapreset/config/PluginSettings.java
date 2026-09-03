package io.github.mapreset.config;

import org.bukkit.configuration.file.FileConfiguration;

public record PluginSettings(
        int parallelism, int bufferBytes, int hashBufferBytes, int queueLimit, boolean backupBeforeReplace,
        String backupDirectory, boolean restoreRegion, boolean restoreEntities, boolean restorePoi,
        boolean allowConcurrentMaps, boolean customDimensionEnabled, boolean customDimensionRequireBackup,
        boolean customDimensionVerifyReload, boolean errorOnVerificationFailure, boolean customDimensionNmsReload, String adminTag,
        String prefix, boolean consoleNotifications, boolean progress, int progressIntervalSeconds,
        boolean autoResume, int transactionRetentionDays, int lifecycleTimeoutSeconds, boolean verbose, boolean consoleDetail) {
    public static PluginSettings from(FileConfiguration c) {
        String mode = c.getString("comparison.mode", "EXACT");
        if (!"EXACT".equalsIgnoreCase(mode)) throw new IllegalArgumentException("comparison.mode must be EXACT");
        int parallelism = range(c.getInt("io.parallelism", 1), 1, 16, "io.parallelism");
        int buffer = range(c.getInt("io.buffer-size-kib", 1024), 64, 16 * 1024, "io.buffer-size-kib") * 1024;
        int hashBuffer = range(c.getInt("comparison.hash-buffer-kib", 1024), 64, 16 * 1024, "comparison.hash-buffer-kib") * 1024;
        return new PluginSettings(parallelism, buffer, hashBuffer, range(c.getInt("io.queue-limit", 256), 1, 100_000, "io.queue-limit"),
                c.getBoolean("restore.backup-before-replace", false), c.getString("restore.backup-directory", "backups"),
                c.getBoolean("restore.restore-region", true), c.getBoolean("restore.restore-entities", true),
                c.getBoolean("restore.restore-poi", true), c.getBoolean("restore.allow-concurrent-maps", false),
                c.getBoolean("custom-dimension.enabled", true), c.getBoolean("custom-dimension.require-backup", true),
                c.getBoolean("custom-dimension.verify-reload", true), c.getBoolean("custom-dimension.error-on-verification-failure", true), c.getBoolean("custom-dimension.nms-reload", true),
                required(c.getString("notifications.admin-tag"), "notifications.admin-tag"), c.getString("notifications.prefix", "[MapReset] "),
                c.getBoolean("notifications.console", true), c.getBoolean("notifications.progress", true),
                range(c.getInt("notifications.progress-interval-seconds", 5), 1, 3600, "notifications.progress-interval-seconds"),
                c.getBoolean("recovery.auto-resume", false), range(c.getInt("recovery.transaction-retention-days", 30), 1, 3650, "recovery.transaction-retention-days"),
                range(c.getInt("lifecycle.retry-timeout-seconds", 10), 1, 120, "lifecycle.retry-timeout-seconds"), c.getBoolean("logging.verbose", false), c.getBoolean("logging.console-detail", true));
    }
    private static int range(int v, int min, int max, String name) { if (v < min || v > max) throw new IllegalArgumentException(name + " must be " + min + ".." + max); return v; }
    private static String required(String v, String name) { if (v == null || v.isBlank()) throw new IllegalArgumentException(name + " must not be empty"); return v; }
    public boolean backupsRequired(boolean customDimension) { return backupBeforeReplace || (customDimension && customDimensionRequireBackup); }
}
