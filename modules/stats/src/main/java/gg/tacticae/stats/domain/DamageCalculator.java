package gg.tacticae.stats.domain;

import java.util.HashMap;
import java.util.Map;

public final class DamageCalculator {

    public Distribution compute(AttackContext ctx) {
        return perAttackDamage(ctx).power(ctx.attacks());
    }

    Distribution perAttackDamage(AttackContext ctx) {
        double pMiss = ctx.pMiss();
        double pNormalHit = ctx.pNormalHit();
        double pCritHit = ctx.pCritHit();
        int k = ctx.sustainedHitsValue();

        Distribution dmgFromWoundRoll = damageFromHitGoingToWoundRoll(ctx);

        Distribution dmgFromMainCrit = ctx.shouldAutoWoundOnCrit()
            ? damageFromAutoWoundedHit(ctx)
            : dmgFromWoundRoll;
        Distribution dmgFromExtras = dmgFromWoundRoll.power(k);
        Distribution dmgFromCritHit = dmgFromMainCrit.convolve(dmgFromExtras);

        Map<Integer, Double> result = new HashMap<>();
        result.merge(0, pMiss, Double::sum);
        mixIn(result, dmgFromWoundRoll, pNormalHit);
        mixIn(result, dmgFromCritHit, pCritHit);
        return Distribution.of(result);
    }

    private Distribution damageFromHitGoingToWoundRoll(AttackContext ctx) {
        double pWoundBase = Math.max(0, (7 - ctx.effectiveWoundOn()) / 6.0);
        double pCritRate = (7 - ctx.critThreshold()) / 6.0;
        double pCritWDie = Math.min(pCritRate, pWoundBase);
        double pNormalWDie = pWoundBase - pCritWDie;

        double pCritW;
        double pNormalW;
        if (ctx.hasTwinLinked()) {
            double pMissW = 1 - pWoundBase;
            pCritW = pCritWDie * (1 + pMissW);
            pNormalW = pNormalWDie * (1 + pMissW);
        } else {
            pCritW = pCritWDie;
            pNormalW = pNormalWDie;
        }

        double pFailSave = ctx.pFailSave();
        Distribution dmgAfterSave = applyFnp(ctx.damage(), ctx);

        Map<Integer, Double> result = new HashMap<>();
        double pZero;
        if (ctx.hasDevastatingWounds()) {
            // V11: a critical wound becomes mortal wounds, capped at min(D, targetWounds)
            // because spillover beyond one model is lost. No save roll for the crit-wound branch.
            Distribution dmgCritDw = applyFnp(ctx.devastatingWoundDamage(), ctx);
            mixIn(result, dmgCritDw, pCritW);
            mixIn(result, dmgAfterSave, pNormalW * pFailSave);
            pZero = 1 - pCritW - pNormalW * pFailSave;
        } else {
            mixIn(result, dmgAfterSave, (pCritW + pNormalW) * pFailSave);
            pZero = 1 - (pCritW + pNormalW) * pFailSave;
        }
        result.merge(0, pZero, Double::sum);
        return Distribution.of(result);
    }

    private Distribution damageFromAutoWoundedHit(AttackContext ctx) {
        double pFailSave = ctx.pFailSave();
        Distribution dmgAfterSave = applyFnp(ctx.damage(), ctx);

        Map<Integer, Double> result = new HashMap<>();
        mixIn(result, dmgAfterSave, pFailSave);
        result.merge(0, 1 - pFailSave, Double::sum);
        return Distribution.of(result);
    }

    private void mixIn(Map<Integer, Double> result, Distribution d, double weight) {
        if (weight == 0) return;
        for (var e : d.pmf().entrySet()) {
            result.merge(e.getKey(), weight * e.getValue(), Double::sum);
        }
    }

    private Distribution applyFnp(int damage, AttackContext ctx) {
        if (!ctx.hasFeelNoPain() || damage == 0) return Distribution.point(damage);
        double pTake = (ctx.feelNoPain() - 1) / 6.0;
        double pIgnore = 1 - pTake;
        Map<Integer, Double> pmf = new HashMap<>();
        for (int k = 0; k <= damage; k++) {
            double binom = binomCoef(damage, k) * Math.pow(pTake, k) * Math.pow(pIgnore, damage - k);
            pmf.merge(k, binom, Double::sum);
        }
        return Distribution.of(pmf);
    }

    private double binomCoef(int n, int k) {
        double r = 1;
        for (int i = 0; i < k; i++) r = r * (n - i) / (i + 1);
        return r;
    }
}
