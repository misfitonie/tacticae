# ADR-002 — BattleScribe/.rosz comme format d'import canonique

## Statut
Accepté

## Contexte
Tacticae permet aux joueurs d'importer leurs listes d'armée pour alimenter
le calculateur de stats et le tracking de parties. Plusieurs options
d'intégration ont été considérées.

New Recruit est l'outil de list-building dominant dans la communauté
compétitive francophone. L'option naturelle serait une intégration directe.
Cependant New Recruit n'expose pas d'API publique officielle.

BSData est une communauté open source qui maintient les données de jeu 40K
au format XML sur GitHub (github.com/BSData/wh40k-10e). Ce format est le
standard de facto de l'écosystème : BattleScribe, New Recruit, Administratum,
et Yellowscribe l'utilisent tous. Les fichiers .rosz sont des archives ZIP
contenant ce XML.

## Décision
Le format d'import canonique de Tacticae est le fichier `.rosz` BattleScribe.

Le catalogue de référence (factions, unités, armes, profils) est seedé
depuis le dépôt BSData GitHub via un script de parsing XML.

## Conséquences
**Positives**
- Compatible avec tous les outils majeurs de l'écosystème en une seule
  intégration
- Pas de dépendance à une API tierce propriétaire
- Le corpus BSData est maintenu par la communauté et mis à jour à chaque
  nouvelle édition
- Le fichier .rosz original est stocké en base (bytea) pour re-parsing futur

**Négatives**
- Le XML BSData est complexe et inconsistant selon les factions
- Le parser devra gérer les cas limites (unités multi-profils, armes
  optionnelles, règles spéciales)
- BSData est dans une zone grise légale avec Games Workshop

## Alternatives rejetées
- **Import via URL New Recruit** : pas d'API officielle, scraping fragile
  et contraire aux CGU
- **Saisie manuelle** : friction trop élevée, pas d'adoption communautaire
- **Upload PDF** : parsing peu fiable, pas de structure exploitable