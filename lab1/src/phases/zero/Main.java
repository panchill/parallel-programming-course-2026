package phases.zero;

import utils.Stopwatch;
import utils.ZipfGenerator;

public class Main {
    static void main() throws InterruptedException {

        Collector collector = new Collector();

        Stopwatch watcher = new Stopwatch();
        double val = watcher.measurePoint(collector, ZipfGenerator.generate((int) Math.pow(2, 20), 1023, 30), 1);

        System.out.println(val);
    }
}
