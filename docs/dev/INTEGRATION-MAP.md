# KjobsUltimate — Carte des Intégrations Inter-Plugins

> Tous les plugins qui doivent être **modifiés ou préparés** avant de coder KjobsUltimate.
> Format : Problème → Solution → Fichier(s) à modifier.

---

## Vue d'Ensemble

```
KjobsUltimate
  │
  ├── [CONSUME] KenchantAPI.getEnchantLevel(item, "extra-kill")
  ├── [CONSUME] KStacker: metadata META_GHOST, META_KILL_MULTIPLIER
  ├── [CONSUME] Kgui: ContentProviderAPI.register(id, provider)
  ├── [CONSUME] Vault: Economy.depositPlayer()
  ├── [CONSUME] PlaceholderAPI: expansion %kjob_xxx%
  │
  ├── [HOOK] Kchat: désactiver tab header/footer si KjobsUltimate actif
  └── [REQUIRE CHANGE] Kstacker: intégrer Extra Kill dans MobLethalDamageListener
```

---

## 1. Kstacker — Extra Kill Enchant

### Problème
`MobLethalDamageListener` ne connaît pas l'enchant Extra Kill de Kenchantement. Il ne gère que deux cas : kill normal (1 mob) et shift-kill (tout le stack).

### Solution
Ajouter un troisième cas dans `MobLethalDamageListener.java` : si le joueur a l'enchant `extra-kill`, tuer `min(3, stackCount)` mobs.

### Fichier à modifier
`Kstacker/src/main/java/me/krunsh/kstacker/listener/MobLethalDamageListener.java`

### Changement

```java
// AVANT (ligne ~killedCount)
int killedCount = shiftKill ? count : 1;

// APRÈS
int killedCount;
if (shiftKill) {
    killedCount = count;  // shift-kill = tout le stack
} else if (killer != null) {
    // Vérifier Extra Kill enchant via KenchantAPI (optionnel, hook soft)
    int extraKillLevel = getExtraKillCount(killer.getInventory().getItemInHand());
    killedCount = (extraKillLevel > 0) ? Math.min(extraKillLevel, count) : 1;
} else {
    killedCount = 1;
}

// Méthode à ajouter dans MobLethalDamageListener
private int getExtraKillCount(ItemStack weapon) {
    if (weapon == null) return 0;
    try {
        // Hook soft : pas de compile-time dep sur Kenchantement
        Plugin kenchant = Bukkit.getPluginManager().getPlugin("Kenchantement");
        if (kenchant == null || !kenchant.isEnabled()) return 0;
        // NBT direct : clé "kenchant_extra_kill" sur l'item
        de.tr7zw.changeme.nbtapi.NBTItem nbt = new de.tr7zw.changeme.nbtapi.NBTItem(weapon);
        if (!nbt.hasKey("kenchant_extra_kill")) return 0;
        int level = nbt.getInteger("kenchant_extra_kill");
        // Extra Kill niveau 1 = 2 mobs, niveau 2 = 3 mobs (cap absolu = 3)
        return Math.min(level + 1, 3);
    } catch (Exception e) {
        return 0;  // Fail silencieux si Kenchantement absent
    }
}
```

### Anti-dupe dans KjobsUltimate (défense en profondeur)
```java
// HunterListener — lire le multiplicateur depuis le ghost
int mult = entity.getMetadata(MobStackService.META_KILL_MULTIPLIER).get(0).asInt();
mult = Math.min(mult, 3);  // cap absolu côté KjobsUltimate aussi
```

### Priorité
� SOFT HOOK — Modification de Kstacker optionnelle.

- **Si Kstacker expose déjà `META_KILL_MULTIPLIER`** sur le ghost entity : aucun changement requis dans Kstacker. KjobsUltimate lit la metadata directement (fallback `killMultiplier = 1` si absente).
- **Si Kstacker ne l'expose pas** : vérifier en compilant Kstacker et testant `entity.hasMetadata("kill_count")`. Si absent, ajouter la metadata (voir code ci-dessus). Ce n'est pas bloquant pour le reste du développement.
- Voir EDGE-CASES.md §16 pour la décision finale.

---

## 2. Kstacker — API Publique `getStackCount`

### Problème
KjobsUltimate n'a pas besoin de connaître le stack count directement (le multiplier est déjà dans les metadata du ghost). Mais une API publique est utile pour des cas edge (debug, admin command).

### Solution
Exposer une méthode statique ou via `KStacker.getInstance()`.

### Fichier à modifier
`Kstacker/src/main/java/me/krunsh/kstacker/KStacker.java`

```java
// Ajouter dans KStacker.java
public static KStacker getInstance() { return instance; }

public int getStackCount(LivingEntity entity) {
    return mobStackService.getStackCount(entity);
}

public boolean isStacked(LivingEntity entity) {
    return mobStackService.isStacked(entity);
}
```

### Priorité
🟡 Optionnel pour V1, utile pour debug et admin.

---

## 3. Kchat — Conflit Tab Header/Footer

### Analyse du conflit
- `NametagManager.java` dans Kchat gère les équipes Scoreboard pour les préfixes/suffixes de joueurs dans le tab → **pas de conflit avec header/footer**
- Si Kchat envoie des packets `PacketPlayOutPlayerListHeaderFooter` quelque part → conflit potentiel
- **Vérification** : grepper Kchat pour `PlayerListHeaderFooter` — si absent, aucun conflit

### Solution (si conflit détecté)
Ajouter dans `Kchat/config.yml` :

```yaml
tab:
  header_footer:
    enabled: true  # Mettre à false si KjobsUltimate gère le header/footer
```

Et dans `KchatPlugin.java` :
```java
// Méthode publique callable par d'autres plugins
public void disableTabHeaderFooter() {
    configManager.setTabHeaderFooterEnabled(false);
    nametagManager.stopHeaderFooterScheduler();
}
```

**Hook dans KjobsUltimate** (`KchatHook.java`) :
```java
if (Bukkit.getPluginManager().getPlugin("Kchat") != null) {
    if (config.getBoolean("hooks.kchat.disable_tab_header_footer", true)) {
        // Via réflexion pour éviter dépendance compile-time
        Plugin kchat = Bukkit.getPluginManager().getPlugin("Kchat");
        Method m = kchat.getClass().getMethod("disableTabHeaderFooter");
        m.invoke(kchat);
    }
}
```

### Fichiers à modifier (si nécessaire)
- `Kchat/src/main/java/me/krunsh/kchat/config/ConfigManager.java` — ajouter `tab.header_footer.enabled`
- `Kchat/src/main/java/me/krunsh/kchat/KchatPlugin.java` — ajouter `disableTabHeaderFooter()`

### Priorité
🟡 Vérifier d'abord si Kchat envoie vraiment des packets header/footer (chercher `HeaderFooter` dans les sources Kchat).

---

## 4. Kgui — ContentProvider pour KjobsUltimate

### Principe
KjobsUltimate utilise l'API `DynamicContentProvider` de Kgui pour les menus paginés (liste des quêtes, détail des jobs).

### Providers à enregistrer depuis KjobsUltimate

| Provider ID | Menu | Contenu |
|---|---|---|
| `kjob_quests_list` | GUI Quêtes (paginé) | Liste des quêtes du job actif ou d'un job donné |
| `kjob_jobs_overview` | GUI Vue Globale | Les 5 jobs avec progression — statique, pas paginé |
| `kjob_active_jobs` | Tab footer info | Jobs actifs du joueur |

### Enregistrement au démarrage (KjobsUltimate onEnable)

```java
// KguiHook.java
if (Bukkit.getPluginManager().getPlugin("Kgui") != null) {
    Object cpm = getContentProviderManager(Bukkit.getPluginManager().getPlugin("Kgui"));
    registerProvider(cpm, "kjob_quests_list", new QuestListProvider(plugin));
    registerProvider(cpm, "kjob_jobs_overview", new JobsOverviewProvider(plugin));
}
```

### Menu YAML (fichiers dans Kgui/menus/ ou plugins/Kgui/menus/)

```yaml
# plugins/Kgui/menus/kjob_quetes.yml
menu_title: "&8§l✦ Mes Quêtes"
size: 54
type: pagination

pagination:
  enabled: true
  content_slots: "19-25,28-34,37-43"
  prev_button_slot: 45
  next_button_slot: 53
  provider: "kjob_quests_list"
  provider_args:
    job: "{arg_job}"    # passé dynamiquement à l'ouverture
  empty_item:
    material: BARRIER
    name: "&cAucune quête"
    lore:
      - "&7Aucune quête disponible pour ce job."
```

### Vérification nécessaire dans Kgui
- Les `DynamicItem` supportent-ils des lores dynamiques avec placeholders joueur ?
- L'API supporte-t-elle la mise à jour d'un item sans fermer/rouvrir le GUI ? (pour la progression en temps réel)
- **Si non** : les GUI de quêtes sont statiques à l'ouverture (acceptable pour V1)

### Fichiers potentiellement à modifier dans Kgui
- Si Kgui ne supporte pas les args dynamiques passés à l'ouverture : ajouter `openMenu(player, menuId, Map<String, String> args)`

### Priorité
🔴 Vérifier la compatibilité Kgui **avant** de coder les GUI de KjobsUltimate.

---

## 5. Kenchantement — Vérifier que `extra-kill` est implémenté

### Status actuel
L'enchant `Extra-Kill` est documenté dans `Kenchantement.md` et dans `config.yml` (target classes). Le NBT key est `kenchant_extra_kill`.

### Vérification nécessaire
- L'enchant `Extra-Kill` est-il **effectivement codé** dans `Kenchantement.java` / `ExtraKillListener` (ou équivalent) ?
- Le NBT `kenchant_extra_kill` est-il bien écrit sur l'item à l'application via enclume ?
- `KenchantAPI.getEnchantLevel(item, "extra-kill")` retourne bien le niveau ?

### Action si absent
Ajouter la logique dans Kenchantement :
- Application du NBT `kenchant_extra_kill` à l'enclume → dans `AnvilListener.java`
- Permission check → `chasseur.level.X` via LuckPerms

### Priorité
🔴 Kenchantement doit avoir Extra-Kill fonctionnel avant de tester HunterListener avec Kstacker.

---

## 6. PAPI — Placeholders KjobsUltimate

Aucun plugin à modifier. KjobsUltimate enregistre sa propre `PlaceholderExpansion`.

### Placeholders exposés

| Placeholder | Valeur |
|---|---|
| `%kjob_level_<jobId>%` | Niveau du joueur dans ce job |
| `%kjob_xp_<jobId>%` | XP actuel dans le niveau |
| `%kjob_xp_next_<jobId>%` | XP requis pour le prochain niveau |
| `%kjob_percent_<jobId>%` | Pourcentage de progression (0-100) |
| `%kjob_display_job%` | Nom du job affiché (displayJob) |
| `%kjob_display_level%` | Niveau du displayJob |
| `%kjob_bar_<jobId>%` | Barre de progression texte `████░░` |
| `%kjob_slots_unlocked%` | Nombre de slots débloqués |
| `%kjob_active_jobs%` | Liste des jobs actifs séparés par virgule |

---

## 7. Kcraft — Hook Artisant XP

### Contexte
Le job Artisant donne de l'XP quand un joueur réalise un craft. Il y a deux sources :
1. **Crafts vanilla** : table de craft 3×3 standard (`CraftItemEvent`)
2. **Crafts Kcraft** : table de craft custom via Kcraft (`CraftGUIListener.handleCraftClick()`)

Kcraft ne fire aucun événement Bukkit custom quand un craft est terminé. KjobsUltimate ne peut donc pas l'intercepter sans modification.

### Solution — Ajouter un callback/événement Kcraft

Ajouter dans Kcraft un événement Bukkit custom `KcraftCraftCompleteEvent` fire après chaque craft réussi :

```java
// Nouveau fichier : Kcraft/src/main/java/me/krunsh/kcraft/events/KcraftCraftCompleteEvent.java
public class KcraftCraftCompleteEvent extends PlayerEvent {
    private final String recipeId;   // ID de la recette craftée
    private final int quantity;      // quantité craftée
    private final ItemStack result;  // item résultant

    public KcraftCraftCompleteEvent(Player player, String recipeId, int quantity, ItemStack result) {
        super(player);
        this.recipeId = recipeId;
        this.quantity = quantity;
        this.result = result;
    }

    public String getRecipeId() { return recipeId; }
    public int getQuantity() { return quantity; }
    public ItemStack getResult() { return result; }
}
```

**Déclencher dans `CraftGUIListener.handleCraftClick()` et `handleMassCraft()` :**

```java
// Dans handleCraftClick() après succès du craft
KcraftCraftCompleteEvent craftEvent = new KcraftCraftCompleteEvent(player, recipe.getId(), 1, result);
Bukkit.getPluginManager().callEvent(craftEvent);

// Dans handleMassCraft() — passer la quantité réelle
KcraftCraftCompleteEvent craftEvent = new KcraftCraftCompleteEvent(player, recipe.getId(), quantity, result);
Bukkit.getPluginManager().callEvent(craftEvent);
```

**Également dans `VanillaCraftListener.onCraftItemConfirm()` pour les crafts vanilla marqués `allowVanillaWorkbench: true` :**
```java
KcraftCraftCompleteEvent craftEvent = new KcraftCraftCompleteEvent(player, recipe.getId(), 1, result);
Bukkit.getPluginManager().callEvent(craftEvent);
```

### Hook dans KjobsUltimate

```java
// ArtisanListener.java — listen KcraftCraftCompleteEvent
@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
public void onKcraftComplete(KcraftCraftCompleteEvent event) {
    Player player = event.getPlayer();
    PlayerData data = playerDataManager.get(player);
    if (!jobSlotManager.isJobActive(data, "artisant")) return;

    // Vérifier si ce recipeId donne de l'XP (liste dans artisant.yml → kcraft_actions)
    int xp = jobsConfig.getJob("artisant").getKcraftAction(event.getRecipeId());
    if (xp <= 0) return;

    int totalXP = xp * event.getQuantity();
    // ... flux standard addXP
}

// Pour les crafts vanilla (CraftItemEvent)
@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
public void onVanillaCraft(CraftItemEvent event) {
    if (!(event.getWhoClicked() instanceof Player)) return;
    Player player = (Player) event.getWhoClicked();
    // Vérifier vanilla_actions dans artisant.yml
    Material result = event.getRecipe().getResult().getType();
    int xp = jobsConfig.getJob("artisant").getVanillaAction(result.name());
    if (xp <= 0) return;
    // ... flux standard
}
```

### Config artisant.yml (section actions)

```yaml
# jobs/artisant.yml
actions:
  # --- Crafts vanilla (CraftItemEvent) ---
  # Identifié par le Material du résultat
  vanilla_actions:
    IRON_SWORD: {xp: 20}
    GOLD_SWORD: {xp: 15}
    DIAMOND_SWORD: {xp: 80}
    IRON_PICKAXE: {xp: 15}
    DIAMOND_PICKAXE: {xp: 75}
    IRON_AXE: {xp: 15}
    DIAMOND_AXE: {xp: 75}
    IRON_CHESTPLATE: {xp: 25}
    DIAMOND_CHESTPLATE: {xp: 90}
    BOW: {xp: 30}

  # --- Crafts Kcraft (KcraftCraftCompleteEvent) ---
  # Identifié par l'ID de la recette Kcraft
  kcraft_actions:
    # exemple (à compléter une fois les recettes Kcraft définies)
    epee_de_feu: {xp: 150}
    arc_runique: {xp: 200}
    armure_ancienne: {xp: 300}
```

### Fichiers à modifier dans Kcraft
- `Kcraft/src/main/java/me/krunsh/kcraft/events/KcraftCraftCompleteEvent.java` — CRÉER
- `Kcraft/src/main/java/me/krunsh/kcraft/listeners/CraftGUIListener.java` — ajouter appel dans `handleCraftClick()` et `handleMassCraft()`
- `Kcraft/src/main/java/me/krunsh/kcraft/listeners/VanillaCraftListener.java` — ajouter appel si `allowVanillaWorkbench: true`

### Priorité
🔴 BLOQUANT — Sans cet événement, les crafts Kcraft ne peuvent pas donner d'XP au job Artisant.

---

## 8. Résumé — Tableau des Actions Requises

| Plugin | Action | Priorité | Avant de coder... |
|---|---|---|---|
| **Kstacker** | Ajouter Extra Kill dans `MobLethalDamageListener` | 🔴 Bloquant | HunterListener |
| **Kstacker** | Exposer `getStackCount()` public | 🟡 Optionnel | Phase debug |
| **Kenchantement** | Vérifier que `extra-kill` est bien codé + NBT écrit | 🔴 Bloquant | HunterListener |
| **Kcraft** | Créer `KcraftCraftCompleteEvent` + appels dans les listeners | 🔴 Bloquant | ArtisanListener |
| **Kchat** | Vérifier conflit header/footer + ajouter config si besoin | 🟡 Vérifier | Phase tab scoreboard |
| **Kgui** | Vérifier que ContentProviderAPI supporte args dynamiques | 🔴 Vérifier | Phase GUI |
| **Kgui** | Ajouter `openMenu(player, id, args)` si absent | 🟡 Si nécessaire | Phase GUI |
| **Vault** | Rien — hook soft standard | ✅ OK | Phase rewards |
| **PAPI** | Rien — expansion dans KjobsUltimate | ✅ OK | Phase hooks |
