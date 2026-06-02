package gg.tacticae.stats.domain;

public sealed interface Keyword permits
    Keyword.SustainedHits,
    Keyword.TwinLinked,
    Keyword.LethalHits,
    Keyword.DevastatingWounds,
    Keyword.AntiKeyword,
    Keyword.Torrent,
    Keyword.Lance,
    Keyword.Melta,
    Keyword.Cleave,
    Keyword.Psychic {

    record SustainedHits(int value) implements Keyword {}
    record TwinLinked() implements Keyword {}
    record LethalHits() implements Keyword {}
    record DevastatingWounds() implements Keyword {}
    record AntiKeyword(String target, int threshold) implements Keyword {}
    record Torrent() implements Keyword {}
    record Lance() implements Keyword {}
    record Melta(int extraDamage) implements Keyword {}
    record Cleave(int extraDicePerFiveModels) implements Keyword {}
    record Psychic() implements Keyword {}
}