package gg.tacticae.stats.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DiceExpression {

    private static final Pattern DICE = Pattern.compile("^(\\d+)?D(\\d+)(?:\\+(\\d+))?$");
    private static final Pattern CONSTANT = Pattern.compile("^(\\d+)$");

    private DiceExpression() {}

    // Parses W40K dice expressions into a Distribution:
    //   "1", "10"        → constant
    //   "D6", "D3"       → 1d6, 1d3 (uniform)
    //   "3D6", "2D3"     → k convolutions of dN
    //   "D6+1"           → 1d6 shifted +1
    //   "3D6+2"          → 3d6 shifted +2
    // Whitespace and case are ignored.
    public static Distribution parse(String expr) {
        if (expr == null) throw new IllegalArgumentException("expression null");
        String e = expr.replaceAll("\\s", "").toUpperCase();
        if (e.isEmpty()) throw new IllegalArgumentException("expression empty");

        Matcher dice = DICE.matcher(e);
        if (dice.matches()) {
            int k = dice.group(1) != null ? Integer.parseInt(dice.group(1)) : 1;
            int n = Integer.parseInt(dice.group(2));
            int mod = dice.group(3) != null ? Integer.parseInt(dice.group(3)) : 0;
            if (k < 1) throw new IllegalArgumentException("dice count must be >= 1: " + expr);
            if (n < 2) throw new IllegalArgumentException("die faces must be >= 2: " + expr);
            Distribution kDice = uniformDie(n).power(k);
            return mod == 0 ? kDice : kDice.map(x -> x + mod);
        }

        Matcher constant = CONSTANT.matcher(e);
        if (constant.matches()) {
            return Distribution.point(Integer.parseInt(e));
        }

        throw new IllegalArgumentException("Cannot parse dice expression: " + expr);
    }

    private static Distribution uniformDie(int n) {
        Map<Integer, Double> pmf = new HashMap<>();
        for (int i = 1; i <= n; i++) pmf.put(i, 1.0 / n);
        return Distribution.of(pmf);
    }
}
