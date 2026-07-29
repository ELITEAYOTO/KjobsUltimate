# KjobUltimate — Plan d'Implémentation

> Ordre recommandé : du socle vers les couches hautes.
> Chaque phase est autonome et testable indépendamment.

---

## Légende

| Symbole | Signification |
|---|---|
| 🔴 | Bloquant — doit être fait avant la prochaine phase |
| 🟡 | Important — peut être fait en parallèle |
| 🟢 | Optionnel / peaufinage |
| ✅ | Terminé |
| 📌 | Décision requise avant de coder (voir QUESTIONS-LIST.md) |

---

## Phase 0 — Préparation (Avant toute ligne de code)

- [ ] 📌 Répondre aux questions critiques de [QUESTIONS-LIST.md](QUESTIONS-LIST.md) :
  - Version serveur cible (1.8.8 KhopeSpigot confirmé ?)
  - Stockage données (YAML ou SQLite ?)
  - Kgui ou GUI interne ?
  - Max jobs par joueur ?
  - Jobs par défaut (tous au join ou activation manuelle) ?

- [ ] 🔴 Créer le projet Maven (copier pom.xml de Kchat comme base)
  - `groupId: me.krunsh.kjobultimate`
  - `artifactId: KjobUltimate`
  - Dépendances : SpigotAPI 1.8.8, Vault (provided), PAPI (provided)

- [ ] 🔴 Créer la structure de packages (tous vides) pour avoir une vision claire dès le début

---

## Phase 1 — Socle : Structure de Données et Config (Semaine 1)

**Objectif** : Charger les configs, créer les modèles de données, persister les joueurs.

### 1.1 Modèles de Données

- [ ] 🔴 `Job.java` — id, displayName, maxLevel, actions, xpPerLevel, levelRewards
- [ ] 🔴 `JobAction.java` — xp, money, material/entity, silktouch, requiresMature
- [ ] 🔴 `PlayerData.java` — jobLevels, jobXP, questProgress, blockCooldowns, activeJobDisplay
- [ ] 🔴 `Quest.java` — id, job, type, target, objective, reset, minLevel, rewards
- [ ] 🔴 `QuestData.java` — progress, completed, claimed, completedAt
- [ ] 🔴 `QuestReward.java` — xp, money, items, commands

### 1.2 ConfigManager

- [ ] 🔴 `ConfigManager.java` — load config.yml, expose les settings globaux
- [ ] 🔴 `JobsConfigLoader.java` — parse jobs.yml → construit Map<String, Job>
- [ ] 🔴 `QuestsConfigLoader.java` — parse quests.yml → construit Map<String, Quest>
- [ ] 🔴 `MessagesConfig.java` — charge messages.yml, méthode `send(player, key, params)`
- [ ] 🟡 `HudConfigLoader.java` — charge hud.yml
- [ ] 🟡 `SoundsConfig.java` — charge sounds.yml

**Livrables Phase 1** : Le plugin démarre, charge les configs, log les jobs chargés. Zéro crash.

---

## Phase 2 — Persistance Joueurs (Semaine 1-2)

**Objectif** : Charger/sauvegarder les données joueurs fiablement.

- [ ] 🔴 `PlayerDataManager.java`
  - Cache `Map<UUID, PlayerData>` en RAM
  - `load(UUID)` depuis YAML
  - `save(UUID)` vers YAML
  - `saveAll()` à l'arrêt du serveur
  - `get(Player)` avec load auto si absent du cache

- [ ] 🔴 `PlayerJoinListener.java`
  - `PlayerLoginEvent` → `playerDataManager.load(uuid)`
  - Si premier join → créer les données avec tous les jobs au level 0 (si `default_all_jobs: true`)

- [ ] 🔴 `PlayerQuitListener.java`
  - `PlayerQuitEvent` → `playerDataManager.save(uuid)`

- [ ] 🟡 Scheduler AutoSave — `saveAll()` toutes les X minutes (configurable)

- [ ] 🟡 `data.addXP(jobId, amount)` avec détection levelup
  - Incrémenter XP
  - Si XP >= xpForNextLevel → levelup, réinitialiser XP, incrémenter niveau
  - Retourner `boolean leveledUp` pour que les listeners sachent quoi déclencher

**Livrables Phase 2** : Join = données chargées. Quit = données sauvées. `/kjobadmin info <player>` affiche les données en chat.

---

## Phase 3 — Listeners XP + Anti-Abuse (Semaine 2)

**Objectif** : Les jobs donnent de l'XP. Les protections sont en place.

### 3.1 AntiAbuseService

- [ ] 🔴 `AntiAbuseService.java`
  - `isCreative(player)` — bloquer XP
  - `hasSilkTouch(player)` — vérifier enchantement
  - `isCropMature(block)` — via `CropUtil.isMature()`
  - `isBlockOnCooldown(data, location)` / `setBlockCooldown(data, location)`
  - `isPvPTargetOnCooldown(data, targetUUID)` / `setPvPTargetCooldown(data, targetUUID)`

- [ ] 🔴 `CropUtil.java` — voir [ANTI-ABUSE.md](ANTI-ABUSE.md) section 3

### 3.2 Listeners

- [ ] 🔴 `MinerListener.java` — BlockBreakEvent → mineur (blocs config + anti-abuse)
- [ ] 🔴 `FarmerListener.java` — BlockBreakEvent → farmer (check mature via CropUtil)
- [ ] 🔴 `HunterListener.java` — EntityDeathEvent → hunter (entity config)
- [ ] 🟡 `PretorianListener.java` — PlayerDeathEvent → prétorien (PvP, anti-abuse cooldown)
- [ ] 🟡 `ArtisanListener.java` — CraftItemEvent → artisant (material résultat)

### 3.3 Tests Phase 3

- [ ] Miner un bloc de stone → XP mineur incrémenté
- [ ] Miner avec SilkTouch sur diamond_ore → pas d'XP
- [ ] Casser blé immature → pas d'XP Farmer
- [ ] Casser blé mature → XP Farmer ok
- [ ] Tuer 10 zombies → level up hunter (si XP configuré petit)

**Livrables Phase 3** : Tous les jobs donnent de l'XP. Anti-abus fonctionnel. Level up déclenché.

---

## Phase 4 — HUD : BossBar + ActionBar (Semaine 2-3)

**Objectif** : Le joueur voit sa progression en temps réel.

- [ ] 🔴 `NMSUtil.java` — sendPacket utilitaire, accès playerConnection NMS
- [ ] 🔴 `ColorUtil.java` — traduction codes couleur &
- [ ] 🔴 `BarRenderer.java` — génère "§a████░░░░" à partir d'un float 0-1

- [ ] 🔴 `BossBarManager.java`
  - `init(player)` → créer EntityWither NMS, envoyer SpawnEntityLiving
  - `update(player, title, progress)` → envoyer PacketPlayOutEntityMetadata
  - `remove(player)` → envoyer PacketPlayOutEntityDestroy
  - `markDirty(UUID)` / `isDirty(UUID)` / `clearDirty(UUID)`
  - `checkTeleport(player)` → retéléporter si joueur déplacé > 100 blocs

- [ ] 🔴 `ActionBarManager.java`
  - `onXpGain(playerId, jobId, amount)` → accumule + reset timer
  - `tick(player)` → envoie PacketPlayOutChat position=2 si timer > 0

- [ ] 🔴 Scheduler Global (dans `KjobUltimate.java onEnable`)
  - Toutes les 40 ticks
  - BossBar dirty flush
  - ActionBar tick
  - BossBar wither check teleport

- [ ] 🟡 Intégrer `PlayerJoinListener` → `bossBarManager.init(player)`
- [ ] 🟡 Intégrer `PlayerQuitListener` → `bossBarManager.remove(player)`

**Livrables Phase 4** : Bossbar visible au join, se met à jour quand on mine, titre configurable.

---

## Phase 5 — Level Up : Achievement + Son + Title (Semaine 3)

**Objectif** : La montée de niveau est spectaculaire.

- [ ] 🔴 `SoundManager.java` — `playSound(player, soundName, volume, pitch)` via NMS
- [ ] 🔴 `AchievementManager.java`
  - `sendLevelUpPopup(player, jobId)` → PacketPlayOutStatistic
  - `sendLevelUpTitle(player, jobId, level)` → PacketPlayOutTitle TIMES + SUBTITLE + TITLE

- [ ] 🔴 `HudManager.java` — façade qui orchestre : bossbar dirty + actionbar + achievement + son + message + rewards

- [ ] 🟡 `JobRewardManager.java` — exécuter les commandes de `level_rewards` au level up

- [ ] 🔴 Resource pack minimal :
  - `sounds.json` avec les entrées custom
  - `lang/fr_FR.lang` avec les textes des achievements overridés
  - Texture CIT pour l'item achievement (le bloc non obtensible)

**Livrables Phase 5** : Level up déclenche popup + son + title + message chat + récompenses.

---

## Phase 6 — GUI (Semaine 3-4)

**Objectif** : Le joueur peut voir ses jobs et naviguer dans l'interface.

- [ ] 🔴 `GUIUtils.java` — créer items CIT, buildBar(), déco
- [ ] 🔴 `JobsOverviewGUI.java` — GUI 54 slots principal
- [ ] 🔴 `GUIListener.java` — InventoryClickEvent, dispatch vers les GUI
- [ ] 🟡 `JobDetailGUI.java` — détail d'un job (stats + quêtes du job)
- [ ] 🟡 `KjobCommand.java` — `/jobs` ouvre `JobsOverviewGUI.open(player)`

**Livrables Phase 6** : `/jobs` ouvre le GUI. Les 5 jobs sont affichés avec leur progression. Navigation fonctionne.

---

## Phase 7 — Système de Quêtes (Semaine 4-5)

**Objectif** : Les quêtes sont visibles, progressent, et les récompenses sont claimables.

- [ ] 🔴 `QuestManager.java`
  - `loadQuests()` — parse quests.yml
  - `progress(player, type, target, amount)` — incrémenter progression
  - `claimReward(player, questId)` — donner items/money + marquer claimed
  - `checkDailyReset()` — reset quotidien à minuit
  - `checkWeeklyReset()` — reset hebdomadaire

- [ ] 🔴 Intégrer progress() dans les listeners :
  - `MinerListener` → `questManager.progress(player, MINING, "STONE", 1)`
  - `FarmerListener` → `questManager.progress(player, FARMING, "WHEAT", 1)`
  - `HunterListener` → `questManager.progress(player, HUNTING, "ZOMBIE", 1)`
  - etc.

- [ ] 🔴 `QuestGUI.java` — affichage paginé avec états (disponible/en cours/claimable/verrouillé)
- [ ] 🔴 Intégrer `QuestGUI` dans `JobDetailGUI` (bouton "Quêtes")

- [ ] 🟡 Notification en chat quand quête complétée (+ son)
- [ ] 🟡 Scheduler reset daily/weekly dans le scheduler global

**Livrables Phase 7** : Les quêtes progressent. Le GUI affiche l'état. Le claim fonctionne.

---

## Phase 8 — Scoreboard / Tab (Semaine 5)

**Objectif** : Le tab affiche les infos configurables.

- [ ] 🟡 `ScoreboardManager.java` — header/footer via PacketPlayOutPlayerListHeaderFooter
- [ ] 🟡 `ScoreboardConfig.java` — parse scoreboard.yml
- [ ] 🟡 Intégration dans PlayerJoinListener → `scoreboardManager.init(player)`
- [ ] 🟡 Intégration dans le scheduler global → `scoreboardManager.refresh(player)`
- [ ] 🟡 `KchatHook.java` — désactiver tab Kchat si conflit

**Livrables Phase 8** : Tab affiche header/footer avec jobs, argent, staff.

---

## Phase 9 — Placeholders + Intégrations (Semaine 5-6)

- [ ] 🟡 `PlaceholderAPIHook.java` — exposer `%kjob_level_<job>%`, `%kjob_xp_<job>%`, etc.
- [ ] 🟡 `VaultHook.java` — charger Economy depuis Vault
- [ ] 🟡 `KguiHook.java` — optionnel, pour déléguer les GUI à Kgui

---

## Phase 10 — Commandes Admin (Semaine 6)

- [ ] 🟡 `KjobAdminCommand.java`
  - `/kjobadmin reload` — recharger configs + notifier tous les managers
  - `/kjobadmin setlevel <player> <job> <level>`
  - `/kjobadmin givexp <player> <job> <xp>`
  - `/kjobadmin reset <player>`
  - `/kjobadmin info <player>` — afficher données en chat

---

## Phase 11 — Tests, Optimisation, Production (Semaine 6-7)

- [ ] 🟢 Tests de charge : simuler 50+ joueurs minant simultanément, vérifier TPS
- [ ] 🟢 Profiler le scheduler global (Java Flight Recorder ou spark)
- [ ] 🟢 Vérifier que la map `blockCooldowns` est nettoyée correctement
- [ ] 🟢 Tester le reload à chaud sans redémarrage
- [ ] 🟢 Vérifier la sauvegarde des données en cas de crash (kill -9)
- [ ] 🟢 Désactiver tous les logs debug en production
- [ ] 🟢 Vérifier les edge cases : joueur se déconnecte mid-level-up, reload pendant quête en cours

---

## Calendrier Estimatif

| Semaine | Phases | Livrables |
|---|---|---|
| S1 | 0, 1, 2 | Plugin démarre, config chargée, données persistées |
| S2 | 3 | XP fonctionne pour les 5 jobs avec anti-abuse |
| S3 | 4, 5 | BossBar + ActionBar + Level Up complet |
| S4 | 6 | GUI principal fonctionnel |
| S5 | 7, 8 | Quêtes complètes + Tab scoreboard |
| S6 | 9, 10 | PAPI + Admin commands |
| S7 | 11 | Tests, optimisation, release |

---

## Dépendances entre Phases

```
Phase 0 (décisions)
    └─→ Phase 1 (modèles)
            └─→ Phase 2 (persistance)
                    └─→ Phase 3 (listeners XP)
                            ├─→ Phase 4 (HUD bossbar)
                            │       └─→ Phase 5 (level up)
                            ├─→ Phase 6 (GUI) ─→ Phase 7 (quêtes)
                            └─→ Phase 8 (scoreboard)
                                        └─→ Phase 9 (PAPI)
                                                    └─→ Phase 10 (admin)
                                                                └─→ Phase 11 (prod)
```
