package gg.tacticae.armies.domain;

import java.util.List;

public record ParsedWeapon(
    String name,
    int count,
    String range,   // "Melee" ou portée ex. "24\""
    String attacks,
    int skill,
    int strength,
    int ap,
    String damage,
    List<String> keywords
) {}
