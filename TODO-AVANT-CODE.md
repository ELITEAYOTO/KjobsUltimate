# KjobUltimate — TODO Avant de Commencer le Code

> Résumé opérationnel de tout ce qui doit être fait ou vérifié **avant d'écrire la première ligne Java**.
> Document vivant — cocher les items au fur et à mesure.
>
> Détail complet → [docs/dev/PRE-DEV-CHECKLIST.md](docs/dev/PRE-DEV-CHECKLIST.md)

---

## État Global

| Zone | Statut | Bloquant ? |
|---|---|---|
| A1 — Kenchantement Extra-Kill | ✅ Prêt | Oui (Phase 4 Hunter) |
| A2 — Kstacker Extra-Kill      | ✅ Prêt | Soft (fallback = 1) |
| A3 — Kgui ContentProviderAPI  | ✅ Prêt | Oui (Phase 7 GUI) |
| A4 — Kchat conflict Tab       | ✅ Pas de conflit | Non bloquant |
| A5 — Kcraft CraftCompleteEvent| ✅ Prêt | Oui (Phase 4 Artisan) |
| B — Décisions design          | ✅ Toutes prises | — |
| C1 — Projet Maven             | ⬜ À créer    | Oui |
| C2 — Structure packages       | ⬜ À créer    | Oui |
| C3 — Config templates         | ⬜ À créer    | Oui |
| C4 — Schéma SQLite            | ⬜ À écrire   | Oui |
| Docs                          | ✅ Complètes  | — |

**→ Hooks plugins terminés. Prochaine étape : C1 (Maven) + C2 (packages) + C4 (SQLite schema).**

---

## HOOKS À PRÉPARER — Plugins à Modifier

### 🔴 A3 — Kgui : Vérifier ContentProviderAPI

**Fichiers à toucher :** `Kgui/src/main/java/me/krunsh/kgui/`

- [ ] `DynamicContentProvider.getContent(Player, Map<String,String> args)` — vérifier la signature exacte
- [ ] `Kgui.openMenu(player, "kjob_jobs_overview", args)` — vérifier que la méthode accepte des args dynamiques
- [ ] `DynamicItem.onClick(Player, ItemStack, ClickType)` — vérifier que CLICK_LEFT vs CLICK_RIGHT est distinguable
- [ ] Si manquant : ajouter `openMenu(Player, String, Map<String,String>)` dans `KguiPlugin.java`
- [ ] **Compiler et déployer Kgui** avant de démarrer la Phase 7

---

### 🔴 A5 — Kcraft : Ajouter `KcraftCraftCompleteEvent`

**Fichiers à toucher :** `Kcraft/src/main/java/me/krunsh/kcraft/`

- [ ] Créer `events/KcraftCraftCompleteEvent.java` :

```java
public class KcraftCraftCompleteEvent extends Event {
    private final Player player;
    private final String recipeId;       // Identifiant de la recette Kcraft
    private final ItemStack result;      // Item résultant
    private final int amount;            // Quantité craftée

    // Constructeur, getters, static HandlerList
    private static final HandlerList handlers = new HandlerList();
    @Override public HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}
```

- [ ] Appeler `Bukkit.getPluginManager().callEvent(event)` dans :
  - `CraftGUIListener.handleCraftClick()` (craft normal)
  - `CraftGUIListener.handleMassCraft()` (craft en masse — passer la quantité réelle)
  - `VanillaCraftListener` (pour les recettes avec `allowVanillaWorkbench: true`)
- [ ] **Compiler et déployer Kcraft**

---

### 🟡 A2 — Kstacker : Intégrer Extra-Kill dans MobLethalDamageListener

> Soft hook — pas obligatoire pour démarrer, fallback = 1 kill si absent.

**Fichier à toucher :** `Kstacker/src/main/java/me/krunsh/kstacker/listener/MobLethalDamageListener.java`

- [ ] Ajouter le cas Extra-Kill entre le kill normal (1) et le shift-kill (tout le stack) :

```java
// Avant
int killedCount = shiftKill ? count : 1;

// Après
int killedCount;
if (shiftKill) {
    killedCount = count;
} else if (killer != null) {
    int extraLevel = getExtraKillLevel(killer.getInventory().getItemInHand());
    // Extra Kill niveau 1 = 2 mobs, niveau 2 = 3 mobs
    killedCount = (extraLevel > 0) ? Math.min(extraLevel + 1, count) : 1;
} else {
    killedCount = 1;
}

private int getExtraKillLevel(ItemStack weapon) {
    if (weapon == null) return 0;
    try {
        Plugin ke = Bukkit.getPluginManager().getPlugin("Kenchantement");
        if (ke == null || !ke.isEnabled()) return 0;
        NBTItem nbt = new NBTItem(weapon);
        if (!nbt.hasKey("kenchant_extra_kill")) return 0;
        return Math.min(nbt.getInteger("kenchant_extra_kill"), 2); // cap niveau 2
    } catch (Exception e) { return 0; }
}
```

- [ ] S'assurer que `META_KILL_MULTIPLIER` est posé sur le ghost après `killedCount` calculé
- [ ] **Compiler et déployer Kstacker**

---

### 🟡 A1 — Kenchantement : Vérifier Extra-Kill NBT

> Nécessaire pour que A2 fonctionne correctement.

- [ ] Vérifier que l'application de l'enchant `extra-kill` à l'enclume écrit bien `kenchant_extra_kill` (Integer) sur l'item
- [ ] Tester : `NBTItem.getInteger("kenchant_extra_kill")` → niveau 1 ou 2
- [ ] Vérifier que `KenchantAPI.getEnchantLevel(item, "extra-kill")` est accessible depuis un autre plugin

---

### 🟢 A4 — Kchat : Vérifier Conflit Tab Header/Footer

> Non bloquant — à régler avant la Phase 9 (Tab).

- [ ] Chercher `PlayerListHeaderFooter` dans `Kchat/src/` :
```powershell
Select-String -Path "C:\Users\timot\Desktop\minecraft\SparrowMCALL\Kchat\src\**\*.java" -Pattern "PlayerListHeaderFooter" -Recurse
```
- [ ] Si trouvé → ajouter `tab.enabled: true/false` dans `Kchat/config.yml` + méthode de désactivation dans `KchatPlugin.java`
- [ ] Si non trouvé → aucune action requise

---

## SETUP TECHNIQUE — Projet KjobUltimate

### 🔴 C1 — Créer le Projet Maven

- [ ] Copier `pom.xml` de Kchat comme base
- [ ] Modifier : `artifactId: KjobUltimate`, `groupId: me.krunsh.kjobultimate`, `version: 1.0.0`
- [ ] Dépendances `provided` : SpigotAPI 1.8.8 (KhopeSpigot), Vault, PlaceholderAPI
- [ ] Dépendance `compile` + shade : `org.xerial:sqlite-jdbc:3.42.0.0`
- [ ] Configurer `maven-shade-plugin` pour relocaliser sqlite : `org.sqlite` → `me.krunsh.kjobultimate.libs.sqlite`

```xml
<!-- pom.xml — dépendances clés -->
<dependency>
    <groupId>org.xerial</groupId>
    <artifactId>sqlite-jdbc</artifactId>
    <version>3.42.0.0</version>
    <scope>compile</scope>
</dependency>
```

- [ ] Créer `src/main/resources/plugin.yml` :

```yaml
name: KjobUltimate
version: 1.0.0
main: me.krunsh.kjobultimate.KjobUltimate
api-version: 1.8
softdepend: [Vault, PlaceholderAPI, Kgui, Kstacker, Kenchantement, Kcraft, Kchat]
commands:
  jobs:
    description: Menu des jobs
    aliases: [job]
  kjob:
    description: Admin jobs
    permission: kjob.admin
    aliases: [kjobs, kjobultimate, kjobsultimate]
permissions:
  kjob.admin:
    default: op
  kjob.xp.vip:
    default: false
  kjob.xp.premium:
    default: false
```

---

### 🔴 C2 — Structure de Packages

Créer tous ces packages **vides** :

```
me.krunsh.kjobultimate/
├── KjobUltimate.java          ← classe principale (extend JavaPlugin)
├── api/                        ← API publique KjobAPI
├── config/                     ← ConfigManager, loaders YAML
├── jobs/                       ← Job, JobAction, JobManager
├── quests/                     ← Quest, QuestData, QuestManager
├── data/                       ← PlayerData, PlayerDataManager, SQLiteStorage
├── hud/                        ← BossBarManager, ActionBarManager, AchievementManager
├── scoreboard/                 ← ScoreboardManager (tab header/footer)
├── slots/                      ← JobSlotManager
├── listeners/                  ← MinerListener, FarmerListener, HunterListener...
├── gui/                        ← KguiHook, providers (ContentProvider impls)
├── commands/                   ← JobCommand, KjobAdminCommand
├── hooks/                      ← VaultHook, PapiHook, KchatHook, KguiHook...
└── util/                       ← KjobLogger, ColorUtil, TimeUtil
```

---

### 🔴 C3 — Fichiers de Config par Défaut (src/main/resources/)

- [ ] `config.yml` (voir [docs/config/CONFIG-FICHIERS-STRUCTURE.md](docs/config/CONFIG-FICHIERS-STRUCTURE.md))
- [ ] `messages.yml`
- [ ] `sounds.yml`
- [ ] `hud.yml`
- [ ] `tab.yml`
- [ ] `jobs/mineur.yml`, `jobs/farmer.yml`, `jobs/hunter.yml`, `jobs/pretorien.yml`, `jobs/artisant.yml`
- [ ] `quests/quests_mineur.yml`, `quests/quests_farmer.yml`, etc.

---

### 🔴 C4 — Schéma SQLite Initial

Écrire le `CREATE TABLE IF NOT EXISTS` pour les 7 tables dans `SQLiteStorage.java` :

| Table | Description |
|---|---|
| `players` | UUID, hud_enabled, display_job, last_seen |
| `job_data` | XP, level, daily_xp, last_daily_reset par job |
| `job_slots` | slots 1-5, unlocked_slots |
| `quest_progress` | progress, completed, claimed par quête |
| `bonus_multipliers` | multiplicateurs admin persistants |
| `prestige` | réservé — vide en V1 |

Voir schéma complet → [docs/architecture/DONNEES-JOUEUR-SCHEMA.md](docs/architecture/DONNEES-JOUEUR-SCHEMA.md)

---

## DÉCISIONS DESIGN — Tout est Réglé ✅

| # | Question | Décision |
|---|---|---|
| B1 | Quêtes attribution | 3 daily aléatoires + 5 weekly + toutes les permanentes |
| B2 | Difficulté quêtes | Facile / Avancé / Difficile (couleurs vert/or/rouge) |
| B3 | Hunter spawners | Même XP que mobs sauvages |
| B4 | Artisan sources XP | vanilla_actions + kcraft_actions dans artisant.yml |
| B5 | Prétorien PvP | Kill joueur + consommation items de combat |
| B6 | BossBar multi-job | 1 seule bossbar = dernier job ayant donné XP (displayJob auto) |
| B7 | Niveau max | Configurable par job (max_level dans jobs/<id>.yml) |
| B8 | Quêtes chaîne | Reporté V2 |
| B9 | Prestige | Reporté V2 — champ DB réservé |

---

## ORDRE D'EXÉCUTION RECOMMANDÉ

```
Maintenant (parallèle) :
  ├── Vérifier/modifier Kgui (A3)        ← débloque Phase 7 GUI
  ├── Coder KcraftCraftCompleteEvent (A5) ← débloque Phase 4 Artisan
  └── Créer projet Maven + packages (C1+C2)

Ensuite :
  ├── Vérifier Kenchantement (A1)
  ├── Modifier Kstacker (A2)
  ├── Écrire configs par défaut (C3)
  └── Écrire schéma SQLite (C4)

Puis → démarrer Phase 1 du plan d'implémentation
  └── docs/dev/PLAN-IMPLEMENTATION.md
```

---

## RESSOURCE PACK (Parallèle — Non Bloquant)

- [ ] Icônes des 5 jobs (CIT via NBT `sparrowmc-item`) pour les GUI
- [ ] Override achievements dans `assets/minecraft/lang/fr_FR.lang`
- [ ] Sons custom `.ogg` dans `assets/minecraft/sounds/custom/`
- [ ] Texture cheval GUI si utilisée (`horse_gui_background.png`)
