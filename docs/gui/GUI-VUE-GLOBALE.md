# KjobUltimate — GUI : Vue Globale et Quêtes

---

## 1. GUI Principal — `/jobs` (54 slots)

```
Slot :  0  1  2  3  4  5  6  7  8
        9 10 11 12 13 14 15 16 17
       18 19 20 21 22 23 24 25 26
       27 28 29 30 31 32 33 34 35
       36 37 38 39 40 41 42 43 44
       45 46 47 48 49 50 51 52 53
```

```
╔═══════════════════════════════════════════════════════════╗
║               §8§l✦ Mes Jobs ✦                            ║
╠═══════════════════════════════════════════════════════════╣
║ [D][D][D][D][D][D][D][D][D]     ← rangée 0 : déco gris   ║
║ [D][D][D][D][D][D][D][D][D]     ← rangée 1 : déco gris   ║
║ [D][D][J1][D][J2][D][J3][D][D]  ← rangée 2 : 3 jobs      ║
║ [D][D][D][D][D][D][D][D][D]     ← rangée 3 : spacer       ║
║ [D][D][J4][D][J5][D][Q!][D][D]  ← rangée 4 : 2 jobs + Q  ║
║ [D][D][D][HUD][✖][D][D][D][D]  ← rangée 5 : toggle + ✖  ║
╚═══════════════════════════════════════════════════════════╝

J1=Mineur (slot 20)  J2=Farmer (slot 22)  J3=Hunter (slot 24)
J4=Prétorien (slot 38)  J5=Artisant (slot 40)
Q! = Quêtes globales (slot 42)
[HUD] = Toggle HUD bossbar/actionbar (slot 48)  [✖] = Fermer (slot 49)
[D] = stained_glass_pane gris + nbt_cit gui_separator
```

### Items de Job dans le GUI

```
Slot 20 — §bMineur
  Material: IRON_PICKAXE
  NBT: sparrowmc-item=job_mineur_icon  → CIT remplace l'icône
  Lore:
    §7Niveau : §fLv.7
    §7XP : §f3200§7/§f5800
    §8[§a███████░░░§8] §f55%
    ─────────────────────
    §eClique gauche §7→ Voir détails & quêtes
```

### Items de Navigation

```
Slot 42 — §e§lQuêtes
  Material: BOOK
  NBT: sparrowmc-item=quests_icon
  Lore:
    §7Voir toutes tes quêtes actives.
    §f{done}§7/§f{total} §7quêtes du jour terminées.

Slot 48 — §b§lToggle HUD  [état actuel]
  Material: TORCH (si HUD ON) / REDSTONE_TORCH_OFF (si HUD OFF)
  NBT: sparrowmc-item=hud_toggle_icon
  Lore (si HUD ON):
    §aHUD activé §7— Bossbar et ActionBar visibles.
    §7Clic : §cDésactiver le HUD
  Lore (si HUD OFF):
    §cHUD désactivé §7— Bossbar et ActionBar masquées.
    §7Clic : §aActiver le HUD
  onClick: toggle HUD → met à jour PlayerData.hudEnabled → rafraîchit l'item

Slot 49 — §c§lFermer
  Material: BARRIER
  Lore: §7Fermer le menu.
```

---

## 2. GUI Détail d'un Job

```
╔═══════════════════════════════════════════════════════════╗
║           §8§lMineur §7— §bDétails & Quêtes               ║
╠═══════════════════════════════════════════════════════════╣
║ [D][D][D][ST][D][D][D][D][D]    ← stats résumé (slot 3)  ║
║ [D][D][D][D][D][D][D][D][D]                               ║
║ [D][Q][Q][Q][Q][Q][Q][Q][D]    ← 7 quêtes (slots 19-25)  ║
║ [D][Q][Q][Q][Q][Q][Q][Q][D]    ← 7 quêtes (slots 28-34)  ║
║ [D][Q][Q][Q][Q][Q][Q][Q][D]    ← 7 quêtes (slots 37-43)  ║
║ [◄][D][D][D][✖][D][D][D][►]   ← navigation               ║
╚═══════════════════════════════════════════════════════════╝

ST = Statistiques du job (slot 3 ou 4)
Q  = Quêtes
◄  = Page précédente (slot 45)
►  = Page suivante (slot 53)
✖  = Retour GUI principal (slot 49)
```

### Item Statistiques (slot 4)

```
§b§lStatsMineur
Material: IRON_ORE
Lore:
  §7Niveau actuel : §bLv.7
  §7XP : §f3200 §8/ §f5800
  §8[§a███████░░░§8] §f55%
  ─────────────────────
  §7Niveau max : §f50
  §7XP total accumulé : §f42000
```

---

## 3. GUI Quêtes — Layout Complet

### Zones et Slots

| Zone | Slots | Description |
|---|---|---|
| Quêtes (page) | 10,11,12,13,14,15,16 (7 par ligne × 3 lignes) | Items de quête, 21 max par page |
| Précédente | 45 | Arrow gauche |
| Fermer | 49 | Barrière rouge |
| Suivante | 53 | Arrow droite |
| Déco bords | Tous les autres | stained_glass_pane gris |

### Représentation ASCII

```
╔═══════════════════════════════════════════════════════════╗
║     §8§lQuêtes — §bMineur §8(Page 1/2)                    ║
╠═══════════════════════════════════════════════════════════╣
║ [D][D][D][D][D][D][D][D][D]    ← bords                   ║
║ [D][Q][Q][Q][Q][Q][Q][Q][D]    ← slots 10→16              ║
║ [D][Q][Q][Q][Q][Q][Q][Q][D]    ← slots 19→25              ║
║ [D][Q][Q][Q][Q][Q][Q][Q][D]    ← slots 28→34              ║
║ [D][D][D][D][D][D][D][D][D]    ← bords                   ║
║ [◄][D][D][D][✖][D][D][D][►]                               ║
╚═══════════════════════════════════════════════════════════╝
```

### Items de Quête selon État

**Disponible / En cours** → `PAPER` (note de parchemin)
```
§7Collecteur de Pierre §8[DAILY]
Lore:
  §7Casser 500 blocs de pierre.
  ──────────────────────────────
  §7Progression : §f312§7/§f500
  §8[§a████████████░░░░░░░░§8] §f62%
  ──────────────────────────────
  §7Reset : §fQuotidien à minuit
  §8Récompenses :
    §8• §f500 §7XP Mineur
    §8• §f50§7$
```

**Complétée, à claim** → `EMERALD` (brillant)
```
§a§lCollecteur de Pierre §8[DAILY]
Lore:
  §a✔ QUÊTE TERMINÉE !
  ──────────────────────────────
  §7Récompenses à récupérer :
    §8• §f500 §7XP Mineur §8(déjà donné)
    §8• §f50§7$ §8(à claim ici)
  ──────────────────────────────
  §e⬆ Clique pour récupérer tes récompenses !
```

**Verrouillée** → `BARRIER`
```
§8§lQuête Verrouillée
Lore:
  §7Cette quête nécessite :
    §8• §fNiveau 5 §7en Mineur
  ──────────────────────────────
  §8Niveau actuel : §f3
```

---

## 4. GUIUtils.java — Helper Création Items

```java
public class GUIUtils {

    /**
     * Créer un ItemStack avec NBT sparrowmc-item pour CIT.
     */
    public static ItemStack createCITItem(Material material, int data,
                                          String citTag, String displayName,
                                          List<String> lore) {
        ItemStack item = new ItemStack(material, 1, (short) data);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ColorUtil.translate(displayName));
        meta.setLore(lore.stream()
            .map(ColorUtil::translate)
            .collect(Collectors.toList()));
        item.setItemMeta(meta);

        // Appliquer le tag NBT CIT via NBT-API ou NMS
        // Avec NMS 1.8.8 :
        net.minecraft.server.v1_8_R3.ItemStack nmsItem =
            org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack.asNMSCopy(item);
        NBTTagCompound tag = nmsItem.hasTag() ? nmsItem.getTag() : new NBTTagCompound();
        tag.setString("sparrowmc-item", citTag);
        nmsItem.setTag(tag);

        return org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack.asBukkitCopy(nmsItem);
    }

    /**
     * Créer un item de décoration (verre teinté gris, nom vide).
     */
    public static ItemStack createDecoration(Material material, int data, String citTag) {
        return createCITItem(material, data, citTag, " ", Collections.emptyList());
    }

    /**
     * Générer la barre de progression texte.
     * @param filled   Caractère plein (ex: "§a█")
     * @param empty    Caractère vide  (ex: "§8░")
     * @param length   Longueur totale
     * @param progress 0.0 à 1.0
     */
    public static String buildBar(String filled, String empty, int length, float progress) {
        int filledCount = Math.round(progress * length);
        return filled.repeat(filledCount) + empty.repeat(length - filledCount);
    }
}
```

---

## 5. JobsOverviewGUI.java — Structure

```java
public class JobsOverviewGUI {

    private final KjobUltimate plugin;
    private final JobManager jobManager;
    private final PlayerDataManager playerDataManager;
    private final GUIConfig guiConfig;

    /**
     * Ouvre le GUI principal des jobs pour un joueur.
     */
    public void open(Player player) {
        // Créer un inventaire avec titre coloré
        Inventory inv = Bukkit.createInventory(null, 54,
            ColorUtil.translate(guiConfig.getJobsOverviewTitle()));

        // Remplir de déco
        ItemStack deco = GUIUtils.createDecoration(
            Material.STAINED_GLASS_PANE, 7, "gui_separator");
        for (int i = 0; i < 54; i++) inv.setItem(i, deco);

        // Placer chaque job
        PlayerData data = playerDataManager.get(player);
        for (String jobId : jobManager.getJobIds()) {
            Integer slot = guiConfig.getJobSlot(jobId);
            if (slot == null) continue;

            Job job = jobManager.getJob(jobId);
            int level = data.getLevel(jobId);
            int xp = data.getXP(jobId);
            int xpNext = Math.max(1, job.getXpForLevel(level));
            float pct = (float) xp / xpNext;
            String bar = GUIUtils.buildBar("§a█", "§8░", 10, pct);

            List<String> lore = guiConfig.getJobLoreTemplate().stream()
                .map(line -> line
                    .replace("{level}", String.valueOf(level))
                    .replace("{xp}", String.valueOf(xp))
                    .replace("{xp_next}", String.valueOf(xpNext))
                    .replace("{progress_pct}", String.valueOf((int)(pct * 100)))
                    .replace("{bar}", bar))
                .collect(Collectors.toList());

            ItemStack jobItem = GUIUtils.createCITItem(
                job.getIconMaterial(),
                job.getIconData(),
                job.getIconCITTag(),
                job.getDisplayName() + " §7Lv.§f" + level,
                lore
            );
            inv.setItem(slot, jobItem);
        }

        // Bouton quêtes
        ItemStack questBtn = GUIUtils.createCITItem(
            Material.BOOK, 0, "quests_icon",
            "§e§lQuêtes",
            Arrays.asList("§7Accéder à tes quêtes.", "§eClique !")
        );
        inv.setItem(guiConfig.getQuestsButtonSlot(), questBtn);

        // Bouton fermer
        ItemStack closeBtn = GUIUtils.createCITItem(
            Material.BARRIER, 0, "gui_close",
            "§c§lFermer",
            Collections.singletonList("§7Fermer ce menu.")
        );
        inv.setItem(49, closeBtn);

        player.openInventory(inv);
    }
}
```

---

## 6. Listener des Clics GUI

```java
public class GUIListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        Inventory inv = event.getInventory();
        String title = event.getView().getTitle();

        // Identifier quel GUI est ouvert par son titre
        if (title.startsWith(ColorUtil.translate("§8§l✦ Mes Jobs"))) {
            event.setCancelled(true);
            handleJobsOverviewClick(player, event.getRawSlot(), event.getClick());
        }
        else if (title.startsWith(ColorUtil.translate("§8§lQuêtes"))) {
            event.setCancelled(true);
            handleQuestGUIClick(player, event.getRawSlot(), event.getClick());
        }
    }

    private void handleJobsOverviewClick(Player player, int slot, ClickType click) {
        if (slot == 49) {
            // Fermer
            player.closeInventory();
            return;
        }

        // Vérifier si c'est un slot de job
        String jobId = guiConfig.getJobIdForSlot(slot);
        if (jobId != null) {
            if (click == ClickType.LEFT) {
                // Ouvrir le GUI de détail du job
                jobDetailGUI.open(player, jobId);
            } else if (click == ClickType.RIGHT) {
                // Sélectionner ce job comme actif pour la bossbar
                playerDataManager.get(player).setActiveJobDisplay(jobId);
                bossBarManager.markDirty(player.getUniqueId());
                messagesConfig.send(player, "job_selected",
                    Map.of("job", jobManager.getJob(jobId).getDisplayName()));
            }
        }

        // Bouton quêtes
        if (slot == guiConfig.getQuestsButtonSlot()) {
            questGUI.open(player, null); // null = toutes les quêtes
        }
    }

    private void handleQuestGUIClick(Player player, int slot, ClickType click) {
        // Récupérer la quête correspondant au slot
        QuestGUIState state = questGUIStates.get(player.getUniqueId());
        if (state == null) return;

        String questId = state.getQuestIdForSlot(slot);
        if (questId != null) {
            // Tenter le claim
            questManager.claimReward(player, questId);
            // Rafraîchir le GUI après claim
            questGUI.refresh(player, state);
            return;
        }

        // Navigation
        if (slot == 45 && state.getPage() > 0) {
            questGUI.open(player, state.getJobId(), state.getPage() - 1);
        }
        if (slot == 53) {
            questGUI.open(player, state.getJobId(), state.getPage() + 1);
        }
        if (slot == 49) {
            jobsOverviewGUI.open(player); // retour au menu principal
        }
    }
}
```
