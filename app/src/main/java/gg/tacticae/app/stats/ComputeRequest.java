package gg.tacticae.app.stats;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ComputeRequest(
    @NotBlank             String attacks,
    @Min(2) @Max(6)       int hitOn,
    @Min(2) @Max(7)       int woundOn,
    @Min(2) @Max(7)       int saveOn,
    @NotBlank             String damage,
    @Min(2) @Max(6)       int critThreshold,
    @Min(0) @Max(5)       int sustainedHits,
    boolean twinLinked,
    boolean lethalHits,
    boolean devastatingWounds,
    String antiTarget,
    @Min(2) @Max(6)       int antiThreshold,
    Integer targetWounds,
    Integer feelNoPain,
    boolean torrent,
    boolean lance,
    Integer melta,            // null/0 = pas de Melta, sinon X (extra damage)
    Integer cleave,           // null/0 = pas de Cleave, sinon X (dés par 5 modèles)
    boolean charged,          // l'unité attaquante a chargé ce tour
    boolean halfRange,        // la cible est à demi-portée
    Integer targetUnitSize    // taille de l'unité cible (pour Cleave)
) {}
