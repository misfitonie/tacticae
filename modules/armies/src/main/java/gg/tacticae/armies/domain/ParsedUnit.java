package gg.tacticae.armies.domain;

import java.util.List;

public record ParsedUnit(
    String name,
    int count,
    int toughness,
    int wounds,
    int save,
    int invSave,   // 7 = aucune save invulnérable
    List<String> keywords,
    List<ParsedWeapon> weapons
) {}
