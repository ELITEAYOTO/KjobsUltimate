# KjobUltimate — Référence Complète des Fichiers de Configuration

> Tous les fichiers de configuration sont dans `plugins/KjobUltimate/`
> Rechargement à chaud : `/kjobadmin reload`

---

## config.yml — Configuration Principale

```yaml
############################################################
# KjobUltimate - Configuration Principale
# Version: 1.0.0
############################################################

# Mode debug - à désactiver en production
debug: false
debug_xp: false
debug_quest: false
debug_hud: false

# Stockage des données joueurs
# SQLITE = recommandé (performant, robuste, WAL mode activé)
# YAML   = legacy uniquement (migration KJob2 → utiliser /kjob migrate ensuite)
storage:
  type: SQLITE         # SQLITE (défaut) | YAML (legacy)
  autosave_interval: 10  # minutes entre autosave

# Vault (économie)
vault:
  enabled: true        # si false, les rewards money sont ignorés

# PlaceholderAPI
placeholderapi:
  enabled: true        # si false, les placeholders %kjob_xxx% sont désactivés

# Comportement des jobs
jobs:
  # Nombre max de jobs actifs par joueur simultanément
  # -1 = illimité (tous les jobs actifs en même temps)
  max_active_per_player: -1

  # Si true, les joueurs ont tous les jobs dès la première connexion au level 0
  # Si false, les joueurs doivent "activer" un job depuis le GUI
  default_all_jobs: true

  # Si true, bloquer l'XP quand le joueur est en mode créatif
  block_xp_creative: true

  # Si true, bloquer l'XP quand le joueur est en mode spectateur
  block_xp_spectator: true

  # Commande pour ouvrir le GUI principal
  menu_command: jobs   # /jobs

# WorldGuard : si true, vérifie les régions WG avant de donner de l'XP
worldguard:
  enabled: false
  require_flag: BUILD  # flag WG requis pour gagner de l'XP

# Anti-abuse
anti_abuse:
  # Silk Touch : bloquer l'XP sur les blocs configurés avec silktouch: true
  silktouch_enabled: true

  # Cultures immatures : pas d'XP si culture pas mature
  crops_mature_only: true

  # Cooldown par position de bloc (anti break-replace-break)
  # Durée en secondes (0 = désactivé)
  block_position_cooldown: 300   # 5 minutes

  # Anti-farm PvP (Prétorien) : cooldown entre XP sur le même joueur
  pvp_target_cooldown: 60        # secondes

  # Cap d'XP quotidien par job (0 = désactivé)
  daily_xp_cap:
    enabled: false
    default: 0         # Override global (0 = pas de cap)
    # Overrides par job (optionnel)
    # mineur: 50000
    # farmer: 30000
```

---

## jobs.yml — Définition des Jobs

```yaml
############################################################
# KjobUltimate - Configuration des Jobs
#
# Chaque job a :
#   display_name   : Nom affiché (supporte les codes couleur §)
#   icon           : Material pour le GUI + NBT sparrowmc-item pour CIT
#   max_level      : Niveau maximum (configurable par job)
#   xp_curve       : Courbe XP (linear | exponential | custom)
#   bossbar        : Couleur et style de la bossbar pour ce job
#   actions        : Actions qui donnent de l'XP
#   level_rewards  : Récompenses par niveau (commandes console)
#
# Types d'actions supportés :
#   type: mining    → BlockBreakEvent (blocs)
#   type: farming   → BlockBreakEvent (cultures, check maturité auto)
#   type: hunting   → EntityDeathEvent (mobs)
#   type: pvp       → PlayerDeathEvent (joueurs)
#   type: crafting  → CraftItemEvent (fabrication)
#   type: fishing   → PlayerFishEvent (pêche)
############################################################

jobs:

  # ─────────────────────────────────────────────────────
  # MINEUR
  # ─────────────────────────────────────────────────────
  mineur:
    display_name: "§bMineur"
    icon:
      material: IRON_PICKAXE
      nbt_cit: "job_mineur_icon"    # tag sparrowmc-item pour CIT dans les GUI
    max_level: 50

    xp_curve:
      type: custom                  # custom = table manuelle niveau par niveau
      # Si des niveaux manquent, fallback sur type: linear
      fallback_type: linear
      fallback_base: 1000
      fallback_multiplier: 1.3
      custom_levels:
        1: 1000
        2: 1500
        3: 2200
        4: 3000
        5: 4200
        6: 5800
        7: 7800
        8: 10200
        9: 13000
        10: 17000
        # ... jusqu'au niveau max_level
        # Conseil : définir au moins les 10 premiers, le reste en fallback

    bossbar:
      color: BLUE                   # WHITE, RED, GREEN, BLUE, YELLOW, PURPLE, PINK
      style: SEGMENTED_10           # SOLID, SEGMENTED_6, SEGMENTED_10, SEGMENTED_12, SEGMENTED_20

    # Récompenses à chaque niveau (commandes console)
    # Placeholders disponibles : {player}, {level}, {job}
    level_rewards:
      5:
        - "eco give {player} 500"
        - "broadcast §b{player} §fpasse §bniveau 5 §fMineur !"
      10:
        - "eco give {player} 2000"
        - "give {player} DIAMOND 5"
        - "broadcast §b{player} §fpasse §bniveau 10 §fMineur !"
      25:
        - "eco give {player} 10000"
        - "give {player} DIAMOND 15"
      50:
        - "eco give {player} 50000"
        - "give {player} DIAMOND_BLOCK 5"
        - "broadcast §b{player} §fa atteint le §bniveau MAXIMUM §fdu job Mineur !"

    # Actions qui donnent de l'XP
    # silktouch: true = bloqué si silk touch (config anti_abuse.silktouch_enabled requis aussi)
    actions:
      mine_stone:
        material: STONE              # nom exact du Material Bukkit 1.8.8
        xp: 5
        money: 1
        silktouch: false             # la stone ne donne pas de XP avec silktouch (rien à casser)
        description: "Miner de la pierre"

      mine_coal_ore:
        material: COAL_ORE
        xp: 15
        money: 3
        silktouch: true              # si silktouch = bloque XP
        description: "Miner du minerai de charbon"

      mine_iron_ore:
        material: IRON_ORE
        xp: 25
        money: 5
        silktouch: true
        description: "Miner du minerai de fer"

      mine_gold_ore:
        material: GOLD_ORE
        xp: 40
        money: 8
        silktouch: true
        description: "Miner du minerai d'or"

      mine_diamond_ore:
        material: DIAMOND_ORE
        xp: 100
        money: 20
        silktouch: true
        description: "Miner du diamant"

      mine_lapis_ore:
        material: LAPIS_ORE
        xp: 30
        money: 6
        silktouch: true
        description: "Miner du lapis lazuli"

      mine_redstone_ore:
        material: REDSTONE_ORE
        xp: 20
        money: 4
        silktouch: true
        description: "Miner de la redstone"

      mine_obsidian:
        material: OBSIDIAN
        xp: 50
        money: 10
        silktouch: false
        description: "Miner de l'obsidienne"

      mine_gravel:
        material: GRAVEL
        xp: 3
        money: 0
        silktouch: false
        description: "Miner du gravier"

  # ─────────────────────────────────────────────────────
  # FARMER
  # ─────────────────────────────────────────────────────
  farmer:
    display_name: "§aFarmer"
    icon:
      material: WHEAT
      nbt_cit: "job_farmer_icon"
    max_level: 50

    xp_curve:
      type: custom
      fallback_type: linear
      fallback_base: 800
      fallback_multiplier: 1.25
      custom_levels:
        1: 800
        2: 1200
        3: 1800
        4: 2500
        5: 3500

    bossbar:
      color: GREEN
      style: SEGMENTED_10

    level_rewards:
      5:
        - "eco give {player} 300"
      10:
        - "eco give {player} 1500"
        - "give {player} WHEAT_SEEDS 64"

    # Pour le farming, le check de maturité est automatique si type: farming
    actions:
      harvest_wheat:
        material: WHEAT              # 1.8.8 : Material.WHEAT (BlockBreakEvent)
        type: farming                # active le check de maturité automatique
        xp: 10
        money: 2
        description: "Récolter du blé mature"

      harvest_carrots:
        material: CARROT             # 1.8.8 : Material.CARROT (pas CARROTS)
        type: farming
        xp: 10
        money: 2
        description: "Récolter des carottes matures"

      harvest_potatoes:
        material: POTATO             # 1.8.8 : Material.POTATO
        type: farming
        xp: 10
        money: 2
        description: "Récolter des pommes de terre matures"

      harvest_melon:
        material: MELON_BLOCK        # 1.8.8 : Material.MELON_BLOCK
        type: farming                # pas de check maturité (melon = toujours cassable)
        xp: 8
        money: 1
        description: "Récolter un melon"

      harvest_pumpkin:
        material: PUMPKIN
        type: farming
        xp: 8
        money: 1
        description: "Récolter une citrouille"

      harvest_sugarcane:
        material: SUGAR_CANE_BLOCK   # 1.8.8 : Material.SUGAR_CANE_BLOCK
        type: farming
        xp: 5
        money: 1
        description: "Récolter de la canne à sucre"

  # ─────────────────────────────────────────────────────
  # HUNTER
  # ─────────────────────────────────────────────────────
  hunter:
    display_name: "§cHunter"
    icon:
      material: BOW
      nbt_cit: "job_hunter_icon"
    max_level: 50

    xp_curve:
      type: linear
      base: 1200
      multiplier: 1.35

    bossbar:
      color: RED
      style: SEGMENTED_10

    level_rewards:
      10:
        - "eco give {player} 1000"
        - "give {player} BONE 32"

    actions:
      kill_zombie:
        entity: ZOMBIE
        xp: 10
        money: 2
        description: "Tuer un zombie"

      kill_skeleton:
        entity: SKELETON
        xp: 12
        money: 3
        description: "Tuer un squelette"

      kill_creeper:
        entity: CREEPER
        xp: 15
        money: 4
        description: "Tuer un creeper"

      kill_spider:
        entity: SPIDER
        xp: 8
        money: 2
        description: "Tuer une araignée"

      kill_enderman:
        entity: ENDERMAN
        xp: 25
        money: 8
        description: "Tuer un enderman"

      kill_blaze:
        entity: BLAZE
        xp: 30
        money: 10
        description: "Tuer un blaze"

  # ─────────────────────────────────────────────────────
  # PRETORIEN
  # ─────────────────────────────────────────────────────
  pretorien:
    display_name: "§4Prétorien"
    icon:
      material: DIAMOND_SWORD
      nbt_cit: "job_pretorien_icon"
    max_level: 50

    xp_curve:
      type: linear
      base: 2000
      multiplier: 1.4

    bossbar:
      color: PURPLE
      style: SEGMENTED_10

    level_rewards:
      10:
        - "eco give {player} 5000"

    # Actions PvP - anti-abuse : cooldown sur la cible (pvp_target_cooldown dans config.yml)
    actions:
      kill_player:
        type: pvp                    # type pvp = PlayerDeathEvent, killer != null
        xp: 80
        money: 10
        description: "Tuer un joueur"

  # ─────────────────────────────────────────────────────
  # ARTISANT
  # ─────────────────────────────────────────────────────
  artisant:
    display_name: "§eArtisant"
    icon:
      material: WORKBENCH
      nbt_cit: "job_artisant_icon"
    max_level: 50

    xp_curve:
      type: linear
      base: 600
      multiplier: 1.2

    bossbar:
      color: YELLOW
      style: SEGMENTED_10

    level_rewards:
      10:
        - "eco give {player} 1000"

    # CraftItemEvent - donner XP pour les crafts
    actions:
      craft_wood_planks:
        material: WOOD               # résultat du craft (Material du résultat)
        type: crafting
        xp: 1
        money: 0
        description: "Fabriquer des planches"

      craft_stick:
        material: STICK
        type: crafting
        xp: 1
        money: 0
        description: "Fabriquer des bâtons"

      craft_chest:
        material: CHEST
        type: crafting
        xp: 5
        money: 1
        description: "Fabriquer un coffre"

      craft_iron_sword:
        material: IRON_SWORD
        type: crafting
        xp: 20
        money: 3
        description: "Fabriquer une épée en fer"

      craft_diamond_sword:
        material: DIAMOND_SWORD
        type: crafting
        xp: 80
        money: 15
        description: "Fabriquer une épée en diamant"

      craft_iron_armor:
        material: IRON_CHESTPLATE    # idem pour HELMET, LEGGINGS, BOOTS
        type: crafting
        xp: 30
        money: 5
        description: "Fabriquer une armure en fer"
```

---

## quests.yml — Définition des Quêtes

```yaml
############################################################
# KjobUltimate - Configuration des Quêtes
#
# Chaque quête a :
#   display_name   : Nom affiché dans le GUI
#   description    : Description (lore de l'item dans le GUI)
#   job            : Job requis pour cette quête
#   type           : Type de quête (mining, farming, hunting, pvp, crafting)
#   target         : Ce qu'il faut faire (block, entity, material)
#   objective      : Nombre d'actions requises
#   reset          : daily | weekly | never
#   min_level      : Niveau minimum requis dans le job pour voir cette quête (0 = toujours visible)
#   rewards:
#     xp           : XP du job donné DIRECT à completion (pas besoin de claim)
#     money        : Argent donné au claim
#     items        : Items donnés au claim (liste)
#     commands     : Commandes console exécutées au claim
############################################################

quests:

  # ─── QUÊTES MINEUR ───────────────────────────────────

  mineur_daily_stone:
    display_name: "§7Collecteur de Pierre"
    description:
      - "§7Casser §f500 §7blocs de pierre."
      - ""
      - "§8Job : §bMineur"
      - "§8Reset : §fQuotidien"
    job: mineur
    type: mining
    target:
      block: STONE
      data: 0                        # variant du bloc (damage value 1.8.8)
    objective: 500
    reset: daily
    min_level: 0
    rewards:
      xp: 500                        # XP donné direct à completion
      money: 50                      # Argent donné au claim
      items: []
      commands: []

  mineur_daily_coal:
    display_name: "§8Chercheur de Charbon"
    description:
      - "§7Miner §f50 §7minerais de charbon."
      - ""
      - "§8Job : §bMineur"
      - "§8Reset : §fQuotidien"
    job: mineur
    type: mining
    target:
      block: COAL_ORE
    objective: 50
    reset: daily
    min_level: 0
    rewards:
      xp: 800
      money: 100
      items:
        - "COAL:0:32"                # format : MATERIAL:damage:quantite
      commands: []

  mineur_weekly_diamond:
    display_name: "§bChasseur de Diamants"
    description:
      - "§7Miner §f30 §7diamants."
      - ""
      - "§8Job : §bMineur"
      - "§8Reset : §fHebdomadaire"
      - "§8Niveau requis : §b5"
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
        - "DIAMOND:0:5"
      commands:
        - "broadcast §b{player} §fa completé la quête §fChasseur de Diamants !"

  # ─── QUÊTES FARMER ───────────────────────────────────

  farmer_daily_wheat:
    display_name: "§aRécolte de Blé"
    description:
      - "§7Récolter §f200 §7blés matures."
      - ""
      - "§8Job : §aFarmer"
      - "§8Reset : §fQuotidien"
    job: farmer
    type: farming
    target:
      block: WHEAT
    objective: 200
    reset: daily
    min_level: 0
    rewards:
      xp: 400
      money: 40
      items:
        - "WHEAT:0:64"
      commands: []

  # ─── QUÊTES HUNTER ───────────────────────────────────

  hunter_daily_zombies:
    display_name: "§cExtermination Zombie"
    description:
      - "§7Tuer §f50 §7zombies."
      - ""
      - "§8Job : §cHunter"
      - "§8Reset : §fQuotidien"
    job: hunter
    type: hunting
    target:
      entity: ZOMBIE
    objective: 50
    reset: daily
    min_level: 0
    rewards:
      xp: 600
      money: 75
      items: []
      commands: []

  hunter_weekly_endermen:
    display_name: "§5Tueur d'Endermen"
    description:
      - "§7Tuer §f20 §7endermens."
      - ""
      - "§8Job : §cHunter"
      - "§8Reset : §fHebdomadaire"
      - "§8Niveau requis : §c10"
    job: hunter
    type: hunting
    target:
      entity: ENDERMAN
    objective: 20
    reset: weekly
    min_level: 10
    rewards:
      xp: 3000
      money: 1500
      items:
        - "ENDER_PEARL:0:16"
      commands: []

  # ─── QUÊTES PRETORIEN ─────────────────────────────────

  pretorien_daily_kills:
    display_name: "§4Guerrier"
    description:
      - "§7Tuer §f5 §7joueurs."
      - ""
      - "§8Job : §4Prétorien"
      - "§8Reset : §fQuotidien"
    job: pretorien
    type: pvp
    target:
      type: player
    objective: 5
    reset: daily
    min_level: 0
    rewards:
      xp: 1000
      money: 300
      items: []
      commands: []
```

---

## hud.yml — Configuration du HUD

```yaml
############################################################
# KjobUltimate - Configuration du HUD
# BossBar, ActionBar, Achievement, Sons
############################################################

# ─── BOSSBAR ─────────────────────────────────────────────
bossbar:
  enabled: true

  # Format du titre. Placeholders disponibles :
  # {job_name}   = nom affiché du job (ex: §bMineur)
  # {level}      = niveau actuel
  # {xp}         = XP actuel dans le niveau
  # {xp_next}    = XP requis pour le prochain niveau
  # {progress}   = pourcentage 0-100
  # {bar}        = barre texte (voir bar_config)
  title_format: "{job_name} §7Lv.§e{level} §8| §f{xp}§7/{xp_next}§7xp"

  # Barre de progression texte dans le titre (optionnel, inclus via {bar})
  bar_config:
    enabled: false                   # si true, {bar} est remplacé dans title_format
    length: 10                       # nombre de caractères de la barre
    filled_char: "§a█"
    empty_char: "§8░"

  # Comportement d'affichage
  always_visible: true               # true = toujours visible | false = visible X secondes après action
  timeout_ticks: 200                 # si always_visible: false, disparaît après 200 ticks (10s)

  # Job affiché par défaut (si le joueur n'a pas sélectionné de job actif)
  default_display_job: mineur        # premier job dans la liste

# ─── ACTIONBAR (hotbar message) ──────────────────────────
actionbar:
  enabled: true

  # Format du message XP gagné.
  # Placeholders : {xp} = XP gagné (accumulé si accumulate: true), {job_name}
  format: "§a+{xp} §fXP §8(§7{job_name}§8)"

  # Accumulation : si true, "+5" puis "+3" en 2s = affiche "+8" au lieu de "+3"
  accumulate: true

  # Durée d'affichage en ticks (1 tick = 50ms) après la dernière action
  display_ticks: 60                  # 3 secondes

# ─── ACHIEVEMENT POPUP (level up) ────────────────────────
achievement:
  enabled: true

  # Item affiché dans le popup (bloc custom non obtensible dans votre resource pack)
  # Le material doit être un bloc existant en 1.8.8 mais jamais placé sur le serveur
  # Ex: SPONGE (éponge sèche), MUSHROOM_EGG, etc.
  # CIT remplace l'apparence de ce material dans le pack
  item:
    material: SPONGE                 # Material 1.8.8, texturé via CIT dans le pack
    data: 0

  # Texte du popup (modifié dans le resource pack lang/fr_FR.lang)
  # Ces clés doivent correspondre aux clés dans lang/fr_FR.lang
  # Configurer ici quel achievement vanille déclencher pour chaque job/niveau
  # Mapping job → achievement_id (achievements vanille de la liste)
  achievement_mapping:
    mineur: "achievement.buildPickaxe"   # déclenche l'achievement "Fabriquer une Pioche"
    farmer: "achievement.makeBread"      # déclenche l'achievement "Faire du Pain"
    hunter: "achievement.killEnemy"      # déclenche l'achievement "Tuer un Ennemi"
    pretorien: "achievement.buildSword"  # déclenche l'achievement "Fabriquer une Epée"
    artisant: "achievement.buildWorkBench"

  # Format du texte affiché (dans lang/fr_FR.lang côté resource pack)
  # NE PAS configurer ici → configurer dans le resource pack
  # achievement.buildPickaxe = Mineur — Niveau {level} !
  # (le {level} ne peut pas être dynamique via achievement vanille — voir note)

  # ALTERNATIVE : Title/Subtitle en plus du popup
  title_on_levelup:
    enabled: true
    title: "§6§lNIVEAU {level} !"
    subtitle: "§7{job_name} — §eBravo !"
    fade_in: 10
    stay: 40
    fade_out: 10

# ─── SONS ─────────────────────────────────────────────────
sounds:
  # Son joué à chaque gain d'XP (désactivé par défaut = spam)
  on_xp_gain:
    enabled: false
    sound: "custom.xp_tick"          # nom dans sounds.json du resource pack
    volume: 0.5
    pitch: 1.0

  # Son joué au level up
  on_level_up:
    enabled: true
    sound: "custom.levelup"          # son custom dans le resource pack
    volume: 1.0
    pitch: 1.0
    # Override par job (optionnel)
    per_job:
      mineur: "custom.levelup_mineur"
      farmer: "custom.levelup_farmer"
      hunter: "custom.levelup_hunter"
      pretorien: "custom.levelup_pretorien"
      artisant: "custom.levelup_artisant"

  # Son joué quand une quête est complétée
  on_quest_complete:
    enabled: true
    sound: "custom.quest_complete"
    volume: 1.0
    pitch: 1.0

  # Son joué quand le joueur claim une récompense
  on_quest_claim:
    enabled: true
    sound: "custom.quest_claim"
    volume: 1.0
    pitch: 1.2
```

---

## scoreboard.yml — Configuration du Tab/Scoreboard

```yaml
############################################################
# KjobUltimate - Configuration du Tab List Scoreboard
#
# Les sections sont affichées de gauche à droite dans le tab.
# Chaque section peut contenir des lignes statiques ou dynamiques.
# Placeholders {xxx} remplacés par les valeurs en temps réel.
############################################################

scoreboard:
  enabled: true

  # Interval de rafraichissement en ticks (40 = 2 secondes)
  refresh_ticks: 40

  # Header du tab (multilignes)
  header:
    - "§6§lSparrowMC §8— §7Factions PvP"
    - "§8En ligne : §f{online}§8/§f{max_online}"

  # Footer du tab
  footer:
    - "§7IP : §fplay.sparrowmc.fr"

  # Sections
  sections:

    # ─── Section 1 : Staff en ligne ───────────────────
    staff:
      title: "§c§lSTAFF EN LIGNE"
      type: STAFF_LIST
      # Permission pour apparaître dans cette liste
      permission: "kjob.display.staff"
      # Si personne n'est connecté avec la permission
      empty_text: "§8Aucun staff en ligne"
      # Format de chaque ligne de joueur : {player_name}, {rank_prefix}
      format: "§c● §f{player_name}"
      max_entries: 8

    # ─── Section 2 : Infos Serveur ────────────────────
    server:
      title: "§e§lINFOS SERVEUR"
      type: SERVER_INFO
      lines:
        - "§7Joueurs : §f{online}§8/§f{max_online}"
        - "§7Votre argent : §a${money}"
        - "§7TPS : §f{tps}"
        - "§7Vote dispo : §f{can_vote}"
        - ""
        - "§7Saison : §61"

    # ─── Section 3 : Infos Jobs ───────────────────────
    jobs:
      title: "§b§lJOBS"
      type: JOBS_INFO
      # Format par job. Placeholders : {job_name}, {level}, {progress_bar}, {progress_pct}
      job_format: "§7{job_short_name} §fLv.{level} §8[{progress_bar}§8]"
      progress_bar:
        length: 5
        filled: "§a▌"
        empty: "§8▌"
      # Afficher uniquement les jobs avec level > 0 ?
      show_only_active: false        # false = afficher tous les 5 jobs même au level 0

# Placeholders disponibles dans scoreboard.yml :
# {online}          → joueurs connectés
# {max_online}      → slots serveur
# {money}           → argent Vault du joueur
# {tps}             → TPS serveur (requiert NMS ou PAPI TPS)
# {can_vote}        → Oui / Non (requiert plugin vote hook)
# {player_name}     → nom du joueur
# {rank_prefix}     → prefix LuckPerms/PEX
# %kjob_level_<job>% → via PAPI
```

---

## messages.yml — Messages et Textes

```yaml
############################################################
# KjobUltimate - Messages
# Codes couleur : & (converti automatiquement)
# Placeholders : {player}, {job}, {level}, {xp}, {amount}
############################################################

prefix: "&8[&6Jobs&8] "

level_up:
  default: "&6Félicitations {player} ! &7Tu passes &e{job} &7niveau &6{level} !"
  mineur:  "&bMineur &7— &fNiveau &b{level} &7débloqué ! Continue de miner !"
  farmer:  "&aFarmer &7— &fNiveau &a{level} &7débloqué ! La récolte se poursuit !"
  hunter:  "&cHunter &7— &fNiveau &c{level} &7débloqué ! La chasse continue !"
  pretorien: "&4Prétorien &7— &fNiveau &4{level} &7débloqué ! En avant guerrier !"
  artisant: "&eArtisant &7— &fNiveau &e{level} &7débloqué ! L'artisanat maîtrisé !"

quest:
  complete: "&7Quête &f{quest_name} &7terminée ! &8Rendez-vous au GUI pour claim."
  claimed:  "&aRécompenses de &f{quest_name} &acollectées !"
  inventory_full: "&cInventaire plein ! Libère de la place pour récupérer tes récompenses."
  not_claimable: "&cCette quête n'est pas encore terminée."

errors:
  job_not_found: "&cJob inconnu : {job}"
  no_permission: "&cTu n'as pas la permission d'effectuer cette action."
  player_not_found: "&cJoueur {player} introuvable."

admin:
  reload_success: "&aConfiguration rechargée avec succès !"
  level_set: "&aLevel de {player} pour {job} mis à {level}."
  xp_given: "&a{xp} XP donné à {player} pour le job {job}."
  data_reset: "&aToutes les données de {player} ont été reset."
```

---

## gui.yml — Configuration des GUIs

```yaml
############################################################
# KjobUltimate - Configuration des GUIs
############################################################

# ─── GUI PRINCIPAL (vue globale tous les jobs) ────────────
jobs_overview:
  title: "§8§lMes Jobs"
  size: 54

  # Disposition des jobs dans le GUI (slots 0-53)
  # Layout conseillé :
  #  [ ][ ][ ][ ][ ][ ][ ][ ][ ]
  #  [ ][ ]J1 [ ]J2 [ ]J3 [ ][ ]
  #  [ ][ ][ ][ ][ ][ ][ ][ ][ ]
  #  [ ][ ]J4 [ ]J5 [ ]XX [ ][ ]  (XX = quêtes)
  #  [ ][ ][ ][ ][ ][ ][ ][ ][ ]
  #  [ ][ ][ ][ ][ ][ ][ ][ ][ ]

  job_slots:
    mineur: 20
    farmer: 22
    hunter: 24
    pretorien: 29
    artisant: 31

  # Slot du bouton "Quêtes globales"
  quests_button_slot: 33

  # Items de décoration (slots de remplissage)
  # CIT via nbt_cit tag pour custom textures
  decoration:
    material: STAINED_GLASS_PANE
    data: 7                          # gris foncé
    nbt_cit: "gui_separator"
    name: " "

  # Format des items de job (lore dynamique)
  job_item_lore:
    - "§7Niveau : §f{level}"
    - "§7XP : §f{xp}§7/§f{xp_next}"
    - "§7Progression : §f{progress_pct}§7%"
    - "§8[{bar}§8]"
    - ""
    - "§eClique pour voir les détails"

# ─── GUI QUÊTES ───────────────────────────────────────────
quests_gui:
  title: "§8§lQuêtes — {job_name}"
  size: 54

  # Slots pour les items de quête (pagination)
  quest_slots: [10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34]

  # Slot navigation
  prev_page_slot: 45
  next_page_slot: 53
  close_slot: 49

  # Format lore d'une quête disponible (non commencée)
  quest_lore_available:
    - "§7{description}"
    - ""
    - "§7Progression : §f0§7/§f{objective}"
    - "§7Reset : §f{reset}"
    - ""
    - "§8Min. niveau : §f{min_level}"

  # Format lore d'une quête en cours
  quest_lore_in_progress:
    - "§7{description}"
    - ""
    - "§7Progression : §f{current}§7/§f{objective} §8(§f{pct}§8%)"
    - "§7Reset : §f{reset}"
    - ""
    - "§a→ En cours..."

  # Format lore d'une quête complétée (à claim)
  quest_lore_claimable:
    - "§7{description}"
    - ""
    - "§a§l✔ QUÊTE TERMINÉE"
    - ""
    - "§7Récompenses :"
    - "§8• §f{xp_reward} §7XP {job_name}"
    - "§8• §f{money_reward}§7$"
    - ""
    - "§e⬆ Clique pour claim tes récompenses !"

  # Couleur de l'item de quête selon son état
  quest_item_material:
    available: PAPER
    in_progress: MAP
    claimable: EMERALD
    locked: BARRIER              # quête verrouillée (niveau insuffisant)
```
