package phases.zero;

import utils.MetricsCollector;
import utils.Snapshot;

import java.util.Arrays;

public class Collector implements MetricsCollector {

    private long[] buckets = new long[256];
    private long count;
    private long sum;
    private long min = Long.MAX_VALUE;
    private long max = Long.MIN_VALUE;

    @Override
    public void record(long value) {
        buckets[(int) Math.min(value / 4, 255)]++;
        count++;
        sum += value;
        if (value > max)
            max = value;
        if (value < min)
            min = value;

    }

    @Override
    public Snapshot snapshot() {
        long[] copyBuckets = Arrays.copyOf(buckets, 256);
        long p50 = percentile(copyBuckets, count, 0.50);
        long p99 = percentile(copyBuckets, count, 0.99);

        return new Snapshot(copyBuckets, count, sum, min, max, p50, p99);
    }

    private static long percentile(long[] buckets, long count, double p) {
        if (count == 0)
            return 0;
        long rank = Math.max(1, (long) Math.ceil(p * count));
        long cumulative = 0;
        for (int i = 0; i < buckets.length; i++) {
            cumulative += buckets[i];
            if (cumulative >= rank) {
                return (long) i * 4;
            }
        }
        return (long) (buckets.length - 1) * 4;
    }
}
