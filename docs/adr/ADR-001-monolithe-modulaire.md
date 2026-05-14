# ADR-001 — Monolithe modulaire plutôt que microservices

## Statut
Accepté

## Contexte
Tacticae est un projet solo avec un budget temps de 10-20h/semaine.
Le projet a des ambitions de scalabilité à long terme (matchmaking, ELO,
IA règles) mais part d'une base simple (calculateur de stats).

Trois options architecturales ont été considérées :
- Monolithe classique (tout dans un seul module)
- Monolithe modulaire (bounded contexts séparés, un seul deployable)
- Microservices (services indépendants, déploiements séparés)

## Décision
Monolithe modulaire avec Gradle multi-module.

Chaque bounded context (`stats`, `reference`, `armies`, `users`, `games`)
est un module Gradle indépendant avec ses propres classes, tests, et
dépendances déclarées explicitement. La communication inter-modules
passe par des interfaces publiques, jamais par accès direct aux internals.

## Conséquences
**Positives**
- Déploiement simple (un seul JAR, un seul conteneur)
- Coût d'infrastructure minimal
- Discipline de séparation des contextes dès le début
- Extraction future en microservice possible si besoin

**Négatives**
- Discipline requise pour respecter les frontières de modules
- Gradle multi-module plus verbeux qu'un projet simple

## Alternatives rejetées
- **Microservices** : overhead opérationnel disproportionné pour un projet solo.
  Kubernetes, service discovery, tracing distribué — trop coûteux sans équipe.
- **Monolithe classique** : pas de séparation des contextes, dette technique
  rapide dès qu'on ajoute le matchmaking et l'IA règles.