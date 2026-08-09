# KjobsUltimate — Structure Complète des Fichiers de Configuration

> Principe : 1 fichier = 1 responsabilité. Tout est modifiable sans toucher au code.
> Rechargement : `/kjobadmin reload` recharge tous les fichiers à chaud.

---

## Arborescence Complète

```
plugins/KjobUltimate/
│
├── config.yml               ← Settings globaux, storage, slots, anti-abuse
├── messages.yml             ← Tous les messages visibles par les joueurs
├── sounds.yml               ← Tous les sons (levelup, quest, notification...)
├── hud.yml                  ← BossBar + ActionBar + Achievement popup
├── tab.yml                  ← Tab header/footer + sections
│
├── jobs/
│   ├── mineur.yml           ← Définition complète du job Mineur
│   ├── farmer.yml           ← Définition complète du job Farmer
│   ├── hunter.yml           ← Définition complète du job Hunter
│   ├── pretorien.yml        ← Définition complète du job Prétorien
│   └── artisant.yml         ← Définition complète du job Artisant
│
├── quests/
│   ├── quests_mineur.yml    ← Toutes les quêtes du Mineur
│   ├── quests_farmer.yml    ← Toutes les quêtes du Farmer
│   ├── quests_hunter.yml    ← Toutes les quêtes du Hunter
│   ├── quests_pretorien.yml ← Toutes les quêtes du Prétorien
│   └── quests_artisant.yml  ← Toutes les quêtes du Artisant
│
└── data/
    └── kjobultimate.db      ← Base SQLite (NE PAS MODIFIER À LA MAIN)
```

---

## config.yml

```yaml
# ============================================================
# KjobUltimate — Configuration Principale
# ============================================================

debug: false
debug_xp: false
debug_quest: false
debug_hud: false
debug_slots: false

# ─── Stockage ───────────────────────────────────────────────
storage:
  type: SQLITE                 # SQLITE (recommandé) | YAML (legacy)
  autosave_interval: 10        # minutes entre autosaves

# ─── Comportement au premier join ───────────────────────────
join:
  # false = le joueur choisit son premier job (GUI s'ouvre automatiquement)
  # true  = tous les jobs assignés automatiquement dès la connexion
  default_all_jobs: false
  # Message affiché quand le GUI de sélection s'ouvre au premier join
  first_join_message: "§6§lBienvenue ! §7Choisis ton premier métier dans le menu !"

# ─── Système de Slots de Jobs ───────────────────────────────
job_slots:
  enabled: true                # false = tous les jobs actifs sans restriction
  default_slots: 1
  unlock_condition: MAIN_JOB_LEVEL   # MAIN_JOB_LEVEL | HIGHEST_JOB_LEVEL | TOTAL_LEVEL
  unlock_thresholds:
    2: 5
    3: 15
    4: 30
    5: 50
  max_slots: 5
  notify_unlock: true
  allow_job_change: true
  change_cooldown: 0

# ─── Multiplicateurs XP ─────────────────────────────────────
xp_multipliers:
  # Bonus par permission (rang VIP, Premium, etc.)
  # Format : permission: multiplicateur (ex: 1.5 = +50% XP)
  permissions:
    kjob.xp.vip: 1.25
    kjob.xp.premium: 1.5
    kjob.xp.youtuber: 2.0
  # Multiplicateur global d'événement (changeable à chaud via /kjobadmin event)
  event_multiplier: 1.0

# ─── Anti-abuse ─────────────────────────────────────────────
# (voir aussi anti_abuse.yml pour la config détaillée)
anti_abuse:
  silktouch_enabled: true
  crops_mature_only: true
  block_position_cooldown: 300    # secondes (0 = désactivé)
  pvp_target_cooldown: 60         # secondes
  block_creative: true
  block_spectator: true
  daily_xp_cap:
    enabled: false
    # Override par job dans jobs/<jobId>.yml

# ─── Intégrations ───────────────────────────────────────────
hooks:
  vault:
    enabled: true
  placeholderapi:
    enabled: true
  kchat:
    enabled: true
    disable_tab_header_footer: true   # Désactiver le header/footer de Kchat si présent
  kgui:
    enabled: true
  worldguard:
    enabled: false
    require_build_flag: false         # true = XP seulement dans zones de build autorisées
```

---

## messages.yml

```yaml
# ============================================================
# KjobUltimate — Messages
# Codes couleur : &a &b &c ... | §a §b §c ...
# Placeholders : {player} {job} {level} {xp} {xp_next} {quest}
# ============================================================

prefix: "§8[§6Jobs§8] §r"

# ─── Level Up ───────────────────────────────────────────────
levelup:
  message: "{prefix}§6Bravo §e{player} §6! Tu passes §b{job} §6niveau §e{level} §6!"
  broadcast: ""               # Vide = pas de broadcast. Sinon : affiché à tous.
  title:
    enabled: true
    title: "§6§lNIVEAU {level}"
    subtitle: "§b{job} §7atteint !"
    fade_in: 10
    stay: 40
    fade_out: 10

# ─── Quêtes ─────────────────────────────────────────────────
quest:
  completed: "{prefix}§a§lQuête terminée ! §f{quest} §7— Ouvre §e/jobs §7pour récupérer ta récompense."
  claimed: "{prefix}§aRécompense récupérée pour §f{quest} §a!"
  reward_inventory_full: "{prefix}§cTon inventaire est plein ! Les items ont été droppés à tes pieds."
  reset_daily: "{prefix}§7Tes quêtes quotidiennes ont été réinitialisées !"
  reset_weekly: "{prefix}§7Tes quêtes hebdomadaires ont été réinitialisées !"

# ─── Slots ──────────────────────────────────────────────────
slots:
  unlocked: "{prefix}§a§lNOUVEAU SLOT DÉBLOQUÉ ! §7Tu peux pratiquer un second métier via §e/jobs §7!"
  job_selected: "{prefix}§7Tu pratiques maintenant §b{job} §7dans le slot §f{slot}§7."
  job_changed: "{prefix}§7Tu changes de métier dans le slot §f{slot} §7: §b{old_job} §7→ §b{new_job}§7."
  no_slot_available: "{prefix}§cTu n'as pas de slot disponible. Progresse dans ton métier actuel !"
  choose_first_job: "{prefix}§6Choisis ton premier métier pour commencer !"

# ─── HUD toggle ────────────────────────────────────────────
hud_toggle:
  enabled: "{prefix}§aHUD activé."
  disabled: "{prefix}§cHUD désactivé."

# ─── Anti-abuse ─────────────────────────────────────────────
anti_abuse:
  creative_blocked: ""         # Vide = pas de message (silencieux)
  silktouch_blocked: ""
  block_cooldown: ""
  daily_cap_reached: "{prefix}§cTu as atteint le cap d'XP quotidien pour §b{job}§c. Reviens demain !"
  hud_toggle_disabled: "{prefix}§cLe toggle HUD est désactivé sur ce serveur."
  worldguard_blocked: "{prefix}§cTu ne peux pas gagner d'XP dans cette zone."

# ─── Changement de job ──────────────────────────────────────
job_change:
  warning: "{prefix}§cAttention ! Tu vas abandonner §b{old_job}§c. Toutes ses quêtes (en cours et non réclamées) seront perdues."
  unclaimed_warning: "{prefix}§c§l{unclaimed} quête(s) complétée(s) non réclamée(s) pour §b{old_job}§c seront perdues définitivement !"
  confirm_prompt: "{prefix}§eTape §f/jobs confirmer §epour confirmer ou §f/jobs annuler §epour annuler."
  confirmed: "{prefix}§aMétier changé avec succès !"
  cancelled: "{prefix}§7Changement de métier annulé."
  confirm_expired: "{prefix}§cLa confirmation a expiré. Recommence depuis §e/jobs§c."

# ─── Niveau max ─────────────────────────────────────────────
job_max_level:
  reached: "{prefix}§6§lNiveau maximum atteint pour §b{job}§6 !"
  # Texte affiché dans la GUI à la place de la barre de progression
  gui_badge: "§6§l★ NIVEAU MAX ★"

# ─── Divers ─────────────────────────────────────────────────
misc:
  no_job_yet: "{prefix}§7Tu n'as pas encore de métier. Utilise §e/jobs §7pour en choisir un !"

# ─── Commandes ──────────────────────────────────────────────
commands:
  no_permission: "{prefix}§cTu n'as pas la permission."
  player_not_found: "{prefix}§cJoueur introuvable."
  reload_success: "{prefix}§aConfiguration rechargée avec succès."
  job_not_found: "{prefix}§cJob '{job}' introuvable. Jobs disponibles : {list}"
  admin_xp_given: "{prefix}§a{xp} XP ajoutés à {player} pour le job {job}."
  admin_level_set: "{prefix}§aNiveau de {player} pour {job} défini à {level}."
  event_multiplier_set: "{prefix}§aMultiplicateur d'événement défini à §e{mult}x§a."
```

---

## sounds.yml

```yaml
# ============================================================
# KjobUltimate — Sons
# Noms valides 1.8.8 : https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Sound.html
# volume: 0.0-2.0 | pitch: 0.0-2.0 (1.0 = normal)
# Chaque section peut être désactivée avec enabled: false
# ============================================================

levelup:
  enabled: true
  mineur:    { sound: LEVEL_UP, volume: 1.0, pitch: 1.0 }
  farmer:    { sound: LEVEL_UP, volume: 1.0, pitch: 1.2 }
  hunter:    { sound: LEVEL_UP, volume: 1.0, pitch: 0.9 }
  pretorien: { sound: LEVEL_UP, volume: 1.0, pitch: 0.8 }
  artisant:  { sound: LEVEL_UP, volume: 1.0, pitch: 1.1 }
  default:   { sound: LEVEL_UP, volume: 1.0, pitch: 1.0 }

quest_complete:
  enabled: true
  sound: ORB_PICKUP
  volume: 1.0
  pitch: 1.5

quest_claim:
  enabled: true
  sound: CHEST_OPEN
  volume: 1.0
  pitch: 1.0

quest_reset_daily:
  enabled: true
  sound: NOTE_PLING
  volume: 0.8
  pitch: 1.0

quest_reset_weekly:
  enabled: true
  sound: NOTE_PLING
  volume: 1.0
  pitch: 1.2

new_quest_assigned:
  enabled: true
  sound: ORB_PICKUP
  volume: 0.7
  pitch: 1.4

slot_unlocked:
  enabled: true
  sound: FIREWORK_BLAST
  volume: 1.0
  pitch: 1.0

job_change:
  enabled: true
  sound: NOTE_PLING
  volume: 1.0
  pitch: 1.0

max_level_reached:
  enabled: true
  sound: FIREWORK_BLAST
  volume: 1.0
  pitch: 1.5

xp_gain:
  enabled: false                # Si true, son à chaque gain XP (peut être spammant)
  sound: ORB_PICKUP
  volume: 0.3
  pitch: 1.8
```

---

## hud.yml

```yaml
# ============================================================
# KjobUltimate — HUD (BossBar + ActionBar + Achievement)
# ============================================================

bossbar:
  enabled: true
  # Format du titre : placeholders {job} {level} {xp} {xp_next} {percent}
  title_format: "§b{job} §7Lv.§e{level} §8| §f{xp}§7/§f{xp_next}§8xp"
  # Format du titre quand le job est au niveau max (remplace title_format)
  # Placeholders : {job} {level}
  title_format_max_level: "§b{job} §7Lv.§e{level} §8| §6MAX"
  # Timer avant disparition de la bossbar si aucun XP gagné dans AUCUN job (en secondes)
  # 0 = toujours visible (jamais cachée automatiquement)
  bossbar_timing_reset: 7
  # Afficher si aucun job sélectionné ?
  show_if_no_job: false

  # Mapping achievement vanilla → job (pour le popup de level up)
  # Texte du popup → remplacer dans le resource pack (lang/fr_FR.lang)
  achievement_mapping:
    mineur: "achievement.buildPickaxe"
    farmer: "achievement.makeBread"
    hunter: "achievement.killEnemy"
    pretorien: "achievement.buildSword"
    artisant: "achievement.buildWorkBench"

actionbar:
  enabled: true
  # Format du message +XP après une action
  # Placeholders disponibles : {xp} {job} {level}
  format: "§a+{xp}xp §7(§f{job} §eLv.{level}§7)"
  # Durée d'affichage avant disparition (secondes)
  display_duration: 3
  # Accumuler les gains d'XP du même job dans la même fenêtre
  # true  = "+5xp" puis mine encore "+3xp" → affiche "+8xp"
  # false = chaque gain remplace le précédent → affiche "+3xp"
  # Note : si le job change, l'accumulation se remet à zéro dans les deux cas
  accumulate: true

achievement_popup:
  enabled: true
  # Cooldown entre deux popups consécutifs (ms)
  # Évite les rafales visuelles quand un admin donne plusieurs levels d'un coup
  # 0 = pas de cooldown (déconseillé)
  popup_cooldown_ms: 2000
  # Si plusieurs levels gagnés d'un coup (commande admin) : n'afficher que le dernier
  # true = seul le popup du niveau final est envoyé (recommandé pour les perfs)
  # false = tous les niveaux sont en file avec le cooldown ci-dessus
  show_last_only_on_bulk: true

title_levelup:
  enabled: true
  title_format: "§6§lNIVEAU {level}"
  subtitle_format: "§b{job} §7— en progression !"
  fade_in: 10
  stay: 40
  fade_out: 10
```

---

## tab.yml

```yaml
# ============================================================
# KjobUltimate — Tab List (Header/Footer NMS)
# ============================================================

tab:
  enabled: true
  # Intervalle de mise à jour du header/footer (en ticks, 20 = 1s)
  update_interval: 40

  header:
    lines:
      - "§6§lSparrowMC §8— §7Factions PvP"
      - "§8En ligne : §f{online}§8/§f{max_players}"
      - ""

  footer:
    # Lignes statiques avant la section jobs
    lines_before:
      - "§b§lMes Métiers"

    # Format pour chaque job actif du joueur (1 ligne générée par job)
    # Placeholders : {job} {level} {bar} {percent}
    # La section est générée dynamiquement dans ScoreboardManager.buildFooter()
    job_line_format: "§7{job}  §fLv.{level} §8[{bar}§8] §f{percent}%"

    # Caractères de la barre de progression
    bar_filled: "§a█"
    bar_empty: "§8░"
    bar_length: 10

    # Lignes statiques après la section jobs
    lines_after:
      - ""
      - "§eArgent : §f{vault_balance}§e  §7PP : §f{playerpoints}"
      - "§7IP : §fplay.sparrowmc.fr"
      - ""

    # Note : {playerpoints} nécessite le hook PlayerPoints activé dans config.yml
    # Note : les infos argent/PP peuvent aussi être gérées par Kchat si configuré ainsi
```

---

## jobs/mineur.yml (Exemple complet)

```yaml
# ============================================================
# KjobUltimate — Job Mineur
# ============================================================

display_name: "§bMineur"
description:
  - "§7Le Mineur extrait les ressources"
  - "§7des profondeurs de la terre."

icon:
  material: IRON_PICKAXE
  nbt_cit: "job_mineur_icon"

max_level: 50

xp_curve:
  type: custom
  fallback_type: linear
  fallback_base: 1000
  fallback_multiplier: 1.3
  custom_levels:
    1: 1000
    2: 1500
    3: 2200
    5: 4200
    10: 17000

bossbar:
  color: BLUE
  style: SEGMENTED_10

daily_xp_cap: 0      # 0 = pas de cap (override config.yml global)

level_rewards:
  5:
    - "eco give {player} 500"
    - "broadcast §b{player} §fpasse §bniveau 5 §fMineur !"
  10:
    - "eco give {player} 2000"
    - "give {player} diamond 3"
  25:
    - "eco give {player} 10000"
    - "lp user {player} permission set kjob.xp.vip true"

actions:
  stone:
    material: STONE
    xp: 5
    money: 0.01
    silktouch: false

  coal_ore:
    material: COAL_ORE
    xp: 15
    money: 0.5
    silktouch: false

  iron_ore:
    material: IRON_ORE
    xp: 25
    money: 1.0
    silktouch: true      # Pas d'XP si SilkTouch

  gold_ore:
    material: GOLD_ORE
    xp: 40
    money: 2.0
    silktouch: true

  diamond_ore:
    material: DIAMOND_ORE
    xp: 100
    money: 10.0
    silktouch: true

  emerald_ore:
    material: EMERALD_ORE
    xp: 120
    money: 15.0
    silktouch: true

  obsidian:
    material: OBSIDIAN
    xp: 30
    money: 0.5
    silktouch: false
```

---

## quests/quests_mineur.yml (Exemple)

```yaml
# ============================================================
# KjobUltimate — Quêtes du Mineur
# ============================================================

quests:

  mineur_daily_stone:
    display_name: "§7Extraction Standard"
    description:
      - "Miner 200 blocs de stone."
    job: mineur
    type: mining
    target:
      block: STONE
    objective: 200
    reset: daily
    min_level: 0
    rewards:
      xp: 500
      money: 50

  mineur_weekly_diamond:
    display_name: "§bChasseur de Diamants"
    description:
      - "Miner 30 diamants."
      - "§8(SilkTouch ne compte pas)"
    job: mineur
    type: mining
    target:
      block: DIAMOND_ORE
    objective: 30
    reset: weekly
    min_level: 5
    rewards:
      xp: 5000
      money: 2000
      items:
        - "DIAMOND:0:3"
      commands:
        - "broadcast §b{player} §fa completé §fChasseur de Diamants !"

  mineur_perm_obsidian_master:
    display_name: "§8Maître de l'Obsidienne"
    description:
      - "Miner 1000 blocs d'obsidienne."
      - "§8Quête permanente — récompense unique."
    job: mineur
    type: mining
    target:
      block: OBSIDIAN
    objective: 1000
    reset: never
    min_level: 20
    rewards:
      xp: 50000
      money: 25000
      items:
        - "DIAMOND_PICKAXE:0:1"
      commands:
        - "lp user {player} permission set kjob.title.obsidian_master true"
```

---

## jobs/pretorien.yml (Exemple complet)

```yaml
# ============================================================
# KjobUltimate — Job Prétorien
# XP via kills PvP ET consommation d'items de combat
# ============================================================

display_name: "§cPrétorien"
description:
  - "§7Le Prétorien est un guerrier né."
  - "§7Il gagne en puissance au combat."

icon:
  material: IRON_SWORD
  nbt_cit: "job_pretorien_icon"

max_level: 50
bossbar:
  color: PURPLE
  style: SEGMENTED_10

daily_xp_cap: 0

level_rewards:
  5:
    - "eco give {player} 1000"
  10:
    - "eco give {player} 5000"

# ─── XP via kills PvP ────────────────────────────────────────
pvp_kill:
  enabled: true
  xp: 100
  # Cooldown anti-farm PvP (secondes, 0 = désactivé)
  # Si le joueur re-tue la même cible avant la fin du cooldown → pas d'XP
  target_cooldown: 60

# ─── XP via consommation d'items ─────────────────────────────
# Support CIT : si 'cit' est défini, vérifier que l'item a le bon NBT CIT name
# Si 'cit' est absent → tout item du material correspondant donne XP
consume_list:
  - material: GOLDEN_APPLE
    xp: 1500
    # Pas de 'cit' → toutes les pommes d'or donnent XP

  - material: GOLDEN_APPLE
    damage: 1           # ENCHANTED golden apple (notch apple, damage value 1 en 1.8.8)
    xp: 3000

  - material: CLAY_BALL
    xp: 500
    cit: joint          # Seulement si l'item a le tag NBT CIT "joint"
    # Le tag CIT est vérifié via le display name ou le NBT tag "cit_name"

  # Ajouter ici d'autres items custom via leur CIT name :
  # - material: BLAZE_ROD
  #   xp: 800
  #   cit: potion_guerrier
```

---

## jobs/artisant.yml (Exemple complet)

```yaml
# ============================================================
# KjobUltimate — Job Artisant
# XP via crafts vanilla ET crafts Kcraft custom
# ============================================================

display_name: "§eArtisant"
description:
  - "§7L'Artisant crée des équipements"
  - "§7de qualité pour ses alliés."

icon:
  material: WORKBENCH
  nbt_cit: "job_artisant_icon"

max_level: 50
bossbar:
  color: YELLOW
  style: SEGMENTED_10

daily_xp_cap: 0

level_rewards:
  5:
    - "eco give {player} 500"
  10:
    - "eco give {player} 3000"

# ─── Crafts Vanilla (CraftItemEvent) ─────────────────────────
# Identifiés par le Material résultant du craft standard
vanilla_actions:
  IRON_SWORD:       { xp: 20,  money: 2.0 }
  GOLD_SWORD:       { xp: 15,  money: 1.5 }
  DIAMOND_SWORD:    { xp: 80,  money: 15.0 }
  IRON_AXE:         { xp: 15,  money: 1.0 }
  DIAMOND_AXE:      { xp: 75,  money: 12.0 }
  IRON_PICKAXE:     { xp: 15,  money: 1.0 }
  DIAMOND_PICKAXE:  { xp: 75,  money: 12.0 }
  IRON_SPADE:       { xp: 10,  money: 0.5 }
  DIAMOND_SPADE:    { xp: 60,  money: 8.0 }
  BOW:              { xp: 30,  money: 5.0 }
  IRON_HELMET:      { xp: 20,  money: 2.0 }
  IRON_CHESTPLATE:  { xp: 25,  money: 3.0 }
  IRON_LEGGINGS:    { xp: 22,  money: 2.5 }
  IRON_BOOTS:       { xp: 18,  money: 1.5 }
  DIAMOND_HELMET:   { xp: 85,  money: 15.0 }
  DIAMOND_CHESTPLATE: { xp: 90, money: 20.0 }
  DIAMOND_LEGGINGS: { xp: 88,  money: 18.0 }
  DIAMOND_BOOTS:    { xp: 80,  money: 14.0 }

# ─── Crafts Kcraft Custom (KcraftCraftCompleteEvent) ─────────
# Identifiés par l'ID de la recette Kcraft (tel que défini dans les YAML de Kcraft)
# À compléter une fois les recettes Kcraft définies
kcraft_actions:
  # epee_de_feu:    { xp: 150, money: 30.0 }
  # arc_runique:    { xp: 200, money: 50.0 }
  # armure_ancienne: { xp: 300, money: 80.0 }
  # (liste à compléter avec les IDs des recettes Kcraft du serveur)
```
