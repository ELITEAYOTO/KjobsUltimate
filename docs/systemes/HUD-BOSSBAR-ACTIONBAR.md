# KjobUltimate — HUD : BossBar, ActionBar, Achievement, Sons

> Serveur cible : KhopeSpigot 1.8.8 — NMS `net.minecraft.server.v1_8_R3`
> Tout ce qui concerne l'affichage en temps réel du joueur.

---

## 0. Règles Fondamentales du HUD

### Quel job est affiché ?

**Règle unique : toujours le dernier job ayant produit de l'XP.**

- Il n'y a PAS de sélection manuelle de job pour la bossbar.
- Le champ interne `displayJob` (dans `PlayerData`) = le `jobId` du dernier gain d'XP, quel que soit le job.
- Exemple : le joueur est Mineur + Artisant. Il mine → bossbar Mineur. Il craft → bossbar bascule vers Artisant. Il mine à nouveau → bossbar revient sur Mineur.
- Cette règle s'applique identiquement à la BossBar et à l'ActionBar.

### Timer de disparition (BossBar uniquement)

- Configurable : `bossbar_timing_reset` (secondes) dans `hud.yml`.
- Si le joueur ne gagne d'XP dans AUCUN job pendant `bossbar_timing_reset` secondes → la bossbar disparaît (le wither est rendu invisible via packet metadata, mais PAS détruit).
- Au prochain gain d'XP → la bossbar réapparaît immédiatement (re-visible via packet metadata).
- La valeur `0` désactive la disparition (toujours visible si le joueur a un job actif).

### Toggle HUD joueur : `/jobs hud`

- Commande joueur disponible depuis `/job` ou `/jobs`.
- `/jobs hud` alterne le master **HUD ON/OFF**.
- `/jobs hud bossbar` alterne uniquement la BossBar jobs.
- `/jobs hud actionbar` alterne uniquement les messages ActionBar XP.
- `/jobs hud on` reactive le master HUD + BossBar + ActionBar.
- `/jobs hud off` coupe tout le HUD jobs.
- Quand l'ActionBar est desactivee, le plugin purge son cache interne et envoie un paquet ActionBar vide pour nettoyer le client.
- Persistant : `hud_enabled`, `bossbar_enabled`, `actionbar_enabled` en DB SQLite/MySQL.
- Accessible également dans le GUI `/jobs` via le menu paramètres.

### Bonus XP — Multiplicateurs

- Les multiplicateurs XP (permissions, événement, `/kjob bonus`) s'appliquent **UNIQUEMENT aux actions de job** (minage, kill, craft, farm, consommation d'items).
- Ils **NE s'appliquent PAS** aux récompenses XP des quêtes. L'XP de quête est fixé dans la config de la quête, non modifiable par multiplicateur.
- Cette règle est non configurable (comportement figé).

### Niveau Maximum — Comportement BossBar

Quand un job atteint son `max_level` configuré :

- Le titre de la bossbar utilise `title_format_max_level` (hud.yml) au lieu de `title_format`.
- Défaut : `"§b{job} §7Lv.§e{level} §8| §6MAX"` — configurable via hud.yml.
- Placeholders disponibles dans `title_format_max_level` : `{job}`, `{level}`. Ne pas utiliser `{xp_next}` (= 0 au max level).
- La bossbar suit le timer normal (`bossbar_timing_reset`) — elle ne reste pas affichée en permanence.
- Message chat envoyé au joueur : clé `job_max_level.reached` dans messages.yml.
- Badge GUI sur le job à max level : clé `job_max_level.gui_badge` dans messages.yml (ex: `§6§l★ NIVEAU MAX ★`).

### Achievement Popup — Cooldown et Rafales

- Le popup achievement est mis en file via `AchievementManager.enqueuePopup()` (voir section 3).
- Un cooldown configurable (`popup_cooldown_ms` dans hud.yml) empêche les rafales visuelles.
- Si plusieurs niveaux sont gagnés d'un coup (commande admin `/kjob addxp`) et `show_last_only_on_bulk: true` → seul le popup du niveau final est envoyé. En jeu normal (farming), un seul niveau est gagné à la fois.
- La séquence de level up enqueue toujours via `enqueuePopup()`, jamais d'appel direct à `sendLevelUpPopup()`.

---

## 1. BossBar — Fake Wither NMS

### Pourquoi NMS et pas Bukkit BossBar API ?

L'API `org.bukkit.boss.BossBar` n'existe qu'à partir de Spigot 1.9. Sur KhopeSpigot 1.8.8, la seule méthode pour afficher une bossbar est de spawner un `EntityWither` factice en packet seulement (jamais ajouté au monde), en dehors du rendu du monde, et de le maintenir à portée du joueur.

### Architecture BossBarManager

```
Joueur connecté → BossBarManager.init(player)
  ├─ Créer EntityWither NMS (jamais world.addEntity())
  ├─ Définir witherName via DataWatcher
  ├─ Envoyer PacketPlayOutSpawnEntityLiving
  ├─ Placer le wither à -255 Y (sous les bedrock = invisible mais à portée)
  ├─ État initial : invisible si joueur n'a pas de job actif ou HUD OFF
  └─ Enregistrer: witherEntityId, witherObject → witherByPlayer Map

Chaque gain XP → BossBarManager.onXpGain(player, jobId)
  ├─ data.displayJob = jobId  ← TOUJOURS mettre à jour le displayJob
  ├─ data.lastXpTimestamp = now()  ← reset le timer de disparition
  ├─ dirtySet.add(player.getUniqueId())  ← bossbar à mettre à jour
  └─ [Si bossbar était invisible] → PacketPlayOutEntityMetadata pour rendre visible

Scheduler global (40 ticks) → BossBarManager.tick(allPlayers)
  ├─ Pour chaque player dans dirtySet :
  │     ├─ [GATE] data.hudEnabled == false → skip (bossbar cachée)
  │     ├─ Calculer progressPct = data.getXP(displayJob) / xpForNextLevel
  │     ├─ Construire titleFormatted (avec placeholders résolus)
  │     ├─ DataWatcher.set(nameIndex, titleFormatted)
  │     ├─ DataWatcher.set(healthIndex, progressPct * 300f) ← barre = vie du wither
  │     ├─ Envoyer PacketPlayOutEntityMetadata
  │     └─ dirtySet.remove(player)
  │
  ├─ [TIMER DISPARITION] Pour chaque player (toujours) :
  │     ├─ [si bossbar_timing_reset > 0]
  │     │     elapsed = now - data.lastXpTimestamp
  │     │     si elapsed >= bossbar_timing_reset * 1000 ET bossbar est visible
  │     │     → sendInvisible(player)  ← metadata invisible (pas destroy)
  │     └─ [si bossbar_timing_reset == 0] → jamais caché par timer
  │
  └─ Pour chaque player (toujours) :
        Vérifier distance wither-joueur > seuil → PacketPlayOutEntityTeleport
        (En dessous de Y=-255 et à la position XZ du joueur)
```

### Visibilité / Invisibilité sans détruire le wither

Pour masquer la bossbar sans détruire et re-créer l'entité, on joue sur le flag invisible dans le DataWatcher :

```java
// Masquer la bossbar (invisible = true)
private void sendInvisible(Player player) {
    EntityWither wither = witherByPlayer.get(player.getUniqueId());
    if (wither == null) return;
    wither.setInvisible(true);
    // Optionnel : réduire health à 0.5 pour que la barre disparaisse aussi
    wither.setHealth(0.5f);
    PacketPlayOutEntityMetadata metaPacket =
        new PacketPlayOutEntityMetadata(wither.getId(), wither.getDataWatcher(), true);
    sendPacket(player, metaPacket);
    bossbarVisibleByPlayer.put(player.getUniqueId(), false);
}

// Ré-afficher la bossbar (invisible = false)
private void sendVisible(Player player) {
    EntityWither wither = witherByPlayer.get(player.getUniqueId());
    if (wither == null) return;
    wither.setInvisible(false);
    PacketPlayOutEntityMetadata metaPacket =
        new PacketPlayOutEntityMetadata(wither.getId(), wither.getDataWatcher(), true);
    sendPacket(player, metaPacket);
    bossbarVisibleByPlayer.put(player.getUniqueId(), true);
}
```

### Code NMS — BossBarManager.java

```java
import net.minecraft.server.v1_8_R3.*;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;

public class BossBarManager {

    private final Map<UUID, Integer> witherIdByPlayer = new HashMap<>();
    private final Map<UUID, EntityWither> witherByPlayer = new HashMap<>();
    private final Set<UUID> dirtySet = ConcurrentHashMap.newKeySet();

    /**
     * Initialise la bossbar d'un joueur (appelé au join).
     * Le wither est créé en mémoire UNIQUEMENT, jamais ajouté au monde.
     */
    public void init(Player player) {
        EntityWither wither = createFakeWither(player);
        witherByPlayer.put(player.getUniqueId(), wither);
        witherIdByPlayer.put(player.getUniqueId(), wither.getId());
        sendSpawnPacket(player, wither);
        // Placer sous la map (invisible, mais à portée de tracking)
        teleportWither(player, wither);
    }

    private EntityWither createFakeWither(Player player) {
        // Obtenir le monde NMS
        WorldServer worldServer = ((CraftPlayer) player).getHandle().getWorld();
        EntityWither wither = new EntityWither(worldServer);
        // Position initiale : sous la bedrock
        wither.setLocation(
            player.getLocation().getX(),
            -255.0,
            player.getLocation().getZ(),
            0f, 0f
        );
        // Nom custom (ne pas afficher de nom par défaut)
        wither.setCustomName("§bMineur §7Lv.1");
        wither.setCustomNameVisible(true);
        // Santé max = 300 (= barre pleine visible côté client)
        wither.setHealth(150f); // 50% par défaut
        return wither;
    }

    private void sendSpawnPacket(Player player, EntityWither wither) {
        PacketPlayOutSpawnEntityLiving spawnPacket =
            new PacketPlayOutSpawnEntityLiving(wither);
        sendPacket(player, spawnPacket);
    }

    /**
     * Maintenir le wither sous le joueur pour qu'il reste dans son range de tracking.
     * Appelé si le joueur s'est déplacé de plus de 100 blocs XZ.
     */
    public void teleportWither(Player player, EntityWither wither) {
        wither.setLocation(
            player.getLocation().getX(),
            -255.0,
            player.getLocation().getZ(),
            0f, 0f
        );
        PacketPlayOutEntityTeleport tp = new PacketPlayOutEntityTeleport(wither);
        sendPacket(player, tp);
    }

    /**
     * Marquer la bossbar comme "à mettre à jour" pour ce joueur.
     * N'envoie PAS de packet ici — c'est le scheduler qui envoie.
     */
    public void markDirty(UUID playerId) {
        dirtySet.add(playerId);
    }

    /**
     * Mise à jour du nom + de la barre (santé = progression XP).
     * Appelé par le scheduler global toutes les 40 ticks pour les dirty players.
     */
    public void update(Player player, String title, float progressPct) {
        EntityWither wither = witherByPlayer.get(player.getUniqueId());
        if (wither == null) return;

        // Mettre à jour le nom
        wither.setCustomName(title);

        // Mettre à jour la santé = progression de la barre visuelle
        // MaxHealth wither = 300 en 1.8.8
        float health = Math.max(0.5f, Math.min(300f, progressPct * 300f));
        wither.setHealth(health);

        // Envoyer le packet metadata
        PacketPlayOutEntityMetadata metaPacket = new PacketPlayOutEntityMetadata(
            wither.getId(),
            wither.getDataWatcher(),
            true
        );
        sendPacket(player, metaPacket);
    }

    /**
     * Supprimer la bossbar d'un joueur (déconnexion).
     */
    public void remove(Player player) {
        EntityWither wither = witherByPlayer.remove(player.getUniqueId());
        witherIdByPlayer.remove(player.getUniqueId());
        dirtySet.remove(player.getUniqueId());
        if (wither != null) {
            PacketPlayOutEntityDestroy destroyPacket =
                new PacketPlayOutEntityDestroy(wither.getId());
            sendPacket(player, destroyPacket);
        }
    }

    /**
     * Utilitaire d'envoi de packet via NMS.
     */
    private void sendPacket(Player player, Packet<?> packet) {
        ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packet);
    }
}
```

### Scheduler Global — Intégration BossBar + ActionBar + Timer

```java
// Dans KjobUltimate.java onEnable()
new BukkitRunnable() {
    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uid = player.getUniqueId();
            PlayerData data = playerDataManager.get(uid);

            // ── HUD désactivé par le joueur ──────────────────────
            if (!data.isHudEnabled()) {
                // S'assurer que la bossbar est invisible
                bossBarManager.ensureInvisible(player);
                // Pas d'actionbar
                continue;
            }

            // ── displayJob = dernier job ayant donné XP ──────────
            String displayJob = data.getDisplayJob();
            if (displayJob == null) continue; // joueur sans job actif

            // ── ActionBar ──────────────────────────────────────────
            actionBarManager.tick(player);

            // ── BossBar mise à jour ────────────────────────────────
            if (bossBarManager.isDirty(uid)) {
                Job job = jobManager.getJob(displayJob);
                if (job == null) continue;
                int level = data.getLevel(displayJob);
                int xp = data.getXP(displayJob);
                int xpNext = job.getXpForLevel(level);
                float pct = xpNext > 0 ? (float) xp / xpNext : 0f;

                String title = hudConfig.getBossbarFormat()
                    .replace("{job}", job.getDisplayName())
                    .replace("{level}", String.valueOf(level))
                    .replace("{xp}", String.valueOf(xp))
                    .replace("{xp_next}", String.valueOf(xpNext))
                    .replace("{percent}", String.valueOf((int)(pct * 100)));

                // S'assurer que la bossbar est visible avant la mise à jour
                bossBarManager.ensureVisible(player);
                bossBarManager.update(player, title, pct);
                bossBarManager.clearDirty(uid);
            }

            // ── Timer de disparition ───────────────────────────────
            int timingReset = hudConfig.getBossbarTimingReset(); // secondes
            if (timingReset > 0) {
                long elapsed = System.currentTimeMillis() - data.getLastXpTimestamp();
                if (elapsed >= timingReset * 1000L) {
                    bossBarManager.ensureInvisible(player);
                }
            }

            // ── Téléportation du wither si le joueur s'est déplacé ─
            bossBarManager.checkTeleport(player);
        }
    }
}.runTaskTimer(this, 0L, 40L); // 40 ticks = 2 secondes
```

---

## 2. ActionBar — Hotbar Message XP

### Règles

- L'actionbar affiche toujours le **dernier job ayant produit de l'XP** (champ `lastJobForXP`), exactement comme la BossBar utilise `displayJob`.
- Si un joueur mine (Mineur +5xp) puis craft (Artisant +20xp) en quelques secondes → l'actionbar affiche `+20xp (Artisant Lv.3)`.
- **Accumulation configurable** (`accumulate: true/false` dans `hud.yml`) :
  - `accumulate: true` → les gains sont cumulés dans la fenêtre de temps → un seul message `+Xxp`
  - `accumulate: false` → chaque gain remplace le précédent (dernier gain toujours affiché)
  - Note : si le job change (ex: mine → craft), l'accumulation se **remet à zéro** même si `accumulate: true` — il n'y a jamais de mix de jobs dans un seul message.

**Placeholders disponibles dans `format`** : `{xp}`, `{job}`, `{level}`

Exemple par défaut : `§a+{xp}xp §7(§f{job} §eLv.{level}§7)`

### Comportement avec accumulation

```
accumulate: true
t=0s: mine pierre → +5 XP (Mineur) → affiche "§a+5xp §7(§bMineur§7)"
t=0.3s: mine fer → +25 XP (Mineur) → cumul → affiche "§a+30xp §7(§bMineur§7)"
t=1.2s: craft épée → +20 XP (Artisant) → job change → reset cumul → affiche "§a+20xp §7(§eArtisant§7)"
t=4.2s: (timer expire) → efface le message

accumulate: false
t=0s: mine pierre → +5 XP → affiche "§a+5xp §7(§bMineur§7)"
t=0.3s: mine fer → +25 XP → remplace → affiche "§a+25xp §7(§bMineur§7)"
t=3.3s: (timer expire) → efface le message
```

### ActionBarManager.java

```java
public class ActionBarManager {

    // XP accumulé par joueur (reset après display_ticks)
    private final Map<UUID, Integer> accumulatedXP = new HashMap<>();
    // Job du dernier gain (pour l'affichage)
    private final Map<UUID, String> lastJobForXP = new HashMap<>();
    // Ticks restants avant effacement
    private final Map<UUID, Integer> displayTimer = new HashMap<>();

    private final HudConfig hudConfig;

    /**
     * Appelé lors d'un gain d'XP (depuis le listener).
     * N'envoie PAS le packet ici — le scheduler l'envoie via tick().
     */
    public void onXpGain(UUID playerId, String jobId, int xpAmount) {
        String previousJob = lastJobForXP.get(playerId);
        boolean jobChanged = !jobId.equals(previousJob);

        if (hudConfig.isAccumulateEnabled() && !jobChanged) {
            // Même job → accumuler
            int current = accumulatedXP.getOrDefault(playerId, 0);
            accumulatedXP.put(playerId, current + xpAmount);
        } else {
            // Job différent OU accumulation désactivée → reset et afficher ce gain uniquement
            accumulatedXP.put(playerId, xpAmount);
        }
        lastJobForXP.put(playerId, jobId);
        displayTimer.put(playerId, hudConfig.getDisplayTicks()); // reset timer
    }

    /**
     * Appelé par le scheduler global toutes les 40 ticks.
     * Envoie le message si timer > 0, efface si timer <= 0.
     */
    public void tick(Player player) {
        UUID uid = player.getUniqueId();
        int timer = displayTimer.getOrDefault(uid, 0);

        if (timer > 0) {
            // Construire le message
            int xp = accumulatedXP.getOrDefault(uid, 0);
            String jobId = lastJobForXP.getOrDefault(uid, "");
            Job job = jobManager.getJob(jobId);
            String jobName = job != null ? job.getDisplayName() : jobId;
            int level = playerData.getJobLevel(jobId); // niveau actuel du joueur dans ce job

            String msg = hudConfig.getActionBarFormat()
                .replace("{xp}", String.valueOf(xp))
                .replace("{job}", jobName)      // placeholder config = {job}
                .replace("{level}", String.valueOf(level));

            sendActionBar(player, ColorUtil.translate(msg));
            displayTimer.put(uid, timer - 40); // décrémenter de 40 ticks
        } else if (displayTimer.containsKey(uid)) {
            // Timer expiré → effacer
            sendActionBar(player, "");
            accumulatedXP.remove(uid);
            lastJobForXP.remove(uid);
            displayTimer.remove(uid);
        }
    }

    /**
     * Envoie un message sur l'actionbar via NMS PacketPlayOutChat position=2.
     */
    private void sendActionBar(Player player, String message) {
        IChatBaseComponent component = ChatSerializer.a("{\"text\":\"" +
            message.replace("\"", "\\\"") + "\"}");
        PacketPlayOutChat packet = new PacketPlayOutChat(component, (byte) 2);
        ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packet);
    }
}
```

---

## 3. Achievement Popup — Notification de Level Up

### Principe

Etat terrain valide sur KhopeSpigot/PandaSpigot 1.8.8 :
- `BUKKIT` est le mode fiable pour afficher le vrai toast achievement vanilla.
- `PACKET` envoie bien `PacketPlayOutStatistic`, mais certains clients/forks ne montrent pas le toast meme si le log confirme l'envoi.
- `PACKET_THEN_BUKKIT` permet de diagnostiquer le packet puis de forcer le toast Bukkit quelques ticks apres.

Limite importante 1.8 : le serveur choisit seulement un achievement vanilla. Il n'envoie pas de texte custom ni d'ItemStack/NBT custom dans le toast. Le texte vient des cles `achievement.*` du resource pack/lang du client, et l'icone vient de l'achievement choisi.

Quand le joueur monte de niveau, on déclenche un achievement vanille dont on a :
1. **Remplacé le texte** dans `lang/fr_FR.lang` du resource pack
2. **Remplacé l'item** avec un bloc custom (CIT via resource pack)

L'achievement est déclenché côté serveur via `PacketPlayOutStatistic`.

### Mapping Achievement → Job (dans hud.yml)

```yaml
achievement_mapping:
  mineur: "achievement.buildPickaxe"      # item = WOODEN_PICKAXE → CIT = icône Mineur
  farmer: "achievement.makeBread"          # item = BREAD → CIT = icône Farmer
  hunter: "achievement.killEnemy"          # item = BONE → CIT = icône Hunter
  pretorien: "achievement.buildSword"      # item = WOODEN_SWORD → CIT = icône Prétorien
  artisant: "achievement.buildWorkBench"   # item = CRAFTING_TABLE → CIT = icône Artisant
```

### Resource Pack — lang/fr_FR.lang

```properties
# Texte des achievements de level up (overwrite dans le pack)
achievement.buildPickaxe=Mineur — Nouveau Niveau !
achievement.buildPickaxe.desc=Tu as progressé dans le job Mineur.
achievement.makeBread=Farmer — Nouveau Niveau !
achievement.makeBread.desc=Tu as progressé dans le job Farmer.
achievement.killEnemy=Hunter — Nouveau Niveau !
achievement.killEnemy.desc=Tu as progressé dans le job Hunter.
achievement.buildSword=Prétorien — Nouveau Niveau !
achievement.buildSword.desc=Tu as progressé dans le job Prétorien.
achievement.buildWorkBench=Artisant — Nouveau Niveau !
achievement.buildWorkBench.desc=Tu as progressé dans le job Artisant.
```

### Resource Pack — CIT (.properties)

```properties
# assets/minecraft/optifine/cit/jobs/job_levelup_mineur.properties
type=item
items=wooden_pickaxe
texture=job_levelup_mineur    # PNG dans le même dossier CIT
```

### AchievementManager.java — Envoi du Packet

```java
import net.minecraft.server.v1_8_R3.*;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;

public class AchievementManager {

    private final HudConfig hudConfig;

    // File d'attente par joueur pour les popups en rafale (multi-level via commande admin)
    private final Map<UUID, Queue<String>> pendingPopups = new HashMap<>();
    // Timestamp du dernier popup envoyé par joueur
    private final Map<UUID, Long> lastPopupTime = new HashMap<>();

    /**
     * Enqueue un popup achievement pour le level up d'un job.
     * Si plusieurs levels sont gagnés d'un coup (commande admin), seul le dernier
     * niveau est conservé si show_last_only_on_bulk=true ; sinon ils sont mis en file.
     */
    public void enqueuePopup(Player player, String jobId, int newLevel) {
        if (!hudConfig.isAchievementPopupEnabled()) return;

        UUID uid = player.getUniqueId();
        if (hudConfig.isShowLastOnlyOnBulk()) {
            // Remplacer la file par le seul dernier niveau
            Queue<String> q = new ArrayDeque<>();
            q.add(jobId);
            pendingPopups.put(uid, q);
        } else {
            pendingPopups.computeIfAbsent(uid, k -> new ArrayDeque<>()).add(jobId);
        }
    }

    /**
     * Tické toutes les 40 ticks par le scheduler global.
     * Envoie le prochain popup en file si le cooldown est écoulé.
     */
    public void tick(Player player) {
        UUID uid = player.getUniqueId();
        Queue<String> queue = pendingPopups.get(uid);
        if (queue == null || queue.isEmpty()) return;

        long cooldownMs = hudConfig.getAchievementPopupCooldownMs(); // configurable
        long now = System.currentTimeMillis();
        long last = lastPopupTime.getOrDefault(uid, 0L);
        if (now - last < cooldownMs) return; // cooldown non écoulé

        String jobId = queue.poll();
        sendLevelUpPopup(player, jobId);
        lastPopupTime.put(uid, now);
    }

    /**
     * Déclenche réellement le popup achievement pour un job (usage interne).
     */
    private void sendLevelUpPopup(Player player, String jobId) {
        String achievementKey = hudConfig.getAchievementMapping(jobId);
        if (achievementKey == null) return;

        Achievement achievement = getAchievementByKey(achievementKey);
        if (achievement == null) {
            plugin.getLogger().warning("[KjobUltimate] Achievement inconnu : " + achievementKey);
            return;
        }

        PacketPlayOutStatistic packet = new PacketPlayOutStatistic(achievement, 1);
        ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packet);
    }

    /**
     * Cherche l'Achievement NMS par sa clé (ex: "achievement.buildPickaxe")
     */
    private Achievement getAchievementByKey(String key) {
        for (Achievement ach : AchievementList.e) {
            if (ach != null && ach.e().equals(key)) {
                return ach;
            }
        }
        return null;
    }
}
```

### Title/Subtitle Complémentaire (en plus du popup)

Pour afficher le niveau exact (le popup achievement a un texte statique), on envoie un `PacketPlayOutTitle` en parallèle :

```java
public void sendLevelUpTitle(Player player, String jobId, int newLevel) {
    if (!hudConfig.isTitleOnLevelUpEnabled()) return;

    Job job = jobManager.getJob(jobId);
    String title = hudConfig.getTitleFormat()
        .replace("{level}", String.valueOf(newLevel))
        .replace("{job_name}", job != null ? job.getDisplayName() : jobId);
    String subtitle = hudConfig.getSubtitleFormat()
        .replace("{level}", String.valueOf(newLevel))
        .replace("{job_name}", job != null ? job.getDisplayName() : jobId);

    sendTitle(player, title, subtitle,
        hudConfig.getTitleFadeIn(),
        hudConfig.getTitleStay(),
        hudConfig.getTitleFadeOut()
    );
}

private void sendTitle(Player player, String title, String subtitle,
                       int fadeIn, int stay, int fadeOut) {
    // 1. Définir les timings
    PacketPlayOutTitle times = new PacketPlayOutTitle(
        PacketPlayOutTitle.EnumTitleAction.TIMES, null, fadeIn, stay, fadeOut
    );
    // 2. Envoyer le subtitle
    IChatBaseComponent subComp = ChatSerializer.a("{\"text\":\"" +
        ColorUtil.translate(subtitle).replace("\"", "\\\"") + "\"}");
    PacketPlayOutTitle subPacket = new PacketPlayOutTitle(
        PacketPlayOutTitle.EnumTitleAction.SUBTITLE, subComp
    );
    // 3. Envoyer le title (déclenche l'affichage)
    IChatBaseComponent titleComp = ChatSerializer.a("{\"text\":\"" +
        ColorUtil.translate(title).replace("\"", "\\\"") + "\"}");
    PacketPlayOutTitle titlePacket = new PacketPlayOutTitle(
        PacketPlayOutTitle.EnumTitleAction.TITLE, titleComp
    );

    PlayerConnection conn = ((CraftPlayer) player).getHandle().playerConnection;
    conn.sendPacket(times);
    conn.sendPacket(subPacket);
    conn.sendPacket(titlePacket);
}
```

---

## 4. Sons Custom

### Enregistrement dans le Resource Pack

**assets/minecraft/sounds.json**
```json
{
  "custom.levelup":          { "sounds": ["custom/levelup"] },
  "custom.levelup_mineur":   { "sounds": ["custom/levelup_mineur"] },
  "custom.levelup_farmer":   { "sounds": ["custom/levelup_farmer"] },
  "custom.levelup_hunter":   { "sounds": ["custom/levelup_hunter"] },
  "custom.levelup_pretorien":{ "sounds": ["custom/levelup_pretorien"] },
  "custom.levelup_artisant": { "sounds": ["custom/levelup_artisant"] },
  "custom.quest_complete":   { "sounds": ["custom/quest_complete"] },
  "custom.quest_claim":      { "sounds": ["custom/quest_claim"] },
  "custom.xp_tick":          { "sounds": ["custom/xp_tick"] }
}
```

**Structure des fichiers** :
```
assets/minecraft/sounds/custom/
├── levelup.ogg
├── levelup_mineur.ogg
├── levelup_farmer.ogg
├── levelup_hunter.ogg
├── levelup_pretorien.ogg
├── levelup_artisant.ogg
├── quest_complete.ogg
├── quest_claim.ogg
└── xp_tick.ogg
```

### SoundManager.java — Envoi NMS

```java
public class SoundManager {

    private final SoundsConfig soundsConfig;

    /**
     * Joue un son custom via PacketPlayOutNamedSoundEffect.
     * @param event  Clé d'événement dans sounds.yml (ex: "on_level_up")
     * @param jobId  Job concerné pour l'override per-job (peut être null)
     */
    public void play(Player player, String event, String jobId) {
        SoundEntry entry = soundsConfig.getSound(event, jobId);
        if (entry == null || !entry.isEnabled()) return;

        playSound(player, entry.getSoundName(), entry.getVolume(), entry.getPitch());
    }

    /**
     * Envoi direct d'un son custom par nom (depuis sounds.json du pack).
     */
    public void playSound(Player player, String soundName, float volume, float pitch) {
        Location loc = player.getLocation();
        // En 1.8.8, PacketPlayOutNamedSoundEffect prend le nom brut du son
        PacketPlayOutNamedSoundEffect packet = new PacketPlayOutNamedSoundEffect(
            soundName,
            loc.getX(), loc.getY(), loc.getZ(),
            volume, pitch
        );
        ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packet);
    }
}
```

---

## 5. Séquence Complète Level Up

Quand `data.addXP()` détecte un passage de niveau, voici l'ordre exact des actions :

```
LevelUpEvent(player, "mineur", newLevel)
  │
  ├─ [1] messagesConfig.send(player, "level_up.mineur", {level: 15})
  │         → message chat coloré au joueur
  │
  ├─ [2] soundManager.play(player, "on_level_up", "mineur")
  │         → PacketPlayOutNamedSoundEffect("custom.levelup_mineur", ...)
  │
  ├─ [3] achievementManager.enqueuePopup(player, "mineur", newLevel)
  │         → Ajout en file d'attente (débité par le scheduler avec cooldown configurable)
  │         → Si show_last_only_on_bulk=true : seul le dernier niveau est conservé en file
  │         → Popup en haut à droite, item = WOODEN_PICKAXE avec CIT icône Mineur
  │
  ├─ [4] achievementManager.sendLevelUpTitle(player, "mineur", 15)
  │         → PacketPlayOutTitle TIMES + SUBTITLE + TITLE
  │         → "§6§lNIVEAU 15 !" + "§7Mineur — §eBravo !"
  │
  ├─ [5] bossBarManager.markDirty(player)
  │         → La bossbar sera mise à jour au prochain tick du scheduler (barre reset à 0%)
  │
  └─ [6] rewardManager.applyRewards(player, "mineur", 15)
             → Exécuter les commandes configurées dans level_rewards
             → eco give {player} X, give {player} ITEM Q, broadcast ...
```

---

## 6. Performance — Résumé

| Composant | Fréquence d'envoi | Packets/s (600j) | Impact |
|---|---|---|---|
| BossBar title update | Event-driven (dirty flag) | ~0-200 | Minimal |
| BossBar health update | Idem | Inclus ci-dessus | Minimal |
| BossBar téléport wither | Rare (joueur > 100 blocs) | ~0-10 | Négligeable |
| ActionBar refresh | 1x / 40 ticks par joueur actif | ~300 | Négligeable |
| Achievement popup | Event-driven (level up uniquement) | ~0-20 | Négligeable |
| Title level up | Idem | ~0-20 | Négligeable |
| Son level up | Idem | ~0-20 | Négligeable |
| **Total** | | **~320-550/s** | **< 20 KB/s réseau** |

> **Règle d'or** : Le scheduler global tourne toutes les **40 ticks** (pas 1 tick).
> Jamais de `BukkitRunnable` individuel par joueur. Toujours un seul scheduler global.
