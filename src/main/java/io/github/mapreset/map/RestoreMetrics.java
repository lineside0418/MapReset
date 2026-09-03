package io.github.mapreset.map;

public record RestoreMetrics(
        long filesScanned, long filesHashed, long filesChanged, long filesCopied,
        long filesDeleted, long bytesHashed, long bytesCopied, long durationMillis,
        String result) {
    public static RestoreMetrics empty() {
        return new RestoreMetrics(0, 0, 0, 0, 0, 0, 0, 0, "NEVER");
    }
}
