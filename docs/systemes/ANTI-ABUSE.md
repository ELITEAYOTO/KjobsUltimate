# KjobUltimate — Système Anti-Abuse / Anti-Dupe

> Toutes les protections configurables dans `config.yml` section `anti_abuse`.

---

## 1. Vue d'ensemble des Protections

| Protection | Cible | Méthode de détection |
|---|---|---|
| Silk Touch | Minerais, blocs précieux | Vérifier enchantement de l'outil |
| Cultures Immatures | Blé, carottes, etc. | Vérifier `block.getData()` (1.8.8) |
| Mode Créatif | Tous | `player.getGameMode()` |
| Cooldown Position | Break-replace-break | `Map<String, Long>` par "x,y,z,world" |
| Anti-farm PvP | Prétorien | Cooldown par UUID cible |
| XP Cap Quotidien | Tous | Compteur + date reset minuit |
| Kstacker Multi-Kill | Hunter | Cap absolu META_KILL_MULTIPLIER ≤ 3 |
| Fortune bonus | Optionnel | Pas de bonus XP (XP fixe en V1) |

---

## 7. Kstacker — Multi-Kill Anti-Dupe

### Contexte
Kstacker stack les mobs en un seul entity. Quand un joueur tue un mob d'un stack, Kstacker spawn un **ghost entity** avec les métadonnées appropriées et c'est ce ghost qui déclenche `EntityDeathEvent`.

### Comportement selon le cas

| Situation | META_KILL_MULTIPLIER sur le ghost | XP Hunter |
|---|---|---|
| Kill normal (stack x10, pas d'enchant) | 1 | 1 × xp_action |
| Kill avec Extra Kill Lv.1 (min 2, stack x10) | 2 | 2 × xp_action |
| Kill avec Extra Kill Lv.2 (min 3, stack x10) | 3 | 3 × xp_action |
| Shift-Kill (tout le stack x10) | 10 | → capé à 3 × xp_action |
| Mob non stacké | absent | 1 × xp_action |

### Code dans HunterListener

```java
@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
public void onEntityDeath(EntityDeathEvent event) {
    LivingEntity entity = (LivingEntity) event.getEntity();
    Player killer = entity.getKiller();
    if (killer == null) return;

    // Vérifier que le Hunter est un job actif du joueur
    PlayerData data = playerDataManager.get(killer);
    if (!jobSlotManager.getActiveJobs(data).contains("hunter")) return;

    // Récupérer l'action configurée pour ce type d'entité
    JobAction action = jobsConfig.getJob("hunter").getAction(entity.getType().name());
    if (action == null) return;

    // Calcul du multiplicateur (Kstacker ghost ou mob normal)
    int killMultiplier = 1;
    if (entity.hasMetadata(MobStackService.META_GHOST)
            && entity.hasMetadata(MobStackService.META_KILL_MULTIPLIER)) {
        killMultiplier = entity.getMetadata(MobStackService.META_KILL_MULTIPLIER)
                              .get(0).asInt();
        // *** ANTI-DUPE : cap absolu à 3 ***
        killMultiplier = Math.min(killMultiplier, 3);
    }

    // Gates anti-abuse standard
    if (antiAbuseService.isCreative(killer)) return;
    if (antiAbuseService.isDailyCapReached(data, "hunter")) return;

    int xpTotal = action.getXp() * killMultiplier;
    // ... continuer avec le flux standard
}
```

### Prérequis (côté Kstacker)
- `MobLethalDamageListener` doit positionner `META_KILL_MULTIPLIER` à `min(extraKillCount, stackSize)` quand le joueur a l'enchant Extra Kill
- Voir INTEGRATION-MAP.md section A2 pour le code à modifier dans Kstacker

---

## 2. Silk Touch — Détection et Blocage

### Logique

Si un joueur mine un minerai avec Silk Touch, il récupère le bloc entier sans le casser "normalement". Donner de l'XP dans ce cas faciliterait le farm de minerais en surface/storage.

La protection s'applique uniquement aux blocs marqués `silktouch: true` dans jobs.yml.

### Code

```java
// Dans AntiAbuseService.java
public boolean hasSilkTouch(Player player) {
    ItemStack tool = player.getInventory().getItemInHand();
    if (tool == null || tool.getType() == Material.AIR) return false;
    return tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0;
}

// Dans MinerListener.java
@EventHandler
public void onBlockBreak(BlockBreakEvent event) {
    // ...
    JobAction action = job.getAction(blockType);
    if (action == null) return;

    // Check silk touch
    if (action.isBlockXPWithSilkTouch() &&
        antiAbuseService.hasSilkTouch(event.getPlayer())) {
        if (debugEnabled) log("SilkTouch détecté sur " + blockType + " — XP bloqué");
        return;
    }
    // ... continuer avec le gain XP
}
```

### Configuration par Bloc (jobs.yml)

```yaml
actions:
  mine_stone:
    material: STONE
    xp: 5
    silktouch: false    # La stone n'a pas de silk touch en pratique, pas besoin

  mine_diamond_ore:
    material: DIAMOND_ORE
    xp: 100
    silktouch: true     # Bloquer XP si silk touch sur les diamants
```

---

## 3. Cultures Immatures — Détection 1.8.8

En Minecraft 1.8.8, il n'y a pas d'API `org.bukkit.block.data.Ageable`. La maturité d'une culture se vérifie via `block.getData()` qui retourne la valeur de damage du bloc.

### Valeurs de Maturité par Culture (1.8.8)

| Culture | Material | Age Max | getData() max |
|---|---|---|---|
| Blé | `WHEAT` | 7 | 7 |
| Carottes | `CARROT` | 7 | 7 |
| Pommes de Terre | `POTATO` | 7 | 7 |
| Betteraves | `BEETROOT_BLOCK` | 3 | 3 |
| Nether Wart | `NETHER_WART` | 3 | 3 |
| Cacao | `COCOA` | 2 | 2 (high byte) |
| Melon / Citrouille | — | — | Toujours mature (bloc entier) |
| Canne à sucre | `SUGAR_CANE_BLOCK` | — | Toujours mature |

### CropUtil.java

```java
import org.bukkit.Material;
import org.bukkit.block.Block;

public class CropUtil {

    /**
     * Vérifie si une culture est mature selon son getData() en 1.8.8.
     * @return true si mature (XP autorisé), false si pas encore mature
     */
    public static boolean isMature(Block block) {
        Material type = block.getType();
        byte data = block.getData();

        switch (type) {
            case WHEAT:
                return data >= 7;       // 0 = semis, 7 = mature
            case CARROT:
                return data >= 7;
            case POTATO:
                return data >= 7;
            case BEETROOT_BLOCK:
                return data >= 3;
            case NETHER_WART:
                return data >= 3;
            case COCOA:
                // En 1.8.8, la maturité du cacao est dans les bits 2-3
                // (bits 0-1 = orientation, bits 2-3 = âge)
                return ((data >> 2) & 3) >= 2;
            case MELON_BLOCK:
            case PUMPKIN:
            case SUGAR_CANE_BLOCK:
                return true;            // Toujours "mature"
            default:
                return true;            // Tous les autres blocs : pas de check
        }
    }

    /**
     * Vérifie si un bloc est une culture gérée par le job Farmer.
     */
    public static boolean isFarmingCrop(Material material) {
        switch (material) {
            case WHEAT:
            case CARROT:
            case POTATO:
            case BEETROOT_BLOCK:
            case NETHER_WART:
            case COCOA:
            case MELON_BLOCK:
            case PUMPKIN:
            case SUGAR_CANE_BLOCK:
                return true;
            default:
                return false;
        }
    }
}
```

### Utilisation dans FarmerListener.java

```java
@EventHandler
public void onBlockBreak(BlockBreakEvent event) {
    Block block = event.getBlock();
    Material type = block.getType();

    // Est-ce une culture gérée ?
    if (!CropUtil.isFarmingCrop(type)) return;

    // Check maturité (si activé dans config et si l'action le requiert)
    JobAction action = farmerJob.getAction(type);
    if (action == null) return;

    if (action.isRequiresMature() &&
        configManager.isAntiAbuseCropsMatureOnly() &&
        !CropUtil.isMature(block)) {
        if (debugEnabled) log("Culture immature : " + type + " data=" + block.getData() + " — XP bloqué");
        return;
    }

    // XP accordé
    data.addXP("farmer", action.getXp());
}
```

---

## 4. Mode Créatif / Spectateur

```java
// Check universel dans chaque listener
public boolean isXPBlockedByGameMode(Player player) {
    GameMode gm = player.getGameMode();
    if (configManager.isBlockXPCreative() && gm == GameMode.CREATIVE) return true;
    if (configManager.isBlockXPSpectator() && gm == GameMode.SPECTATOR) return true;
    return false;
}
```

---

## 5. Cooldown par Position de Bloc (Anti Break-Replace-Break)

### Problème

Un joueur peut casser un bloc, replacer le même bloc, recasser, etc. pour générer de l'XP sans limite.

### Solution

On enregistre un timestamp par position de bloc. Si le même bloc est cassé dans les X secondes, pas d'XP.

```java
// Dans PlayerData ou dans AntiAbuseService
// Clé = "x,y,z" (coordonnées en string)
private final Map<String, Long> blockCooldowns = new HashMap<>();

public boolean isBlockOnCooldown(Location loc, int cooldownSeconds) {
    String key = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    Long lastBreak = blockCooldowns.get(key);
    if (lastBreak == null) return false;
    return (System.currentTimeMillis() - lastBreak) < (cooldownSeconds * 1000L);
}

public void setBlockCooldown(Location loc) {
    String key = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    blockCooldowns.put(key, System.currentTimeMillis());
}
```

### Utilisation dans MinerListener

```java
// Avant de donner l'XP
Location blockLoc = event.getBlock().getLocation();
int cooldownSec = configManager.getBlockPositionCooldown();

if (cooldownSec > 0) {
    if (antiAbuseService.isBlockOnCooldown(data, blockLoc, cooldownSec)) {
        if (debugEnabled) log("Bloc en cooldown positon : " + blockLoc);
        return;
    }
    antiAbuseService.setBlockCooldown(data, blockLoc);
}
```

### Nettoyage de la Map (mémoire)

La `blockCooldowns` map peut grossir indéfiniment. Nettoyage périodique dans le scheduler global :

```java
// Toutes les 5 minutes dans le scheduler global
if (++cleanupCounter % 150 == 0) { // 150 × 40 ticks = 6000 ticks = 5 min
    playerDataManager.cleanExpiredBlockCooldowns(cooldownSeconds);
}
```

---

## 6. Anti-Abuse PvP (Prétorien)

### Problème

Deux joueurs alliés qui se tuent mutuellement pour farmer l'XP Prétorien.

### Solution : Cooldown par UUID cible

```java
// Dans PlayerData
private final Map<UUID, Long> pvpTargetCooldown = new HashMap<>();

public boolean isPvPTargetOnCooldown(UUID targetId, int cooldownSec) {
    Long lastKill = pvpTargetCooldown.get(targetId);
    if (lastKill == null) return false;
    return (System.currentTimeMillis() - lastKill) < (cooldownSec * 1000L);
}

public void setPvPTargetCooldown(UUID targetId) {
    pvpTargetCooldown.put(targetId, System.currentTimeMillis());
}
```

### Utilisation dans PretorianListener

```java
@EventHandler
public void onPlayerDeath(PlayerDeathEvent event) {
    Player victim = event.getEntity();
    Player killer = victim.getKiller();

    if (killer == null || killer == victim) return;

    PlayerData killerData = playerDataManager.get(killer);

    // Anti-abuse : même victime = cooldown
    int pvpCooldown = configManager.getPvPTargetCooldown();
    if (killerData.isPvPTargetOnCooldown(victim.getUniqueId(), pvpCooldown)) {
        if (debugEnabled) log("PvP cooldown sur " + victim.getName() + " — XP bloqué");
        return;
    }

    killerData.setPvPTargetCooldown(victim.getUniqueId());
    killerData.addXP("pretorien", action.getXp());
}
```

---

## 7. XP Cap Quotidien (Optionnel)

```java
// Dans PlayerData.addXP() — vérifier avant d'ajouter
public void addXP(String jobId, int amount) {
    if (configManager.isDailyXPCapEnabled()) {
        int capForJob = configManager.getDailyXPCap(jobId);
        if (capForJob > 0) {
            int todayXP = getDailyXP(jobId);
            if (todayXP >= capForJob) {
                // Cap atteint, on ne donne pas l'XP
                return;
            }
            // Limiter au cap si l'ajout dépasse
            amount = Math.min(amount, capForJob - todayXP);
        }
        addDailyXP(jobId, amount);
    }

    // Procéder normalement
    // ...
}
```

---

## 8. Fortune — Pas de Bonus XP (recommandé)

Par défaut, Fortune sur un minerai multiplie les drops mais l'XP reste fixe (basé sur le bloc cassé, pas sur les drops). Comportement voulu = XP fixe peu importe Fortune.

Si tu veux désactiver la prise en compte de Fortune pour l'XP : c'est déjà le comportement par défaut (on ne check pas Fortune dans les listeners, on donne simplement l'XP de l'action).

---

## 9. WorldGuard — Zones de Protection

Si WorldGuard est présent et activé dans config.yml :

```java
// Dans AntiAbuseService.java
public boolean isXPAllowedByWorldGuard(Player player, Location loc) {
    if (!configManager.isWorldGuardEnabled()) return true;
    if (Bukkit.getPluginManager().getPlugin("WorldGuard") == null) return true;

    // Vérifier si le joueur a le droit de builder dans la région
    // Utilise l'API WorldGuard pour 1.8.8
    RegionManager rm = WorldGuard.getInstance()
        .getRegionManager(BukkitUtil.getLocalWorld(loc.getWorld()));
    if (rm == null) return true;

    ApplicableRegionSet regions = rm.getApplicableRegions(
        BukkitUtil.toVector(loc));

    return regions.testState(
        LocalPlayer.adapt(player),
        Flags.BUILD
    );
}
```

---

## 10. Résumé des Priorités d'Implémentation

| Protection | Priorité | Complexité |
|---|---|---|
| Mode créatif bloqué | Haute | Simple |
| Cultures immatures (1.8.8 getData) | Haute | Simple |
| Silk Touch configurable par bloc | Haute | Simple |
| Anti-PvP cooldown même joueur | Haute | Moyen |
| Cooldown position bloc | Moyenne | Moyen |
| XP cap quotidien | Faible | Simple |
| WorldGuard hook | Faible | Complexe |
| Anti-AFK farm mobs | Faible | Moyen |
