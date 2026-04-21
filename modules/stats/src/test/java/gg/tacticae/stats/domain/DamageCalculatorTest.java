package gg.tacticae.stats.domain;

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

        private final AttackContext ctx = new AttackContext(10, 2, 6, 3, 1, 0, 6);

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
            AttackContext baseline = new AttackContext(10, 2, 6, 3, 1, 0, 6);
            AttackContext sustained = new AttackContext(10, 2, 6, 3, 1, 1, 6);

            double ratio = calculator.compute(sustained).mean()
                         / calculator.compute(baseline).mean();

            assertThat(ratio).isCloseTo(1.20, within(0.01));
        }

        @Test
        @DisplayName("Sustained Hits 2 augmente la moyenne de ~40%")
        void sustainedHitsTwoIncreaseMeanByFortyPercent() {
            AttackContext baseline = new AttackContext(10, 2, 6, 3, 1, 0, 6);
            AttackContext sustained = new AttackContext(10, 2, 6, 3, 1, 2, 6);

            double ratio = calculator.compute(sustained).mean()
                         / calculator.compute(baseline).mean();

            assertThat(ratio).isCloseTo(1.40, within(0.01));
        }
    }

    @Nested
    @DisplayName("Multiplicateur de damage")
    class DamageMultiplier {

        @Test
        @DisplayName("damage 2 double la moyenne")
        void damageTwoDoublesMean() {
            AttackContext d1 = new AttackContext(10, 2, 6, 3, 1, 0, 6);
            AttackContext d2 = new AttackContext(10, 2, 6, 3, 2, 0, 6);

            assertThat(calculator.compute(d2).mean())
                .isCloseTo(2 * calculator.compute(d1).mean(), within(1e-9));
        }

        @Test
        @DisplayName("damage 2 quadruple la variance")
        void damageTwoQuadruplesVariance() {
            AttackContext d1 = new AttackContext(10, 2, 6, 3, 1, 0, 6);
            AttackContext d2 = new AttackContext(10, 2, 6, 3, 2, 0, 6);

            assertThat(calculator.compute(d2).variance())
                .isCloseTo(4 * calculator.compute(d1).variance(), within(1e-9));
        }
    }

    @Nested
    @DisplayName("Cross-validation Monte Carlo")
    class MonteCarloCrossValidation {

        @Test
        @DisplayName("analytique vs Monte Carlo — écart moyenne < 0.02")
        void analyticalMatchesMonteCarlo() {
            AttackContext ctx = new AttackContext(10, 2, 6, 3, 1, 1, 6);

            Distribution analytical = calculator.compute(ctx);
            Distribution monteCarlo = new MonteCarloSimulator().simulate(ctx, 500_000);

            assertThat(analytical.mean())
                .isCloseTo(monteCarlo.mean(), within(0.02));
        }
    }
}