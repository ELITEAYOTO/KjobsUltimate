# KjobsUltimate — Schéma Données Joueur + SQLite

> Stockage : SQLite (1 fichier `data/kjobultimate.db`)
> Accès thread-safe : toutes les lectures/écritures hors main thread SAUF `get(player)` qui retourne depuis le cache RAM.

---

## 1. Architecture de la Couche Données

```
[Main Thread]                          [Async Thread]
     │                                       │
     │  playerDataManager.get(player)        │
     │  → retourne depuis cache RAM          │
     │  (chargé au join, toujours dispo)     │
     │                                       │
     │  data.addXP(...)                      │  playerDataManager.saveAsync(uuid)
     │  → modifie directement en RAM         │  → écrit dans SQLite
     │                                       │
```

### PlayerDataManager.java

```java
public class PlayerDataManager {
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private final SQLiteStorage storage;

    // Appelé depuis PlayerLoginEvent (async safe)
    public void loadAsync(UUID uuid, Runnable onLoaded) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            PlayerData data = storage.load(uuid);
            if (data == null) data = PlayerData.createDefault(uuid);
            cache.put(uuid, data);
            Bukkit.getScheduler().runTask(plugin, onLoaded);  // callback main thread
        });
    }

    // Appelé depuis PlayerQuitEvent
    public void saveAsync(UUID uuid) {
        PlayerData data = cache.get(uuid);
        if (data == null) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> storage.save(data));
        cache.remove(uuid);
    }

    // Main thread seulement — JAMAIS null si le joueur est connecté
    public PlayerData get(Player player) {
        return cache.get(player.getUniqueId());
    }

    // onDisable — synchrone (serveur s'arrête)
    public void saveAll() {
        cache.values().forEach(storage::save);
    }
}
```

---

## 2. Schéma SQLite

### Table `players`

```sql
CREATE TABLE IF NOT EXISTS players (
    uuid        TEXT NOT NULL PRIMARY KEY,  -- UUID string (sans tirets)
    last_seen   INTEGER NOT NULL,           -- timestamp UNIX dernière connexion
    first_join  INTEGER NOT NULL,           -- timestamp UNIX premier join
    hud_enabled INTEGER NOT NULL DEFAULT 1, -- master HUD: 1 = visible, 0 = tout masque (/jobs hud)
    bossbar_enabled INTEGER NOT NULL DEFAULT 1, -- 1 = bossbar jobs visible
    actionbar_enabled INTEGER NOT NULL DEFAULT 1, -- 1 = messages actionbar XP visibles
    display_job TEXT,                       -- dernier jobId ayant donné XP (bossbar affichée)
    last_xp_timestamp INTEGER NOT NULL DEFAULT 0, -- timestamp UNIX du dernier gain XP (timer bossbar)
    last_job_change_at INTEGER NOT NULL DEFAULT 0
);
```

### Table `job_data`

```sql
CREATE TABLE IF NOT EXISTS job_data (
    uuid                    TEXT NOT NULL,
    job_id                  TEXT NOT NULL,
    level                   INTEGER NOT NULL DEFAULT 1,
    xp                      INTEGER NOT NULL DEFAULT 0,
    daily_xp                INTEGER NOT NULL DEFAULT 0,    -- XP accumulé aujourd'hui
    join_timestamp          INTEGER NOT NULL DEFAULT 0,    -- timestamp UNIX du premier join du job
    last_daily_reset        INTEGER NOT NULL DEFAULT 0,    -- timestamp UNIX du dernier reset daily
    last_weekly_reset       INTEGER NOT NULL DEFAULT 0,    -- timestamp UNIX du dernier reset weekly
    assigned_daily_quests   TEXT,   -- JSON array des IDs de quêtes daily assignées
    assigned_weekly_quests  TEXT,   -- JSON array des IDs de quêtes weekly assignées
    PRIMARY KEY (uuid, job_id),
    FOREIGN KEY (uuid) REFERENCES players(uuid)
);
```

**Exemple de valeurs `assigned_daily_quests`** : `["mineur_daily_stone_01", "mineur_daily_coal", "mineur_daily_gravel"]`

**Logique de reset :**
- Si `now - last_daily_reset >= 86400000` (24H) → re-tirer 3 nouvelles quêtes du pool daily, update `last_daily_reset = now`
- Si `now - last_weekly_reset >= 604800000` (7 jours) → re-tirer 5 nouvelles quêtes du pool weekly, update `last_weekly_reset = now`
- `join_timestamp` est écrit une seule fois (premier select du job). Il sert de valeur initiale pour `last_daily_reset` et `last_weekly_reset`

### Table `job_slots`

```sql
CREATE TABLE IF NOT EXISTS job_slots (
    uuid            TEXT NOT NULL PRIMARY KEY,
    unlocked_slots  INTEGER NOT NULL DEFAULT 1,
    slot_1          TEXT,   -- jobId ou NULL si vide
    slot_2          TEXT,
    slot_3          TEXT,
    slot_4          TEXT,
    slot_5          TEXT,
    -- NOTE : display_job est stocké dans la table `players`, pas ici (supprimé pour éviter la duplication)
    FOREIGN KEY (uuid) REFERENCES players(uuid)
);
```

### Table `quest_progress`

```sql
CREATE TABLE IF NOT EXISTS quest_progress (
    uuid          TEXT NOT NULL,
    quest_id      TEXT NOT NULL,
    progress      INTEGER NOT NULL DEFAULT 0,
    completed     INTEGER NOT NULL DEFAULT 0,  -- 0=false, 1=true
    claimed       INTEGER NOT NULL DEFAULT 0,
    completed_at  INTEGER NOT NULL DEFAULT 0,  -- timestamp UNIX
    PRIMARY KEY (uuid, quest_id),
    FOREIGN KEY (uuid) REFERENCES players(uuid)
);
```

### Table `anti_abuse` — Supprimée (fusionnée dans `job_data`)

> **Raison** : Les colonnes `daily_xp` et `last_daily_reset` étaient déjà présentes dans `job_data`.
> Stocker les mêmes données dans deux tables était redondant et risquait des désynchros.
> Le cap XP quotidien est géré directement via `job_data.daily_xp` et `job_data.last_daily_reset`.

**Ancienne table supprimée — ne pas recréer en code.**

### Table `bonus_multipliers`

Stocke les multiplicateurs XP persistants appliqués via `/kjob bonus`.

```sql
CREATE TABLE IF NOT EXISTS bonus_multipliers (
    uuid          TEXT NOT NULL,
    job_id        TEXT NOT NULL,   -- jobId ou "all" pour tous les jobs
    multiplier    REAL NOT NULL DEFAULT 1.0,  -- ex: 2.0 = double XP
    set_by        TEXT,            -- nom de l'admin qui a appliqué le bonus
    set_at        INTEGER NOT NULL DEFAULT 0, -- timestamp UNIX de création
    PRIMARY KEY (uuid, job_id),
    FOREIGN KEY (uuid) REFERENCES players(uuid)
);
```

**Notes `/kjob bonus` :**
- `/kjob bonus all 2.0 mineur` → insère/update toutes les lignes où job_id=`mineur` (tous les joueurs connectés + en DB)
- `/kjob bonus <joueur> 1.5 all` → insère/update la ligne `(uuid, "all")` du joueur
- Lecture : au gain XP → `SELECT multiplier FROM bonus_multipliers WHERE uuid=? AND (job_id=? OR job_id='all')` → prendre le max
- Valeur 1.0 = aucun bonus (comportement par défaut)

### Table `block_cooldowns`

Stockée **en RAM uniquement** (perdu au reboot = comportement acceptable).

```java
// Dans PlayerData (RAM uniquement, jamais persisté)
private final Map<String, Long> blockCooldowns = new HashMap<>();
// Clé = "x,y,z,world" | Valeur = timestamp d'expiration
```

### Table `pvp_cooldowns`

Stockée **en RAM uniquement**.

```java
// Dans PlayerData (RAM uniquement)
private final Map<UUID, Long> pvpTargetCooldowns = new HashMap<>();
// Clé = UUID de la cible | Valeur = timestamp d'expiration
```

---

## 3. Modèle Java — PlayerData.java

```java
public class PlayerData {
    // ─── Identifiant ───────────────────────────────────────
    private final UUID uuid;
    private long lastSeen;
    private long firstJoin;

    // ─── Jobs : niveaux et XP ──────────────────────────────
    // jobId → niveau actuel (minimum 1)
    private final Map<String, Integer> jobLevels = new HashMap<>();
    // jobId → XP actuel dans le niveau courant
    private final Map<String, Integer> jobXP     = new HashMap<>();

    // ─── Système de slots ──────────────────────────────────
    private int unlockedSlots = 1;
    // numéro slot (1-5) → jobId assigné (null si vide)
    private final Map<Integer, String> slotJobs  = new HashMap<>();
    // Job dont la bossbar est affichée
    private String displayJob = null;

    // ─── Quêtes ────────────────────────────────────────────
    // questId → QuestData (progression)
    private final Map<String, QuestData> questProgress = new HashMap<>();

    // ─── Anti-abuse (persisté) ─────────────────────────────
    // jobId → XP gagné aujourd'hui
    private final Map<String, Integer> dailyXP        = new HashMap<>();
    // jobId → timestamp du dernier reset daily
    private final Map<String, Long> dailyXpResetTime  = new HashMap<>();

    // ─── Anti-abuse (RAM uniquement) ───────────────────────
    // "x,y,z,world" → timestamp expiration
    private final Map<String, Long> blockCooldowns    = new HashMap<>();
    // UUID cible → timestamp expiration
    private final Map<UUID, Long> pvpTargetCooldowns  = new HashMap<>();

    // ─── HUD state (persisté en DB via table `players`) ───
    private boolean hudEnabled      = true;   // /jobs hud toggle
    private String displayJob       = null;   // dernier jobId ayant donné XP
    private long lastXpTimestamp    = 0L;     // ms epoch du dernier gain XP

    // ─── Bonus XP admin (chargé depuis bonus_multipliers) ──
    // jobId (ou "all") → multiplicateur; ex: "mineur" → 2.0
    private final Map<String, Double> bonusMultipliers = new HashMap<>();

    // ─── Factory ───────────────────────────────────────────
    public static PlayerData createDefault(UUID uuid) {
        PlayerData data = new PlayerData(uuid);
        data.firstJoin = System.currentTimeMillis();
        data.lastSeen  = data.firstJoin;
        // Les slots et jobs sont vides — le joueur doit choisir son premier job
        return data;
    }

    // ─── addXP (voir FLUX-XP-LEVELUP.md section 2) ─────────
    public LevelUpResult addXP(String jobId, int xp) { ... }
}
```

---

## 4. SQLiteStorage.java — Interface

```java
public interface DataStorage {
    PlayerData load(UUID uuid);
    void save(PlayerData data);
    void saveAll(Collection<PlayerData> allData);
    void close();
}

public class SQLiteStorage implements DataStorage {

    private Connection connection;
    private final File dbFile;

    public SQLiteStorage(Plugin plugin) {
        this.dbFile = new File(plugin.getDataFolder(), "data/kjobultimate.db");
    }

    public void init() throws SQLException {
        // Charger le driver SQLite
        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

        // Activer WAL mode pour meilleures performances lecture/écriture concurrentes
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL;");
            stmt.execute("PRAGMA synchronous=NORMAL;");
        }

        createTables();
    }

    private void createTables() throws SQLException {
        // Exécuter les CREATE TABLE IF NOT EXISTS vus en section 2
    }
}
```

---

## 5. Dépendance Maven — Driver SQLite

```xml
<!-- Dans pom.xml — SHADED dans le jar final -->
<dependency>
    <groupId>org.xerial</groupId>
    <artifactId>sqlite-jdbc</artifactId>
    <version>3.43.2.2</version>
    <scope>compile</scope>
</dependency>
```

---

## 6. Reset Daily XP Cap

Lors de chaque `addXP` ou au join :

```java
public boolean isDailyCapReached(String jobId) {
    // Vérifier si la date a changé depuis le dernier reset
    long lastReset = dailyXpResetTime.getOrDefault(jobId, 0L);
    long midnight  = getMidnightTimestamp();  // minuit du jour courant
    if (lastReset < midnight) {
        // Nouveau jour → reset
        dailyXP.put(jobId, 0);
        dailyXpResetTime.put(jobId, midnight);
    }
    int cap = configManager.getDailyXpCap(jobId);  // 0 = pas de cap
    if (cap <= 0) return false;
    return dailyXP.getOrDefault(jobId, 0) >= cap;
}
```

---

## 7. Migration depuis KJob2 (YAML → SQLite)

Si des données KJob2 existent dans `plugins/Kjob/players/`, créer une commande :

```
/kjobadmin migrate-kjob2
```

- Lire tous les fichiers `UUID.yml` dans le dossier KJob2
- Pour chaque joueur : récupérer levels + XP des jobs communs
- Insérer dans SQLite (ignorer si déjà présent)
- Logger le résultat dans la console

**La migration est optionnelle** — les joueurs sans données commencent à zéro.
