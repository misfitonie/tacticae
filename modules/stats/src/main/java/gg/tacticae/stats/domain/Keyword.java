package gg.tacticae.stats.domain;

public sealed interface Keyword permits
    Keyword.SustainedHits,
    Keyword.TwinLinked,
    Keyword.LethalHits,
    Keyword.DevastatingWounds,
    Keyword.AntiKeyword {

    record SustainedHits(int value) implements Keyword {}
    record TwinLinked() implements Keyword {}
    record LethalHits() implements Keyword {}
    record DevastatingWounds() implements Keyword {}
    record AntiKeyword(String target, int threshold) implements Keyword {}
}