package gg.tacticae.stats.domain;

import java.util.HashMap;
import java.util.Map;

public final class DamageCalculator {

    public Distribution compute(AttackContext ctx) {
        if (ctx.hasLethalHits()) {
            return computeWithLethalHits(ctx);
        }
        Distribution hits = hitPhase(ctx);
        if (ctx.hasDevastatingWounds()) {
            return damagePhase(woundAndSaveWithDevastatingWounds(hits, ctx), ctx);
        }
        Distribution wounds = woundPhase(hits, ctx);
        Distribution unsaved = savePhase(wounds, ctx);
        return damagePhase(unsaved, ctx);
    }

    // Crit hits auto-wound, skipping the wound roll.
    // Each attack die is independent, so we compute the per-die wound distribution
    // and power it by the number of attacks.
    private Distribution computeWithLethalHits(AttackContext ctx) {
        double pWound = ctx.pWoundEffective();
        int k = ctx.sustainedHitsValue();

        // On a crit: 1 auto-wound + k extra normal hits (from SustainedHits) going through wound roll
        Distribution extraHitWounds = Distribution.of(Map.of(0, 1 - pWound, 1, pWound)).power(k);

        Map<Integer, Double> perAttack = new HashMap<>();
        perAttack.merge(0, ctx.pMiss(), Double::sum);
        perAttack.merge(0, ctx.pNormalHit() * (1 - pWound), Double::sum);
        perAttack.merge(1, ctx.pNormalHit() * pWound, Double::sum);
        for (var e : extraHitWounds.pmf().entrySet()) {
            perAttack.merge(1 + e.getKey(), ctx.pCritHit() * e.getValue(), Double::sum);
        }

        Distribution totalWounds = Distribution.of(perAttack).power(ctx.attacks());
        return damagePhase(savePhase(totalWounds, ctx), ctx);
    }

    // Crit wounds bypass the save entirely; normal wounds still roll.
    // Combines wound + save into a single per-hit unsaved probability.
    private Distribution woundAndSaveWithDevastatingWounds(Distribution hits, AttackContext ctx) {
        double pWoundBase = Math.max(0, (7 - ctx.effectiveWoundOn()) / 6.0);
        double pCritW = Math.max(0, Math.min((7 - ctx.critThreshold()) / 6.0, pWoundBase));
        double pNormalW = pWoundBase - pCritW;
        double pFailW = 1 - pWoundBase;

        double pUnsaved = ctx.hasTwinLinked()
            ? (1 + pFailW) * (pNormalW * ctx.pFailSave() + pCritW)
            : pNormalW * ctx.pFailSave() + pCritW;

        Map<Integer, Double> singleHit = Map.of(0, 1 - pUnsaved, 1, pUnsaved);
        Distribution unsavedPerHit = Distribution.of(singleHit);

        Map<Integer, Double> result = new HashMap<>();
        for (var e : hits.pmf().entrySet()) {
            Distribution unsaved = unsavedPerHit.power(e.getKey());
            for (var u : unsaved.pmf().entrySet()) {
                result.merge(u.getKey(), e.getValue() * u.getValue(), Double::sum);
            }
        }
        return Distribution.of(result);
    }

    Distribution hitPhase(AttackContext ctx) {
        Map<Integer, Double> single = new HashMap<>();
        single.merge(0, ctx.pMiss(), Double::sum);
        single.merge(1, ctx.pNormalHit(), Double::sum);
        single.merge(1 + ctx.sustainedHitsValue(), ctx.pCritHit(), Double::sum);
        return Distribution.of(single).power(ctx.attacks());
    }

    Distribution woundPhase(Distribution hits, AttackContext ctx) {
        Map<Integer, Double> singleHit = Map.of(
            0, 1 - ctx.pWoundEffective(),
            1, ctx.pWoundEffective()
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