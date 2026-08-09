# KjobsUltimate - Suivi prod et reste a faire

Date: 05/07/2026

## Etat actuel

| Systeme | Etat | Notes |
|---|---:|---|
| Storage SQLite/MySQL | OK prototype avance | Tables joueurs, jobs, slots, quetes, rankings. A tester en charge MySQL. |
| Jobs YAML | OK | 6 jobs charges: mineur, farmer, hunter, pretorien, artisan, pilleur. |
| XP/levels/rewards | OK prototype | XP, level-up, rewards commandes, claim rewards. A equilibrer. |
| Slots/jobs debloques | OK | Slots par niveau global, favoris, leave avec reset progression. |
| GUI jobs | OK corrige | Bug bouton favori corrige le 05/07/2026. |
| GUI quests | OK base configurable | Dans `gui/quests.yml`; support `material`, `data`, `name`, `lore`, `click_actions`, `nbt`, `cit`. |
| Quetes permanentes | OK prototype | Types MINE, HARVEST, KILL, CRAFT, PVP_KILL, TNT, DYNAMITE, EAT, SMELT, FISH, ENCHANT, PLACE, TAME. |
| HUD actionbar/bossbar/title/toast | OK terrain | Bossbar wither invisible validee, settings joueur OK. |
| Tab header/footer | OK | Sections configurables, PAPI, staff groups. |
| Tab virtuel colonnes | OK prototype terrain | `PACKED_COLUMNS`, skins signes Mojang valides, debug OK. |
| Hooks | OK prototype | Vault, PlaceholderAPI, Kcraft, Kfaction, KStacker. |
| Admin commands | OK partiel avance | Status, reload, addxp, forcejoin/forceleave, quest admin, tab debug. |
| Docs | En cours | Plusieurs docs existent, mais il faut une reference finale unifiee. |

## Correctif applique - bouton favori GUI

Probleme constate:

- Dans le detail d'un job, le bouton affiche `Definir favori`.
- Au clic, le joueur recevait le message `Ce job est deja debloque`.

Cause:

- Les items `detail.items.action.locked`, `unlocked`, `favorite` utilisent tous le meme slot.
- La detection de clic cherchait recursivement et tombait toujours sur `action.locked` en premier.
- Le visuel etait donc correct, mais l'action executee etait celle du mauvais etat.

Correction:

- Le clic du detail job choisit maintenant explicitement la section `action.<etat>` selon l'etat reel du job.

Fichier:

- `src/main/java/me/krunsh/kjobultimate/gui/GuiManager.java`

## GUI quests - niveau actuel

Le GUI des quetes est maintenant modifiable ici:

- `src/main/resources/gui/quests.yml`
- section racine `quests`

Configurable actuellement:

- titre du GUI;
- taille;
- filler;
- slots des quetes;
- slots des filtres job;
- item filtre toutes les quetes;
- item filtre par job;
- item quete en cours;
- item quete claimable;
- item quete deja claim;
- item vide;
- previous/next/refresh/back;
- `material`;
- `data`;
- `amount`;
- `name`;
- `lore`;
- `click_actions`;
- `deny_actions`;
- `cooldown`;
- `nbt`;
- `cit` / `cit_key`.

Exemple CIT:

```yaml
quests:
  items:
    quest:
      claimable:
        material: PAPER
        cit: "quest_claimable"
        name: "&a{quest}"
```

Exemple NBT:

```yaml
quests:
  items:
    quest:
      in_progress:
        material: PAPER
        nbt:
          sparrowmc-item: "quest_in_progress"
          quest-id: "{quest_id}"
```

## GUI quests - reste a faire pour etre niveau Kgui

| Besoin | Etat | Recommandation |
|---|---:|---|
| Fichier dedie `gui/quests.yml` | OK | Charge par `GuiManager.loadAll()`. |
| Templates par type de quete | A faire | Exemple: `templates.MINE`, `templates.SMELT`, `templates.EAT`. |
| Overrides par job | A faire | Exemple: affichage special mineur/farmer/pilleur. |
| Overrides par quest id | A faire | Exemple: `overrides.mineur_stone_1.item`. |
| Sections libres facon Kgui | A faire | Permettre d'ajouter/supprimer des boutons sans code. |
| Conditions avancees | Partiel | Requirements simples OK; a etendre si besoin. |
| Pagination flexible | OK simple | A rendre plus configurable si gros volume de quetes. |
| Preview/debug GUI | A faire | Commande admin pour lister slots/actions resolus. |

## Reste a faire avant prod

### Priorite haute

- Tester le jar sur serveur dev avec le correctif favori.
- Verifier tous les boutons GUI: jobs, detail, leave confirm, settings, top, quests.
- Ameliorer le GUI quests vers une structure encore plus type Kgui.
- Tester MySQL reel: reconnect, pool, latence, sauvegarde async, backup.
- Tester 50-100 joueurs simules au minimum pour tab/hud/db.
- Valider que les listeners ne donnent pas d'XP en creatif/spectator/abus.
- Verifier toutes les actions jobs et quetes avec les noms Bukkit 1.8.8.

### Priorite moyenne

- Ajouter debug GUI cible: slot clique, section resolue, actions executees.
- Ajouter doc finale `CONFIG-REFERENCE` pour tous les fichiers YAML.
- Ajouter plus d'exemples de quetes: smelt, eat, fish, enchant, place, tame.
- Ajouter commandes admin de maintenance DB/backup plus confortables.
- Ajouter export historique admin si besoin.
- Verifier compat Kfaction en conditions PvP reelles.

### Priorite basse

- Nettoyer les anciens fallback hardcodes GUI une fois toute la config stable.
- Ajouter themes GUI preconfigures.
- Ajouter stats/perf console plus detaillees.
- Ajouter tests unitaires sur parsing config/actions si le projet devient plus gros.

## Prochaine etape recommandee

1. Tester le nouveau jar et confirmer que `Definir favori` affiche bien `Job favori: ...`.
2. Ensuite faire l'etape GUI quests avancee:
   - ajouter templates par type/job/quest;
   - ajouter une doc de configuration claire.
