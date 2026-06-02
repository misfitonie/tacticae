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
  damage, critThreshold, targetType, targetWounds, feelNoPain, List<Keyword>)
- `DamageCalculator` — pipeline **per-attack unifiée** : pour chaque dé d'attaque,
  on calcule la distribution complète de dégâts (hit → wound → DW/save → FNP),
  puis `power(attacks)` pour la distribution totale
- `MonteCarloSimulator` — validateur, cross-check des résultats analytiques
- `Keyword` — sealed interface avec SustainedHits, TwinLinked, LethalHits,
  DevastatingWounds, AntiKeyword

### Keywords implémentés (V11)
- `SustainedHits(int value)` — un crit to-hit génère 1+value hits
- `TwinLinked` — relance les wounds ratés (miss only), pWound effective = 2p - p²
- `AntiKeyword(target, threshold)` — wound sur threshold+ contre target
- `LethalHits` — **V11 : choix optionnel**. Heuristique : auto-wound sauf si
  DevastatingWounds présent (auquel cas on laisse le wound roll pour préserver
  la chance de crit-to-wound)
- `DevastatingWounds` — **V11 : crit-to-wound → mortal wounds = min(D, targetWounds)**.
  Le spillover au-delà d'un modèle est perdu (cap par crit). Plus de bypass save AP-infinie.

### Mitigateur défenseur
- `feelNoPain` (champ AttackContext, 0 = aucun, sinon seuil X+) — pour chaque point
  de damage, D6, sur X+ la wound est ignorée. Modélisé via Binomial(damage, pTake).

### Convention critique
Les crits (to-hit et to-wound) sont sur 6+ naturel par défaut. `critThreshold` est
configurable (certaines abilités le baissent à 5+), mais reste indépendant des
modificateurs de hit.

### Choix tactiques non encore optimisés
- `TwinLinked + DW` : actuellement on ne reroll que sur miss. Idéalement, on
  rerollerait aussi les normal wounds si MW > damage*pFailSave (à raffiner).
- `LethalHits + DW` : on choisit globalement (toujours auto-wound ou jamais).
  Le vrai optimal est par-crit selon le contexte, mais l'heuristique "ne pas
  auto-wound si DW" est correcte dans la grande majorité des cas.

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
  - Accepte `targetWounds` et `feelNoPain` (Integer nullable, defaults 1/0)
- API REST `POST /api/armies/import` opérationnelle (parse .rosz → JSON)
  - Retourne : `ParsedUnit` (name, count, toughness, wounds, save, invSave, weapons)
  - Retourne : `ParsedWeapon` (name, count, range, attacks, skill, strength, ap, damage, keywords)
  - Détecte la save invulnérable (champs INV, INV. SV., motif `\d++` dans SV)
  - Sépare armes de tir (`range` = portée ex. `"24\""`) et CAC (`range = "Melee"`)
- Keywords moteur (V11) : SustainedHits, TwinLinked, LethalHits, DevastatingWounds,
  AntiKeyword. Mitigateur FeelNoPain. 40 tests JUnit 5 verts (cross-validation Monte Carlo).
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

### Frontend
1. **Règle V11 24.02 (DUPLICATED ABILITIES)** — si une arme a plusieurs
   instances d'un même keyword (Sustained Hits 1 ET 2 par ex.), le joueur
   choisit. UI à prévoir.
2. **Parser keywords défenseur depuis .rosz** — actuellement l'utilisateur
   doit cocher manuellement INFANTRY/VEHICLE/etc. pour que Anti-X s'applique.
   À terme, parser les keywords du datasheet pour pré-cocher (et virer la
   sélection manuelle dans la plupart des cas).

### Nouveaux keywords V11 (Phase 1 / 1.5)
- `[PSYCHIC]` — ignore les modificateurs au hit (sera utile quand on ajoutera les modifs)
- `[CLEAVE X]` — ajoute des dés selon taille de la cible si mono-cible
- `[MELTA X]` — +X damage à demi-portée
- `[LANCE]` — +1 wound si charge ce tour
- `[TORRENT]` — auto-hit

### Infrastructure
6. **Module `reference`** — modèle de données + seed BSData pour avoir les profils
   officiels GW (alternative à l'import BattleScribe)
7. **Déploiement** — Railway/Fly.io (non commencé)

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