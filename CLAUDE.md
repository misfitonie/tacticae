# Instructions pour Claude Code

## Lire en premier
Consulte `CONTEXT.md` pour le contexte complet du projet.
Consulte `docs/adr/` pour les décisions architecturales.

## Commandes utiles
- Build : `./gradlew build`
- Tests stats : `./gradlew :modules:stats:test`
- Tests tous modules : `./gradlew test`
- Démarrer en dev : `docker compose up` puis `./gradlew :app:bootRun`

## Conventions à respecter
- Aucune annotation Spring dans `modules/stats/domain`
- Tout nouveau keyword doit avoir son implémentation dans `DamageCalculator`,
  sa gestion dans `MonteCarloSimulator`, et ses tests avec validation Monte Carlo
- Les migrations Flyway suivent le format `V{n}__{description}.sql`
- Les commits suivent la convention : `feat:`, `fix:`, `test:`, `docs:`, `chore:`

## Prochaines tâches
1. Keyword `AntiKeyword` dans le moteur de calcul
2. Keyword `LethalHits`
3. Keyword `DevastatingWounds` (pipeline multi-canal)
4. Module `reference` — modèle de données + seed BSData
5. Module `armies` — parser .rosz BattleScribe

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them. Don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" 
If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it. Don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]

Strong success criteria let you loop independently. 
Weak criteria ("make it work") require constant clarification.