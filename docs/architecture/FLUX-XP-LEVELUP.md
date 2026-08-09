# KjobsUltimate — Flux XP et Level Up

> Référence complète du chemin d'un événement depuis le joueur jusqu'à la persistance.
> Tout le flux doit être **synchrone** (Bukkit main thread) sauf la sauvegarde SQLite.

---

## 1. Diagramme Global (exemple : Mineur)

```
[Joueur casse un bloc]
        │
        ▼
BlockBreakEvent (EventPriority.NORMAL)
        │
        ▼
MinerListener.onBlockBreak(event)
  │
  ├─ [GATE 1] player.getGameMode() == CREATIVE → return (XP bloqué)
  ├─ [GATE 2] player.getGameMode() == SPECTATOR → return
  ├─ [GATE 3] jobSlotManager.getActiveJobs(data) ne contient pas "mineur" → return
  ├─ [GATE 4] action = jobsConfig.getAction("mineur", block.getType()) == null → return
  ├─ [GATE 5] action.silktouch == true ET hasSilkTouch(player) → return
  ├─ [GATE 6] antiAbuseService.isBlockOnCooldown(data, location) → return
  ├─ [GATE 7] antiAbuseService.isDailyCapReached(data, "mineur") → return
  │
  ├─ xp = action.getXp()  (valeur brute de jobs/mineur.yml)
  │
  ├─ [BONUS XP] Appliquer multiplicateurs :
  │     ├─ xp *= getPermissionMultiplier(player)    (rang premium, etc.)
  │     ├─ xp *= getEventMultiplier()               (event weekend x2, etc.)
  │     └─ xp = (int) Math.floor(xp)
  │
  ├─ antiAbuseService.setBlockCooldown(data, location)  ← marquer la position
  ├─ antiAbuseService.addDailyXP(data, "mineur", xp)   ← mettre à jour le cap
  │
  ├─ LevelUpResult result = data.addXP("mineur", xp)
  │     └─ [voir section 2 — addXP interne]
  │
  ├─ [HUD STATE] Mettre à jour les champs de tracking HUD dans PlayerData :
  │     ├─ data.displayJob = "mineur"              ← toujours, à chaque gain XP
  │     └─ data.lastXpTimestamp = System.currentTimeMillis()
  │
  ├─ hudManager.onXpGain(player, "mineur", xp, result)
  │     ├─ [GATE HUD] data.hudEnabled == false → skip bossbar + actionbar
  │     ├─ actionBarManager.onXpGain(player, "mineur", xp)
  │     │     └─ [voir HUD-BOSSBAR-ACTIONBAR.md § 2 — accumulation]
  │     ├─ bossBarManager.onXpGain(player, "mineur")
  │     │     └─ [voir HUD-BOSSBAR-ACTIONBAR.md § 1 — dirtySet, timer]
  │     └─ [si result.leveledUp] → déclencher le flux Level Up (section 3)
  │
  └─ questManager.onMining(player, block.getType(), 1)
        └─ [voir section 4 — progression quête]
```

---

## 2. addXP — Logique Interne de PlayerData

```java
public LevelUpResult addXP(String jobId, int xpToAdd) {
    int currentLevel = jobLevels.getOrDefault(jobId, 1);
    int currentXP    = jobXP.getOrDefault(jobId, 0);

    // Cap au niveau max configuré pour ce job
    int maxLevel = jobManager.getJob(jobId).getMaxLevel();
    if (currentLevel >= maxLevel) {
        return LevelUpResult.maxLevel();  // Pas d'XP gagné si déjà au max
    }

    currentXP += xpToAdd;
    int levelsGained = 0;

    // Boucle multi-level (si l'XP permet plusieurs niveaux d'un coup)
    while (currentLevel < maxLevel) {
        int xpRequired = jobManager.getJob(jobId).getXpForLevel(currentLevel);
        if (currentXP < xpRequired) break;
        currentXP -= xpRequired;
        currentLevel++;
        levelsGained++;
    }

    // Sauvegarder dans la structure en RAM
    jobLevels.put(jobId, currentLevel);
    jobXP.put(jobId, currentXP);

    // Vérifier déblocage de slots si pertinent
    // (délégué à JobSlotManager via le listener qui appellera checkAndUnlockSlots)

    return new LevelUpResult(levelsGained > 0, levelsGained, currentLevel, currentXP);
}
```

### LevelUpResult

```java
public class LevelUpResult {
    boolean leveledUp;      // true si au moins 1 niveau gagné
    int levelsGained;       // nombre de niveaux gagnés (cas rare : 2+ d'un coup)
    int newLevel;           // niveau final après l'ajout
    int remainingXP;        // XP restant après le/les level up
}
```

---

## 3. Flux Level Up

Déclenché si `result.leveledUp == true`. Appelé depuis `HudManager.onXpGain()`.

```
[LevelUpResult.leveledUp == true]
        │
        ▼
HudManager.triggerLevelUp(player, jobId, newLevel)
  │
  ├─ 1. Bossbar mise à jour immédiate (markDirty → flush forcé)
  │
  ├─ 2. AchievementManager.sendLevelUpPopup(player, jobId, newLevel)
  │       └─ PacketPlayOutStatistic → popup achievement vanilla en haut à droite
  │
  ├─ 3. SoundManager.play(player, sounds.yml → "levelup.<jobId>")
  │
  ├─ 4. MessagesConfig.send(player, "levelup.message", {job, level})
  │       └─ Message chat configurable
  │
  ├─ 5. [Si config title_enabled: true]
  │       TitleManager.sendLevelUpTitle(player, jobId, newLevel)
  │       └─ PacketPlayOutTitle TIMES + TITLE + SUBTITLE
  │
  ├─ 6. JobRewardManager.applyLevelRewards(player, jobId, newLevel)
  │       └─ Lire jobs/<jobId>.yml → level_rewards.<newLevel>
  │           Exécuter les commandes console avec {player}, {level}, {job}
  │
  └─ 7. JobSlotManager.checkAndUnlockSlots(player, data, jobId, newLevel)
          └─ [Si slot débloqué → notifier joueur, section JOB-SLOTS-SYSTEM.md]
```

---

## 4. Flux Progression Quête

Appelé en fin de chaque listener, après le gain XP.

```
questManager.onMining(player, blockType, count)
        │
        ▼
[Récupérer les quêtes actives du joueur liées au type MINING]
        │
  Pour chaque QuestData active non complétée :
  │
  ├─ Vérifier que quest.target.block == blockType
  ├─ Vérifier que data.getJobLevel(quest.job) >= quest.minLevel
  ├─ questData.progress += count
  │
  └─ [si questData.progress >= quest.objective]
        ├─ questData.completed = true
        ├─ questData.completedAt = System.currentTimeMillis()
        ├─ XP immédiat : data.addXP(quest.job, quest.rewards.xp) [si rewards.xp > 0]
        │     └─ Relancer le flux Level Up si level up déclenché
        └─ MessagesConfig.send(player, "quest.completed", {quest_name})
           [Les autres récompenses (money, items, cmds) = réclamées via GUI]
```

---

## 5. Flux Spécifique : Hunter + Kstacker + Extra Kill

Le `HunterListener` écoute `EntityDeathEvent`. Avec Kstacker, les mobs stackés ne triggent PAS un seul event pour tout le stack — au lieu de cela, Kstacker spawn des **ghost entities** via `META_GHOST` et chaque ghost déclenche un `EntityDeathEvent` individuel.

```
[EntityDeathEvent déclenché pour une entité]
        │
  ├─ [GATE 1] event.getEntity() ne cast pas vers LivingEntity → return
  ├─ [GATE 2] event.getEntity().getKiller() == null → return (pas tué par joueur)
  ├─ [GATE 3] entity.hasMetadata("kstacker-ghost") ?
  │     ├─ OUI → C'est un ghost Kstacker :
  │     │     killMultiplier = entity.getMetadata("kstacker-multiplier")
  │     │                          .get(0).asInt()
  │     │     [ANTI-DUPE] killMultiplier = Math.min(killMultiplier, 3)
  │     │     xpTotal = action.getXp() * killMultiplier
  │     │     → addXP("hunter", xpTotal) [1 seul appel, pas de boucle]
  │     │
  │     └─ NON → Mob non stacké (ou stack de 1) :
  │           killMultiplier = 1
  │           → addXP("hunter", action.getXp())
  │
  └─ [Continuer le flux standard XP → HUD → Quête]
```

**Comment Kstacker positionne le bon multiplicateur :**

Dans `MobLethalDamageListener.java` (Kstacker), la logique est :
```
shiftKill → killedCount = count (tout le stack)
normal kill, joueur a Extra Kill enchant → killedCount = min(3, count)
normal kill, sans Extra Kill → killedCount = 1
```

Le `META_KILL_MULTIPLIER` est positionné à `killedCount` sur le ghost. KjobsUltimate lit ce metadata et cap à 3 (défense en profondeur).

---

## 6. Flux Farmer — Cas Spécial Cultures

```
BlockBreakEvent → FarmerListener
  │
  ├─ [GATE] jobSlotManager: farmer actif ? → sinon return
  ├─ action = jobsConfig.getAction("farmer", block.getType()) → null ? return
  ├─ [GATE] anti_abuse.crops_mature_only: true ET !CropUtil.isMature(block) → return
  │           ↑ Jamais d'XP sur une culture immature
  │
  └─ → Flux standard XP
```

---

## 7. Flux Prétorien — PvP

```
PlayerDeathEvent → PretorianListener
  │
  ├─ [GATE] killer = event.getEntity().getKiller() == null → return
  ├─ [GATE] killer == victim (suicide) → return
  ├─ [GATE] pretorien actif dans les slots du killer → sinon return
  ├─ [GATE] antiAbuseService.isPvPTargetOnCooldown(killerData, victimUUID) → return
  │
  ├─ antiAbuseService.setPvPTargetCooldown(killerData, victimUUID)
  └─ → Flux standard XP (addXP → HUD → Quête)
```

---

## 8. Scheduler Global (40 ticks = 2s)

Un seul `BukkitRunnable` pour TOUS les joueurs. Aucun scheduler individuel.

```java
new BukkitRunnable() {
    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            // 1. BossBar : flush si dirty
            if (bossBarManager.isDirty(player.getUniqueId())) {
                PlayerData data = playerDataManager.get(player);
                String jobId = jobSlotManager.getDisplayJob(data);
                if (jobId != null) {
                    bossBarManager.update(player, jobId, data);
                }
                bossBarManager.clearDirty(player.getUniqueId());
            }

            // 2. BossBar : retéléporter le wither si joueur bougé > 100 blocs
            bossBarManager.checkTeleport(player);

            // 3. ActionBar : envoyer le message +Xxp si timer actif
            actionBarManager.tick(player);
        }
    }
}.runTaskTimer(plugin, 40L, 40L);
```

---

## 9. Sauvegarde Asynchrone

La persistance SQLite ne se fait **jamais sur le main thread** pour ne pas bloquer.

```
[Autosave scheduler — toutes les X minutes]
  → Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
        playerDataManager.saveAll();  // écriture SQLite hors main thread
    });

[PlayerQuitEvent]
  → playerDataManager.saveAsync(uuid);  // idem

[onDisable]
  → playerDataManager.saveAll();  // synchrone car le serveur s'arrête
```
