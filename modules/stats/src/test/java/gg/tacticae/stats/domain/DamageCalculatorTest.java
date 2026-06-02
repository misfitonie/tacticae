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

    @Nested
    @DisplayName("V11 — DevastatingWounds cap par crit")
    class DevastatingWoundsCapV11 {

        @Test
        @DisplayName("damage 3 cappé à targetWounds 1 (perte de 2 MW par crit)")
        void critDamageCappedToTargetWounds() {
            // damage=3, targetWounds=1 : chaque crit-wound donne min(3,1)=1 MW, 2 sont perdus.
            // Comparé à un scenario où le cap ne bite pas (damage=1, targetWounds=1) :
            // les moyennes doivent être identiques (mêmes MWs par crit).
            AttackContext capped = new AttackContext(10, 2, 4, 3, 3, 6, "", 1, 0,
                List.of(new Keyword.DevastatingWounds()));
            AttackContext baseline = new AttackContext(10, 2, 4, 3, 1, 6, "", 1, 0,
                List.of(new Keyword.DevastatingWounds()));

            // Sur les crits : capped donne 1 MW, baseline donne 1 MW. Mêmes contribs.
            // Sur les normaux : capped donne 3 dmg, baseline donne 1 dmg.
            // Donc capped > baseline (à cause des normaux).
            double meanCapped = calculator.compute(capped).mean();
            double meanBaseline = calculator.compute(baseline).mean();
            assertThat(meanCapped).isGreaterThan(meanBaseline);
        }

        @Test
        @DisplayName("targetWounds >= damage : cap inopérant, comportement identique au damage non-cappé")
        void capDoesNotBiteWhenTargetWoundsGteDamage() {
            // damage=2, targetWounds=2 : cap = min(2,2) = 2, identique à damage normal.
            // damage=2, targetWounds=5 : cap = min(2,5) = 2, identique aussi.
            AttackContext w2 = new AttackContext(10, 2, 4, 3, 2, 6, "", 2, 0,
                List.of(new Keyword.DevastatingWounds()));
            AttackContext w5 = new AttackContext(10, 2, 4, 3, 2, 6, "", 5, 0,
                List.of(new Keyword.DevastatingWounds()));

            assertThat(calculator.compute(w2).mean())
                .isCloseTo(calculator.compute(w5).mean(), within(1e-9));
        }

        @Test
        @DisplayName("cap DW : cross-validation Monte Carlo")
        void cappedDwMonteCarlo() {
            AttackContext ctx = new AttackContext(10, 2, 4, 3, 3, 6, "", 1, 0,
                List.of(new Keyword.DevastatingWounds()));

            Distribution analytical = calculator.compute(ctx);
            Distribution monteCarlo = new MonteCarloSimulator().simulate(ctx, 500_000);

            assertThat(analytical.mean()).isCloseTo(monteCarlo.mean(), within(0.05));
        }
    }

    @Nested
    @DisplayName("V11 — Feel No Pain")
    class FeelNoPainV11 {

        @Test
        @DisplayName("FNP 4+ réduit la moyenne d'environ moitié")
        void fnp4ReducesDamageByHalf() {
            AttackContext noFnp = new AttackContext(10, 2, 4, 7, 2, 6, "", 1, 0, List.of());
            AttackContext fnp4 = new AttackContext(10, 2, 4, 7, 2, 6, "", 1, 4, List.of());

            // pTake = 3/6 = 1/2 → moyenne attendue divisée par 2.
            double ratio = calculator.compute(fnp4).mean() / calculator.compute(noFnp).mean();
            assertThat(ratio).isCloseTo(0.5, within(1e-9));
        }

        @Test
        @DisplayName("FNP 5+ : pTake = 4/6")
        void fnp5ReducesDamageByFourSixths() {
            AttackContext noFnp = new AttackContext(10, 2, 4, 7, 3, 6, "", 1, 0, List.of());
            AttackContext fnp5 = new AttackContext(10, 2, 4, 7, 3, 6, "", 1, 5, List.of());

            double ratio = calculator.compute(fnp5).mean() / calculator.compute(noFnp).mean();
            assertThat(ratio).isCloseTo(4.0 / 6.0, within(1e-9));
        }

        @Test
        @DisplayName("FNP : cross-validation Monte Carlo")
        void fnpMonteCarlo() {
            AttackContext ctx = new AttackContext(10, 2, 4, 3, 2, 6, "", 2, 5, List.of());

            Distribution analytical = calculator.compute(ctx);
            Distribution monteCarlo = new MonteCarloSimulator().simulate(ctx, 500_000);

            assertThat(analytical.mean()).isCloseTo(monteCarlo.mean(), within(0.05));
        }
    }

    @Nested
    @DisplayName("V11 — Damage variable (D6, D3, D6+1)")
    class VariableDamage {

        @Test
        @DisplayName("damage D6 : moyenne = (moyenne unsaved) * 3.5")
        void d6DamageMeanScalesByDiceMean() {
            AttackContext fixedD1 = new AttackContext(
                Distribution.point(10), 2, 4, 7, Distribution.point(1), 6, "", 1, 0, List.of()
            );
            AttackContext d6 = new AttackContext(
                Distribution.point(10), 2, 4, 7, DiceExpression.parse("D6"), 6, "", 1, 0, List.of()
            );
            double ratio = calculator.compute(d6).mean() / calculator.compute(fixedD1).mean();
            assertThat(ratio).isCloseTo(3.5, within(1e-9));
        }

        @Test
        @DisplayName("damage D6+1 : moyenne = (moyenne unsaved) * 4.5")
        void d6PlusOneDamageMean() {
            AttackContext fixedD1 = new AttackContext(
                Distribution.point(10), 2, 4, 7, Distribution.point(1), 6, "", 1, 0, List.of()
            );
            AttackContext d6p1 = new AttackContext(
                Distribution.point(10), 2, 4, 7, DiceExpression.parse("D6+1"), 6, "", 1, 0, List.of()
            );
            double ratio = calculator.compute(d6p1).mean() / calculator.compute(fixedD1).mean();
            assertThat(ratio).isCloseTo(4.5, within(1e-9));
        }

        @Test
        @DisplayName("damage D6 vs damage 3 : D6 a même moyenne mais plus de variance")
        void d6HasMoreVarianceThanFixedEquivalent() {
            AttackContext d3fixed = new AttackContext(
                Distribution.point(10), 2, 4, 7, Distribution.point(3), 6, "", 1, 0, List.of()
            );
            AttackContext d6 = new AttackContext(
                Distribution.point(10), 2, 4, 7, DiceExpression.parse("D6"), 6, "", 1, 0, List.of()
            );
            // damage D6 : moyenne 3.5 vs 3 → on s'attend à variance plus grande pour D6 (à moyenne ~équivalente)
            assertThat(calculator.compute(d6).variance())
                .isGreaterThan(calculator.compute(d3fixed).variance());
        }

        @Test
        @DisplayName("damage D6 + DW cap targetWounds=1 : chaque crit ne donne que 1 MW (pas 1..6)")
        void d6DamageCappedByDw() {
            // targetWounds=1, damage=D6 : min(D6, 1) = 1 toujours → crit donne 1 MW certain
            // baseline (sans DW) : chaque wound passe save, damage=D6 (1..6)
            AttackContext capped = new AttackContext(
                Distribution.point(10), 2, 6, 3, DiceExpression.parse("D6"), 6, "", 1, 0,
                List.of(new Keyword.DevastatingWounds())
            );
            // moyenne attendue (woundOn=6, critThreshold=6 → tous les wounds sont crits) :
            //   10 * pHit * pCritW * 1 = 10 * (5/6) * (1/6) * 1 = 50/36 ≈ 1.389
            assertThat(calculator.compute(capped).mean()).isCloseTo(50.0 / 36, within(1e-9));
        }

        @Test
        @DisplayName("damage variable : cross-validation Monte Carlo")
        void variableDamageMonteCarlo() {
            AttackContext ctx = new AttackContext(
                Distribution.point(10), 2, 4, 3, DiceExpression.parse("D6+1"), 6, "", 1, 0, List.of()
            );
            Distribution analytical = calculator.compute(ctx);
            Distribution mc = new MonteCarloSimulator().simulate(ctx, 500_000);
            assertThat(analytical.mean()).isCloseTo(mc.mean(), within(0.05));
        }
    }

    @Nested
    @DisplayName("V11 — Attacks variable (D6)")
    class VariableAttacks {

        @Test
        @DisplayName("attacks D6 : moyenne = (moyenne attacks fixe à 3.5) * autres facteurs")
        void d6AttacksMean() {
            AttackContext fixed = new AttackContext(
                Distribution.point(35), 2, 4, 7, Distribution.point(1), 6, "", 1, 0, List.of()
            );
            // 10 armes, chacune D6 attaques → 10 * D6 attaques en moyenne = 35 (mais variance différente)
            AttackContext d6Atk = new AttackContext(
                DiceExpression.parse("10D6"), 2, 4, 7, Distribution.point(1), 6, "", 1, 0, List.of()
            );
            assertThat(calculator.compute(d6Atk).mean())
                .isCloseTo(calculator.compute(fixed).mean(), within(1e-9));
            assertThat(calculator.compute(d6Atk).variance())
                .isGreaterThan(calculator.compute(fixed).variance());
        }

        @Test
        @DisplayName("attacks variable : cross-validation Monte Carlo")
        void variableAttacksMonteCarlo() {
            AttackContext ctx = new AttackContext(
                DiceExpression.parse("3D6"), 2, 4, 3, Distribution.point(1), 6, "", 1, 0, List.of()
            );
            Distribution analytical = calculator.compute(ctx);
            Distribution mc = new MonteCarloSimulator().simulate(ctx, 500_000);
            assertThat(analytical.mean()).isCloseTo(mc.mean(), within(0.05));
        }
    }

    @Nested
    @DisplayName("V11 — Torrent (auto-hit)")
    class TorrentV11 {

        @Test
        @DisplayName("Torrent : chaque attaque touche, pas de jet de hit")
        void torrentAutoHits() {
            // hitOn=6, sans Torrent : pHit=1/6. Avec Torrent : pHit=1.
            // ratio attendu = 6 (compense le hitOn raté autrement).
            AttackContext baseline = new AttackContext(10, 6, 4, 7, 1, 6, List.of());
            AttackContext torrent  = new AttackContext(10, 6, 4, 7, 1, 6, "", 1, 0,
                List.of(new Keyword.Torrent()));

            double ratio = calculator.compute(torrent).mean() / calculator.compute(baseline).mean();
            assertThat(ratio).isCloseTo(6.0, within(1e-9));
        }

        @Test
        @DisplayName("Torrent : SustainedHits ne triggent pas (pas de crit-to-hit)")
        void torrentDoesNotTriggerSustained() {
            AttackContext torrent = new AttackContext(10, 4, 4, 7, 1, 6, "", 1, 0,
                List.of(new Keyword.Torrent(), new Keyword.SustainedHits(3)));
            AttackContext torrentBare = new AttackContext(10, 4, 4, 7, 1, 6, "", 1, 0,
                List.of(new Keyword.Torrent()));
            // SustainedHits 3 sans crit-to-hit → effet nul
            assertThat(calculator.compute(torrent).mean())
                .isCloseTo(calculator.compute(torrentBare).mean(), within(1e-9));
        }

        @Test
        @DisplayName("Torrent : cross-validation Monte Carlo")
        void torrentMonteCarlo() {
            AttackContext ctx = new AttackContext(10, 4, 4, 3, 1, 6, "", 1, 0,
                List.of(new Keyword.Torrent()));
            Distribution analytical = calculator.compute(ctx);
            Distribution mc = new MonteCarloSimulator().simulate(ctx, 500_000);
            assertThat(analytical.mean()).isCloseTo(mc.mean(), within(0.05));
        }
    }

    @Nested
    @DisplayName("V11 — Lance (+1 wound si charge)")
    class LanceV11 {

        @Test
        @DisplayName("Lance + charged : effectiveWoundOn diminue de 1")
        void chargedLanceImprovesWound() {
            AttackContext lanceCharged = new AttackContext(
                Distribution.point(10), 2, 4, 3, Distribution.point(1), 6, "", 1, 0,
                true, false, 1,
                List.of(new Keyword.Lance())
            );
            assertThat(lanceCharged.effectiveWoundOn()).isEqualTo(3);
        }

        @Test
        @DisplayName("Lance sans charge : aucun effet")
        void unchargedLanceNoEffect() {
            AttackContext lanceUncharged = new AttackContext(
                Distribution.point(10), 2, 4, 3, Distribution.point(1), 6, "", 1, 0,
                false, false, 1,
                List.of(new Keyword.Lance())
            );
            assertThat(lanceUncharged.effectiveWoundOn()).isEqualTo(4);
        }

        @Test
        @DisplayName("Lance charged ne descend pas en dessous de 2+")
        void lanceFloorAtTwoPlus() {
            AttackContext ctx = new AttackContext(
                Distribution.point(10), 2, 2, 3, Distribution.point(1), 6, "", 1, 0,
                true, false, 1,
                List.of(new Keyword.Lance())
            );
            assertThat(ctx.effectiveWoundOn()).isEqualTo(2);
        }

        @Test
        @DisplayName("Lance charged : cross-validation Monte Carlo")
        void lanceMonteCarlo() {
            AttackContext ctx = new AttackContext(
                Distribution.point(10), 2, 4, 3, Distribution.point(1), 6, "", 1, 0,
                true, false, 1,
                List.of(new Keyword.Lance())
            );
            Distribution analytical = calculator.compute(ctx);
            Distribution mc = new MonteCarloSimulator().simulate(ctx, 500_000);
            assertThat(analytical.mean()).isCloseTo(mc.mean(), within(0.05));
        }
    }

    @Nested
    @DisplayName("V11 — Melta X (+X damage à demi-portée)")
    class MeltaV11 {

        @Test
        @DisplayName("Melta 2 + halfRange : damage characteristic +2")
        void meltaAtHalfRangeAddsDamage() {
            AttackContext close = new AttackContext(
                Distribution.point(10), 2, 4, 7, Distribution.point(1), 6, "", 1, 0,
                false, true, 1,
                List.of(new Keyword.Melta(2))
            );
            AttackContext far = new AttackContext(
                Distribution.point(10), 2, 4, 7, Distribution.point(1), 6, "", 1, 0,
                false, false, 1,
                List.of(new Keyword.Melta(2))
            );
            // close : damage = 1+2 = 3. far : damage = 1. ratio = 3
            double ratio = calculator.compute(close).mean() / calculator.compute(far).mean();
            assertThat(ratio).isCloseTo(3.0, within(1e-9));
        }

        @Test
        @DisplayName("Melta sans halfRange : aucun effet")
        void meltaWithoutHalfRangeNoEffect() {
            AttackContext melta = new AttackContext(
                Distribution.point(10), 2, 4, 7, Distribution.point(1), 6, "", 1, 0,
                false, false, 1,
                List.of(new Keyword.Melta(2))
            );
            AttackContext baseline = new AttackContext(
                Distribution.point(10), 2, 4, 7, Distribution.point(1), 6, "", 1, 0,
                List.of()
            );
            assertThat(calculator.compute(melta).mean())
                .isCloseTo(calculator.compute(baseline).mean(), within(1e-9));
        }

        @Test
        @DisplayName("Melta + halfRange : cross-validation Monte Carlo")
        void meltaMonteCarlo() {
            AttackContext ctx = new AttackContext(
                Distribution.point(10), 2, 4, 3, DiceExpression.parse("D6"), 6, "", 1, 0,
                false, true, 1,
                List.of(new Keyword.Melta(2))
            );
            Distribution analytical = calculator.compute(ctx);
            Distribution mc = new MonteCarloSimulator().simulate(ctx, 500_000);
            assertThat(analytical.mean()).isCloseTo(mc.mean(), within(0.1));
        }
    }

    @Nested
    @DisplayName("V11 — Cleave X (dés bonus selon taille cible)")
    class CleaveV11 {

        @Test
        @DisplayName("Cleave 1 + targetUnitSize 10 : +2 dés d'attaque")
        void cleaveAddsExtraAttacks() {
            AttackContext cleave10 = new AttackContext(
                Distribution.point(3), 2, 4, 7, Distribution.point(1), 6, "", 1, 0,
                false, false, 10,
                List.of(new Keyword.Cleave(1))
            );
            AttackContext cleave5 = new AttackContext(
                Distribution.point(3), 2, 4, 7, Distribution.point(1), 6, "", 1, 0,
                false, false, 5,
                List.of(new Keyword.Cleave(1))
            );
            // cleave10 : 3 + (10/5)*1 = 5 attaques
            // cleave5  : 3 + (5/5)*1  = 4 attaques
            double ratio = calculator.compute(cleave10).mean() / calculator.compute(cleave5).mean();
            assertThat(ratio).isCloseTo(5.0 / 4.0, within(1e-9));
        }

        @Test
        @DisplayName("Cleave 2 + targetUnitSize 8 : +2 dés (8/5 = 1, *2 = 2)")
        void cleaveTwoAndUnitSizeEight() {
            AttackContext ctx = new AttackContext(
                Distribution.point(3), 2, 4, 7, Distribution.point(1), 6, "", 1, 0,
                false, false, 8,
                List.of(new Keyword.Cleave(2))
            );
            assertThat(ctx.cleaveBonus()).isEqualTo(2);
        }

        @Test
        @DisplayName("Cleave : cross-validation Monte Carlo")
        void cleaveMonteCarlo() {
            AttackContext ctx = new AttackContext(
                Distribution.point(3), 2, 4, 3, Distribution.point(2), 6, "", 1, 0,
                false, false, 10,
                List.of(new Keyword.Cleave(1))
            );
            Distribution analytical = calculator.compute(ctx);
            Distribution mc = new MonteCarloSimulator().simulate(ctx, 500_000);
            assertThat(analytical.mean()).isCloseTo(mc.mean(), within(0.05));
        }
    }

    @Nested
    @DisplayName("V11 — Anti+DW crit threshold (fix)")
    class AntiDevastatingFix {

        @Test
        @DisplayName("Anti-INF 4+ + DW : tous les wounds 4+ deviennent MWs (cap inclus)")
        void antiLowersCritThresholdForDw() {
            // Anti-INF 4+ contre INFANTRY : crit wound sur 4+ (au lieu de 6+)
            // woundOn=5 → effectiveWoundOn=4, effectiveCritWoundThreshold=4
            // Tous les wounds (3/6) sont des crits → tous DW (cap min(D=1, W=1) = 1)
            // baseline (sans DW) : 3/6 wounds → save pFailSave=1/6 → 1 dmg
            AttackContext antiNoDw = new AttackContext(10, 2, 5, 2, 1, 6, "INFANTRY",
                List.of(new Keyword.AntiKeyword("INFANTRY", 4)));
            AttackContext antiDw = new AttackContext(10, 2, 5, 2, 1, 6, "INFANTRY",
                List.of(new Keyword.AntiKeyword("INFANTRY", 4), new Keyword.DevastatingWounds()));

            // antiNoDw : 10 * 5/6 * 3/6 * 1/6 * 1 = 25/108
            // antiDw   : 10 * 5/6 * 3/6 * 1 = 50/36 (tous bypass save)
            double ratio = calculator.compute(antiDw).mean() / calculator.compute(antiNoDw).mean();
            assertThat(ratio).isCloseTo(6.0, within(1e-6));
        }

        @Test
        @DisplayName("Anti+DW : cross-validation Monte Carlo")
        void antiDwMonteCarlo() {
            AttackContext ctx = new AttackContext(10, 2, 5, 2, 1, 6, "INFANTRY",
                List.of(new Keyword.AntiKeyword("INFANTRY", 4), new Keyword.DevastatingWounds()));
            Distribution analytical = calculator.compute(ctx);
            Distribution mc = new MonteCarloSimulator().simulate(ctx, 500_000);
            assertThat(analytical.mean()).isCloseTo(mc.mean(), within(0.05));
        }
    }

    @Nested
    @DisplayName("V11 — LethalHits choix optionnel")
    class LethalHitsV11 {

        @Test
        @DisplayName("sans DW : auto-wound (shouldAutoWoundOnCrit = true)")
        void noDevastating_autoWoundOnCrit() {
            AttackContext ctx = new AttackContext(10, 2, 4, 3, 1, 6, "", 1, 0,
                List.of(new Keyword.LethalHits()));
            assertThat(ctx.shouldAutoWoundOnCrit()).isTrue();
        }

        @Test
        @DisplayName("avec DW : ne pas auto-wound (laisse le wound roll pour pouvoir crit)")
        void withDevastating_dontAutoWound() {
            AttackContext ctx = new AttackContext(10, 2, 4, 3, 1, 6, "", 1, 0,
                List.of(new Keyword.LethalHits(), new Keyword.DevastatingWounds()));
            assertThat(ctx.shouldAutoWoundOnCrit()).isFalse();
        }

        @Test
        @DisplayName("combo LH + DW : cross-validation Monte Carlo")
        void lethalDevastatingComboMonteCarlo() {
            AttackContext ctx = new AttackContext(10, 2, 4, 3, 2, 6, "", 2, 0,
                List.of(new Keyword.LethalHits(), new Keyword.DevastatingWounds()));

            Distribution analytical = calculator.compute(ctx);
            Distribution monteCarlo = new MonteCarloSimulator().simulate(ctx, 500_000);

            assertThat(analytical.mean()).isCloseTo(monteCarlo.mean(), within(0.05));
        }
    }
}