package stage.fourth;

import utils.MetricsCollector;
import utils.Snapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class DoubleBufferingCollector implements MetricsCollector {
    private static final int NOWHERE = -1;

    static final class ThreadBuffers {
        final long[][] buckets = new long[2][256];
        final long[] count = new long[2];
        final long[] sum = new long[2];
        final long[] min = {Long.MAX_VALUE, Long.MAX_VALUE};
        final long[] max = {0, 0};
        final AtomicInteger inside = new AtomicInteger(NOWHERE);
    }

    private volatile int active = 0;

    private final Object snapLock = new Object();
    private final List<ThreadBuffers> allBuffers = new ArrayList<>();

    private final long[] globalBuckets = new long[256];
    private long globalCount = 0;
    private long globalSum = 0;
    private long globalMin = Long.MAX_VALUE;
    private long globalMax = 0;

    private final ThreadLocal<ThreadBuffers> myBuffers = ThreadLocal.withInitial(() -> {
        ThreadBuffers tb = new ThreadBuffers();
        synchronized (snapLock) {
            allBuffers.add(tb);
        }
        return tb;
    });

    @Override
    public void record(long value) {
        ThreadBuffers my = myBuffers.get();

         int b;
         while (true) {
            b = active;
            my.inside.set(b);
            if (active == b) break;
            my.inside.setRelease(NOWHERE);
         }

//        int b = active;
//        my.inside.set(b);

        int bucket = (int) Math.min(value / 4, 255);
        my.buckets[b][bucket]++;
        my.count[b]++;
        my.sum[b] += value;
        if (value < my.min[b]) my.min[b] = value;
        if (value > my.max[b]) my.max[b] = value;

        my.inside.setRelease(NOWHERE);
    }

    @Override
    public Snapshot snapshot() {
        synchronized (snapLock) {
            int old = active;
            active = 1 - old;

            for (ThreadBuffers s : allBuffers) {
                while (s.inside.get() == old) {
                    Thread.onSpinWait();
                }
                long[] src = s.buckets[old];
                for (int i = 0; i < 256; i++) {
                    globalBuckets[i] += src[i];
                }
                globalCount += s.count[old];
                globalSum += s.sum[old];
                globalMin = Math.min(globalMin, s.min[old]);
                globalMax = Math.max(globalMax, s.max[old]);

                Arrays.fill(src, 0);
                s.count[old] = 0;
                s.sum[old] = 0;
                s.min[old] = Long.MAX_VALUE;
                s.max[old] = 0;
            }

            long p50 = percentile(globalBuckets, globalCount, 0.50);
            long p99 = percentile(globalBuckets, globalCount, 0.99);

            return new Snapshot(Arrays.copyOf(globalBuckets, 256),
                    globalCount, globalSum, globalMin, globalMax, p50, p99);
        }
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