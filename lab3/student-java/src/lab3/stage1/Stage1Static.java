package lab3.stage1;

import lab3.Schedule;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Stage1Static {
    private static final long[] EMPTY = new long[0];

    private record Index(long[][] outgoing, long[][] byPair, int airports) {
        long[] fromTo(int from, int to) {
            return byPair[from * airports + to];
        }
    }

    private record Route(int price, int duration, int first, int second, int third, int legs) {
    }

    private record Result(long count, List<Route> top) {
    }

    public static void main(String[] args) throws Exception {
        Path data = args.length > 0 ? Path.of(args[0]) : Path.of("lab3", "data");
        int threads = args.length > 1 ? Integer.parseInt(args[1]) : 1;

        Schedule schedule = Schedule.open(data.resolve("schedule.bin"));
        Index index = buildIndex(schedule);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        List<String> queries = Files.readAllLines(data.resolve("queries.txt"));

        int queryNumber = 0;
        for (String line : queries) {
            if (line.isBlank()) {
                continue;
            }

            queryNumber++;
            String[] parts = line.trim().split("\\s+");
            int from = schedule.airportId(parts[0]);
            int to = schedule.airportId(parts[1]);
            int day = Integer.parseInt(parts[2]);

            long started = System.nanoTime();
            Result result = search(schedule, index, from, to, day);
            long ms = (System.nanoTime() - started) / 1_000_000;

            System.out.printf("Q%d %s %s %d count=%d ms=%d%n", queryNumber, parts[0], parts[1], day, result.count, ms);

            for (Route route : result.top) {
                System.out.printf("  %d %d %s%n", route.price, route.duration, flightIds(route));
            }
        }
        pool.shutdown();
    }

    private static Index buildIndex(Schedule schedule) {
        int airports = schedule.nAirports;
        int[] outgoingSizes = new int[airports];
        int[] pairSizes = new int[airports * airports];

        for (int id = 0; id < schedule.nFlights; id++) {
            int from = schedule.from(id);
            int to = schedule.to(id);
            outgoingSizes[from]++;
            pairSizes[from * airports + to]++;
        }

        long[][] outgoing = new long[airports][];
        long[][] byPair = new long[airports * airports][];

        for (int i = 0; i < airports; i++) {
            outgoing[i] = new long[outgoingSizes[i]];
        }
        for (int i = 0; i < byPair.length; i++) {
            byPair[i] = pairSizes[i] == 0 ? EMPTY : new long[pairSizes[i]];
        }

        int[] outgoingPositions = new int[airports];
        int[] pairPositions = new int[airports * airports];

        for (int id = 0; id < schedule.nFlights; id++) {
            int from = schedule.from(id);
            int to = schedule.to(id);
            int pair = from * airports + to;

            long entry = ((long) schedule.dep(id) << 32) | id;
            outgoing[from][outgoingPositions[from]++] = entry;
            byPair[pair][pairPositions[pair]++] = entry;
        }

        for (long[] flights : outgoing) {
            Arrays.sort(flights);
        }
        for (long[] flights : byPair) {
            Arrays.sort(flights);
        }

        return new Index(outgoing, byPair, airports);
    }

    private static Result search(Schedule schedule, Index index, int from, int to, int day) {
        long[] firstFlights = index.outgoing[from];
        int begin = lowerBound(firstFlights, day * 1440);
        int end = lowerBound(firstFlights, (day + 1) * 1440);

        return searchRange(schedule, index, from, to, begin, end);
    }

    private static Result searchRange(Schedule schedule, Index index, int from, int to, int begin, int end) {
        PriorityQueue<Route> top = new PriorityQueue<>(11, (a, b) -> compareRoutes(b, a));

        long count = 0;

        long[] firstFlights = index.outgoing[from];

        for (int i = begin; i < end; i++) {
            long firstEntry = firstFlights[i];
            int departure = (int) (firstEntry >>> 32);

            int first = (int) firstEntry;
            int via1 = schedule.to(first);
            int firstPrice = schedule.price(first);

            if (via1 == to) {
                count++;
                offerTop(top, firstPrice, schedule.arr(first) - departure, first, -1, -1, 1);
                continue;
            }
            if (via1 == from) {
                continue;
            }

            int firstArrival = schedule.arr(first);

            long[] lastAfterFirst = index.fromTo(via1, to);
            int lastStart = lowerBound(lastAfterFirst, firstArrival + 60);

            for (int j = lastStart; j < lastAfterFirst.length; j++) {
                long entry = lastAfterFirst[j];
                int lastDeparture = (int) (entry >>> 32);
                if (lastDeparture > firstArrival + 360) {
                    break;
                }

                int last = (int) entry;
                count++;
                offerTop(top, firstPrice + schedule.price(last), schedule.arr(last) - departure, first, last, -1, 2);
            }

            long[] middleFlights = index.outgoing[via1];
            int middleStart = lowerBound(middleFlights, firstArrival + 60);

            for (int j = middleStart; j < middleFlights.length; j++) {
                long middleEntry = middleFlights[j];
                int middleDeparture = (int) (middleEntry >>> 32);
                if (middleDeparture > firstArrival + 360) {
                    break;
                }

                int second = (int) middleEntry;
                int via2 = schedule.to(second);
                if (via2 == from || via2 == via1 || via2 == to) {
                    continue;
                }

                int secondArrival = schedule.arr(second);
                int partialPrice = firstPrice + schedule.price(second);
                long[] lastFlights = index.fromTo(via2, to);
                int thirdStart = lowerBound(lastFlights, secondArrival + 60);

                for (int k = thirdStart; k < lastFlights.length; k++) {
                    long lastEntry = lastFlights[k];
                    int lastDeparture = (int) (lastEntry >>> 32);
                    if (lastDeparture > secondArrival + 360) {
                        break;
                    }

                    int third = (int) lastEntry;
                    count++;
                    offerTop(top, partialPrice + schedule.price(third), schedule.arr(third) - departure, first, second, third, 3);
                }
            }
        }

        List<Route> best = new ArrayList<>(top);
        best.sort(Stage1Static::compareRoutes);
        return new Result(count, best);
    }

    private static int lowerBound(long[] flights, int earliest) {
        int left = 0;
        int right = flights.length;

        while (left < right) {
            int middle = (left + right) / 2;
            int departure = (int) (flights[middle] >>> 32);

            if (departure < earliest) {
                left = middle + 1;
            } else {
                right = middle;
            }
        }
        return left;
    }

    private static int compareCandidate(int price, int duration, int first, int second, int third, int legs, Route other) {
        int result = Integer.compare(price, other.price);
        if (result != 0) return result;

        result = Integer.compare(duration, other.duration);
        if (result != 0) return result;

        int commonLegs = Math.min(legs, other.legs);
        for (int i = 0; i < commonLegs; i++) {
            int candidateId = switch (i) {
                case 0 -> first;
                case 1 -> second;
                default -> third;
            };
            int otherId = switch (i) {
                case 0 -> other.first;
                case 1 -> other.second;
                default -> other.third;
            };

            result = Integer.compare(candidateId, otherId);
            if (result != 0) return result;
        }

        return Integer.compare(legs, other.legs);
    }

    private static int compareRoutes(Route a, Route b) {
        return compareCandidate(a.price, a.duration, a.first, a.second, a.third, a.legs, b);
    }

    private static void offerTop(PriorityQueue<Route> top, int price, int duration, int first, int second, int third, int legs) {
        if (top.size() == 10 && compareCandidate(price, duration, first, second, third, legs, top.peek()) >= 0) {
            return;
        }

        if (top.size() == 10) {
            top.poll();
        }
        top.add(new Route(price, duration, first, second, third, legs));
    }

    private static String flightIds(Route route) {
        String result = Integer.toString(route.first);
        if (route.legs >= 2) result += "-" + route.second;
        if (route.legs >= 3) result += "-" + route.third;
        return result;
    }
}