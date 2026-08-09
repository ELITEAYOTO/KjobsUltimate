# KjobUltimate — Système de Quêtes

> Inspiré de Kclan (structure, QuestData, reset daily), adapté aux jobs.

---

## 1. Concepts Fondamentaux

| Concept | Description |
|---|---|
| **Quest** | Définition d'une quête (depuis quests.yml) — statique, chargée en mémoire au démarrage |
| **QuestData** | Progression d'UN joueur sur UNE quête — dynamique, sauvegardée |
| **QuestManager** | Orchestrateur : charge les quêtes, gère la progression, les resets, les claims |
| **QuestType** | Enum : MINING, FARMING, HUNTING, PVP, CRAFTING, FISHING, CONSUME |
| **Difficulty** | Enum : FACILE, AVANCE, DIFFICILE — détermine la couleur d'affichage et les récompenses |

---

## 2. Types de Quêtes et Déclencheurs

| Type | Événement Bukkit | Condition | Paramètre cible |
|---|---|---|---|
| `mining` | `BlockBreakEvent` | block.getType() | `block: STONE` |
| `farming` | `BlockBreakEvent` | + check mature (getData()) | `block: WHEAT` |
| `hunting` | `EntityDeathEvent` | entity.getKiller() != null | `entity: ZOMBIE` |
| `pvp` | `PlayerDeathEvent` | killer != null, killer ≠ target | `type: player` |
| `crafting` | `CraftItemEvent` ou `KcraftCraftCompleteEvent` | résultat du craft | `material: IRON_SWORD` ou `kcraft_recipe: epee_de_feu` |
| `consume` | `PlayerItemConsumeEvent` | item.getType() + CIT check | `material: GOLDEN_APPLE` ou `cit: joint` |
| `fishing` | `PlayerFishEvent` | state == CAUGHT_FISH | `type: any` ou `item: SALMON` |

---

## 3. Système de Difficulté

Les quêtes ont 3 niveaux de difficulté, remplaçant le système de rareté.

| Difficulté | Couleur GUI | Description |
|---|---|---|
| `facile` | `§a` (vert) | Objectif simple, récompenses modestes — accessible à tous |
| `avance` | `§6` (or) | Objectif intermédiaire, meilleures récompenses |
| `difficile` | `§c` (rouge) | Objectif élevé, grosses récompenses — pour joueurs avancés |

### Règle de distribution recommandée par pool
- Daily pool : 50% facile, 35% avancé, 15% difficile
- Weekly pool : 30% facile, 45% avancé, 25% difficile
- Permanentes : toutes les difficultés représentées

---

## 4. Attribution et Renouvellement

### Système d'assignation aléatoire (inspiré de Kclan)

| Type | Nb assigné | Pool source | Reset |
|---|---|---|---|
| Daily | 3 quêtes | Pool daily du job (toutes difficultés confondues) | 24H depuis `join_timestamp` |
| Weekly | 5 quêtes | Pool weekly du job | 7 jours depuis `join_timestamp` |
| Permanent | Toutes | Pool permanent du job | Jamais |

### Timer personnalisé par joueur (pas de minuit global)
- Le timer est ancré sur `job_join_timestamp` (timestamp du premier select du job)
- Reset daily : si `now - last_daily_reset >= 86 400 000 ms` → re-tirer 3 quêtes
- Reset weekly : si `now - last_weekly_reset >= 604 800 000 ms` → re-tirer 5 quêtes
- Avantage : chaque joueur a son propre cycle, pas de rush au reset de minuit

### Vérification du reset
Vérifié à chaque fois que le joueur :
- Ouvre son GUI de quêtes
- Se connecte au serveur
- Complète une quête (pour s'assurer que les données sont à jour)

---

## 5. Format Config quests.yml — Référence Complète

```yaml
quests:

  # ─── ID UNIQUE de la quête ────────────────────────────
  mineur_daily_stone_facile:

    # Nom affiché dans le GUI (codes couleur &)
    display_name: "§aCasseur de Pierres"

    # Description (lore multi-lignes)
    description:
      - "Miner 200 blocs de pierre."
      - "Une tâche quotidienne de base."

    # Job auquel cette quête est liée
    job: mineur

    # Type de quête (mining, farming, hunting, pvp, crafting, consume, fishing)
    type: mining

    # Difficulté : facile | avance | difficile
    difficulty: facile

    # Cible selon le type :
    target:
      block: STONE              # pour mining/farming : Material 1.8.8
      data: 0                   # damage value (0 = variant par défaut)
      # entity: ZOMBIE          # pour hunting
      # material: IRON_SWORD    # pour crafting vanilla
      # kcraft_recipe: epee_de_feu  # pour crafting Kcraft
      # cit: joint              # pour consume : CIT custom item (optionnel)

    # Nombre d'actions requises
    objective: 200

    # Type de reset
    # daily  = inclus dans le pool daily (3 tirés aléatoirement / 24H par joueur)
    # weekly = inclus dans le pool weekly (5 tirés aléatoirement / 7 jours par joueur)
    # never  = quête permanente — toutes visibles, ne se reset jamais
    reset: daily

    # Niveau minimum requis dans le job pour voir/accéder à la quête
    min_level: 0

    # Récompenses
    rewards:
      xp: 800                  # XP donné IMMÉDIATEMENT à la complétion
      money: 200               # Vault — clic dans GUI pour claim
      items:
        - "COAL:0:16"          # FORMAT : MATERIAL:damage:quantite
      commands: []             # commandes console exécutées au claim

  # ─── Exemple quête difficile ──────────────────────────
  mineur_weekly_diamant_difficile:
    display_name: "§cChasseur de Diamants"
    description:
      - "Miner 30 diamants."
      - "Réservé aux mineurs expérimentés."
    job: mineur
    type: mining
    difficulty: difficile
    target:
      block: DIAMOND_ORE
      data: 0
    objective: 30
    reset: weekly
    min_level: 10
    rewards:
      xp: 8000
      money: 3000
      items:
        - "DIAMOND:0:5"
      commands:
        - "broadcast §b{player} §fa complété §cChasseur de Diamants !"
```

---

## 6. Structure des Données Joueur

### QuestData.java

```java
public class QuestData {
    private final String questId;
    private int progress;           // progression actuelle
    private boolean completed;      // quête terminée ?
    private boolean claimed;        // récompenses récupérées ?
    private long completedAt;       // timestamp de complétion

    // Getters/setters
    public boolean isClaimReady() {
        return completed && !claimed;
    }
}
```

### Stockage dans PlayerData

```java
class PlayerData {
    // ...
    Map<String, QuestData> questProgress;
    // questId → QuestData
    // ex: "mineur_weekly_diamond" → QuestData{progress=12, completed=false}
}
```

### Stockage persistant — SQLite (table `quest_progress`)

> Le stockage est **SQLite uniquement** (plus de YAML par joueur).
> Voir DONNEES-JOUEUR-SCHEMA.md section 2 pour le schéma complet.

```sql
-- Une ligne par (joueur, quêteId)
SELECT * FROM quest_progress WHERE uuid = '...' AND quest_id = 'mineur_daily_stone';
-- Résultat : progress=312, completed=1, claimed=0, completed_at=1748217600000
```

**Le daily_xp et last_daily_reset** sont dans `job_data`, pas dans `quest_progress`.

---

## 5. QuestManager — Logique Principale

### 5.1 Chargement des Quêtes

```java
public class QuestManager {

    // Toutes les quêtes définies dans quests.yml (statique, RAM uniquement)
    private final Map<String, Quest> questRegistry = new HashMap<>();

    // Quêtes par job et par type (index pour performance)
    private final Map<String, Map<QuestType, List<Quest>>> questsByJobAndType = new HashMap<>();

    public void loadQuests(FileConfiguration questsConfig) {
        questRegistry.clear();
        questsByJobAndType.clear();

        ConfigurationSection section = questsConfig.getConfigurationSection("quests");
        if (section == null) return;

        for (String questId : section.getKeys(false)) {
            ConfigurationSection q = section.getConfigurationSection(questId);
            Quest quest = parseQuest(questId, q);
            questRegistry.put(questId, quest);

            // Index par job + type
            String job = quest.getJob();
            QuestType type = quest.getQuestType();
            questsByJobAndType
                .computeIfAbsent(job, k -> new HashMap<>())
                .computeIfAbsent(type, k -> new ArrayList<>())
                .add(quest);
        }
    }
}
```

### 5.2 Progression d'une Quête

```java
/**
 * Appelé par les listeners quand une action se produit.
 * @param player   Le joueur
 * @param type     Type de quête concerné (MINING, FARMING, etc.)
 * @param target   Cible (Material.name() ou EntityType.name())
 * @param amount   Quantité accomplie (généralement 1)
 */
public void progress(Player player, QuestType type, String target, int amount) {
    PlayerData data = playerDataManager.get(player);

    // Récupérer toutes les quêtes de type MINING actives pour le joueur
    for (String jobId : jobManager.getJobIds()) {
        List<Quest> candidates = getQuestsByJobAndType(jobId, type);
        if (candidates == null) continue;

        for (Quest quest : candidates) {
            // Vérifier si cette quête correspond au target
            if (!quest.matchesTarget(target)) continue;

            // Vérifier le niveau minimum
            if (data.getLevel(jobId) < quest.getMinLevel()) continue;

            // Récupérer ou créer la progression
            QuestData questData = data.getOrCreateQuestData(quest.getId());

            // Si déjà complétée, ignorer
            if (questData.isCompleted()) continue;

            // Incrémenter
            questData.addProgress(amount);

            // Vérifier si complétée
            if (questData.getProgress() >= quest.getObjective()) {
                questData.setCompleted(true);
                questData.setCompletedAt(System.currentTimeMillis());

                // Donner l'XP immédiatement
                if (quest.getRewards().getXp() > 0) {
                    data.addXP(jobId, quest.getRewards().getXp());
                }

                // Notifier le joueur
                messagesConfig.send(player, "quest.complete",
                    Map.of("quest_name", quest.getDisplayName()));

                // Son
                soundManager.play(player, "on_quest_complete", null);

                // Marquer comme claimable (items/money nécessitent retour GUI)
                if (quest.getRewards().hasMaterialRewards()) {
                    notifyClaimable(player, quest);
                }
            }
        }
    }
}
```

### 5.3 Claim des Récompenses (GUI)

```java
/**
 * Appelé quand le joueur clique sur une quête "claimable" dans le GUI.
 */
public boolean claimReward(Player player, String questId) {
    PlayerData data = playerDataManager.get(player);
    QuestData questData = data.getQuestData(questId);

    if (questData == null || !questData.isClaimReady()) {
        messagesConfig.send(player, "quest.not_claimable", Map.of());
        return false;
    }

    Quest quest = questRegistry.get(questId);
    QuestRewards rewards = quest.getRewards();

    // Vérifier l'inventaire (si items à donner)
    if (!rewards.getItems().isEmpty()) {
        int freeSlots = countFreeInventorySlots(player);
        if (freeSlots < rewards.getItems().size()) {
            messagesConfig.send(player, "quest.inventory_full", Map.of());
            return false;
        }
    }

    // Donner les items
    for (ItemStack item : rewards.buildItems()) {
        player.getInventory().addItem(item);
    }

    // Donner l'argent
    if (rewards.getMoney() > 0 && vaultHook.isEnabled()) {
        vaultHook.getEconomy().depositPlayer(player, rewards.getMoney());
    }

    // Exécuter les commandes
    for (String cmd : rewards.getCommands()) {
        String resolved = cmd
            .replace("{player}", player.getName())
            .replace("{level}", String.valueOf(data.getLevel(quest.getJob())));
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
    }

    // Marquer comme claimé
    questData.setClaimed(true);

    // Feedback
    messagesConfig.send(player, "quest.claimed",
        Map.of("quest_name", quest.getDisplayName()));
    soundManager.play(player, "on_quest_claim", null);

    return true;
}
```

---

## 6. Système de Reset des Quêtes

### Reset Daily (minuit)

```java
// Dans le scheduler global, vérifier toutes les 20 ticks (1 seconde suffit)
public void checkDailyReset() {
    String today = DATE_FORMAT.format(new Date());
    if (!today.equals(lastResetDate)) {
        resetDailyQuests();
        lastResetDate = today;
        saveResetDate();
    }
}

private void resetDailyQuests() {
    plugin.getLogger().info("[KjobUltimate] Reset des quêtes daily...");
    // Pour chaque joueur chargé en mémoire
    for (PlayerData data : playerDataManager.getAllLoaded()) {
        resetQuestsOfType(data, "daily");
        // Reset aussi le cap XP quotidien
        data.resetDailyXP();
    }
    // Sauvegarder les fichiers des joueurs connectés
    playerDataManager.saveAll();
}
```

### Reset Weekly (lundi minuit)

```java
private boolean isWeeklyResetDay() {
    Calendar cal = Calendar.getInstance();
    return cal.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY;
}
```

---

## 7. Interface GUI des Quêtes

```
╔═══════════════════════════════════════════════════════════╗
║           §8§lQuêtes — §bMineur                              ║
╠═══════════════════════════════════════════════════════════╣
║ [■] [■] [■] [■] [■] [■] [■] [■] [■]                      ║
║ [■] [Q] [Q] [Q] [Q] [Q] [Q] [Q] [■]  ← 7 quêtes/ligne    ║
║ [■] [Q] [Q] [Q] [Q] [Q] [Q] [Q] [■]                      ║
║ [■] [Q] [Q] [Q] [Q] [Q] [Q] [Q] [■]  ← max 21 slots quête║
║ [■] [■] [■] [■] [■] [■] [■] [■] [■]                      ║
║ [◄] [■] [■] [■] [✖] [■] [■] [■] [►]  ← nav prev/close/next
╚═══════════════════════════════════════════════════════════╝

[Q] : PAPER (disponible), MAP (en cours), EMERALD (à claim), BARRIER (verrouillé)
Lore selon état de la quête (voir gui.yml)
```

### Navigation entre GUIs

```
/jobs
  └─ [JobsOverviewGUI 54 slots]
        ├─ Clic job MINEUR → [JobDetailGUI]
        │       ├─ Stats du job
        │       ├─ Bouton "Quêtes" → [QuestGUI - quêtes Mineur]
        │       └─ Bouton "Retour" → JobsOverviewGUI
        │
        └─ Bouton "Toutes les Quêtes" → [QuestGUI - toutes quêtes]
```

---

## 8. Anti-Spam de Progression

Le `QuestManager.progress()` est appelé à chaque `BlockBreakEvent`, `EntityDeathEvent`, etc.
La progression doit être efficace (pas de lecture disque à chaque event) :

1. **Toutes les données de quêtes en RAM** (`QuestData` dans `PlayerData` en cache mémoire)
2. **Pas de sauvegarde à chaque progression** → sauvegarde seulement :
   - À la déconnexion du joueur
   - Lors de l'autosave (toutes les X minutes)
   - Lors d'un `/kjobadmin reload`
   - À la complétion d'une quête (optionnel, pour sécurité)
3. **Index par type** : `questsByJobAndType` évite d'itérer TOUTES les quêtes à chaque event
   - Mining block break → on ne check que les quêtes `type: mining` du job concerné
