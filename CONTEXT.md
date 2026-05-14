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
- **Frontend** : Angular 19 standalone, Angular Material 19 M3, Chart.js
- **Infra** : Docker Compose pour le dev local, CI GitHub Actions

## Architecture
Monolithe modulaire — bounded contexts séparés dans un seul deployable.

### Modules
- `app` — point d'entrée Spring Boot, controllers REST, config
- `modules/stats` — moteur de calcul analytique, POJO purs sans Spring
- `modules/shared` — utilitaires partagés (vide pour l'instant)
- `modules/reference` — catalogue BSData : factions, unités, armes (à créer)
- `modules/armies` — import .rosz BattleScribe, parsing XML (**opérationnel**)
- `frontend/` — Angular 19, port 4200 en dev, proxy /api → backend 8080

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

### Backend
- API REST `POST /api/stats/compute` opérationnelle
- API REST `POST /api/armies/import` opérationnelle (parse .rosz → JSON)
  - Retourne : `ParsedUnit` (name, count, toughness, wounds, save, invSave, weapons)
  - Retourne : `ParsedWeapon` (name, count, range, attacks, skill, strength, ap, damage, keywords)
  - Détecte la save invulnérable (champs INV, INV. SV., motif `\d++` dans SV)
  - Sépare armes de tir (`range` = portée ex. `"24\""`) et CAC (`range = "Melee"`)
- Keywords moteur : SustainedHits, TwinLinked implémentés
- 17+ tests JUnit 5 verts
- CI GitHub Actions active sur chaque push main
- Flyway migration V1 appliquée
- Docker Compose : `docker compose up` démarre app + postgres

### Frontend (Angular 19)
- Page `/import` : import de deux listes .rosz (mon armée + armée adverse)
- Layout 3 colonnes : mon armée | résultats (sticky) | armée adverse
- Cartes unités avec stats (T/W/Sv/Inv), badge ×N si doublons
- Sélection attaquant (épée) / défenseur (bouclier) par clic, désélection par reclic
- Détail armes au clic sur une card : tableau séparé Tir / Corps à corps
  (colonne Portée uniquement pour les armes de tir)
- Auto-calcul dès qu'un attaquant et un défenseur sont sélectionnés
  - Calcul parallèle sur toutes les armes de l'attaquant via forkJoin
  - Affiche la meilleure arme (moyenne dégâts la plus haute)
  - Pipeline de dégâts : Attaques → Touches → Blessures → Saves → Dégâts
  - Graphe PMF (Chart.js) de la distribution de dégâts
  - Écart-type avec tooltip explicatif
- Keywords transmis au backend : Sustained Hits, Twin-Linked, Lethal Hits, Devastating Wounds

## Reste à faire (Phase 1)

### Moteur de calcul (priorité)
1. **`AntiKeyword(target, threshold)`** — wound sur threshold+ contre une cible typée
   (ex. Anti-Infantry 4+ = wound sur 4+ contre Infantry)
2. **`LethalHits`** — crit to-hit = auto-wound, skip wound phase
3. **`DevastatingWounds`** — crit to-wound bypass la save (pipeline multi-canal,
   un canal normal + un canal "mortal wounds" convergeant sur la distribution finale)

### Frontend
4. **Sélection croisée** — actuellement on ne peut attaquer qu'avec mon armée vs adversaire.
   Permettre n'importe quelle combinaison (unité adverse peut attaquer mon unité).
5. **Anti-Target UI** — le champ `antiTarget` du ComputeRequest n'est pas encore
   alimenté depuis les keywords de l'arme (ex. "Anti-Chaos 2+" → antiTarget="Chaos", threshold=2)
6. **Dégâts variables** — les weapons avec `attacks` ou `damage` en "D6"/"D3" etc.
   ne sont pas encore résolus : `parseInt(weapon.attacks) || 1` retourne 1 pour "D6".
   À traiter côté backend (distribution sur D6) plutôt que frontend.

### Infrastructure
7. **Module `reference`** — modèle de données + seed BSData pour avoir les profils
   officiels GW (alternative à l'import BattleScribe)
8. **Déploiement** — Railway/Fly.io (non commencé)

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