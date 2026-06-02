package gg.tacticae.app.stats;

import gg.tacticae.stats.domain.AttackContext;
import gg.tacticae.stats.domain.DamageCalculator;
import gg.tacticae.stats.domain.DiceExpression;
import gg.tacticae.stats.domain.Distribution;
import gg.tacticae.stats.domain.Keyword;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        int targetWounds = request.targetWounds() != null ? request.targetWounds() : 1;
        int feelNoPain = request.feelNoPain() != null ? request.feelNoPain() : 0;

        Distribution attacks = DiceExpression.parse(request.attacks());
        Distribution damage = DiceExpression.parse(request.damage());

        AttackContext ctx = new AttackContext(
            attacks,
            request.hitOn(),
            request.woundOn(),
            request.saveOn(),
            damage,
            request.critThreshold(),
            targetType,
            targetWounds,
            feelNoPain,
            keywords
        );

        Distribution result = calculator.compute(ctx);

        return new ComputeResponse(result.mean(), result.variance(), result.pmf());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadExpression(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
