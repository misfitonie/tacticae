package gg.tacticae.stats.domain;

import java.util.List;

public record AttackContext(
    int attacks,
    int hitOn,
    int woundOn,
    int saveOn,
    int damage,
    int critThreshold,
    String targetType,
    int targetWounds,
    int feelNoPain,
    List<Keyword> keywords
) {
    public AttackContext {
        if (attacks < 0) throw new IllegalArgumentException("attacks < 0");
        if (hitOn < 2 || hitOn > 6) throw new IllegalArgumentException("hitOn out of range");
        if (woundOn < 2 || woundOn > 7) throw new IllegalArgumentException("woundOn out of range");
        if (saveOn < 2 || saveOn > 7) throw new IllegalArgumentException("saveOn out of range");
        if (damage < 1) throw new IllegalArgumentException("damage < 1");
        if (critThreshold < 2 || critThreshold > 6) throw new IllegalArgumentException("crit out of range");
        if (targetType == null) throw new IllegalArgumentException("targetType null");
        if (targetWounds < 1) throw new IllegalArgumentException("targetWounds < 1");
        if (feelNoPain != 0 && (feelNoPain < 2 || feelNoPain > 6)) throw new IllegalArgumentException("feelNoPain out of range");
        keywords = List.copyOf(keywords);
    }

    public AttackContext(int attacks, int hitOn, int woundOn, int saveOn, int damage, int critThreshold, String targetType, List<Keyword> keywords) {
        this(attacks, hitOn, woundOn, saveOn, damage, critThreshold, targetType, 1, 0, keywords);
    }

    public AttackContext(int attacks, int hitOn, int woundOn, int saveOn, int damage, int critThreshold, List<Keyword> keywords) {
        this(attacks, hitOn, woundOn, saveOn, damage, critThreshold, "", 1, 0, keywords);
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

    public int effectiveWoundOn() {
        return keywords.stream()
            .filter(k -> k instanceof Keyword.AntiKeyword ak && ak.target().equals(targetType))
            .mapToInt(k -> ((Keyword.AntiKeyword) k).threshold())
            .filter(t -> t < woundOn)
            .min()
            .orElse(woundOn);
    }

    public double pWoundEffective() {
        double p = (7 - effectiveWoundOn()) / 6.0;
        if (hasTwinLinked()) return 2 * p - p * p;
        return p;
    }

    // V11: a critical wound from DevastatingWounds inflicts min(D, targetWounds) MWs,
    // because mortal-wound spillover is now capped at 1 model per critical wound.
    public int devastatingWoundDamage() {
        return Math.min(damage, targetWounds);
    }

    public boolean hasFeelNoPain() {
        return feelNoPain != 0;
    }

    // V11: LethalHits is now optional. Heuristic: when DevastatingWounds is also
    // present, we DON'T auto-wound — letting the wound roll happen preserves the
    // chance of a critical wound (which triggers DW's capped mortal wounds).
    // Without DW, auto-wound is always strictly better than (or equal to) rolling.
    public boolean shouldAutoWoundOnCrit() {
        return hasLethalHits() && !hasDevastatingWounds();
    }
}
