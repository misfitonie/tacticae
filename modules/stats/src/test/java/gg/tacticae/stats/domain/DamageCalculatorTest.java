package gg.tacticae.stats.domain;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DamageCalculatorTest {

    private final DamageCalculator calculator = new DamageCalculator();

    @Nested
    @DisplayName("Baseline — 10 Bolters vs Carnifex")
    class Baseline {

        private final AttackContext ctx = new AttackContext(10, 2, 6, 3, 1, 6, List.of());

        @Test
        @DisplayName("moyenne analytique correspond à la formule théorique")
        void meanMatchesFormula() {
            double expected = 10 * (5.0/6) * (1.0/6) * (2.0/6) * 1;

            assertThat(calculator.compute(ctx).mean())
                .isCloseTo(expected, within(1e-9));
        }

        @Test
        @DisplayName("la distribution somme à 1")
        void distributionSumsToOne() {
            double sum = calculator.compute(ctx).pmf().values().stream()
                .mapToDouble(Double::doubleValue).sum();

            assertThat(sum).isCloseTo(1.0, within(1e-9));
        }

        @Test
        @DisplayName("P(X=0) est la valeur la plus probable")
        void zeroIsTheMostLikelyOutcome() {
            Distribution result = calculator.compute(ctx);
            double pZero = result.pmf().get(0);

            result.pmf().forEach((k, v) ->
                assertThat(pZero).isGreaterThanOrEqualTo(v));
        }
    }

    @Nested
    @DisplayName("Sustained Hits")
    class SustainedHits {

        @Test
        @DisplayName("Sustained Hits 1 augmente la moyenne de ~20%")
        void sustainedHitsOneIncreaseMeanByTwentyPercent() {
            AttackContext baseline = new AttackContext(10, 2, 6, 3, 1, 6, List.of());
            AttackContext sustained = new AttackContext(10, 2, 6, 3, 1, 6,
                List.of(new Keyword.SustainedHits(1)));

            double ratio = calculator.compute(sustained).mean()
                         / calculator.compute(baseline).mean();

            assertThat(ratio).isCloseTo(1.20, within(0.01));
        }

        @Test
        @DisplayName("Sustained Hits 2 augmente la moyenne de ~40%")
        void sustainedHitsTwoIncreaseMeanByFortyPercent() {
            AttackContext baseline = new AttackContext(10, 2, 6, 3, 1, 6, List.of());
            AttackContext sustained = new AttackContext(10, 2, 6, 3, 1, 6,
                List.of(new Keyword.SustainedHits(2)));

            double ratio = calculator.compute(sustained).mean()
                         / calculator.compute(baseline).mean();

            assertThat(ratio).isCloseTo(1.40, within(0.01));
        }
    }

    @Nested
    @DisplayName("Twin-linked")
    class TwinLinked {

        @Test
        @DisplayName("Twin-linked applique la formule 2p - p²")
        void twinLinkedFormula() {
            double p = 3.0 / 6;
            double pEffective = 2 * p - p * p;

            AttackContext ctx = new AttackContext(10, 2, 4, 3, 1, 6,
                List.of(new Keyword.TwinLinked()));

            assertThat(ctx.pWoundEffective()).isCloseTo(pEffective, within(1e-9));
        }

        @Test
        @DisplayName("Twin-linked augmente la moyenne vs baseline")
        void twinLinkedIncreasesMean() {
            AttackContext baseline = new AttackContext(10, 2, 4, 3, 1, 6, List.of());
            AttackContext twinLinked = new AttackContext(10, 2, 4, 3, 1, 6,
                List.of(new Keyword.TwinLinked()));

            assertThat(calculator.compute(twinLinked).mean())
                .isGreaterThan(calculator.compute(baseline).mean());
        }

        @Test
        @DisplayName("Twin-linked cross-validation Monte Carlo")
        void twinLinkedMonteCarlo() {
            AttackContext ctx = new AttackContext(10, 2, 4, 3, 1, 6,
                List.of(new Keyword.TwinLinked()));

            Distribution analytical = calculator.compute(ctx);
            Distribution monteCarlo = new MonteCarloSimulator().simulate(ctx, 500_000);

            assertThat(analytical.mean()).isCloseTo(monteCarlo.mean(), within(0.02));
        }
    }

    @Nested
    @DisplayName("Multiplicateur de damage")
    class DamageMultiplier {

        @Test
        @DisplayName("damage 2 double la moyenne")
        void damageTwoDoublesMean() {
            AttackContext d1 = new AttackContext(10, 2, 6, 3, 1, 6, List.of());
            AttackContext d2 = new AttackContext(10, 2, 6, 3, 2, 6, List.of());

            assertThat(calculator.compute(d2).mean())
                .isCloseTo(2 * calculator.compute(d1).mean(), within(1e-9));
        }

        @Test
        @DisplayName("damage 2 quadruple la variance")
        void damageTwoQuadruplesVariance() {
            AttackContext d1 = new AttackContext(10, 2, 6, 3, 1, 6, List.of());
            AttackContext d2 = new AttackContext(10, 2, 6, 3, 2, 6, List.of());

            assertThat(calculator.compute(d2).variance())
                .isCloseTo(4 * calculator.compute(d1).variance(), within(1e-9));
        }
    }

    @Nested
    @DisplayName("Anti Keyword")
    class AntiKeywordTests {

        @Test
        @DisplayName("effectiveWoundOn réduit au threshold quand la cible correspond")
        void effectiveWoundOnReducedWhenTargetMatches() {
            // Anti-INFANTRY 4+ contre woundOn=5 : effectif = 4
            AttackContext ctx = new AttackContext(10, 2, 5, 3, 1, 6, "INFANTRY",
                List.of(new Keyword.AntiKeyword("INFANTRY", 4)));

            assertThat(ctx.effectiveWoundOn()).isEqualTo(4);
            assertThat(ctx.pWoundEffective()).isCloseTo(3.0 / 6, within(1e-9));
        }

        @Test
        @DisplayName("effectiveWoundOn inchangé quand la cible ne correspond pas")
        void effectiveWoundOnUnchangedWhenTargetMismatches() {
            AttackContext ctx = new AttackContext(10, 2, 5, 3, 1, 6, "VEHICLE",
                List.of(new Keyword.AntiKeyword("INFANTRY", 4)));

            assertThat(ctx.effectiveWoundOn()).isEqualTo(5);
            assertThat(ctx.pWoundEffective()).isCloseTo(2.0 / 6, within(1e-9));
        }

        @Test
        @DisplayName("Anti n'aide pas quand threshold >= woundOn")
        void antiNoEffectWhenThresholdWorseThanWoundOn() {
            // Anti-INFANTRY 5+ contre woundOn=3 : threshold=5 >= woundOn=3, pas d'effet
            AttackContext ctx = new AttackContext(10, 2, 3, 3, 1, 6, "INFANTRY",
                List.of(new Keyword.AntiKeyword("INFANTRY", 5)));

            assertThat(ctx.effectiveWoundOn()).isEqualTo(3);
        }

        @Test
        @DisplayName("Anti augmente la moyenne vs baseline sur la bonne cible")
        void antiIncreasesMeanVsBaselineOnMatchingTarget() {
            AttackContext baseline = new AttackContext(10, 2, 5, 3, 1, 6, "INFANTRY", List.of());
            AttackContext anti = new AttackContext(10, 2, 5, 3, 1, 6, "INFANTRY",
                List.of(new Keyword.AntiKeyword("INFANTRY", 4)));

            assertThat(calculator.compute(anti).mean())
                .isGreaterThan(calculator.compute(baseline).mean());
        }

        @Test
        @DisplayName("Anti sans effet sur une cible différente")
        void antiNoEffectOnWrongTarget() {
            AttackContext baseline = new AttackContext(10, 2, 5, 3, 1, 6, "VEHICLE", List.of());
            AttackContext anti = new AttackContext(10, 2, 5, 3, 1, 6, "VEHICLE",
                List.of(new Keyword.AntiKeyword("INFANTRY", 4)));

            assertThat(calculator.compute(anti).mean())
                .isCloseTo(calculator.compute(baseline).mean(), within(1e-9));
        }

        @Test
        @DisplayName("Anti cross-validation Monte Carlo")
        void antiMonteCarlo() {
            AttackContext ctx = new AttackContext(10, 2, 5, 3, 1, 6, "INFANTRY",
                List.of(new Keyword.AntiKeyword("INFANTRY", 4)));

            Distribution analytical = calculator.compute(ctx);
            Distribution monteCarlo = new MonteCarloSimulator().simulate(ctx, 500_000);

            assertThat(analytical.mean()).isCloseTo(monteCarlo.mean(), within(0.02));
        }
    }

    @Nested
    @DisplayName("Lethal Hits")
    class LethalHitsTests {

        @Test
        @DisplayName("crit auto-blesse même avec woundOn impossible (7)")
        void lethalHitsAutoWoundsOnCritWithImpossibleWoundRoll() {
            // hitOn=6, critThreshold=6 → tous les succès sont des crits, pNormalHit=0
            // woundOn=7 → sans LethalHits, 0 blessures ; avec LethalHits, crits passent
            // saveOn=7 → toutes les blessures franchissent la save
            AttackContext ctx = new AttackContext(10, 6, 7, 7, 1, 6, List.of(new Keyword.LethalHits()));
            double expected = 10 * (1.0 / 6); // P(crit) * attacks
            assertThat(calculator.compute(ctx).mean()).isCloseTo(expected, within(1e-9));
        }

        @Test
        @DisplayName("LethalHits double la moyenne quand woundOn=6")
        void lethalHitsDoublesMeanWhenWoundOnSix() {
            // saveOn=7 → auto-fail save pour isoler l'effet wound
            // baseline wound/attaque = (5/6)*(1/6) = 5/36
            // lethal  wound/attaque = (4/6)*(1/6) + (1/6) = 10/36  → ×2
            AttackContext baseline = new AttackContext(10, 2, 6, 7, 1, 6, List.of());
            AttackContext lethal   = new AttackContext(10, 2, 6, 7, 1, 6, List.of(new Keyword.LethalHits()));

            double ratio = calculator.compute(lethal).mean() / calculator.compute(baseline).mean();
            assertThat(ratio).isCloseTo(2.0, within(1e-6));
        }

        @Test
        @DisplayName("LethalHits augmente la moyenne vs baseline")
        void lethalHitsIncreasesMean() {
            AttackContext baseline = new AttackContext(10, 2, 4, 3, 1, 6, List.of());
            AttackContext lethal   = new AttackContext(10, 2, 4, 3, 1, 6, List.of(new Keyword.LethalHits()));

            assertThat(calculator.compute(lethal).mean())
                .isGreaterThan(calculator.compute(baseline).mean());
        }

        @Test
        @DisplayName("LethalHits cross-validation Monte Carlo")
        void lethalHitsMonteCarlo() {
            AttackContext ctx = new AttackContext(10, 2, 4, 3, 1, 6, List.of(new Keyword.LethalHits()));

            Distribution analytical = calculator.compute(ctx);
            Distribution monteCarlo = new MonteCarloSimulator().simulate(ctx, 500_000);

            assertThat(analytical.mean()).isCloseTo(monteCarlo.mean(), within(0.02));
        }
    }

    @Nested
    @DisplayName("Devastating Wounds")
    class DevastatingWoundsTests {

        @Test
        @DisplayName("quand tous les wounds sont des crits, la save est entièrement bypassée")
        void devastatingWoundsBypassesSaveWhenAllWoundsAreCrits() {
            // woundOn=6, critThreshold=6 → tout wound est un crit wound (pNormalWound=0)
            // saveOn=2 → save très forte (pFailSave=1/6) que DevastatingWounds bypass
            // ratio attendu = 1/pFailSave = 6
            AttackContext baseline    = new AttackContext(10, 2, 6, 2, 1, 6, List.of());
            AttackContext devastating = new AttackContext(10, 2, 6, 2, 1, 6, List.of(new Keyword.DevastatingWounds()));

            double ratio = calculator.compute(devastating).mean() / calculator.compute(baseline).mean();
            assertThat(ratio).isCloseTo(6.0, within(1e-6));
        }

        @Test
        @DisplayName("DevastatingWounds augmente la moyenne vs baseline")
        void devastatingWoundsIncreasesMean() {
            AttackContext baseline    = new AttackContext(10, 2, 4, 2, 1, 6, List.of());
            AttackContext devastating = new AttackContext(10, 2, 4, 2, 1, 6, List.of(new Keyword.DevastatingWounds()));

            assertThat(calculator.compute(devastating).mean())
                .isGreaterThan(calculator.compute(baseline).mean());
        }

        @Test
        @DisplayName("DevastatingWounds sans effet quand save déjà auto-fail (saveOn=7)")
        void devastatingWoundsNoEffectWhenSaveAlwaysFails() {
            // saveOn=7 → pFailSave=1, le bypass crit ne change rien
            AttackContext baseline    = new AttackContext(10, 2, 4, 7, 1, 6, List.of());
            AttackContext devastating = new AttackContext(10, 2, 4, 7, 1, 6, List.of(new Keyword.DevastatingWounds()));

            assertThat(calculator.compute(devastating).mean())
                .isCloseTo(calculator.compute(baseline).mean(), within(1e-9));
        }

        @Test
        @DisplayName("DevastatingWounds cross-validation Monte Carlo")
        void devastatingWoundsMonteCarlo() {
            AttackContext ctx = new AttackContext(10, 2, 4, 3, 1, 6, List.of(new Keyword.DevastatingWounds()));

            Distribution analytical = calculator.compute(ctx);
            Distribution monteCarlo = new MonteCarloSimulator().simulate(ctx, 500_000);

            assertThat(analytical.mean()).isCloseTo(monteCarlo.mean(), within(0.02));
        }
    }

    @Nested
    @DisplayName("Cross-validation Monte Carlo")
    class MonteCarloCrossValidation {

        @Test
        @DisplayName("analytique vs Monte Carlo — écart moyenne < 0.02")
        void analyticalMatchesMonteCarlo() {
            AttackContext ctx = new AttackContext(10, 2, 6, 3, 1, 6,
                List.of(new Keyword.SustainedHits(1)));

            Distribution analytical = calculator.compute(ctx);
            Distribution monteCarlo = new MonteCarloSimulator().simulate(ctx, 500_000);

            assertThat(analytical.mean()).isCloseTo(monteCarlo.mean(), within(0.02));
        }
    }
}