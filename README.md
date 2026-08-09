# KjobUltimate — Index et Vue d'Ensemble

> Plugin de jobs 100% configurable pour SparrowMC (KhopeSpigot 1.8.8)
> Version cible : 1.0.0 | Java 8 | Maven

---

## 🗂 Structure du Projet

```
KjobsUltimate/
├── README.md                    ← ce fichier
├── TODO-AVANT-CODE.md           ← checklist complète avant de coder ← LIRE EN PREMIER
├── libs/                        ← JARs serveur (Vault, PAPI, Kgui, etc.)
│
├── docs/
│   ├── architecture/            ← Vue globale, flux XP, schéma DB
│   │   ├── ARCHITECTURE-GLOBALE.md
│   │   ├── FLUX-XP-LEVELUP.md
│   │   └── DONNEES-JOUEUR-SCHEMA.md
│   │
│   ├── systemes/                ← Un doc par système fonctionnel
│   │   ├── ANTI-ABUSE.md
│   │   ├── HUD-BOSSBAR-ACTIONBAR.md
│   │   ├── JOB-SLOTS-SYSTEM.md
│   │   ├── QUETES-SYSTEM.md
│   │   └── SCOREBOARD-TAB.md
│   │
│   ├── gui/                     ← Tout ce qui concerne les interfaces
│   │   ├── GUI-VUE-GLOBALE.md
│   │   ├── GUI-BACKGROUNDS-CUSTOM.md
│   │   └── REVIEW-GUI-HORSE-COMPLET.md
│   │
│   ├── config/                  ← Structure et référence des fichiers YAML
│   │   ├── CONFIG-FICHIERS-STRUCTURE.md
│   │   └── CONFIG-REFERENCE.md
│   │
│   └── dev/                     ← Documents pour le développement
│       ├── PRE-DEV-CHECKLIST.md  ← Checklist détaillée par item
│       ├── PLAN-IMPLEMENTATION.md ← Ordre des phases de développement
│       ├── INTEGRATION-MAP.md    ← Hooks inter-plugins (Kstacker, Kcraft, Kgui…)
│       ├── QUESTIONS-LIST.md     ← Toutes les décisions de design (toutes prises ✅)
│       ├── EDGE-CASES.md         ← Comportements limites documentés
│       ├── FAISABILITE-JOBS-SYSTEM.md
│       └── CONSOLE-LOGGING.md   ← Logger coloré KjobLogger (ANSI, PandaSpigot)
│
└── notes/                       ← Notes internes, idées, historique
    ├── CREATIVITE-COMPLETE.md
    ├── historique-discussion-sonnet.md
    └── idees-chat-avec-chatgpt.md
```

---

## Résumé du Plugin

**KjobUltimate** est le système de jobs principal du serveur SparrowMC. Il gère :
- **6 jobs** : Mineur, Farmer, Hunter, Prétorien, Artisan et Pilleur
- **Système de slots configurable** : 1 slot par défaut, déblocage progressif au level 5, 10, 15... (désactivable)
- **Quêtes permanentes V1** avec progression et récompenses transactionnelles
- **HUD temps réel** : BossBar NMS (fake wither), ActionBar XP, Achievement level up
- **Tab Scoreboard** : Header/Footer NMS configurable dans `tab.yml`
- **GUI via Kgui** : ContentProviderAPI — menus paginés délégués à Kgui
- **Anti-abuse** : SilkTouch, cultures immatures, cooldown position, anti-PvP farm
- **100% configurable** : Config splitée par responsabilité (1 fichier par job, 1 fichier par quêtes-job)

---

## État d'implémentation

Ce tableau distingue volontairement le code présent des systèmes seulement
décrits dans les documents de conception.

| Système | État réel |
|---|---|
| Six métiers et gains d'XP actuels | Implémenté ; validation en jeu continue |
| Slots de métiers | Implémenté et configurable |
| HUD BossBar/ActionBar, achievement, TAB et GUI KGUI | Implémenté ; validation multi-client/menus requise |
| Quêtes permanentes configurées dans `quests.yml` | Implémenté, y compris les cibles `materiau:data` 1.8 (`PRISMARINE:2`) |
| Récompenses de quêtes transactionnelles | Implémenté et testé sur SQLite |
| Quêtes daily/weekly et resets automatiques | Retiré du cahier des charges Volkaria |
| Chaînes séquentielles de quêtes | Implémenté et testé : une étape active par chaîne, chaînes parallèles, pause métier et conditions de niveau |
| Nouveaux types non présents dans `quests.yml` | Planifié |
| SQLite réel | Testé localement et dans le profil intégré de 44 JAR ; charge 750 joueurs à réaliser |
| MySQL/Hikari réel | Compile uniquement ; aucune base MySQL réelle validée |
| Intégration KStacker | API `StackKillResult` explicite ; seules les metadata appartenant à KStacker sont acceptées |

## Décisions Techniques Actées

| Décision | Choix | Raison |
|---|---|---|
| Stockage par défaut | **SQLite WAL** | Simple à exploiter, écritures sérialisées et I/O disque maîtrisées |
| GUI | **Kgui ContentProviderAPI** | Config YAML menus, pas de code GUI custom |
| Slots jobs | **Configurable (défaut=1)** | Déblocage progressif, désactivable |
| Premier join | **Choix joueur** | GUI sélection s'ouvre automatiquement |
| Config files | **1 fichier par job + 1 par quêtes-job** | Lisible, maintenable, hot-reload |
| Kchat tab | **KjobsUltimate propriétaire header/footer** | Kchat gère équipes/prefixes — pas de conflit |
| Kstacker kills | **META_KILL_MULTIPLIER = min(3, stackCount)** | Anti-dupe cap absolu à 3 |
| Console logging | **KjobLogger + ANSI** | Affichage coloré premium dans PandaSpigot |

---

## Chaînes de quêtes

Une chaîne explicite contient des étapes ordonnées. Une seule étape progresse
dans chaque chaîne, tandis que plusieurs chaînes différentes restent actives
en parallèle. La complétion déverrouille automatiquement l'étape suivante et
la progression des étapes terminées est conservée.

Le catalogue YAML est construit et validé intégralement avant de remplacer le
snapshot actif. Un doublon de clé, une collision de casse, un stage invalide,
une clé inconnue ou un type custom non déclaré fait refuser le candidat sans
interrompre le catalogue déjà chargé.

Une action ne reporte pas son surplus vers l'étape suivante pendant le même
événement. Les sauvegardes ordinaires fusionnent les compteurs de manière
monotone afin qu'une ancienne écriture asynchrone ne puisse pas diminuer une
progression plus récente.

Les chaînes actuelles sont `mineur_stone` et `farmer_wheat`, chacune avec trois
étapes. Les autres quêtes historiques restent des chaînes unitaires pour
préserver leur comportement.

---

## Récompenses de quête transactionnelles

La récupération d'une récompense ne repose plus uniquement sur le booléen
`quest_progress.claimed`. Une table durable `quest_reward_claims`, unique par
couple joueur/quête, suit les états :

`PREPARED → DISTRIBUTING → DISTRIBUTED`

Une erreur pendant ou après une commande produit l'état `FAILED`. Les états
`PREPARED`, `DISTRIBUTING` et `FAILED` ne sont jamais rejoués
automatiquement : une commande de console peut avoir eu un effet avant
l'erreur, et la relancer aveuglément créerait une duplication.

Ordre d'une récupération normale :

1. verrou RAM contre les doubles clics ;
2. réservation SQL atomique ;
3. passage SQL en `DISTRIBUTING` ;
4. fermeture de l'état RAM `claimed` ;
5. attribution de l'XP et exécution des commandes sur le thread principal ;
6. écriture transactionnelle de `quest_progress.claimed=1` et
   `quest_reward_claims.status=DISTRIBUTED`.

Au démarrage, toute ligne non terminale est signalée dans la console pour
vérification staff. Le reset administratif explicite d'une quête efface le
registre et la progression dans une même transaction ; il est refusé tant
qu'une distribution du même joueur et de la même quête est en cours.

Requête d'audit utile :

```sql
SELECT uuid, quest_id, player_name, status, reserved_at, updated_at, last_error
FROM quest_reward_claims
WHERE status <> 'DISTRIBUTED';
```

---

## Sons — Durée Côté Client

La durée d'un son est entièrement déterminée par le fichier `.ogg`. Le serveur envoie juste le nom du son et le joueur joue le fichier en entier.
