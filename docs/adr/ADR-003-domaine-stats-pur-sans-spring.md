# ADR-003 — Domaine stats en Java pur sans Spring

## Statut
Accepté

## Contexte
Le moteur de calcul de stats est la feature centrale de Tacticae Phase 1.
Il implémente un pipeline analytique complexe (Hit → Wound → Save → Damage)
avec gestion de keywords (Sustained Hits, Twin-linked, Lethal Hits,
Devastating Wounds, Anti-X).

La question est de savoir si ce domaine doit utiliser les abstractions
Spring (beans, annotations, injection de dépendances) ou rester en
Java pur.

## Décision
Le package `modules/stats/domain` est composé exclusivement de classes
Java 21 pures : records immuables, sealed interfaces, classes finales.
Aucune annotation Spring (`@Component`, `@Service`, `@Autowired`) n'est
autorisée dans ce package.

Spring est utilisé uniquement dans les couches `application` et `web`
du module `app`.

Cette règle est vérifiable via ArchUnit (à mettre en place).

## Conséquences
**Positives**
- Testabilité maximale : les tests instancient les classes directement,
  sans contexte Spring, sans mocks de framework
- Temps d'exécution des tests < 1s pour l'ensemble du domaine
- Le domaine est portable : réutilisable dans un CLI, un batch,
  ou un autre framework sans modification
- Force une réflexion claire sur ce qui est "métier" vs "infrastructure"

**Négatives**
- Mapping DTO ↔ domaine nécessaire dans la couche application
- Plus de boilerplate que si on injectait directement les entités JPA

## Alternatives rejetées
- **Spring beans dans le domaine** : couplage fort au framework, tests
  plus lents, perte de portabilité, obscurcissement de la logique métier
  derrière des annotations
- **Architecture hexagonale complète** : trop de boilerplate pour un
  projet solo en phase initiale. L'approche actuelle (domaine pur +
  couches dans app) offre 80% des bénéfices pour 20% de la complexité.
  L'hexagonal reste une option d'évolution naturelle.