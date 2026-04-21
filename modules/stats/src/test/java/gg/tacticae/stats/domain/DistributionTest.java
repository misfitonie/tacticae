package gg.tacticae.stats.domain;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DistributionTest {

    @Test
    @DisplayName("point(n) crée une distribution certaine sur n")
    void pointDistribution() {
        Distribution d = Distribution.point(3);

        assertThat(d.mean()).isEqualTo(3.0);
        assertThat(d.pmf().get(3)).isEqualTo(1.0);
        assertThat(d.pmf()).hasSize(1);
    }

    @Test
    @DisplayName("of() rejette une distribution dont les probas ne somment pas à 1")
    void invalidProbabilitiesThrow() {
        assertThatThrownBy(() -> Distribution.of(Map.of(0, 0.4, 1, 0.4)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sum to 1.0");
    }

    @Test
    @DisplayName("convolve de deux point distributions donne leur somme")
    void convolvePointDistributions() {
        Distribution d1 = Distribution.point(2);
        Distribution d2 = Distribution.point(3);

        Distribution result = d1.convolve(d2);

        assertThat(result.mean()).isEqualTo(5.0);
        assertThat(result.pmf().get(5)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("power(0) donne point(0) quelle que soit la distribution")
    void powerZeroGivesPointZero() {
        Distribution d = Distribution.of(Map.of(0, 0.5, 1, 0.5));
        Distribution result = d.power(0);
        assertThat(result.pmf()).isEqualTo(Distribution.point(0).pmf());
    }

    @Test
    @DisplayName("mean d'un dé à 6 faces équitable vaut 3.5")
    void fairDieMean() {
        Distribution die = Distribution.of(Map.of(
                1, 1.0 / 6, 2, 1.0 / 6, 3, 1.0 / 6,
                4, 1.0 / 6, 5, 1.0 / 6, 6, 1.0 / 6
        ));

        assertThat(die.mean()).isCloseTo(3.5, within(1e-9));
    }

    @Test
    @DisplayName("probabilityAtLeast est cohérente avec la PMF")
    void probabilityAtLeast() {
        Distribution d = Distribution.of(Map.of(0, 0.5, 1, 0.3, 2, 0.2));

        assertThat(d.probabilityAtLeast(1)).isCloseTo(0.5, within(1e-9));
        assertThat(d.probabilityAtLeast(0)).isCloseTo(1.0, within(1e-9));
        assertThat(d.probabilityAtLeast(3)).isCloseTo(0.0, within(1e-9));
    }
}
