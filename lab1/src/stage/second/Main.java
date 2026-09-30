package stage.second;

import utils.Stopwatch;
import utils.ZipfGenerator;

public class Main {
    static void main() throws InterruptedException {
        LockStripingCollector collector = new LockStripingCollector();

        Stopwatch watcher = new Stopwatch();
        double val = watcher.measurePoint(collector, ZipfGenerator.generate((int) Math.pow(2, 20), 1023, 30), 12);

        System.out.println(val);
    }
}
