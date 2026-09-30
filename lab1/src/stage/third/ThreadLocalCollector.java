package stage.third;

import utils.MetricsCollector;
import utils.Snapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

public class ThreadLocalCollector implements MetricsCollector {
    static final class ThreadState {
        final AtomicLongArray buckets = new AtomicLongArray(256);
        final AtomicLong count = new AtomicLong();
        final AtomicLong sum = new AtomicLong();
        final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
        final AtomicLong max = new AtomicLong(0);
    }

    private final List<ThreadState> allStates = new ArrayList<>();
    private final Object listLock = new Object();

    private final ThreadLocal<ThreadState> myState = ThreadLocal.withInitial(() -> {
        ThreadState s = new ThreadState();
        synchronized (listLock) {
            allStates.add(s);
        }
        return s;
    });

    @Override
    public void record(long value) {
        ThreadState s = myState.get();
        int b = (int) Math.min(value / 4, 255);
        s.buckets.setRelease(b, s.buckets.getPlain(b) + 1);
        s.count.setRelease(s.count.getPlain() + 1);
        s.sum.setRelease(s.sum.getPlain() + value);

        if (value < s.min.getPlain()) s.min.setRelease(value);
        if (value > s.max.getPlain()) s.max.setRelease(value);
    }

    @Override
    public Snapshot snapshot() {
        List<ThreadState> copyOfStates;
        synchronized (listLock) {
            copyOfStates = new ArrayList<>(allStates);
        }

        long[] out = new long[256];
        long count = 0, sum = 0, min = Long.MAX_VALUE, max = 0;

        for (ThreadState s : copyOfStates) {
            for (int i = 0; i < 256; i++) {
                out[i] += s.buckets.get(i);
            }
            count += s.count.get();
            sum += s.sum.get();
            min = Math.min(min, s.min.get());
            max = Math.max(max, s.max.get());
        }

        long total = 0;
        for (long x : out) total += x;

        long p50 = percentile(out, total, 0.50);
        long p99 = percentile(out, total, 0.99);

        return new Snapshot(out, count, sum, min, max, p50, p99);
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