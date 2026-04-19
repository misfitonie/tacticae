package gg.tacticae.stats.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.random.RandomGenerator;

public final class MonteCarloSimulator {

    private final RandomGenerator rng;

    public MonteCarloSimulator() {
        this.rng = RandomGenerator.getDefault();
    }

    public Distribution simulate(AttackContext ctx, int trials) {
        Map<Integer, Integer> counts = new HashMap<>();

        for (int t = 0; t < trials; t++) {
            int totalHits = 0;
            for (int a = 0; a < ctx.attacks(); a++) {
                int roll = rng.nextInt(6) + 1;
                if (roll == 1) continue;
                if (roll >= ctx.critThreshold()) {
                    totalHits += 1 + ctx.sustainedHits();
                } else if (roll >= ctx.hitOn()) {
                    totalHits += 1;
                }
            }

            int totalWounds = 0;
            for (int h = 0; h < totalHits; h++) {
                int roll = rng.nextInt(6) + 1;
                if (roll >= ctx.woundOn()) totalWounds++;
            }

            int unsaved = 0;
            for (int w = 0; w < totalWounds; w++) {
                int roll = rng.nextInt(6) + 1;
                if (roll < ctx.saveOn()) unsaved++;
            }

            counts.merge(unsaved * ctx.damage(), 1, Integer::sum);
        }

        Map<Integer, Double> pmf = new HashMap<>();
        for (var e : counts.entrySet()) {
            pmf.put(e.getKey(), e.getValue() / (double) trials);
        }

        double sum = pmf.values().stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(sum - 1.0) > 1e-9) {
            pmf.merge(0, 1.0 - sum, Double::sum);
        }

        return Distribution.of(pmf);
    }
}