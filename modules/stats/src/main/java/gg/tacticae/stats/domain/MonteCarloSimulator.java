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
        boolean autoWoundChoice = ctx.shouldAutoWoundOnCrit();
        int effectiveWoundOn = ctx.effectiveWoundOn();
        int fnp = ctx.feelNoPain();
        int targetW = ctx.targetWounds();

        for (int t = 0; t < trials; t++) {
            int attacks = sample(ctx.attacks());

            int totalHits = 0;
            int autoWounds = 0;
            for (int a = 0; a < attacks; a++) {
                int roll = rng.nextInt(6) + 1;
                if (roll == 1) continue;
                if (roll >= ctx.critThreshold()) {
                    if (autoWoundChoice) {
                        autoWounds++;
                    } else {
                        totalHits++;
                    }
                    totalHits += ctx.sustainedHitsValue();
                } else if (roll >= ctx.hitOn()) {
                    totalHits++;
                }
            }

            int normalWoundsToSave = autoWounds;
            int devastatingCrits = 0;
            for (int h = 0; h < totalHits; h++) {
                int roll = rollWound(ctx, effectiveWoundOn);
                if (roll == 0) continue;
                boolean isCrit = roll >= ctx.critThreshold();
                if (ctx.hasDevastatingWounds() && isCrit) {
                    devastatingCrits++;
                } else {
                    normalWoundsToSave++;
                }
            }

            int normalUnsavedWounds = 0;
            for (int w = 0; w < normalWoundsToSave; w++) {
                int saveRoll = rng.nextInt(6) + 1;
                if (saveRoll < ctx.saveOn()) normalUnsavedWounds++;
            }

            int totalDamage = 0;
            for (int w = 0; w < normalUnsavedWounds; w++) {
                int d = sample(ctx.damage());
                totalDamage += applyFnp(d, fnp);
            }
            for (int c = 0; c < devastatingCrits; c++) {
                int d = Math.min(sample(ctx.damage()), targetW);
                totalDamage += applyFnp(d, fnp);
            }

            counts.merge(totalDamage, 1, Integer::sum);
        }

        Map<Integer, Double> pmf = new HashMap<>();
        for (var e : counts.entrySet()) pmf.put(e.getKey(), e.getValue() / (double) trials);
        double sum = pmf.values().stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(sum - 1.0) > 1e-9) pmf.merge(0, 1.0 - sum, Double::sum);
        return Distribution.of(pmf);
    }

    private int rollWound(AttackContext ctx, int effectiveWoundOn) {
        int roll = rng.nextInt(6) + 1;
        if (roll >= effectiveWoundOn) return roll;
        if (!ctx.hasTwinLinked()) return 0;
        int reroll = rng.nextInt(6) + 1;
        return reroll >= effectiveWoundOn ? reroll : 0;
    }

    private int applyFnp(int damage, int fnp) {
        if (fnp == 0) return damage;
        int taken = 0;
        for (int i = 0; i < damage; i++) {
            int roll = rng.nextInt(6) + 1;
            if (roll < fnp) taken++;
        }
        return taken;
    }

    private int sample(Distribution dist) {
        double r = rng.nextDouble();
        double cum = 0;
        int last = 0;
        for (var e : dist.pmf().entrySet()) {
            last = e.getKey();
            cum += e.getValue();
            if (r < cum) return e.getKey();
        }
        return last;
    }
}
