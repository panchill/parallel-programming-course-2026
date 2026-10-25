package lab2;

import java.util.List;

/* Стратегии выбора следующего потока.
 * FirstAlive — этап 1, Replay — этап 2, RandomPick — этап 3, Dfs — этап 4 (ограничение bound — этап 5).
 * enabled — номера потоков, которые могут сделать шаг, по возрастанию; last — прошлый выбранный (-1 в начале). */
public final class Strategies {
    private Strategies() { }

    /** Всегда первый поток из enabled. */
    public static final class FirstAlive implements Strategy {
        public int pick(List<Integer> enabled, int last) {
            return enabled.getFirst();
        }
    }

    /** Проигрывает трассу: на i-м решении выбирает поток trace[i] ('A' -> 0, 'B' -> 1). */
    public static final class Replay implements Strategy {
        private String trace;
        private int position;
        public Replay(String trace) {
            this.trace = trace;
        }

        public void setPosition(int position) {
            this.position = position;
        }

        /** Сбросить позицию в трассе перед прогоном. */
        public void beginRun() {
            setPosition(0);
        }

        public int pick(List<Integer> enabled, int last) {
            if (position >= trace.length())
                throw new IllegalStateException("Трасса закончилась");
            int num = trace.charAt(position) - 65;
            position++;
            if (!enabled.contains(num))
                throw new IllegalArgumentException("Поток сейчас не может ходить");
            return num;
        }
    }

    /** Случайный поток: enabled.get(rnd.nextInt(enabled.size())), rnd = new java.util.Random(seed). */
    public static final class RandomPick implements Strategy {
        private final java.util.Random rnd;

        public RandomPick(long seed) {
            this.rnd = new java.util.Random(seed);
        }

        public int pick(List<Integer> enabled, int last) {
            return enabled.get(rnd.nextInt(enabled.size()));
        }
    }

    /** Stateless DFS по дереву выборов: каждый прогон с нуля, повторяет префикс прошлого и берёт следующий вариант.
     *  bound — максимум вытеснений (этап 5); для dfs runner передаёт Integer.MAX_VALUE. */
    public static final class Dfs implements Strategy {
        private final int bound;
        private final List<List<Integer>> options = new java.util.ArrayList<>();
        private final List<Integer> chosen = new java.util.ArrayList<>();
        private int depth;
        private int preemptions;

        public Dfs(int bound) {
            this.bound = bound;
        }

        /** Подготовиться к новому прогону (позиция в пути, счётчик вытеснений). */
        public void beginRun() {
            depth = 0;
            preemptions = 0;
        }

        public int pick(List<Integer> enabled, int last) {
            boolean lastCanGo = last != -1 && enabled.contains(last);

            if (depth == options.size()) {
                List<Integer> opts;
                if (lastCanGo && preemptions >= bound)
                    opts = List.of(last);
                else
                    opts = new java.util.ArrayList<>(enabled);
                options.add(opts);
                chosen.add(0);
            }

            int choice = options.get(depth).get(chosen.get(depth));
            if (lastCanGo && choice != last)
                preemptions++;
            depth++;
            return choice;
        }

        /** Выбрать следующий путь; false — дерево обойдено. */
        public boolean endRun() {
            while (options.size() > depth) {
                options.removeLast();
                chosen.removeLast();
            }
            while (!options.isEmpty()) {
                int i = options.size() - 1;
                if (chosen.get(i) + 1 < options.get(i).size()) {
                    chosen.set(i, chosen.get(i) + 1);
                    return true;
                }
                options.removeLast();
                chosen.removeLast();
            }
            return false;
        }
    }
}
