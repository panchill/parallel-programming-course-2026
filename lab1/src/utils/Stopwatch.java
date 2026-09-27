package utils;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class Stopwatch {



    public double run(MetricsCollector collector, long[] values, int t, int seconds) throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean stop = new AtomicBoolean(false);
        long[] ops = new long[t];
        Thread[] threads = new Thread[t];

        for (int k = 0; k < t; k++) {
            final int id = k;
            threads[k] = new Thread(() -> {
                long local_count = 0;
                int i = id * 1000;
                try {
                    start.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                while (!stop.get())
                {
                    collector.record(values[i]);
                    local_count++;
                    i++;
                    if (i == values.length)
                        i = 0;
                }
                ops[id] = local_count;
            });
            threads[k].start();
        }

        long tStart = System.nanoTime();
        start.countDown();
        Thread.sleep(seconds * 1000L);
        stop.set(true);
        long tEnd = System.nanoTime();

        for (Thread thread: threads)
        {
            thread.join();
        }

        return Arrays.stream(ops).sum() * 1e9 / (tEnd-tStart);
    }

    public double measurePoint(MetricsCollector collector, long[] values, int t) throws InterruptedException {
        run(collector, values, t, 5);
        double[] results = new double[5];

        for (int i = 0; i < 5; i++) {
            results[i] = run(collector, values, t, 5);
        }

        System.out.println(collector.snapshot().count());
        return median(results);

    }

    private static double median(double[] a) {
        double[] sorted = a.clone();
        Arrays.sort(sorted);
        int mid = sorted.length / 2;
        return sorted.length % 2 == 1
                ? sorted[mid]
                : (sorted[mid - 1] + sorted[mid]) / 2.0;
    }
}
