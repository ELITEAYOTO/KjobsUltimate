# KjobsUltimate - GUI configurable interne

> Etat au 03/07/2026 : base d'actions YAML ajoutee dans `GuiManager`.
> Objectif : rapprocher les GUI internes de la flexibilite Kgui sans rendre Kgui obligatoire.

## Ce qui est fait

| Element | Etat | Notes |
|---|---:|---|
| `slot` unique | OK | Compatible avec les anciens fichiers. |
| `slots` multiples | OK | Accepte liste YAML, nombre unique, string avec virgules/ranges (`0-8,45-53`). |
| `display_name` | OK | Alias de `name`, utile pour recopier des patterns Kgui. |
| `click_actions` | OK | Actions generiques au clic. |
| `left_click_actions` | OK | Prioritaire sur `click_actions` pour clic gauche. |
| `right_click_actions` | OK | Prioritaire sur `click_actions` pour clic droit. |
| `shift_click_actions` | OK | Fallback pour shift gauche/droit. |
| `shift_left_click_actions` | OK | Specifique shift clic gauche. |
| `shift_right_click_actions` | OK | Specifique shift clic droit. |
| `middle_click_actions` | OK | Specifique clic molette. |
| Fallback ancien comportement | OK | Si aucune action YAML n'est definie, les handlers internes continuent. |
| Validation au boot/reload | OK | Slots invalides, materials invalides et actions inconnues log en console. |
| `cit` / `cit_key` | OK | Ajoute `sparrowmc-item` comme Kgui. |
| `nbt` custom | OK | String, int, long, bool, double. |
| `hide_attributes` | OK | Cache les attributs item pour les items decoratifs/CIT. |
| `click_requirements` | OK | Conditions AND avant execution des actions. |
| `deny_actions` | OK | Actions executees si requirements refuses. |
| `cooldown` | OK | Cooldown par joueur et par item, en secondes. |
| `cooldown_deny_actions` | OK | Actions optionnelles si le cooldown est actif. |

## Actions supportees

| Action | Exemple | Effet |
|---|---|---|
| `[open]` | `[open] jobs` | Ouvre un menu interne. |
| `[open]` detail | `[open] detail:{job_id}` | Ouvre le detail du job clique. |
| `[back]` | `[back]` | Retour logique selon le menu courant. |
| `[close]` | `[close]` | Ferme l'inventaire. |
| `[message]` | `[message] &aTexte` | Envoie un message au joueur. |
| `[player]` | `[player] jobs info` | Execute une commande joueur. |
| `[joueur]` | `[joueur] jobs info` | Alias FR de `[player]`. |
| `[console]` | `[console] give {player} diamond 1` | Execute une commande console. |
| `[command]` | `[command] give {player} diamond 1` | Alias console, meme convention que les rewards. |
| `[sound]` | `[sound] CLICK 1.0 1.0` | Joue un son Bukkit 1.8 au joueur. |
| `[refresh]` | `[refresh]` | Recharge le menu courant. |
| `[favorite]` | `[favorite] {job_id}` | Definit le job clique comme favori. |
| `[unlock]` | `[unlock] {job_id}` | Debloque/rejoint le job si un slot metier est disponible. |
| `[join]` | `[join] {job_id}` | Alias de `[unlock]`. |
| `[leave_confirm]` | `[leave_confirm] {job_id}` | Ouvre le menu de confirmation de leave. |
| `[leave_confirmed]` | `[leave_confirmed] {job_id}` | Confirme le leave et reset la progression du job. |
| `[toggle_hud]` | `[toggle_hud]` | Active/desactive le HUD jobs du joueur. |
| `[hud_toggle]` | `[hud_toggle]` | Alias de `[toggle_hud]`. |
| `[toggle_bossbar]` | `[toggle_bossbar]` | Active/desactive uniquement la BossBar jobs du joueur. |
| `[bossbar_toggle]` | `[bossbar_toggle]` | Alias de `[toggle_bossbar]`. |
| `[toggle_actionbar]` | `[toggle_actionbar]` | Active/desactive uniquement l'ActionBar XP jobs du joueur. |
| `[actionbar_toggle]` | `[actionbar_toggle]` | Alias de `[toggle_actionbar]`. |
| `[quests]` | `[quests] all` | Ouvre/rafraichit la vue des quetes (`all`, jobId, `previous`, `next`, `refresh`). |
| `[quest]` | `[quest] mineur` | Alias de `[quests]`. |
| `[quest_claim]` | `[quest_claim] {quest_id}` | Claim la recompense d'une quete terminee. |
| `[claim_quest]` | `[claim_quest] {quest_id}` | Alias de `[quest_claim]`. |

## Requirements de clic

Les `click_requirements` fonctionnent en mode AND : toutes les conditions doivent passer pour executer les actions.

```yaml
click_requirements:
  - "permission: kjobsultimate.use"
  - "job_unlocked: {job_id}"
  - "global_level_min: 10"
deny_actions:
  - "[sound] VILLAGER_NO 1.0 1.0"
  - "[message] {prefix}&cTu ne peux pas faire ca."
cooldown: 2
cooldown_deny_actions:
  - "[sound] VILLAGER_NO 1.0 1.0"
```

Requirements supportes :

| Requirement | Exemple | Effet |
|---|---|---|
| `permission` | `permission: kjobsultimate.admin` | Le joueur doit avoir la permission. |
| `perm` | `perm: kjobsultimate.use` | Alias de `permission`. |
| `no_permission` | `no_permission: kjobsultimate.staff` | Le joueur ne doit pas avoir la permission. |
| `!permission` | `!permission: kjobsultimate.staff` | Alias de `no_permission`. |
| `has_jobs` | `has_jobs` | Le joueur a au moins un job debloque. |
| `no_jobs` | `no_jobs` | Le joueur n'a aucun job debloque. |
| `job_unlocked` | `job_unlocked: {job_id}` | Le job est debloque/actif. |
| `job_active` | `job_active: {job_id}` | Alias de `job_unlocked`. |
| `job_locked` | `job_locked: {job_id}` | Le job n'est pas debloque. |
| `favorite_job` | `favorite_job: {job_id}` | Le job est le favori du joueur. |
| `job_favorite` | `job_favorite: {job_id}` | Alias de `favorite_job`. |
| `global_level_min` | `global_level_min: 15` | Niveau global jobs minimum. |
| `slots_min` | `slots_min: 3` | Nombre de slots metiers debloques minimum. |
| `level_min` | `level_min: {job_id} 5` | Niveau minimum sur un job. Si seul un nombre est donne, utilise le job courant. |
| `level_max` | `level_max: {job_id} 20` | Niveau maximum sur un job. |

## Menus internes ciblables avec `[open]`

| Target | Menu |
|---|---|
| `home`, `main`, `kjobs_home` | Menu principal joueur. |
| `jobs`, `list`, `kjobs_jobs` | Liste/selection des jobs. |
| `settings`, `parametres`, `kjobs_settings` | Parametres joueur. |
| `top`, `classement`, `classements` | Menu classements. |
| `quests`, `quest`, `quetes`, `quete` | Menu des quetes. |
| `quests:{job_id}` | Menu des quetes filtre sur un job. |
| `detail:{job_id}` | Detail d'un job. |
| `job:{job_id}` | Alias detail d'un job. |
| `confirm_leave:{job_id}` | Confirmation de leave. |
| `kjobs_detail_mineur` | Compat partielle avec les anciens noms Kgui. |

## Placeholders disponibles dans les actions

| Placeholder | Disponible quand | Valeur |
|---|---|---|
| `{player}` | Toujours | Nom du joueur. |
| `{job_id}` | Item de job/detail/home favori | ID technique du job. |
| `{job}` | Item de job/detail/home favori | Nom affiche du job. |
| `{level}` | Item de job/detail/home favori | Niveau du job. |
| `{max_level}` | Item de job/detail/home favori | Niveau max du job. |
| `{xp}` | Item de job/detail/home favori | XP actuel. |
| `{xp_next}` | Item de job/detail/home favori | XP requis prochain niveau. |
| `{percent}` | Item de job/detail/home favori | Progression en %. |
| `{global_level}` | Item de job/detail/home favori | Niveau global jobs. |
| `{unlocked}` | Item de job/detail/home favori | Nombre de jobs debloques. |
| `{slots}` | Item de job/detail/home favori | Slots metiers debloques. |
| `{max_slots}` | Item de job/detail/home favori | Maximum de slots metiers. |
| `{quest_id}` | Item de quete | ID technique de la quete. |
| `{quest}` | Item de quete | Nom affiche de la quete. |
| `{quest_status}` | Item de quete | Etat lisible: en cours, a recuperer, deja recuperee. |
| `{progress}` | Item de quete | Progression actuelle. |
| `{amount}` | Item de quete | Objectif total. |
| `{reward_xp}` | Item de quete | XP donne au claim. |
| `{filter_status}` | Filtre quetes | Etat lisible du filtre: selectionne/disponible. |
| `{selected}` | Filtre quetes | Texte court affiche quand le filtre est actif. |

## Filtres du GUI quetes

Le GUI `quests` supporte des filtres configurables directement dans `gui/home.yml`:

```yaml
quests:
  filter_job_slots: [1, 2, 3, 4, 5, 6]
  items:
    filter_all:
      slot: 0
      click_actions:
        - "[quests] all"
    filter_job:
      click_actions:
        - "[quests] {job_id}"
```

Les templates optionnels `filter_all_selected` et `filter_job_selected` remplacent le rendu normal quand le filtre est actif.

## Exemple

```yaml
items:
  border:
    slots: "0-8,45-53"
    material: STAINED_GLASS_PANE
    data: 15
    name: " "
    click_actions: []

  jobs_list:
    slot: 11
    material: CHEST
    name: "&eListe des jobs"
    lore:
      - "&7Debloques: &e{unlocked}&7/&e{slots}"
      - "&aClique pour ouvrir."
    click_actions:
      - "[sound] CLICK 1.0 1.0"
      - "[open] jobs"
```

## Items CIT / NBT

Les items GUI internes supportent le meme tag CIT principal que Kgui :

```yaml
items:
  background:
    slots: "0-8,45-53"
    material: CLAY_BALL
    cit: "BackgroundHebreu2"
    name: " "
    hide_attributes: true

  dynamite_info:
    slot: 22
    material: CLAY_BALL
    cit_key: "dynamite"
    name: "&6Dynamite"
    nbt:
      sparrowmc-item: "dynamite"
      kjobs-gui: true
      custom-int: 12
      custom-double: 1.5
```

Notes :
- `cit` et `cit_key` sont des alias.
- Le plugin ecrit le tag NBT `sparrowmc-item` avec la valeur du CIT.
- Les valeurs `nbt:` peuvent utiliser les placeholders disponibles du menu, par exemple `{player}` ou `{job_id}`.
- Les objets NBT imbriques ne sont pas supportes pour le moment; uniquement des key/value simples.

## Reste a faire

| Sujet | Etat | Pourquoi |
|---|---:|---|
| Requirements par item | A faire | Pour cacher/desactiver des boutons selon permission, job, level, etc. |
| Cooldown de clic par item | A faire | Anti double-clic utile sur actions sensibles. |
| Pages dynamiques top/quetes | OK | Top et quetes sont branches cote data. Rewards dedies restent a faire si besoin. |
| Tab ultra configurable | A faire | Actuellement header/footer configurable; prochaine marche : sections/joueurs/staff/economie/jobs plus structurees. |
