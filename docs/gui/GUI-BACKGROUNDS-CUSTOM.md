# GUI Custom Backgrounds — Resource Pack + MCPatcher/CIT/CTM (1.8.8)

> Reponse directe a la question : "Est-ce possible d'avoir plein de GUI 54 slots avec des backgrounds differents via resource pack ?"

---

## Reponse courte

**Non, pas pour plusieurs GUIs de 54 slots avec des backgrounds differents via texture pack seul.**

**Oui, si tu utilises un type de container different par GUI.**

Voici l'explication technique complete et toutes les solutions reelles.

---

## Pourquoi le background est lie au type de container

En 1.8.8, le client determine la texture de fond d'un GUI a partir du champ `b` du `PacketPlayOutOpenWindow` (le "container type string"). Ce string est hard-code cote client et mappe directement a un fichier PNG.

Liste des mappings natifs :

| String envoye par le serveur | Texture cliente | Taille utile |
|---|---|---|
| `minecraft:container` | `textures/gui/container/generic_54.png` | 9x6 = 54 slots |
| `minecraft:chest` | `textures/gui/container/generic_54.png` (taille variable 9x1 a 9x6) | jusqu'a 54 |
| `EntityHorse` | `textures/gui/container/horse.png` | 17 slots |
| `minecraft:furnace` | `textures/gui/container/furnace.png` | 3 slots |
| `minecraft:beacon` | `textures/gui/container/beacon.png` | 1 slot |
| `minecraft:hopper` | `textures/gui/container/hopper.png` | 5 slots |
| `minecraft:crafting_table` | `textures/gui/container/crafting_table.png` | 9+1 slots |
| `minecraft:enchanting_table` | `textures/gui/container/enchanting_table.png` | 2 slots |
| `minecraft:anvil` | `textures/gui/container/anvil.png` | 3 slots |
| `minecraft:villager` | `textures/gui/container/villager.png` | variable |
| `minecraft:brewing_stand` | `textures/gui/container/brewing_stand.png` | 4 slots |

**Conclusion** : deux containers envoyes avec `"minecraft:container"` et une taille de 54 utiliseront TOUJOURS la meme texture `generic_54.png`. Le client ne distingue pas "quel GUI" est ouvert, uniquement quel type.

---

## Ce que MCPatcher/CIT/CTM peuvent et ne peuvent PAS faire pour les GUIs

### Ce que CIT fait

CIT (Custom Item Textures) remplace la texture d'un **item** affiche dans le monde ou dans un inventaire, selon ses proprietes NBT, son nom, son enchantement, sa durabilite, etc.

**CIT ne controle PAS la texture de fond (background) du GUI lui-meme.** CIT agit sur les items a l'interieur du GUI, pas sur la fenetre.

### Ce que CTM fait

CTM (Connected Textures) remplace les textures de **blocs** selon leur contexte. N'a aucun rapport avec les GUIs.

### Ce que le resource pack PEUT faire pour les GUIs

- Remplacer **entierement** le PNG de background de chaque type de container.
- Utiliser des images 256x256 completement redesignees.
- Faire croire que c'est un menu MMO, un panneau magique, un ecran faction, etc.

---

## La vraie solution : un type de container = un background

Puisque chaque type de container a sa propre texture, la strategie est :

**Assigner un type de container different a chaque "categorie" de GUI custom.**

Exemples pour SparrowMC :

| Usage GUI | Container type utilise | Texture a redessiner | Slots exploitables |
|---|---|---|---|
| Menu Jobs | `EntityHorse` | `horse.png` | 17 |
| Menu Shop | `minecraft:container` 54 | `generic_54.png` | 54 |
| Menu Kits | `minecraft:furnace` | `furnace.png` | 3 (+ 36 joueur) |
| Menu Talents / Skills | `minecraft:beacon` | `beacon.png` | 1 special |
| Menu Faction | `minecraft:hopper` | `hopper.png` | 5 |
| Menu Banque | `minecraft:anvil` | `anvil.png` | 3 |
| Menu Marche noir | `minecraft:villager` | `villager.png` | variable |
| Menu Craft custom | `minecraft:crafting_table` | `crafting_table.png` | 9+1 |
| Menu Enchantements | `minecraft:enchanting_table` | `enchanting_table.png` | 2 |
| Menu Potions / Alchemy | `minecraft:brewing_stand` | `brewing_stand.png` | 4 |

Avec cette approche : **10 GUIs completement differents visuellement, tous compatibles client vanilla, sans aucun mod.**

---

## Comment avoir plusieurs GUIs 54 slots avec backgrounds differents

Si tu as besoin de **plusieurs** GUIs de 54 slots avec des visuels distincts (ex: Shop Armes / Shop Armures / Shop Potions = meme taille mais apparence differente), il y a 3 options :

### Option A — Utiliser une section differente du meme PNG (recommande)

Le fichier `generic_54.png` est une image statique que **tu dessines**. Au lieu d'avoir un seul background, tu decoupes le PNG en zones differentes et tu joues sur les items "decoration" places dans les slots pour donner l'illusion d'un fond different.

Technique : remplir les slots inutilises avec des panneaux de verre colores teintes, des items custom CIT avec textures transparentes ou avec des icones qui forment un pattern visuel. Le fond reste le meme PNG mais les items redessinent visuellement l'interface.

### Option B — Titres de fenetres differents + items de decoration CIT

Le titre de la fenetre (envoye dans `IChatBaseComponent` du `PacketPlayOutOpenWindow`) est affiche en haut du GUI. Combine a des items CIT different par GUI, tu peux creer une identite visuelle unique pour chaque menu sans changer le background PNG.

### Option C — Fork NMS : injecter un channel plugin pour un fond custom (avance)

Puisque KhopeSpigot est un **fork avec acces source**, on peut ajouter un channel custom :

1. Envoyer `PacketPlayOutCustomPayload("SPARROW|GUI", data)` au moment de l'ouverture du GUI.
2. Le payload contient l'identifiant de la texture de fond souhaitee.
3. **Mais** : le client vanilla ne sait pas interpreter ce channel. Cette option necessite soit un mod client, soit un resource pack qui inclut un mod MCPatcher capable de lire des custom payloads (ce qui n'existe pas en vanilla).

**Verdict Option C : non viable pour client vanilla pur.**

---

## Technique recommandee : "Trompe l'oeil" par slots de decoration

Pour simuler des backgrounds differents dans un GUI 54 slots :

### Principe

Le client affiche le fond `generic_54.png` (que tu redessinnes en gris neutre ou transparent). Les slots "background" sont remplis avec des **ItemStack custom** dont la texture via CIT dessine le fond visuel voulu.

### Exemple concret

Pour le menu Shop Armes vs Shop Armures :

```
Pack de textures :
optifine/cit/gui/shop_armes_bg.properties
  type=item
  items=stained_glass_pane
  nbt.Display.Name=shop_armes_bg
  texture=shop_armes_background.png   <- image 16x16 de ta couleur/fond

optifine/cit/gui/shop_armures_bg.properties
  type=item
  items=stained_glass_pane
  nbt.Display.Name=shop_armures_bg
  texture=shop_armures_background.png
```

Serveur : remplir les 54 slots avec les bons `stained_glass_pane` NBT-tagged selon le menu ouvert.

Resultat : le joueur voit un GUI avec un fond visuellement different selon le shop, entierement en client vanilla + MCPatcher.

---

## Taille reelle des fichiers PNG de GUI

Pour dessiner tes backgrounds correctement :

| Fichier | Taille PNG | Zone utile |
|---|---|---|
| `generic_54.png` | 256x256 | GUI principal 176x222 px |
| `horse.png` | 256x256 | GUI principal 176x166 px |
| `furnace.png` | 256x256 | GUI principal 176x166 px |
| `beacon.png` | 256x256 | GUI principal 230x219 px |
| `hopper.png` | 256x256 | GUI principal 176x133 px |
| `anvil.png` | 256x256 | GUI principal 176x166 px |
| `crafting_table.png` | 256x256 | GUI principal 176x166 px |
| `enchanting_table.png` | 256x256 | GUI principal 176x166 px |
| `brewing_stand.png` | 256x256 | GUI principal 176x166 px |
| `villager.png` | 512x256 | variable selon trades |

---

## Switcher entre GUIs (navigation entre menus)

Pour switcher de GUI en cours de session (ex: passer du menu Jobs au menu Shop depuis le meme `/menu`) :

```java
// Dans le InventoryClickEvent, detecter le clic sur un item "navigation"
player.closeInventory();
// 1 tick de delai obligatoire avant d'ouvrir un nouveau GUI
Bukkit.getScheduler().runTaskLater(plugin, () -> {
    ouvrirMenuShop(player);
}, 1L);
```

Le delai de 1 tick est necessaire car `closeInventory()` envoie un packet de fermeture et si tu ouvres immediatement un nouveau GUI, le client peut ignorer l'ouverture ou avoir un etat incoerent.

---

## Resume des limites reelles

| Scenario | Possible |
|---|---|
| Plusieurs GUIs avec backgrounds differents via types differents | OUI |
| Plusieurs GUIs 54 slots avec backgrounds differents (meme type) | NON natif — hack par slots decoration |
| Background qui change dynamiquement pendant que le GUI est ouvert | NON — le PNG est charge une fois a l'ouverture |
| GUI avec background anime (video / gif) | NON en 1.8.8 vanilla |
| 10+ GUIs avec identite visuelle unique | OUI avec combinaison types + CIT items |
| Client vanilla sans MCPatcher | Oui pour les types differents / Non pour CIT items |
| Client avec MCPatcher/Optifine (ta cible) | Oui pour tout ce qui est decrit dans ce document |
