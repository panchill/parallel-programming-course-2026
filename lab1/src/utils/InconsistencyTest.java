package utils;

import stage.fourth.DoubleBufferingCollector;
import stage.second.LockStripingCollector;
import stage.third.ThreadLocalCollector;

import java.util.concurrent.CountDownLatch;

public class InconsistencyTest {

    private static volatile boolean stop = false;

    static class Writer implements Runnable {
        private final MetricsCollector collector;
        private final long[] values;
        private final int startIndex;
        private final CountDownLatch ready;
        long counter = 0;

        Writer(MetricsCollector collector, long[] values, int startIndex, CountDownLatch ready) {
            this.collector = collector;
            this.values = values;
            this.startIndex = startIndex;
            this.ready = ready;
        }

        @Override
        public void run() {
            long local = 0;
            int i = startIndex;
            ready.countDown();
            while (!stop) {
                collector.record(values[i]);
                local++;
                if (++i == values.length)
                    i = 0;
            }
            counter = local;
        }
    }

    static void main(String[] args) throws InterruptedException {
        MetricsCollector collector = new DoubleBufferingCollector();
        long[] values = ZipfGenerator.generate((int) Math.pow(2, 20), 1023, 30L);

        CountDownLatch ready = new CountDownLatch(4);
        Writer[] writers = new Writer[4];
        Thread[] threads = new Thread[4];
        for (int k = 0; k < 4; k++) {
            writers[k] = new Writer(collector, values, (k * 1000) % values.length, ready);
            threads[k] = new Thread(writers[k]);
            threads[k].start();
        }
        ready.await();

        int broken = 0, less = 0, greater = 0;
        for (int s = 0; s < 10000; s++) {
            Snapshot snap = collector.snapshot();
            long bucketsSum = 0;
            for (long b : snap.buckets()) bucketsSum += b;

            if (bucketsSum != snap.count()) {
                broken++;
                if (bucketsSum < snap.count()) less++;
                else greater++;
            }
        }

        stop = true;
        for (Thread t : threads) t.join();

        long totalCalls = 0;
        for (Writer w : writers) totalCalls += w.counter;

        Snapshot last = collector.snapshot();
        long lastBucketsSum = 0;
        for (long b : last.buckets()) lastBucketsSum += b;

        System.out.printf("Битых снимков: %d из %d (%.2f%%)%n",
                broken, 10000, 100.0 * broken / 10000);
        System.out.printf("  sum(buckets) < count: %d%n", less);
        System.out.printf("  sum(buckets) > count: %d%n", greater);
        System.out.println();
        System.out.printf("Вызовов record() всего: %,d%n", totalCalls);
        System.out.printf("count в итоговом снимке: %,d%n", last.count());
        System.out.printf("sum(buckets) в итоговом: %,d%n", lastBucketsSum);
        System.out.printf("Потеряно записей:        %,d%n", totalCalls - last.count());
    }
}