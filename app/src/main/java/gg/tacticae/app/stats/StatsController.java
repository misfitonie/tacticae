package gg.tacticae.app.stats;

import gg.tacticae.stats.domain.AttackContext;
import gg.tacticae.stats.domain.DamageCalculator;
import gg.tacticae.stats.domain.Distribution;
import gg.tacticae.stats.domain.Keyword;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final DamageCalculator calculator = new DamageCalculator();

    @PostMapping("/compute")
    public ComputeResponse compute(@Valid @RequestBody ComputeRequest request) {
        List<Keyword> keywords = new ArrayList<>();
        if (request.sustainedHits() > 0)
            keywords.add(new Keyword.SustainedHits(request.sustainedHits()));
        if (request.twinLinked())
            keywords.add(new Keyword.TwinLinked());
        if (request.lethalHits())
            keywords.add(new Keyword.LethalHits());
        if (request.devastatingWounds())
            keywords.add(new Keyword.DevastatingWounds());
        if (request.antiTarget() != null && !request.antiTarget().isBlank())
            keywords.add(new Keyword.AntiKeyword(request.antiTarget(), request.antiThreshold()));

        String targetType = request.antiTarget() != null ? request.antiTarget() : "";

        AttackContext ctx = new AttackContext(
            request.attacks(),
            request.hitOn(),
            request.woundOn(),
            request.saveOn(),
            request.damage(),
            request.critThreshold(),
            targetType,
            keywords
        );

        Distribution result = calculator.compute(ctx);

        return new ComputeResponse(result.mean(), result.variance(), result.pmf());
    }
}
