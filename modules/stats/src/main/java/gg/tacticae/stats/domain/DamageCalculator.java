package gg.tacticae.stats.domain;

import java.util.HashMap;
import java.util.Map;

public final class DamageCalculator {

    public Distribution compute(AttackContext ctx) {
        Distribution hits = hitPhase(ctx);
        Distribution wounds = woundPhase(hits, ctx);
        Distribution unsaved = savePhase(wounds, ctx);
        return damagePhase(unsaved, ctx);
    }

    Distribution hitPhase(AttackContext ctx) {
        Map<Integer, Double> single = new HashMap<>();
        single.merge(0, ctx.pMiss(), Double::sum);
        single.merge(1, ctx.pNormalHit(), Double::sum);
        single.merge(1 + ctx.sustainedHits(), ctx.pCritHit(), Double::sum);
        return Distribution.of(single).power(ctx.attacks());
    }

    Distribution woundPhase(Distribution hits, AttackContext ctx) {
        Map<Integer, Double> singleHit = Map.of(
            0, 1 - ctx.pWound(),
            1, ctx.pWound()
        );
        Distribution woundPerHit = Distribution.of(singleHit);

        Map<Integer, Double> result = new HashMap<>();
        for (var e : hits.pmf().entrySet()) {
            Distribution wounds = woundPerHit.power(e.getKey());
            for (var w : wounds.pmf().entrySet()) {
                result.merge(w.getKey(), e.getValue() * w.getValue(), Double::sum);
            }
        }
        return Distribution.of(result);
    }

    Distribution savePhase(Distribution wounds, AttackContext ctx) {
        Map<Integer, Double> singleWound = Map.of(
            0, 1 - ctx.pFailSave(),
            1, ctx.pFailSave()
        );
        Distribution unsavedPerWound = Distribution.of(singleWound);

        Map<Integer, Double> result = new HashMap<>();
        for (var e : wounds.pmf().entrySet()) {
            Distribution unsaved = unsavedPerWound.power(e.getKey());
            for (var u : unsaved.pmf().entrySet()) {
                result.merge(u.getKey(), e.getValue() * u.getValue(), Double::sum);
            }
        }
        return Distribution.of(result);
    }

    Distribution damagePhase(Distribution unsaved, AttackContext ctx) {
        return unsaved.map(n -> n * ctx.damage());
    }
}