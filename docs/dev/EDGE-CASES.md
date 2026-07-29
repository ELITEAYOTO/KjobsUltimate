# KjobsUltimate — Edge Cases & Comportements Limites

> Ce document répond aux cas non couverts par la doc principale.
> Objectif : 0 zone d'ombre au moment du développement.

---

## 1. Premier Join & Onboarding

### Si le joueur quitte avant de choisir son premier job

**Comportement :** Le GUI de sélection rouvre automatiquement à la prochaine connexion.

**Implémentation :**
```java
// Dans PlayerJoinListener.onPlayerJoin()
PlayerData data = playerDataManager.get(player);
boolean hasNoJob = data.getSlotJobs().isEmpty(); // slot 1 = null
if (hasNoJob && configManager.isFirstJoinGuiEnabled()) {
    // Délai 20 ticks pour laisser le client charger
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
        KguiHook.openJobSelectionGui(player);
        // Ou fallback chat si Kgui absent :
        // MessagesConfig.send(player, "first_join_choose_job");
    }, 20L);
}
```

**Données :** `job_slots.slot_1 = NULL` → indicateur "pas encore sélectionné". La row `players` est créée au premier join. Aucune quête assignée avant un premier job.

---

## 2. Changement de Job — Quêtes en cours

### Quelles quêtes sont perdues si un job est changé ?

**Comportement décidé :** Les quêtes du job remplacé sont **toutes perdues et réinitialisées**.
Cela inclut :
- Les quêtes **en cours** (progression annulée)
- Les quêtes **complétées non claimées** (récompenses perdues — le joueur a abandonné son job volontairement)

**Justification :** Un changement de job est un acte volontaire confirmé avec avertissement. Si le joueur avait des quêtes finies non réclamées, le message d'avertissement les mentionne explicitement.

**Implémentation :**
```java
// Dans JobSlotManager.confirmJobChange(player, slotIndex, newJobId)
String oldJobId = data.getSlotJobs().get(slotIndex);
if (oldJobId != null) {
    // Compter les quêtes complétées non claimées pour l'avertissement
    int unclaimedCount = questManager.countUnclaimedQuests(data, oldJobId);
    // Supprimer TOUTES les quêtes du job (en cours + complétées + claimées)
    questManager.resetJobQuests(player, oldJobId);
    // resetJobQuests() : DELETE FROM quest_progress WHERE uuid=? AND quest_id LIKE oldJobId+'%'
    // Remet aussi à zéro job_data.assigned_daily_quests et assigned_weekly_quests
}
data.getSlotJobs().put(slotIndex, newJobId);
```

**Message d'avertissement AVANT confirmation** (si quêtes non claimées) :
```
§c§lATTENTION — §7Tu as §c{unclaimed} quête(s) complétée(s) non réclamée(s) pour §b{old_job}§7.
§7Si tu changes de métier, ces récompenses seront §cperdues définitivement§7.
§eClique ici pour confirmer : §f/jobs confirmer §8| §f/jobs annuler
```

**Message après confirmation :**
```
§6§lChangement de métier confirmé.
§7Tes quêtes de §b{old_job} §7ont été réinitialisées.
§7Bienvenue dans le métier §b{new_job}§7 !
```

---

## 3. XP Simultané — Même Tick

### Deux listeners donnent XP au même tick (ex: Farmer + event personnalisé)

**Comportement :** Les listeners Bukkit s'exécutent séquentiellement sur le main thread — il n'y a pas de vrai "simultané". L'ordre est déterministe (ordre d'enregistrement).

**displayJob :** Le dernier listener à appeler `HudManager.onXpGain()` dans le même tick l'emporte pour `displayJob`. En pratique, ce scénario est rare (un bloc brisé = un seul type de bloc = un seul job actif).

**Aucune action requise** : comportement accepté.

---

## 4. /kjob bonus — Comportement Exact

### Format et persistance du bonus XP admin

**Stockage :** Table `bonus_multipliers` en SQLite (voir DONNEES-JOUEUR-SCHEMA.md).

**Calcul au gain d'XP :**
```java
// Dans MinerListener (et tous les listeners), après calcul xp brut :
double bonus = data.getBonusMultiplier("mineur");  // cherche "mineur" puis "all", prend le max
xp = (int) Math.floor(xp * bonus);
```

**Règles de précédence :**
- `/kjob bonus <joueur> 2.0 mineur` → multiplie XP *mineur* uniquement, pour ce joueur
- `/kjob bonus all 1.5 all` → multiplie TOUS les XP de TOUS les joueurs (ligne "all")
- Si un joueur a un bonus job-specific ET un bonus "all" : prendre le **plus élevé des deux**

**Appliquer à tous les joueurs connectés :**
```
/kjob bonus all 2.0 mineur
→ UPDATE bonus_multipliers SET multiplier=2.0, set_by='admin', set_at=now
  WHERE job_id='mineur'  [joueurs déjà présents en DB]
→ Pour les joueurs connectés : mettre à jour data.bonusMultipliers.put("mineur", 2.0)
```

**Retirer un bonus :**
```
/kjob bonus <joueur> 1.0 mineur   ← 1.0 = comportement par défaut (no-op)
```

---

## 5. /kjob spy — Format de Sortie

### Qu'est-ce que /kjob spy <joueur> <temps> affiche exactement ?

**Usage :** `/kjob spy <joueur> <minutes>` — lance une **session de surveillance** en temps réel pour le joueur ciblé. La session collecte les gains XP à partir de maintenant pendant X minutes, puis affiche le résumé.

**Approche : SpySessions on-demand (PAS de buffer global per-player)**

> Raison : à 600 joueurs en farm actif (~20 actions/min), un buffer permanent 1H per-player représenterait
> **600 × 1200 entrées × ~100 bytes = ~72 MB** rien que pour le spy. Inacceptable.
> La surveillance est admin-only et rare → on-demand seulement.

```java
// Dans KjobAdminCommand.handleSpy()
public class SpySession {
    UUID watchedPlayer;
    UUID watchingAdmin;
    long startTime;
    int durationMinutes;          // max 60 min (configurable)
    Map<String, Integer> xpByJob; // jobId → XP total collecté
    int actionCount;

    boolean isExpired() {
        return System.currentTimeMillis() - startTime > durationMinutes * 60_000L;
    }
}

// Registre global (dans KjobAdminCommand ou un SpyManager)
Map<UUID, SpySession> activeSessions; // clé = UUID du joueur surveillé
// Max sessions simultanées : 10 (configurable spy_max_sessions dans config.yml)
```

**Alimentation :** Dans `HudManager.onXpGain()` :
```java
SpySession session = spyManager.getSession(player.getUniqueId());
if (session != null && !session.isExpired()) {
    session.xpByJob.merge(jobId, xpGained, Integer::sum);
    session.actionCount++;
} else if (session != null) {
    spyManager.closeSession(player.getUniqueId(), session);  // affiche résumé à l'admin
}
```

**Format de sortie (chat admin, à la fin de la session) :**
```
§8[§eSpy§8] §bTimot §7— §f5 minutes §8(session terminée)
─────────────────────────────────
§7Mineur   : §f+1240 XP §8(§7248 XP/min§8)
§7Hunter   : §f+360 XP  §8(§772 XP/min§8)
─────────────────────────────────
§7Total    : §f1600 XP §8| §f47 actions
```

**Commande d'arrêt anticipé :** `/kjob spy stop <joueur>` — affiche le résumé immédiatement.

**Si le joueur se déconnecte pendant la session :** session auto-fermée, résumé envoyé à l'admin.

---

## 6. /kjob cap — Format de Sortie

### Qu'affiche /kjob cap <joueur> <job> ?

**Format de sortie :**
```
§8[§eKjob§8] Cap XP de §bTimot §7pour §bMineur §7:
  §7Cap configuré  : §f5000 XP§7/jour
  §7XP accumulé   : §f3240 XP §8(§a64%§8)
  §7Restant        : §f1760 XP
  §7Prochain reset : §fminuit §8(dans ~4h20)
```

Si `daily_xp_cap` est 0 pour ce job :
```
§7Aucun cap XP configuré pour §bMineur§7.
```

---

## 7. /kjob reload — Comportement à Chaud

### Que se passe-t-il si /kjob reload est appelé avec des joueurs connectés ?

**Comportement :**
1. Toutes les configs YAML rechargées (`config.yml`, `messages.yml`, `hud.yml`, `jobs/*.yml`, `quests/*.yml`, etc.)
2. Les `PlayerData` en RAM **ne sont PAS touchés** (progression conservée)
3. Les `JobConfig` et `QuestConfig` en RAM **sont remplacés** par les nouvelles valeurs
4. Les listeners utilisent les nouvelles `JobConfig` à partir du prochain event
5. Le scheduler HUD est **non interrompu** (il continue de tourner)

**Avertissement :** Si une action job est supprimée ou modifiée après un reload, les cooldowns en RAM pointent vers les anciens configs. Ce n'est pas un bug (les cooldowns expirent naturellement).

**Message admin :**
```
§a[KjobUltimate] Configuration rechargée (§f12 jobs, §f48 quêtes§a chargés).
§7Les données joueurs en RAM sont préservées.
```

---

## 8. Gestion d'Erreur SQLite

### Que se passe-t-il si la base SQLite est inaccessible ?

**Lors de `onEnable()` — Si `SQLiteStorage.init()` échoue :**
```java
try {
    storage.init();
} catch (SQLException e) {
    getLogger().severe("[KjobUltimate] ERREUR CRITIQUE : Impossible d'ouvrir la DB SQLite !");
    getLogger().severe("Raison : " + e.getMessage());
    getLogger().severe("Le plugin va se désactiver pour éviter toute perte de données.");
    Bukkit.getPluginManager().disablePlugin(this);
    return;
}
```

**Lors d'une sauvegarde async — Si `storage.save(data)` échoue :**
```java
try {
    storage.save(data);
} catch (SQLException e) {
    // Logger l'erreur mais NE PAS crasher le plugin
    getLogger().warning("[KjobUltimate] Erreur sauvegarde pour " + data.getUuid() + " : " + e.getMessage());
    // Les données restent en RAM — sauvegarde retentée à l'autosave suivant
}
```

**Lors du chargement d'un joueur — Si `storage.load(uuid)` échoue :**
```java
try {
    data = storage.load(uuid);
} catch (SQLException e) {
    getLogger().warning("[KjobUltimate] Erreur chargement pour " + uuid + " : " + e.getMessage());
    // Utiliser des données vides par défaut (joueur commence comme un nouveau)
    data = PlayerData.createDefault(uuid);
}
```

**Backup :** Aucun backup automatique en V1. Recommandé : backup externe via cron job ou plugin de backup serveur.

---

## 9. Déconnexion Pendant Quête En Cours

### Que se passe-t-il si un joueur se déconnecte au milieu d'une quête ?

**Comportement :** La progression est **sauvegardée immédiatement** via `saveAsync()` dans `PlayerQuitListener`.

```java
// PlayerQuitListener.onPlayerQuit()
playerDataManager.saveAsync(player.getUniqueId());
// → toute la progression quest_progress est écrite en SQLite
// → le joueur retrouve exactement son état à la reconnexion
```

**Pas de pénalité, pas de reset.** Le joueur reprend là où il était.

---

## 10. /kjob addxp admin — Progression Quêtes ?

### Si un admin fait /kjob addxp <joueur> 1000 mineur — les quêtes avancent-elles ?

**Comportement décidé :** **NON** — `/kjob addxp` donne du raw XP sans déclencher les listeners de quêtes.

**Justification :** La commande admin sert à corriger/tester les niveaux, pas à simuler des actions de jeu. Déclencher les quêtes depuis une commande admin serait confus et potentiellement abusable.

**Si l'admin veut compléter une quête manuellement :** `/kjob questgive <joueur> <questId>`

---

## 11. Kgui Absent — Fallback GUI

### Que se passe-t-il si Kgui n'est pas installé ?

**Comportement par `KguiHook` :**
```java
public boolean isKguiEnabled() {
    return Bukkit.getPluginManager().getPlugin("Kgui") != null;
}
```

**Fallback pour `/jobs` :**
Si Kgui absent → afficher les infos jobs en chat (format compact) :
```
§8[§bKjobs§8] §7Tes métiers actifs :
  §bMineur  §7Lv.§f7  §8[§a███████░░░§8] §f55%
  §bFarmer  §7Lv.§f3  §8[§a███░░░░░░░§8] §f28%
§7Pour les quêtes : §e/jobs quests
```

**Fallback pour `/jobs quests` :**
Liste des quêtes en chat avec numéros cliquables (si supporté).

**NOTE :** Le fallback chat est fonctionnel mais moins ergonomique. Kgui est fortement recommandé.

---

## 12. Kstacker META_KILL_MULTIPLIER — Détails Techniques

### Quel est le type exact de META_KILL_MULTIPLIER ?

**Dans Kstacker :** Le nombre de mobs tués en un seul event EntityDeathEvent (car les mobs stackés comptent pour plusieurs).

**Type :** `int` stocké dans les **EntityMetadata Bukkit** :
```java
// Dans Kstacker (à vérifier dans le code source)
entity.setMetadata("kill_count", new FixedMetadataValue(plugin, killedCount));
// killedCount = nombre de mobs dans le stack tués

// Dans HunterListener de KjobsUltimate :
int killMultiplier = 1;
if (entity.hasMetadata("kill_count")) {
    killMultiplier = entity.getMetadata("kill_count").get(0).asInt();
    killMultiplier = Math.min(killMultiplier, configManager.getMaxKillMultiplier()); // cap = 3 par défaut
}
int xp = action.getXp() * killMultiplier;
```

**Cap configuré dans config.yml :**
```yaml
anti_abuse:
  mob_kill_multiplier_max: 3  # cap absolu pour les mobs stackés
```

---

## 13. Job List Exact — Fisher / Woodcutter ?

### Combien de jobs existent en V1 ?

**Jobs confirmés en V1 : 5**

| Job ID | Nom affiché | Type principal |
|---|---|---|
| `mineur` | Mineur | Minage de blocs |
| `farmer` | Farmer | Récolte de cultures |
| `hunter` | Hunter | Kills de mobs |
| `pretorien` | Prétorien | PvP + items consommables |
| `artisant` | Artisant | Craft (vanilla + Kcraft) |

**Pêcheur / Bûcheron :** Non inclus en V1. Pourrait être ajouté en V1.1 ou V2.
- Pêcheur : `PlayerFishEvent` (1.8.8 supporte)
- Bûcheron : `BlockBreakEvent` avec `Material.LOG` — trivial à ajouter

**Pour ajouter un job future :** Créer un fichier `jobs/<jobId>.yml` + un Listener dédié. Aucune modification du code core requise.

---

## 14. Placeholders PAPI — Liste Complète

| Placeholder | Description | Exemple |
|---|---|---|
| `%kjob_level_<job>%` | Niveau actuel | `%kjob_level_mineur%` → `7` |
| `%kjob_xp_<job>%` | XP actuel dans niveau | `%kjob_xp_mineur%` → `3200` |
| `%kjob_xp_next_<job>%` | XP requis prochain niveau | `%kjob_xp_next_mineur%` → `5800` |
| `%kjob_progress_<job>%` | Pourcentage 0-100 | `%kjob_progress_mineur%` → `55` |
| `%kjob_bar_<job>%` | Barre texte ████░░ | `%kjob_bar_mineur%` → `████████░░` |
| `%kjob_display%` | Job affiché en bossbar | `%kjob_display%` → `mineur` |
| `%kjob_display_name%` | Nom coloré du job actif | `%kjob_display_name%` → `§bMineur` |
| `%kjob_active_jobs%` | Jobs actifs séparés par virgule | `%kjob_active_jobs%` → `mineur,farmer` |
| `%kjob_quests_done%` | Quêtes complétées aujourd'hui | `%kjob_quests_done%` → `2` |
| `%kjob_quests_total%` | Total quêtes daily actives | `%kjob_quests_total%` → `3` |

**Par slot :** Non prévu en V1. Les placeholders s'appliquent au `displayJob` ou à un jobId spécifique.

---

## 15. CONFIG-REFERENCE vs Schéma SQLite — Cohérence

> **Contradiction identifiée et résolue ici.**

CONFIG-REFERENCE.md documente les options de config.yml. La clé `storage.type` peut valoir :
- `SQLITE` ← **recommandé et par défaut**
- `YAML` ← legacy uniquement (pour migration KJob2)

CONFIG-REFERENCE.md mentionnait YAML comme seul exemple. **SQLite est le choix par défaut.**

---

## 16. Intégration Kstacker — Soft ou Obligatoire ?

> **Contradiction identifiée et résolue ici.**

INTEGRATION-MAP.md disait "Kstacker à modifier AVANT". ARCHITECTURE-GLOBALE dit "soft hook".

**Décision finale :** Kstacker est un **soft hook**.
- Si Kstacker absent : HunterListener utilise `killMultiplier = 1` (pas de bonus stack)
- Si Kstacker présent : HunterListener lit `entity.getMetadata("kill_count")`
- **Aucune modification de Kstacker n'est requise** si Kstacker expose déjà ce metadata

PRE-DEV-CHECKLIST.md tâche A2 ("Vérifier que Kstacker expose kill_count") : à exécuter pour confirmation — si le metadata n'est pas exposé, demander à l'équipe Kstacker, sinon no-op.

---

## 17. Thread-Safety des Listeners

### Tous les listeners sont-ils sur le main thread ?

**OUI** — tous les `@EventHandler` Bukkit s'exécutent sur le main thread (sauf si explicitement `Bukkit.getScheduler().runTaskAsynchronously`). KjobsUltimate ne déclare aucun listener async.

**Seules parties async :**
- `PlayerDataManager.loadAsync()` — callback retourne sur main thread via `runTask()`
- `PlayerDataManager.saveAsync()` — écriture DB uniquement, pas de lecture PlayerData
- `GlobalScheduler` — tick sur main thread (pas async)

**ConcurrentHashMap :** Utilisé dans `PlayerDataManager.cache` pour protéger contre les accès rares depuis `saveAsync` (le `cache.remove(uuid)` est appelé depuis le main thread, mais la lecture depuis async est possible brièvement).

---

## 18. Performance à 600 Joueurs Simultanés

### Implications concrètes de la cible 600 joueurs actifs en farm

> Cible confirmée : **600 joueurs simultanés farmant** sur SparrowMC.
> Ce n'est pas une cible théorique — l'architecture DOIT le supporter sans lag perceptible.

---

### 18.1 blockCooldowns / mobCooldowns — Risque d'accumulation RAM

**Problème :** Si un mineur mine 10 blocs/seconde avec un cooldown de 300s par position,
il accumule jusqu'à **3000 entrées** dans sa `HashMap<String, Long> blockCooldowns`.
Pour 600 joueurs : **1 800 000 entrées** ≈ **150-200 MB RAM** dans le pire cas.

**Solution obligatoire — cleanup périodique dans le scheduler :**
```java
// Dans GlobalScheduler, toutes les 60 ticks (~3 secondes) :
if (tick % 60 == 0) {
    long now = System.currentTimeMillis();
    for (PlayerData data : playerDataManager.getOnlineData()) {
        data.getBlockCooldowns().entrySet().removeIf(e -> e.getValue() <= now);
        data.getMobCooldowns().entrySet().removeIf(e -> e.getValue() <= now);
    }
}
```
Avec cleanup toutes les 60 ticks, une entrée reste au maximum 3 secondes après expiration.
Charge réelle en RAM : ~60-90 secondes de cooldowns actifs uniquement.

---

### 18.2 BossBar Wither — Paquets de téléportation

**Chaque bossbar** = 1 fake Wither invisible envoyé au joueur.
Si le scheduler teleporte le Wither à chaque joueur connecté toutes les 40 ticks :
- 600 joueurs × 1 `PacketPlayOutEntityTeleport` = **600 paquets toutes les 2 secondes**
- C'est gérable sur 1.8.8 PandaSpigot.

**Optimisation appliquée :**
- Téléporter le Wither **uniquement si la bossbar est visible** (`data.hudEnabled && lastXpTimestamp dans la fenêtre`)
- Si bossbar invisible (`sendInvisible()` déjà envoyé) → **skip la téléportation** ce tick
- Si le joueur n'a pas bougé de >8 blocs depuis la dernière téléportation → **skip aussi**

```java
// Dans GlobalScheduler, partie HUD :
if (data.hudEnabled && data.isHudVisible()) {
    // Vérifier si le joueur a bougé suffisamment
    if (data.witherNeedsRelocation(player.getLocation())) {
        hudManager.teleportWither(player, data);
        data.updateLastWitherLocation(player.getLocation());
    }
}
```

---

### 18.3 Scheduler Global — Charge CPU

**Iteration sur 600 PlayerData toutes les 40 ticks :**
- Chaque itération = vérification `dirty`, `lastXpTimestamp`, `hudEnabled` → très rapide (O(1))
- Cleanup cooldowns toutes les 60 ticks = itération + removeIf → O(N) mais léger
- Tab header/footer : 1 paquet par joueur toutes les 40 ticks (si `tab.update_interval` = 40)

**Total paquets émis par tick (40) pour 600 joueurs :**
| Action | Condition | Paquets/40 ticks |
|---|---|---|
| Wither teleport | HUD visible + joueur bouge | 0-600 |
| Tab header/footer | Toujours (si tab activé) | 600 |
| ActionBar | Si XP reçu récemment | 0-600 |
| BossBar metadata update | dirty=true uniquement | 0-600 |

**Conclusion :** Charge réseau raisonnable, très en-dessous des limites PandaSpigot.

---

### 18.4 SQLite à 600 Joueurs — Autosave

**Scenario worst case** : 600 joueurs se déconnectent en 1 minute (reboot serveur).
→ 600 appels `saveAsync()` quasi-simultanés sur SQLite.

**SQLite WAL mode** : les writes sont sérialisés par le WAL — pas de corruption.
Chaque save ≈ 3 UPDATEs (players + job_data + quest_progress) = ~3ms max.
600 × 3ms = **~2 secondes** pour tout sauvegarder (async, pas de blocage main thread).

**Autosave périodique** (toutes les 5 minutes) : batch de 600 saves → même analyse. OK.

---

### 18.5 SpyManager — Cap Strict

Rappel : le SpyBuffer est **on-demand uniquement** (voir §5).
```yaml
# config.yml section anti_abuse
spy:
  max_concurrent_sessions: 10   # max 10 joueurs surveillés simultanément
  max_duration_minutes: 60      # durée max d'une session spy
```

Pour 10 sessions actives × 3600 entrées × 100 bytes = **~3.6 MB max**. Négligeable.
