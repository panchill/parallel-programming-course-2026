package lab2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;

/* Этап 1: планировщик, который ведёт настоящие потоки строго по одному (передача хода через семафоры).
 * Этап 2: обнаружение deadlock в главном цикле и корректное завершение застрявших потоков.
 * Внутреннее устройство (свои классы, семафоры, вспомогательные методы для Shared и SchedLock) придумываете сами. */
public final class Scheduler {
    /** Если true, каждая операция с общими данными добавляет строку в log. Выставляет runner. */
    boolean logging;
    /** Лог прогона: строки "<поток>  <операция>", например "A  lock mu", "A  balance -> 100". */
    final List<String> log = new ArrayList<>();
    /** null, если deadlock не было; иначе "held={a:A, b:B}" (этап 2). */
    String deadlock;
    private boolean aborted;
    enum State
    {
        WORKING,
        WAITING_MOVE,
        WAITING_CONDITION,
        FINISHED
    }
    private final List<Worker> workers = new ArrayList<>();
    private final Semaphore schedSemaphore = new Semaphore(0);
    private static final ThreadLocal<Worker> local = new ThreadLocal<>();
    final List<SchedLock> locks = new ArrayList<>();
    StringBuilder trace = new StringBuilder();

    public static Worker getCurrentWorker() {
        return local.get();
    }
    static final class Aborted extends RuntimeException { }


    public class Worker
    {
        int id;
        char name;
        Runnable body;
        Semaphore semaphore;
        State state;
        boolean isTouched;
        BooleanSupplier cond;


        public Worker(int id, Runnable body)
        {
            this.id = id;
            this.body = body;
            this.semaphore = new Semaphore(0);
            this.isTouched = true;
            this.state = State.WAITING_MOVE;
            this.name = (char) (65 + id);
        }

        public void passTurn(State state)
        {
            setState(state);
            schedSemaphore.release();
            semaphore.acquireUninterruptibly();
            if (aborted)
                throw new Aborted();
            setState(State.WORKING);
        }

        public void setState(State state) {
            this.state = state;
        }

        public boolean getTouched()
        {
            return this.isTouched;
        }

        public void setTouched(boolean touched) {
            isTouched = touched;
        }

        public void writeLog(String text)
        {
            if (logging && !aborted)
                log.add(name + "  " + text);
        }

        public State getState() {
            return state;
        }

        public int getId() {
            return id;
        }

        public char getName() {
            return name;
        }

        public Semaphore getSemaphore() {
            return semaphore;
        }

        public void setCond(BooleanSupplier cond) {
            this.cond = cond;
        }

        public BooleanSupplier getCond() {
            return cond;
        }

        public boolean isAborted() {
            return aborted;
        }
    }

    public Scheduler() { }

    /** Зарегистрировать поток. Потоки получают буквы A, B, C… в порядке вызова spawn. */
    public void spawn(Runnable body) {
        int id = workers.size();
        workers.add(new Worker(id, body));
    }

    /** Запустить потоки и вести их по одному: на каждой паузе спросить strategy.pick(enabled, last). */
    public void run(Strategy strategy) {
        List<Thread> threads = new ArrayList<>();
        for (Worker worker: workers)
        {
            threads.add(new Thread(() -> {
                local.set(worker);
                try {
                    worker.body.run();
                }
                catch (Aborted e) {
                }
                finally {
                    worker.setState(State.FINISHED);
                    schedSemaphore.release();
                }
            }));
        }

        for (Thread thread: threads)
        {
            thread.start();
            schedSemaphore.acquireUninterruptibly();
        }

        int last = -1;
        while (true)
        {
            List<Integer> enabled = new ArrayList<>();
            boolean alive = false;
            for (Worker worker: workers)
            {
                if (worker.getState().equals(State.WAITING_MOVE) || (worker.getState().equals(State.WAITING_CONDITION) && worker.getCond().getAsBoolean())) {
                    enabled.add(worker.getId());
                }
                if (worker.getState() != State.FINISHED)
                    alive = true;
            }
            if (!alive)
                break;
            if (enabled.isEmpty()) {
                StringBuilder held = new StringBuilder();
                for (SchedLock lock : locks) {
                    if (lock.owner != null) {
                        if (!held.isEmpty()) held.append(", ");
                        held.append(lock.name).append(":").append(lock.owner.getName());
                    }
                }
                deadlock = "held={" + held + "}";
                aborted = true;
                for (Worker w : workers) {
                    if (w.getState() != State.FINISHED)
                        w.getSemaphore().release();
                }
                break;
            }
            int pick = strategy.pick(enabled, last);
            Worker worker = workers.get(pick);
            trace.append(worker.getName());
            last = pick;
            worker.getSemaphore().release();
            schedSemaphore.acquireUninterruptibly();
        }

        for (Thread thread: threads)
        {
            try {
                thread.join();
            }
            catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /** Трасса прогона: буквы потоков, выбранных во всех решениях подряд, например "AAAABB". */
    public String traceString() {
        return trace.toString();
    }

    /** window() из тестовых программ: пауза перед следующей операцией с общими данными обязательна. */
    public static void point() {
        Worker current = getCurrentWorker();
        current.writeLog("window");
        current.setTouched(true);
    }

    /** Ждать, пока cond не станет true. До этого поток не попадает в enabled. */
    public static void await(BooleanSupplier cond) {
        Worker worker = getCurrentWorker();
        if (worker.getTouched() || !cond.getAsBoolean())
        {
            worker.setCond(cond);
            worker.passTurn(State.WAITING_CONDITION);
            worker.setTouched(false);
            worker.setCond(null);
        }
        worker.writeLog("await done");
        worker.setTouched(true);
    }
}
