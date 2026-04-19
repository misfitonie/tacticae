package gg.tacticae.app.stats;

import java.util.Map;

public record ComputeResponse(
    double mean,
    double variance,
    Map<Integer, Double> pmf
) {}