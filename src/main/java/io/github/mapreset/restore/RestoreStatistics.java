package io.github.mapreset.restore;

import io.github.mapreset.map.RestoreMetrics;

import java.util.concurrent.atomic.AtomicLong;

public final class RestoreStatistics {
    private final long started = System.nanoTime();
    private final AtomicLong scanned = new AtomicLong(), hashed = new AtomicLong(), changed = new AtomicLong(), copied = new AtomicLong(), deleted = new AtomicLong(), bytesHashed = new AtomicLong(), bytesCopied = new AtomicLong();
    public void plan(RestorePlan plan) { scanned.set(plan.scanned()); hashed.set(plan.hashed()); bytesHashed.set(plan.bytesHashed()); changed.set(plan.operations().size()); }
    public void copied(long bytes) { copied.incrementAndGet(); bytesCopied.addAndGet(bytes); }
    public void deleted() { deleted.incrementAndGet(); }
    public long changed() { return changed.get(); }
    public RestoreMetrics snapshot(String result) { return new RestoreMetrics(scanned.get(), hashed.get(), changed.get(), copied.get(), deleted.get(), bytesHashed.get(), bytesCopied.get(), (System.nanoTime()-started)/1_000_000, result); }
}
