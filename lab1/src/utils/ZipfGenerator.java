package utils;

import java.util.Arrays;
import java.util.Random;

public final class ZipfGenerator {

    public static long[] generate(int n, int maxK, long seed) {
        double[] cdf = new double[maxK];
        double total = 0;
        for (int k = 1; k <= maxK; k++) {
            total += 1.0 / Math.pow(k, 1.15);
            cdf[k - 1] = total;
        }
        for (int i = 0; i < maxK; i++) {
            cdf[i] /= total;
        }
        cdf[maxK - 1] = 1.0;

        Random rnd = new Random(seed);
        long[] values = new long[n];
        for (int i = 0; i < n; i++) {
            double u = rnd.nextDouble();
            int idx = Arrays.binarySearch(cdf, u);
            if (idx < 0) idx = -idx - 1;
            else idx = idx + 1;
            values[i] = idx + 1;
        }
        return values;
    }
}
