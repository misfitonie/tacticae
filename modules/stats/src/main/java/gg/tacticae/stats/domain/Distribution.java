package gg.tacticae.stats.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntUnaryOperator;

public final class Distribution {

    private final Map<Integer, Double> pmf;

    private Distribution(Map<Integer, Double> pmf) {
        this.pmf = Map.copyOf(pmf);
    }

    public static Distribution point(int value) {
        return new Distribution(Map.of(value, 1.0));
    }

    public static Distribution of(Map<Integer, Double> pmf) {
        double sum = pmf.values().stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(sum - 1.0) > 1e-9) {
            throw new IllegalArgumentException(
                    "Probabilities must sum to 1.0, got " + sum);
        }
        return new Distribution(pmf);
    }

    public Map<Integer, Double> pmf() {
        return pmf;
    }

    public double mean() {
        return pmf.entrySet().stream()
                .mapToDouble(e -> e.getKey() * e.getValue())
                .sum();
    }

    public double variance() {
        double mean = mean();
        return pmf.entrySet().stream()
                .mapToDouble(e -> e.getValue() * Math.pow(e.getKey() - mean, 2))
                .sum();
    }

    public double probabilityAtLeast(int threshold) {
        return pmf.entrySet().stream()
                .filter(e -> e.getKey() >= threshold)
                .mapToDouble(Map.Entry::getValue)
                .sum();
    }

    public Distribution convolve(Distribution other) {
        Map<Integer, Double> result = new HashMap<>();
        for (var a : this.pmf.entrySet()) {
            for (var b : other.pmf.entrySet()) {
                int sum = a.getKey() + b.getKey();
                double prob = a.getValue() * b.getValue();
                result.merge(sum, prob, Double::sum);
            }
        }
        return new Distribution(result);
    }

    public Distribution power(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("n must be non-negative");
        }
        if (n == 0) {
            return point(0);
        }
        Distribution result = this;
        for (int i = 1; i < n; i++) {
            result = result.convolve(this);
        }
        return result;
    }

    public Distribution map(IntUnaryOperator f) {
        Map<Integer, Double> result = new HashMap<>();
        for (var e : pmf.entrySet()) {
            int newValue = f.applyAsInt(e.getKey());
            result.merge(newValue, e.getValue(), Double::sum);
        }
        return new Distribution(result);
    }

    public String prettyPrint() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("mean=%.4f, variance=%.4f%n", mean(), variance()));
        new TreeMap<>(pmf).forEach((k, v)
                -> sb.append(String.format("  P(X=%d) = %.6f%n", k, v)));
        return sb.toString();
    }
}
