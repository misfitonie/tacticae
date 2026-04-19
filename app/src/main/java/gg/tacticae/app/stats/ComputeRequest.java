package gg.tacticae.app.stats;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ComputeRequest(
    @Min(0) @Max(1000) int attacks,
    @Min(2) @Max(6)    int hitOn,
    @Min(2) @Max(7)    int woundOn,
    @Min(2) @Max(7)    int saveOn,
    @Min(1) @Max(20)   int damage,
    @Min(0) @Max(5)    int sustainedHits,
    @Min(2) @Max(6)    int critThreshold
) {}