package gg.tacticae.stats.domain;

import java.util.List;

public record AttackContext(
    Distribution attacks,
    int hitOn,
    int woundOn,
    int saveOn,
    Distribution damage,
    int critThreshold,
    String targetType,
    int targetWounds,
    int feelNoPain,
    boolean charged,
    boolean halfRange,
    int targetUnitSize,
    List<Keyword> keywords
) {
    public AttackContext {
        if (attacks == null) throw new IllegalArgumentException("attacks null");
        if (damage == null) throw new IllegalArgumentException("damage null");
        if (hitOn < 2 || hitOn > 6) throw new IllegalArgumentException("hitOn out of range");
        if (woundOn < 2 || woundOn > 7) throw new IllegalArgumentException("woundOn out of range");
        if (saveOn < 2 || saveOn > 7) throw new IllegalArgumentException("saveOn out of range");
        if (critThreshold < 2 || critThreshold > 6) throw new IllegalArgumentException("crit out of range");
        if (targetType == null) throw new IllegalArgumentException("targetType null");
        if (targetWounds < 1) throw new IllegalArgumentException("targetWounds < 1");
        if (feelNoPain != 0 && (feelNoPain < 2 || feelNoPain > 6)) throw new IllegalArgumentException("feelNoPain out of range");
        if (targetUnitSize < 1) throw new IllegalArgumentException("targetUnitSize < 1");
        keywords = List.copyOf(keywords);
    }

    public AttackContext(Distribution attacks, int hitOn, int woundOn, int saveOn, Distribution damage, int critThreshold, String targetType, int targetWounds, int feelNoPain, List<Keyword> keywords) {
        this(attacks, hitOn, woundOn, saveOn, damage, critThreshold, targetType, targetWounds, feelNoPain, false, false, 1, keywords);
    }

    public AttackContext(int attacks, int hitOn, int woundOn, int saveOn, int damage, int critThreshold, String targetType, int targetWounds, int feelNoPain, List<Keyword> keywords) {
        this(Distribution.point(attacks), hitOn, woundOn, saveOn, Distribution.point(damage), critThreshold, targetType, targetWounds, feelNoPain, false, false, 1, keywords);
    }

    public AttackContext(int attacks, int hitOn, int woundOn, int saveOn, int damage, int critThreshold, String targetType, List<Keyword> keywords) {
        this(Distribution.point(attacks), hitOn, woundOn, saveOn, Distribution.point(damage), critThreshold, targetType, 1, 0, false, false, 1, keywords);
    }

    public AttackContext(int attacks, int hitOn, int woundOn, int saveOn, int damage, int critThreshold, List<Keyword> keywords) {
        this(Distribution.point(attacks), hitOn, woundOn, saveOn, Distribution.point(damage), critThreshold, "", 1, 0, false, false, 1, keywords);
    }

    public double pMiss() { return (hitOn - 1) / 6.0; }
    public double pCritHit() { return (7 - critThreshold) / 6.0; }
    public double pNormalHit() {
        return Math.max(0, (7 - hitOn) / 6.0 - pCritHit());
    }
    public double pWound() { return (7 - woundOn) / 6.0; }
    public double pFailSave() {
        if (saveOn >= 7) return 1.0;
        return (saveOn - 1) / 6.0;
    }

    public int sustainedHitsValue() {
        return keywords.stream()
            .filter(k -> k instanceof Keyword.SustainedHits)
            .map(k -> ((Keyword.SustainedHits) k).value())
            .findFirst()
            .orElse(0);
    }

    public boolean hasTwinLinked() {
        return keywords.stream().anyMatch(k -> k instanceof Keyword.TwinLinked);
    }

    public boolean hasLethalHits() {
        return keywords.stream().anyMatch(k -> k instanceof Keyword.LethalHits);
    }

    public boolean hasDevastatingWounds() {
        return keywords.stream().anyMatch(k -> k instanceof Keyword.DevastatingWounds);
    }

    public boolean hasTorrent() {
        return keywords.stream().anyMatch(k -> k instanceof Keyword.Torrent);
    }

    public boolean hasLance() {
        return keywords.stream().anyMatch(k -> k instanceof Keyword.Lance);
    }

    public boolean hasCleave() {
        return keywords.stream().anyMatch(k -> k instanceof Keyword.Cleave);
    }

    public int meltaBonus() {
        if (!halfRange) return 0;
        return keywords.stream()
            .filter(k -> k instanceof Keyword.Melta)
            .mapToInt(k -> ((Keyword.Melta) k).extraDamage())
            .sum();
    }

    public int cleaveBonus() {
        return keywords.stream()
            .filter(k -> k instanceof Keyword.Cleave)
            .mapToInt(k -> ((Keyword.Cleave) k).extraDicePerFiveModels() * (targetUnitSize / 5))
            .sum();
    }

    private int antiThresholdIfMatching() {
        return keywords.stream()
            .filter(k -> k instanceof Keyword.AntiKeyword ak && ak.target().equals(targetType))
            .mapToInt(k -> ((Keyword.AntiKeyword) k).threshold())
            .min()
            .orElse(Integer.MAX_VALUE);
    }

    public int effectiveWoundOn() {
        int base = woundOn;
        int anti = antiThresholdIfMatching();
        if (anti < base) base = anti;
        if (hasLance() && charged) base = Math.max(2, base - 1);
        return base;
    }

    // V11: Anti-X Y+ makes "unmodified Y+ a critical wound". So when Anti matches,
    // the crit-wound threshold is min(critThreshold, antiThreshold), not just critThreshold.
    public int effectiveCritWoundThreshold() {
        int anti = antiThresholdIfMatching();
        return Math.min(critThreshold, anti);
    }

    public double pWoundEffective() {
        double p = (7 - effectiveWoundOn()) / 6.0;
        if (hasTwinLinked()) return 2 * p - p * p;
        return p;
    }

    public Distribution effectiveDamage() {
        int bonus = meltaBonus();
        return bonus == 0 ? damage : damage.map(d -> d + bonus);
    }

    // V11: a critical wound from DevastatingWounds inflicts min(D, targetWounds) MWs.
    public Distribution devastatingWoundDamage() {
        final int w = targetWounds;
        return effectiveDamage().map(d -> Math.min(d, w));
    }

    public boolean hasFeelNoPain() {
        return feelNoPain != 0;
    }

    public boolean shouldAutoWoundOnCrit() {
        return hasLethalHits() && !hasDevastatingWounds();
    }

    public Distribution effectiveAttacks() {
        int bonus = cleaveBonus();
        return bonus == 0 ? attacks : attacks.map(n -> n + bonus);
    }
}
