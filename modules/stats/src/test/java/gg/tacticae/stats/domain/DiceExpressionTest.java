package gg.tacticae.stats.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DiceExpressionTest {

    @Test
    @DisplayName("constante : \"5\" → point(5)")
    void constant() {
        assertThat(DiceExpression.parse("5").mean()).isCloseTo(5.0, within(1e-9));
        assertThat(DiceExpression.parse("5").pmf()).containsEntry(5, 1.0);
    }

    @Test
    @DisplayName("D6 : moyenne 3.5, 6 valeurs uniformes")
    void d6() {
        Distribution d = DiceExpression.parse("D6");
        assertThat(d.mean()).isCloseTo(3.5, within(1e-9));
        assertThat(d.pmf()).hasSize(6);
        for (int i = 1; i <= 6; i++) {
            assertThat(d.pmf().get(i)).isCloseTo(1.0 / 6, within(1e-9));
        }
    }

    @Test
    @DisplayName("D3 : moyenne 2, 3 valeurs uniformes")
    void d3() {
        Distribution d = DiceExpression.parse("D3");
        assertThat(d.mean()).isCloseTo(2.0, within(1e-9));
        assertThat(d.pmf()).hasSize(3);
    }

    @Test
    @DisplayName("D6+1 : moyenne 4.5, valeurs 2..7")
    void d6PlusOne() {
        Distribution d = DiceExpression.parse("D6+1");
        assertThat(d.mean()).isCloseTo(4.5, within(1e-9));
        assertThat(d.pmf().keySet()).containsExactlyInAnyOrder(2, 3, 4, 5, 6, 7);
    }

    @Test
    @DisplayName("3D6 : moyenne 10.5, valeurs 3..18")
    void threeD6() {
        Distribution d = DiceExpression.parse("3D6");
        assertThat(d.mean()).isCloseTo(10.5, within(1e-9));
        assertThat(d.pmf().keySet()).contains(3, 18);
    }

    @Test
    @DisplayName("2D3+1 : moyenne 5, valeurs 3..7")
    void twoD3PlusOne() {
        Distribution d = DiceExpression.parse("2D3+1");
        assertThat(d.mean()).isCloseTo(5.0, within(1e-9));
        assertThat(d.pmf().keySet()).containsExactlyInAnyOrder(3, 4, 5, 6, 7);
    }

    @Test
    @DisplayName("insensible à la casse et aux espaces")
    void caseAndWhitespace() {
        assertThat(DiceExpression.parse("d6").mean()).isCloseTo(3.5, within(1e-9));
        assertThat(DiceExpression.parse(" 2 D 6 + 1 ").mean()).isCloseTo(8.0, within(1e-9));
    }

    @Test
    @DisplayName("expressions invalides rejetées")
    void invalid() {
        assertThatThrownBy(() -> DiceExpression.parse("foo")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DiceExpression.parse("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DiceExpression.parse(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DiceExpression.parse("D1")).isInstanceOf(IllegalArgumentException.class);
    }
}
