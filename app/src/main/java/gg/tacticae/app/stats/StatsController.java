package gg.tacticae.app.stats;

import gg.tacticae.stats.domain.AttackContext;
import gg.tacticae.stats.domain.DamageCalculator;
import gg.tacticae.stats.domain.Distribution;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final DamageCalculator calculator = new DamageCalculator();

    @PostMapping("/compute")
    public ComputeResponse compute(@Valid @RequestBody ComputeRequest request) {
        AttackContext ctx = new AttackContext(
            request.attacks(),
            request.hitOn(),
            request.woundOn(),
            request.saveOn(),
            request.damage(),
            request.sustainedHits(),
            request.critThreshold()
        );

        Distribution result = calculator.compute(ctx);

        return new ComputeResponse(
            result.mean(),
            result.variance(),
            result.pmf()
        );
    }
}