package stage.first;

import utils.MetricsCollector;
import utils.Snapshot;

public class EmptyLockCollector implements MetricsCollector {

    @Override
    public synchronized void record(long value) {
    }

    @Override
    public synchronized Snapshot snapshot() {
        return new Snapshot(new long[256], 0, 0, 0, 0, 0, 0);
    }
}