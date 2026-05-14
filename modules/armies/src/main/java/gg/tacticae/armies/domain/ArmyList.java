package gg.tacticae.armies.domain;

import java.util.List;

public record ArmyList(
    String name,
    String factionName,
    List<ParsedUnit> units
) {}
