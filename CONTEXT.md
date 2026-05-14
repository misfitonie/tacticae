# Tacticae — Context

## Vision produit
Outil communautaire pour joueurs Warhammer 40K, orienté stats et tracking.
Site : tacticae.gg (domaine cible)

## Feature principale (Phase 1)
Calculateur de stats analytique — répondre à "combien de blessures cette unité
inflige-t-elle à cette autre unité ?" avec une distribution complète de probabilités,
pas juste une moyenne.

## Roadmap
- **Phase 1** : calculateur de stats + import de listes BattleScribe (.rosz)
- **Phase 2** : comptes utilisateurs + sauvegarde de listes
- **Phase 3** : tracking de parties + dataviz profil + matchmaking + ELO
- **Phase 4** : IA règles (RAG sur corpus officiel GW)

## Stack technique
- **Backend** : Java 21, Spring Boot 3.4, Gradle 8.11 multi-module
- **Base de données** : PostgreSQL 16, Flyway pour les migrations
- **Frontend** : Next.js 15 (à venir)
- **Infra** : Docker Compose pour le dev local, CI GitHub Actions

## Architecture
Monolithe modulaire — bounded contexts séparés dans un seul deployable.

### Modules
- `app` — point d'entrée Spring Boot, controllers REST, config
- `modules/stats` — moteur de calcul analytique, POJO purs sans Spring
- `modules/shared` — utilitaires partagés (vide pour l'instant)
- `modules/reference` — catalogue BSData : factions, unités, armes (à créer)
- `modules/armies` — import .rosz BattleScribe, parsing XML (à créer)

### Règle de dépendances inter-modules
- `app` dépend de tous les modules
- `armies` dépend de `reference`
- `stats` dépend de `reference` uniquement (pas de `armies`)
- `reference` ne dépend de rien sauf `shared`
- Tous dépendent de `shared`

## Moteur de calcul (modules/stats)

### Classes principales
- `Distribution` — map {valeur -> probabilité}, immutable, avec convolve/power/map
- `AttackContext` — paramètres d'une attaque (attacks, hitOn, woundOn, saveOn,
  damage, critThreshold, List<Keyword>)
- `DamageCalculator` — pipeline Hit → Wound → Save → Damage
- `MonteCarloSimulator` — validateur, cross-check des résultats analytiques
- `Keyword` — sealed interface avec SustainedHits, TwinLinked, LethalHits,
  DevastatingWounds, AntiKeyword

### Keywords implémentés
- `SustainedHits(int value)` — un crit to-hit génère 1+value hits
- `TwinLinked` — relance les wounds ratés, pWound effective = 2p - p²

### Keywords à implémenter
- `AntiKeyword(String target, int threshold)` — wound sur threshold+ contre target
- `LethalHits` — crit to-hit = auto-wound, skip wound phase
- `DevastatingWounds` — crit to-wound bypass la save (nécessite pipeline multi-canal)

### Convention critique
Les crits (to-hit et to-wound) sont toujours sur 6+ naturel en V10,
indépendamment des modificateurs de hit. Ne pas modifier critThreshold
sauf si une abilité l'indique explicitement.

## Conventions de code
- Package racine : `gg.tacticae`
- Les entités du domaine stats sont des records Java 21 immuables
- Pas d'annotations Spring dans `modules/stats/domain`
- Chaque nouveau keyword doit avoir :
  1. Son implémentation dans `DamageCalculator`
  2. Sa gestion dans `MonteCarloSimulator` (pour cross-validation)
  3. Ses tests dans `DamageCalculatorTest` avec validation Monte Carlo

## État actuel
- API REST `POST /api/stats/compute` opérationnelle
- 17 tests JUnit 5 verts
- CI GitHub Actions active sur chaque push main
- Flyway migration V1 appliquée
- Docker Compose : `docker compose up` démarre app + postgres

## Données de référence
- Source : BSData GitHub `github.com/BSData/wh40k-10e` (XML, format BattleScribe)
- Format d'import utilisateur : `.rosz` (archive ZIP contenant du XML)
- Le module `reference` sera seedé depuis BSData via un script de parsing XML

## Décisions importantes
- Calcul analytique (pas Monte Carlo) pour la précision et la rapidité
- Monte Carlo gardé comme validateur de cross-check uniquement
- Monolithe modulaire plutôt que microservices (projet solo, simplicité)
- BattleScribe/.rosz comme format d'import canonique (standard communautaire)
- Pas de Kubernetes (homelab uniquement), déploiement Railway/Fly.io prévu