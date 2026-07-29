# KjobUltimate — Architecture Globale

> Serveur cible : **KhopeSpigot (PandaSpigot 1.8.8)** — Java 8 — NMS v1_8_R3
> Stockage : **SQLite** (sqlite-jdbc shadé dans le jar)
> GUI : **Kgui ContentProviderAPI** (providers enregistrés au démarrage)
> Plugin principal de la suite SparrowMC. Gère les jobs, quêtes, HUD et scoreboard.

---

## Décisions Clés

| Aspect | Décision |
|---|---|
| Stockage | SQLite — async toujours, cache RAM sur main thread |
| GUI | Kgui via ContentProviderAPI — fallback chat si Kgui absent |
| Job slots | Configurable — défaut 1, déblocage au level 5 du job principal |
| Premier join | Choix joueur — GUI sélection s'ouvre automatiquement |
| Kchat | KjobsUltimate propriétaire du header/footer tab |
| Kstacker | Multiplicateur kills via META_KILL_MULTIPLIER (cap absolu 3) |

---

## 1. Vue d'ensemble

```
[Joueur mine un bloc]
        │
        ▼
[MinerListener.onBlockBreak]
  ├─ Anti-abuse check (silk touch, creative, cooldown position)
  ├─ jobManager.getJob("mineur") → lire les actions config
  ├─ playerDataManager.get(player) → données joueur en RAM
  ├─ data.addXP("mineur", xp) → calcule levelup si nécessaire
  │     └─ si levelup → LevelUpEvent interne → notifie HudManager
  ├─ hudManager.markDirty(player)  → bossbar à mettre à jour
  ├─ hudManager.showActionBarXP(player, xp, "mineur")  → hotbar +Xxp
  └─ questManager.progressQuest(player, "mining", block, 1)
        └─ si quête complète → marquer "claim ready" dans questData
```

---

## 2. Structure des Packages

```
me.krunsh.kjobultimate/
│
├── KjobUltimate.java              ← Main plugin, onEnable/onDisable
│
├── api/
│   ├── KjobAPI.java               ← API publique (interface)
│   └── KjobAPIImpl.java           ← Implémentation
│
├── config/
│   ├── ConfigManager.java         ← Charge config.yml + accès centralisé
│   ├── JobsConfigLoader.java      ← Charge jobs/<jobId>.yml → construit JobConfig objects
│   ├── QuestsConfigLoader.java    ← Charge quests/quests_<jobId>.yml → QuestConfig objects
│   ├── HudConfigLoader.java       ← Charge hud.yml
│   ├── TabConfigLoader.java       ← Charge tab.yml
│   ├── MessagesConfig.java        ← Charge messages.yml, gère les messages formatés
│   └── SoundsConfig.java          ← Charge sounds.yml
│
├── jobs/
│   ├── Job.java                   ← Modèle d'un job (nom, actions, niveaux, XP curve)
│   ├── JobAction.java             ← Action = (xp, money, description, conditions)
│   ├── JobType.java               ← Enum ou String selon config (MINING, FARMING…)
│   └── JobManager.java            ← Registry des jobs chargés, accès par clé
│
├── quests/
│   ├── Quest.java                 ← Définition d'une quête (depuis quests.yml)
│   ├── QuestData.java             ← Progression d'un joueur sur une quête
│   ├── QuestType.java             ← Enum : MINING, FARMING, HUNTING, CRAFTING, etc.
│   ├── QuestManager.java          ← Orchestrateur : progression, reset, vérification
│   └── QuestReward.java           ← Modèle des récompenses (xp, money, items, cmds)
│
├── data/
│   ├── PlayerData.java            ← Données d'un joueur (jobs levels, xp, quêtes, HUD state)
│   │                                 Champs HUD : displayJob, lastXpTimestamp, hudEnabled
│   ├── PlayerDataManager.java     ← Cache en RAM + persistance YAML/SQLite
│   └── DataStorage.java           ← Interface (implémentations : YamlStorage, SQLiteStorage)
│
├── hud/
│   ├── BossBarManager.java        ← Fake withers NMS par joueur, timer disparition, toggle
│   ├── ActionBarManager.java      ← Messages hotbar (+xp), accumulation configurable, toggle
│   ├── AchievementManager.java    ← Envoie le PacketPlayOutStatistic de level up
│   └── HudManager.java            ← Façade : onXpGain, triggerLevelUp, toggleHud
│
├── slots/
│   └── JobSlotManager.java        ← Système de slots : déblocage, assignation, displayJob
│
├── scoreboard/
│   └── TabManager.java            ← Header/Footer NMS via packets, mise à jour 40 ticks
│
├── listeners/
│   ├── MinerListener.java         ← BlockBreakEvent → job mineur
│   ├── FarmerListener.java        ← BlockBreakEvent → job farmer (check mature)
│   ├── HunterListener.java        ← EntityDeathEvent → job hunter (+ Kstacker META_KILL_MULTIPLIER)
│   ├── PretorianListener.java     ← PlayerDeathEvent (PvP) + PlayerItemConsumeEvent (items consommables)
│   ├── ArtisanListener.java       ← CraftItemEvent (vanilla) + KcraftCraftCompleteEvent (Kcraft custom)
│   ├── PlayerJoinListener.java    ← Charge données async, init HUD, init Tab, first join GUI
│   └── PlayerQuitListener.java    ← Sauvegarde async, nettoie HUD/entités
│
├── gui/
│   ├── providers/
│   │   ├── JobsOverviewProvider.java ← DynamicContentProvider pour vue globale jobs
│   │   └── QuestListProvider.java    ← DynamicContentProvider pour liste quêtes paginée
│   └── GUIUtils.java               ← Helpers (créer item NBT, appliquer CIT tag, etc.)
│
├── commands/
│   ├── KjobCommand.java           ← /job, /jobs : ouvre GUI + sous-cmds joueur (hud, quests, info)
│   └── KjobAdminCommand.java      ← /kjob, /kjobs : reload, setlevel, givexp, reset, etc.
│
├── hooks/
│   ├── VaultHook.java             ← Soft hook Economy via Vault
│   ├── PlaceholderAPIHook.java    ← Expansion PAPI : %kjob_level_mineur% etc.
│   ├── KchatHook.java             ← Désactiver header/footer Kchat si configuré
│   ├── KguiHook.java              ← Enregistrer les ContentProviders dans Kgui
│   ├── KstackerHook.java          ← Lire META_KILL_MULTIPLIER (cap absolu = 3)
│   ├── KenchantHook.java          ← Lire NBT kenchant_extra_kill (via NBT-API)
│   └── KcraftHook.java            ← Écouter KcraftCraftCompleteEvent pour l'XP Artisant
│
└── util/
    ├── NMSUtil.java               ← Utilitaires NMS v1_8_R3 (sendPacket, etc.)
    ├── CropUtil.java              ← Vérification maturité cultures (1.8.8 via getData())
    ├── ColorUtil.java             ← Traduction codes couleurs &
    └── BarRenderer.java           ← Génère la barre de progression texte (████░░)
```

---

## 3. Dépendances Externes

```
KjobUltimate
│
├── [obligatoire]  Spigot API 1.8.8 (KhopeSpigot/PandaSpigot)
│                  → Bukkit events, ItemStack, Inventory, etc.
│
├── [recommandé]   Vault
│                  → Economy hook (money récompenses)
│                  → Si absent = désactiver features économie, log warning
│
├── [recommandé]   PlaceholderAPI
│                  → Exposer les placeholders %kjob_xxx% aux autres plugins
│                  → Si absent = les placeholders ne fonctionnent pas dans Kchat/Kgui
│
├── [optionnel]    Kchat
│                  → Hook pour intégrer le scoreboard/tab directement dans Kchat
│                  → Si absent = KjobUltimate gère le scoreboard seul
│
└── [optionnel]    Kgui
                   → Déléguer les GUI à Kgui via son API ContentProvider
                   → Si absent = GUI codés en interne (toujours fonctionnel)
```

---

## 4. Flux de Données Complet

### 4.1 Démarrage du Plugin

```
onEnable()
  ├─ ConfigManager.loadAll()
  │     ├─ config.yml
  │     ├─ jobs.yml → JobManager.loadJobs()
  │     ├─ quests.yml → QuestManager.loadQuests()
  │     ├─ hud.yml → HudManager.loadConfig()
  │     ├─ scoreboard.yml → ScoreboardManager.loadConfig()
  │     ├─ messages.yml → MessagesConfig.load()
  │     └─ sounds.yml → SoundsConfig.load()
  │
  ├─ DataStorage.init() (YAML ou SQLite selon config)
  │
  ├─ Enregistrement des Listeners (Bukkit.getPluginManager().registerEvents)
  │
  ├─ Démarrage du Scheduler global (1 runnable, toutes les 40 ticks)
  │
  ├─ Hooks optionnels (Vault, PAPI, Kchat, Kgui) → détectés et initialisés
  │
  └─ Pour chaque joueur déjà connecté (restart à chaud) :
        PlayerDataManager.load(player.getUniqueId())
        HudManager.init(player)
        ScoreboardManager.init(player)
```

### 4.2 Gain d'XP (exemple Mineur)

```
BlockBreakEvent
  ↓
MinerListener.onBlockBreak()
  ├─ [1] AntiAbuseService.check(player, block, event)
  │     ├─ isCreative ? → return (pas d'XP)
  │     ├─ hasSilkTouch && isSilkBlocked(block) ? → return
  │     ├─ cooldownMap.contains(blockLocation) && !elapsed ? → return
  │     └─ passe le check → continue
  │
  ├─ [2] job = JobManager.getJob("mineur")
  │       action = job.getAction(block.getType())  → null si non configuré
  │
  ├─ [3] data = PlayerDataManager.get(player)
  │       xpToAdd = action.getXp()
  │       wasLevel = data.getLevel("mineur")
  │
  ├─ [4] data.addXP("mineur", xpToAdd)
  │         → si nouveau level > wasLevel :
  │               fire LevelUpEvent(player, "mineur", newLevel)
  │
  ├─ [5] HudManager.onXpGain(player, "mineur", xpToAdd)
  │         → ActionBarManager.accumulate(player, xpToAdd, "mineur")
  │         → BossBarManager.markDirty(player)
  │
  └─ [6] QuestManager.progress(player, QuestType.MINING, block.getType().name(), 1)
             → check quête complète → si oui, markClaimReady(player, questId)
```

### 4.3 Scheduler Global (toutes les 40 ticks = 2 secondes)

```
GlobalScheduler.run()
  │
  ├─ Pour chaque joueur connecté :
  │     ├─ ActionBarManager.tick(player)
  │     │     → si message actionbar en attente : sendPacketPlayOutChat(player, msg, 2)
  │     │     → décrémenter timer, effacer si expiré
  │     │
  │     └─ BossBarManager.tick(player)
  │           → si dirty == true :
  │                 sendBossBarNameUpdate(player)
  │                 dirty = false
  │           → si not dirty : rien (bossbar conserve son dernier state)
  │
  └─ QuestManager.checkDailyReset()
        → si heure = minuit et pas encore reset aujourd'hui → reset quêtes daily
```

### 4.4 Level Up

```
LevelUpEvent(player, jobId, newLevel)
  ↓
HudManager.onLevelUp(player, jobId, newLevel)
  ├─ [1] AchievementManager.sendAchievementPopup(player, jobId, newLevel)
  │         → PacketPlayOutStatistic (achievement vanille détourné)
  │         → item affiché = bloc custom CIT non obtensible
  │         → texte = config hud.yml achievements.format
  │
  ├─ [2] SoundManager.play(player, "on_level_up", jobId)
  │         → PacketPlayOutNamedSoundEffect (son .ogg depuis resource pack)
  │
  ├─ [3] MessagesConfig.send(player, "level_up", {job: jobId, level: newLevel})
  │
  ├─ [4] BossBarManager.markDirty(player)  ← barre se remet à 0% nouveau niveau
  │
  └─ [5] JobRewardManager.applyRewards(player, jobId, newLevel)
             → commandes console, give items, argent Vault
```

---

## 5. Modèle de Données en Mémoire (PlayerData)

```java
class PlayerData {
    UUID uuid;

    // Niveaux et XP par job
    Map<String, Integer> jobLevels;     // "mineur" → 15
    Map<String, Integer> jobXP;         // "mineur" → 7320 (XP actuel dans niveau courant)
    Map<String, Integer> dailyXP;       // "mineur" → XP accumulé aujourd'hui (pour daily cap)

    // Quêtes
    Map<String, QuestProgress> activeQuests;  // questId → progression
    Set<String> claimableQuests;              // quêtes complétées non claimées
    Map<String, Long> lastDailyReset;         // jobId → timestamp UNIX dernier reset daily
    Map<String, Long> lastWeeklyReset;        // jobId → timestamp UNIX dernier reset weekly
    Map<String, List<String>> assignedDailyQuests;   // jobId → [questId, ...]
    Map<String, List<String>> assignedWeeklyQuests;  // jobId → [questId, ...]

    // Anti-abuse
    Map<String, Long> blockCooldowns;    // "x,y,z,world" → timestamp expiration
    Map<String, Long> mobCooldowns;      // entityUUID.toString() → timestamp expiration
    Map<String, Long> pvpTargetCooldowns; // targetUUID.toString() → timestamp expiration

    // HUD state (persistant en DB)
    boolean hudEnabled;                  // false = bossbar + actionbar masquées (/jobs hud)
    String displayJob;                   // dernier jobId ayant donné XP (bossbar affichée)
    long lastXpTimestamp;               // System.currentTimeMillis() du dernier gain XP

    // Slots/Places de métiers
    int unlockedSlots;                   // nombre de places débloquées (défaut: 1)
    Map<Integer, String> slotJobs;       // slotIndex → jobId actif dans ce slot
    int prestigeCount;                   // réservé pour V2 (toujours 0 en V1)
}
```

---

## 6. Modèle de Données Job (en RAM, chargé depuis jobs.yml)

```java
class Job {
    String id;                              // "mineur"
    String displayName;                     // "§bMineur"
    int maxLevel;                           // 50
    Map<Integer, Integer> xpPerLevel;       // niveau → XP requis (custom_levels)
    String xpCurveType;                     // "custom" | "linear" | "exponential"
    int xpCurveBase;                        // si linear/expo
    double xpCurveMultiplier;

    Map<String, JobAction> actions;         // "mine_stone" → JobAction{xp:5, money:2}
    Map<Integer, List<String>> levelRewards;// niveau → liste de commandes console
}

class JobAction {
    String id;
    int xp;
    int money;
    boolean blockXPWithSilkTouch;          // bloquer XP si silktouch
    boolean requiresMature;                // pour farming uniquement
    String description;
}
```

---

## 7. Intégration NMS (KhopeSpigot 1.8.8)

Les fonctionnalités suivantes nécessitent NMS `net.minecraft.server.v1_8_R3` :

| Feature | Classe NMS | Packet |
|---|---|---|
| BossBar titre custom | `EntityWither` + `DataWatcher` | `PacketPlayOutEntityMetadata` |
| BossBar spawn | `EntityWither` | `PacketPlayOutSpawnEntityLiving` |
| BossBar suivi joueur | — | `PacketPlayOutEntityTeleport` |
| ActionBar | — | `PacketPlayOutChat` (position=2) |
| Achievement popup | `StatisticManager` | `PacketPlayOutStatistic` |
| Son custom | — | `PacketPlayOutNamedSoundEffect` |
| Tab header/footer | — | `PacketPlayOutPlayerListHeaderFooter` |

```java
// Exemple sendPacket utilitaire
public static void sendPacket(Player player, Object packet) {
    Object handle = player.getClass().getMethod("getHandle").invoke(player);
    Object connection = handle.getClass().getField("playerConnection").get(handle);
    connection.getClass().getMethod("sendPacket", /* Packet */ ).invoke(connection, packet);
}
```

Voir [FAISABILITE-JOBS-SYSTEM.md](FAISABILITE-JOBS-SYSTEM.md) et [HUD-BOSSBAR-ACTIONBAR.md](HUD-BOSSBAR-ACTIONBAR.md) pour les extraits de code complets.

---

## 8. Registre des Placeholders PAPI

| Placeholder | Description |
|---|---|
| `%kjob_level_<job>%` | Niveau actuel du joueur dans le job |
| `%kjob_xp_<job>%` | XP actuel dans le niveau en cours |
| `%kjob_xp_next_<job>%` | XP requis pour le prochain niveau |
| `%kjob_progress_<job>%` | Pourcentage de progression (0-100) |
| `%kjob_bar_<job>%` | Barre de progression texte (████░░) |
| `%kjob_active%` | Nom du job actif pour la bossbar |
| `%kjob_quests_done%` | Nombre de quêtes complétées aujourd'hui |
| `%kjob_quests_active%` | Nombre de quêtes actives |

---

## 9. Commandes

### Commandes Joueurs (`/job`, `/jobs` — alias identiques)

| Commande | Permission | Description |
|---|---|---|
| `/jobs` | (aucune) | Ouvre le GUI global des jobs |
| `/jobs quests` | (aucune) | Ouvre directement le GUI des quêtes |
| `/jobs hud` | (aucune) | **Toggle HUD ON/OFF** (bossbar + actionbar) — résultat en chat |
| `/jobs info <job>` | (aucune) | Affiche les stats d'un job en chat |

**Aliases en `plugin.yml`** : `/job`, `/jobs` (joueurs) — `/kjob`, `/kjobs`, `/kjobsultimate`, `/kjobultimate` (admin).
Toute commande contenant `k` devant `job` est admin.

### Commandes Admin (`/kjob` + aliases)

| Commande | Permission | Description |
|---|---|---|
| `/kjob reload` | `kjob.admin` | Recharge toutes les configs à chaud |
| `/kjob addxp <joueur> <montant> <job>` | `kjob.admin` | Donne X XP à un joueur dans un job |
| `/kjob removexp <joueur> <montant> <job>` | `kjob.admin` | Retire X XP à un joueur dans un job |
| `/kjob setlvl <joueur> <niveau> <job>` | `kjob.admin` | Force le niveau d'un joueur |
| `/kjob reset <joueur> <job>` | `kjob.admin` | Reset complètement un job pour un joueur |
| `/kjob seejobs <joueur>` | `kjob.admin` | GUI : tous les jobs/XP/niveaux du joueur |
| `/kjob spy <joueur> <temps>` | `kjob.admin` | Stats d'XP farmé sur X minutes (temps en minutes) |
| `/kjob addjob <joueur> <job>` | `kjob.admin` | Force l'ajout d'un job sans le niveau requis |
| `/kjob bonus <all\|joueur> <montant> <job\|all>` | `kjob.admin` | Multiplic. XP persistant au reboot |
| `/kjob migrate` | `kjob.admin` | Migre les données KJob2 → KjobsUltimate |
| `/kjob questreset <joueur> <job>` | `kjob.admin` | Force le reset des quêtes daily/weekly |
| `/kjob questgive <joueur> <questId>` | `kjob.admin` | Complète manuellement une quête |
| `/kjob cap <joueur> <job>` | `kjob.admin` | Affiche le cap XP daily actuel du joueur |
