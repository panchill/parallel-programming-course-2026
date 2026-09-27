package stage.second;

import utils.MetricsCollector;
import utils.Snapshot;

import java.util.concurrent.atomic.AtomicLong;

public class LockStripingCollector implements MetricsCollector {

    private final long[] buckets = new long[256];
    private final Object[] locks = new Object[16];

    private final AtomicLong count = new AtomicLong(0);
    private final AtomicLong sum = new AtomicLong(0);
    private final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong max = new AtomicLong(Long.MIN_VALUE);

    public LockStripingCollector() {
        for (int i = 0; i < 16; i++) {
            locks[i] = new Object();
        }
    }

    @Override
    public void record(long value) {
        int b = (int) Math.min(value / 4, 255);
        synchronized (locks[b % 16]) {
            buckets[b]++;
        }
        count.incrementAndGet();
        sum.addAndGet(value);
        long cur;
        do {
            cur = min.get();
            if (value >= cur) break;
        } while (!min.compareAndSet(cur, value));

        do {
            cur = max.get();
            if (value <= cur) break;
        } while (!max.compareAndSet(cur, value));
    }

    @Override
    public Snapshot snapshot() {
        long[] copy = new long[256];
        for (int g = 0; g < 16; g++) {
            synchronized (locks[g]) {
                for (int b = g; b < 256; b += 16) {
                    copy[b] = buckets[b];
                }
            }
        }

        long total = 0;
        for (long x : copy) total += x;

        long p50 = percentile(copy, total, 0.50);
        long p99 = percentile(copy, total, 0.99);

        return new Snapshot(copy, count.get(), sum.get(), min.get(), max.get(), p50, p99);
    }

    private static long percentile(long[] buckets, long total, double p) {
        if (total == 0) return 0;
        long rank = Math.max(1, (long) Math.ceil(p * total));
        long cumulative = 0;
        for (int i = 0; i < buckets.length; i++) {
            cumulative += buckets[i];
            if (cumulative >= rank) return (long) i * 4;
        }
        return (long) (buckets.length - 1) * 4;
    }
}