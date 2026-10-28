package lab2;

/* Этап 2: лок, о котором знает планировщик. Поток, ждущий занятый лок, не попадает в enabled;
 * обычный synchronized / ReentrantLock здесь нельзя — поток уснёт с ходом в руках и всё зависнет.
 * Лог: "lock <имя>", "unlock <имя>". Планировщик должен знать все локи, чтобы печатать held={a:A, b:B}. */
public final class SchedLock {
    final String name;
    Scheduler.Worker owner;

    /** Создаётся в setup() тестовой программы; зарегистрируйте лок в планировщике s. */
    public SchedLock(Scheduler s, String name) {
        this.name = name;
        s.locks.add(this);
    }

    /** Пауза; если лок занят — ждать, не участвуя в выборе; затем стать владельцем. */
    public void lock() {
        Scheduler.Worker current = Scheduler.getCurrentWorker();
        if (current.getTouched() || owner != null) {
            current.setCond(() -> owner == null);
            current.passTurn(Scheduler.State.WAITING_CONDITION);
            current.setTouched(false);
            current.setCond(null);
        }
        owner = current;
        current.writeLog("lock " + name);
        current.setTouched(true);
    }

    /** Пауза; освободить лок, ждавшие потоки снова могут быть выбраны. */
    public void unlock() {
        Scheduler.Worker current = Scheduler.getCurrentWorker();
        if (current.isAborted()) return;
        if (current.getTouched()) {
            current.passTurn(Scheduler.State.WAITING_MOVE);
            current.setTouched(false);
        }
        owner = null;
        current.writeLog("unlock " + name);
        current.setTouched(true);
    }
}
