package gg.tacticae.stats.domain;

public record AttackContext(
    int attacks,
    int hitOn,
    int woundOn,
    int saveOn,
    int damage,
    int sustainedHits,
    int critThreshold
) {
    public AttackContext {
        if (attacks < 0) throw new IllegalArgumentException("attacks < 0");
        if (hitOn < 2 || hitOn > 6) throw new IllegalArgumentException("hitOn out of range");
        if (woundOn < 2 || woundOn > 7) throw new IllegalArgumentException("woundOn out of range");
        if (saveOn < 2 || saveOn > 7) throw new IllegalArgumentException("saveOn out of range");
        if (damage < 1) throw new IllegalArgumentException("damage < 1");
        if (sustainedHits < 0) throw new IllegalArgumentException("sustainedHits < 0");
        if (critThreshold < 2 || critThreshold > 6) throw new IllegalArgumentException("crit out of range");
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
}