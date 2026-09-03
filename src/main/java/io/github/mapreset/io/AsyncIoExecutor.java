package io.github.mapreset.io;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** A bounded, replaceable shared executor; it never falls back to the server thread. */
public final class AsyncIoExecutor implements Executor, AutoCloseable {
    private volatile ThreadPoolExecutor delegate;
    public synchronized void configure(int parallelism, int queueLimit) {
        ThreadPoolExecutor old = delegate;
        AtomicInteger ids = new AtomicInteger();
        ThreadFactory factory = task -> { Thread thread = new Thread(task, "MapReset-IO-" + ids.incrementAndGet()); thread.setDaemon(true); return thread; };
        delegate = new ThreadPoolExecutor(parallelism, parallelism, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(queueLimit), factory, new ThreadPoolExecutor.AbortPolicy());
        if (old != null) old.shutdown();
    }
    @Override public void execute(Runnable command) { delegate.execute(command); }
    @Override public synchronized void close() { if (delegate != null) { delegate.shutdown(); try { delegate.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); } delegate.shutdownNow(); } }
}
