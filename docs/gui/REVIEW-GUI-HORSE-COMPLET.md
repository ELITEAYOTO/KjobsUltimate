# Review Complet — GUI Cheval Custom + Lunar + Skin NPC
## Analyse basee sur EntityHorse.java, ContainerHorse.java, PacketPlayOutUpdateAttributes.java (decompile PandaSpigot)

---

## SCHEMA ANATOMIQUE DU GUI CHEVAL

Avant tout, voici ce que le client rend dans le GUI cheval, layer par layer :

```
horse.png (256x256) — couche fond PNG
├── Zone PREVIEW ENTITE  (pixels ~26,18 → 78,70 dans le rendu x1)
│     Le client appelle GuiScreenHorseInventory.drawScreen()
│     puis GlStateManager + RenderManager.renderEntityWithPosYaw()
│     pointe sur l'entity ID passe dans PacketPlayOutOpenWindow
│
├── Slot 0 — SELLE      (pixel x=8,  y=18  dans le GUI)
├── Slot 1 — ARMURE     (pixel x=8,  y=36  dans le GUI)
├── Slots 2..16 — COFFRE CHEVAL 5x3 (si hasChest=true)
│     Grid: colonnes x=80,98,116,134,152  lignes y=18,36,54
│
└── Slots joueur 9..44  (inventaire + hotbar en bas)
```

---

## PARTIE 1 — TEXTE DYNAMIQUE DANS LA ZONE A DROITE DU CHEVAL

### Ce que tu veux faire

Afficher du texte configurable (nom du metier, stats, niveau) dans l'espace vide a droite de la preview cheval.

### Verdict technique : IMPOSSIBLE via packets seuls, POSSIBLE via 3 alternatives

#### Pourquoi c'est impossible nativement

En 1.8.8, le rendu d'un GUI se decompose ainsi cote client :
1. Le PNG de fond est dessiné (horse.png)
2. Les slots sont rendus sur le fond
3. Les labels hardcodes sont rendus : le titre de l'inventaire en haut, "Inventory" en bas pour l'inventaire joueur
4. L'entite est rendue dans la preview zone

**Le serveur ne peut pas injecter de rendu de texte arbitraire dans un GUI vanilla.** Il n'existe aucun packet en 1.8.8 pour "dessiner du texte a la position X,Y dans le GUI ouvert".

---

### Alternative A — Texte bake dans le PNG (recommande pour texte statique)

Le plus simple. Ton `horse.png` est une image que tu controles entierement. Tu peux y dessiner directement :
- Le nom du metier
- Des labels decoratifs ("VITESSE", "SAUT", "NIVEAU")
- Des barres de progression statiques

**Limites** : le texte est fixe dans l'image, pas mis a jour en temps reel. Parfait pour des labels de champs ("XP :", "Niveau :") mais pas pour les valeurs qui changent.

---

### Alternative B — Titre de fenetre dynamique (recommande pour valeur principale)

Le titre de la fenetre est l'`IChatBaseComponent` passe dans `PacketPlayOutOpenWindow`. Il est rendu en haut du GUI en texte colore. Tu peux y mettre n'importe quoi :

```java
// Dans ton plugin, quand tu ouvres le GUI horse :
String titre = "§6Mineur §7Lv.15  §a███████░░░ §e73%";
IChatBaseComponent titleComponent = new ChatComponentText(titre);

// Le packet horse a 5 champs : windowId, "EntityHorse", title, slots, entityId
player.playerConnection.sendPacket(
    new PacketPlayOutOpenWindow(windowId, "EntityHorse", titleComponent, 17, fakeHorseId)
);
```

**Limites** : un seul titre, affiché uniquement en haut du GUI. Ne peut pas etre mis a jour pendant que le GUI est ouvert (il faudrait le fermer et rouvrir).

---

### Alternative C — Items avec lore dans des slots positionnés (recommande pour stats lisibles)

Placer des `ItemStack` dans les slots du coffre du cheval (slots 2 a 16) qui ont comme nom/lore les stats. Le joueur hover l'item et voit les infos. Les items peuvent etre mis a jour en temps reel via `PacketPlayOutSetSlot`.

```java
ItemStack statsItem = new ItemStack(Material.PAPER);
ItemMeta meta = statsItem.getItemMeta();
meta.setDisplayName("§6Stats Metier");
meta.setLore(Arrays.asList(
    "§7Metier : §6Mineur",
    "§7Niveau : §e15",
    "§7XP : §a1460 §7/ §a2000",
    "§7Vitesse : §b+15%",
    "§7Double drop : §cInactif"
));
statsItem.setItemMeta(meta);
// Envoyer via PacketPlayOutSetSlot pour mise a jour en temps reel
```

Avec CIT (MCPatcher), la texture de cet item peut etre remplacee par une icone custom selon le metier.

---

### Alternative D — ArmorStand flottante derriere le GUI (hack creatif)

Spawner une `ArmorStand` invisible avec `setCustomName("§7Lv.15 | 73%")` et `setCustomNameVisible(true)` a la position exacte ou le joueur se tient quand il ouvre le GUI. Le nom flotte visible **derriere** le GUI (le GUI est semi-transparent sur les cotes).

Peu fiable selon l'orientation du joueur. Uniquement decoratif.

---

## PARTIE 2 — LUNAR CLIENT : COMMENT CA MARCHE POUR LES STATS DE CHEVAL

### Ce que Lunar lit et comment

Lunar Client a un module "Horse Stats" qui affiche vitesse, saut et HP. Voici les sources exactes des donnees, confirmees dans le decompile PandaSpigot.

#### Vitesse (`generic.movementSpeed`)

```java
// Dans GenericAttributes.java (ligne 24 du decompile)
public static final IAttribute MOVEMENT_SPEED =
    new AttributeRanged(null, "generic.movementSpeed", 0.7f, 0.0, SpigotConfig.movementSpeed)
    .a("Movement Speed").a(true);
```

Lunar recoit cet attribut via `PacketPlayOutUpdateAttributes`. Envoi automatique par `EntityTrackerEntry` quand l'entite est trackee (ligne 291 du decompile).

**Conversion affichee par Lunar** : `valeur_interne * 42.157...` = blocs/seconde  
Exemple : `0.2250 * 42.157 ≈ 9.49 b/s`

#### Saut (`horse.jumpStrength`)

```java
// Dans EntityHorse.java (ligne 63 du decompile)
public static final IAttribute attributeJumpStrength =
    new AttributeRanged(null, "horse.jumpStrength", 0.7, 0.0, 2.0)
    .a("Jump Strength").a(true);
```

Envoyé via `PacketPlayOutUpdateAttributes` avec l'attribut `"horse.jumpStrength"`.

**Conversion affichee par Lunar** : formule physique de saut vertical (~`(-0.1817 * j^3) + (3.689 * j^2) + (2.1128 * j) - 0.8438` blocs de hauteur)

#### Sante (`generic.maxHealth`)

```java
// Dans GenericAttributes.java (ligne 21 du decompile)
public static final IAttribute maxHealth =
    new AttributeRanged(null, "generic.maxHealth", 20.0, 0.1, SpigotConfig.maxHealth)
    .a("Max Health").a(true);
```

HP actuel lu via `PacketPlayOutEntityMetadata` (index 6 = float health).

---

### CE QUE TU PEUX FAIRE : CONTROLER CE QUE LUNAR AFFICHE

**C'est la partie la plus puissante.** Puisque tu controles le `EntityHorse` NMS fake, tu peux setter ses attributs a n'importe quelle valeur et Lunar les affichera.

```java
EntityHorse fakeHorse = ...;

// Faire afficher "Vitesse : 12.5 b/s" sur Lunar
fakeHorse.getAttributeInstance(GenericAttributes.MOVEMENT_SPEED).setValue(0.2966);

// Faire afficher "Saut : 3.2 blocs" sur Lunar
fakeHorse.getAttributeInstance(EntityHorse.attributeJumpStrength).setValue(0.8);

// Faire afficher "HP : 150" sur Lunar  
fakeHorse.getAttributeInstance(GenericAttributes.maxHealth).setValue(150.0);
fakeHorse.setHealth(150.0f);

// Renvoyer les attributs mis a jour au joueur
Collection<AttributeInstance> attrs = ((AttributeMapServer) fakeHorse.getAttributeMap()).c();
player.playerConnection.sendPacket(new PacketPlayOutUpdateAttributes(fakeHorse.getId(), attrs));
```

**Utilisation creative pour KjobsUltimate** :

| Ce que tu veux afficher | Attribut a faker | Lunar affiche |
|---|---|---|
| Niveau du metier | `maxHealth` = niveau * 10 | "HP: 150" pour niveau 15 |
| XP en pourcentage | `horse.jumpStrength` = xpPct * 2.0 | valeur de saut = 0..2 |
| Bonus de vitesse actif | `movementSpeed` = real * bonus | vitesse en b/s |
| Progression du jour | `maxHealth` courante = objectif complété | barre HP = progression |

Le joueur avec Lunar voit ses stats metier affichees par le module Horse Stats de Lunar, sans aucun mod custom. **Lunar est utilise comme HUD de metier gratuit.**

---

## PARTIE 3 — SLOT SELLE ET SLOT ARMURE : CACHER / CUSTOMISER

### Structure des slots dans ContainerHorse

Cote pixel dans le PNG `horse.png` (coordonnees en pixels du GUI rendu a 2x) :
- **Slot 0** (selle) : x=8, y=18 dans le GUI container
- **Slot 1** (armure) : x=8, y=36 dans le GUI container

### Option 1 — Cacher visuellement via PNG (recommande)

Dessine le fond de ton `horse.png` par-dessus les coordonnees des slots selle et armure. Le slot existe cote serveur mais si tu dessines le background sur ces pixels, le joueur ne voit pas le cadre du slot. L'item reste invisible (place un item transparent ou de la glass pane sans nom).

Attention : le slot existe quand meme. Si le joueur clique a cette position, il interagit avec le slot. Pour bloquer completement l'interaction, voir ci-dessous.

### Option 2 — Rendre le slot non-interactif (via fork ContainerHorse)

Dans `ContainerHorse.java` de PandaSpigot-Server, le slot selle est defini ainsi :

```java
// Slot selle original
this.a(new Slot(iinventory1, 0, 8, 18) {
    @Override
    public boolean isAllowed(ItemStack itemstack) {
        return super.isAllowed(itemstack)
               && itemstack.getItem() == Items.SADDLE
               && !this.hasItem();
    }
});
```

Pour le rendre completement non-interactif et non-prenable, modifier dans le fork :

```java
// Slot selle verrouille - affiche un item decoratif, non modifiable
this.a(new Slot(iinventory1, 0, 8, 18) {
    @Override
    public boolean isAllowed(ItemStack itemstack) {
        return false; // jamais de depot possible
    }

    @Override
    public boolean b() {
        return false; // jamais prenable (isPickupable)
    }
});
```

Puis pre-remplir le slot avec un item custom (voir Option 3).

### Option 3 — Afficher un item CIT custom dans le slot selle

Mettre un `ItemStack` custom avec un NBT specifique dans le slot 0 de l'inventaire du cheval. Avec MCPatcher/CIT, cet item peut avoir une texture completement differente.

```java
// Cree un item "icone de metier Mineur" dans le slot selle
ItemStack jobIcon = new ItemStack(Material.IRON_PICKAXE, 1, (short) 0);
NBTTagCompound tag = new NBTTagCompound();
tag.setString("sparrowmc-item", "job-icon-mineur");
jobIcon.setTag(tag);

// Mettre dans le slot 0 du fakeHorse inventory
fakeHorse.getInventory().setItem(0, CraftItemStack.asNMSCopy(jobIcon));
```

Dans le pack CIT :
```properties
# assets/minecraft/optifine/cit/jobs/job_icon_mineur.properties
type=item
items=iron_pickaxe
nbt.sparrowmc-item=job-icon-mineur
texture=job_icon_mineur.png   <- icone 16x16 de ta pioche custom
```

Le joueur voit l'icone du metier dans la zone selle, completement customisee.

### Option 4 — Slot selle comme indicateur de prestige

Plutot que de cacher le slot selle, l'utiliser comme "badge de prestige" :

```java
// Changer l'icone selon le niveau de prestige
ItemStack prestigeBadge = getPrestigeBadge(player);
// Badge prestige 1 = item custom CIT "prestige1"
// Badge prestige 5 = item custom CIT "prestige5"
```

---

## PARTIE 4 — SPAWN D'UN JOUEUR AVEC SKIN DANS LE GUI

### Verdict : IMPOSSIBLE dans la preview du GUI, POSSIBLE a cote

#### Pourquoi impossible dans la preview

La zone de preview du cheval est rendue par `GuiScreenHorseInventory.drawScreen()` cote client. Cette methode recupere l'entite via son entity ID et appelle `RenderManager.renderEntityWithPosYaw(entity, ...)`. Le rendu est celui de l'`EntityHorse` — hard-coded.

En 1.8.8, les modeles d'entites **ne peuvent pas etre changes via resource pack** (les JSON entity models n'existent que depuis la 1.9+). Tu peux changer la texture du cheval (`assets/minecraft/textures/entity/horse/`) mais le modele reste un cheval.

Il n'existe aucun moyen de faire afficher un `EntityPlayer` dans la preview horse GUI en client vanilla 1.8.8.

---

#### Ce qui EST possible : NPC fake dans le monde, visible DERRIERE le GUI

Le GUI ne cache pas entierement ce qu'il y a derriere lui. Les zones semi-transparentes du GUI permettent de voir le monde. La technique :

1. A l'ouverture du GUI, spawner un `EntityPlayer` NMS fake a ~2 blocs devant le joueur (dans son champ de vision).
2. Ce NPC est visible dans le monde derriere le GUI.
3. Il tient un item caracteristique (pioche pour Mineur, epee pour Guerrier...).
4. A la fermeture du GUI (`InventoryCloseEvent`), detruire le NPC.

```java
// Sequence complete a l'ouverture du GUI Jobs

// 1. Creer le GameProfile avec le skin voulu
GameProfile profile = new GameProfile(UUID.randomUUID(), "NPC_Mineur");
PropertyMap props = profile.getProperties();
props.put("textures", new Property("textures", BASE64_SKIN_MINEUR, BASE64_SIGNATURE_MINEUR));

// 2. Creer l'EntityPlayer fake
EntityPlayer fakeNPC = new EntityPlayer(server, worldServer, profile, new PlayerInteractManager(worldServer));
fakeNPC.setLocation(player.getLocation().add(dx, 0, dz));
fakeNPC.setInvisible(false);

// 3. Envoyer les packets UNIQUEMENT a ce joueur (pas via tracker global)
PacketPlayOutPlayerInfo addInfo = new PacketPlayOutPlayerInfo(
    PacketPlayOutPlayerInfo.EnumPlayerInfoAction.ADD_PLAYER, fakeNPC
);
player.playerConnection.sendPacket(addInfo);

PacketPlayOutNamedEntitySpawn spawnPacket = new PacketPlayOutNamedEntitySpawn(fakeNPC);
player.playerConnection.sendPacket(spawnPacket);

PacketPlayOutEntityHeadRotation headRot = new PacketPlayOutEntityHeadRotation(
    fakeNPC, (byte)((int)(fakeNPC.yaw * 256.0F / 360.0F))
);
player.playerConnection.sendPacket(headRot);

// 4. Retirer du TAB apres 2 ticks
Bukkit.getScheduler().runTaskLater(plugin, () -> {
    PacketPlayOutPlayerInfo removeInfo = new PacketPlayOutPlayerInfo(
        PacketPlayOutPlayerInfo.EnumPlayerInfoAction.REMOVE_PLAYER, fakeNPC
    );
    player.playerConnection.sendPacket(removeInfo);
}, 2L);

// 5. A la fermeture du GUI
PacketPlayOutEntityDestroy destroyPacket = new PacketPlayOutEntityDestroy(fakeNPC.getId());
player.playerConnection.sendPacket(destroyPacket);
```

**Niveau de difficulte : eleve** — le skin en base64 doit etre pre-cache au demarrage (requete Mojang API asynchrone), la gestion des entity IDs doit etre thread-safe, et la position du NPC doit s'adapter a la rotation du joueur.

---

#### Alternative : Changer la texture du cheval pour un humanoide

En changeant la texture dans le resource pack (`assets/minecraft/textures/entity/horse/horse_white.png` par exemple), tu peux faire en sorte que le cheval dans la preview ait l'apparence d'un personnage humain dessiné sur la texture. Le modele reste celui d'un cheval, mais visuellement ca peut etre suffisant si le design est bien fait (personnage vu de face, pose debout, taille adaptee au modele cheval).

Avec le type de cheval (`setType()`) :
- Type 0 = cheval blanc → texture `horse_white.png`
- Type 1 = cheval noir → texture `horse_dark_brown.png`  
- Etc.

Chaque type peut avoir une texture differente dans le pack → plusieurs "personnages" par metier.

---

## PARTIE 5 — RECAP COMPLET DE CE QUI EST CUSTOMISABLE

| Element | Customisable | Methode | Difficulte |
|---|---|---|---|
| Background du GUI | OUI | Remplacer `horse.png` dans le pack | Tres facile |
| Titre de la fenetre | OUI (1 ligne) | `IChatBaseComponent` dans `PacketPlayOutOpenWindow` | Facile |
| Texte dynamique dans le cadre | NON nativement | Hack via items lore + slots | Moyen |
| Items dans les slots coffre (2..16) | OUI total | `PacketPlayOutSetSlot` + CIT | Facile |
| Textures des items (CIT) | OUI | MCPatcher CIT + NBT | Facile |
| Slot selle (slot 0) — contenu | OUI | Mettre un ItemStack custom | Facile |
| Slot selle — rendu non-interactif | OUI via fork | Modifier `ContainerHorse.java` | Moyen |
| Slot armure (slot 1) — meme chose | OUI | Identique slot selle | Facile/Moyen |
| Preview cheval — texture | OUI | Remplacer texture du cheval dans le pack | Facile |
| Preview cheval — modele | NON en 1.8.8 | Impossible via pack en 1.8.8 | — |
| Preview cheval — remplacer par joueur | NON nativement | NPC fake dans le monde (pas dans le GUI) | Eleve |
| Stats affichees par Lunar | OUI total | Setter les attributs NMS du fake horse | Moyen |
| Vitesse affichee Lunar | OUI | `generic.movementSpeed` | Moyen |
| Saut affiche Lunar | OUI | `horse.jumpStrength` | Moyen |
| HP affiché Lunar | OUI | `generic.maxHealth` + `setHealth()` | Facile |
| Nombre de slots | OUI via fork | Modifier `ContainerHorse.java` (max 15 extra) | Moyen |
| Position des slots | OUI via fork | Modifier les coordonnees dans `ContainerHorse` | Moyen |
| Slot selle/armure supprimes | OUI via fork | Ne pas ajouter les Slot(0) et Slot(1) | Facile (fork) |
| Animation des slots | OUI | `PacketPlayOutSetSlot` scheduler | Moyen |

---

## PARTIE 6 — PLAN D'INTEGRATION RECOMMANDE POUR KJOBSULTIMATE

### Utilisation optimale de tout ce qui est faisable

```
/jobs
├── Fake horse spawne (setInvisible = true dans le monde, sauf texture custom dans la preview)
├── setHasChest(true)  → 17 slots disponibles
├── Slot 0 (selle)    : icone du metier actif via CIT + isAllowed=false (fork)
├── Slot 1 (armure)   : badge de prestige via CIT + isAllowed=false (fork)
├── Slots 2..6 (ligne 1) : competences actives avec lore = description + bonus
├── Slots 7..11 (ligne 2) : quetes en cours avec progression dans le lore
├── Slots 12..16 (ligne 3) : navigation (shop metier / quitter / classement)
│
├── Titre fenetre = "§6Mineur §7Lv.15  §a████████░░ §e73%"
│
├── Attributs fake horse pour Lunar :
│     movementSpeed = bonus vitesse actuel (visible par joueurs Lunar)
│     jumpStrength  = xp % actuel converti en 0..2
│     maxHealth     = niveau metier * 10
│
└── NPC fake avec skin Mineur spawne 2 blocs devant (visible derriere le GUI)
    Detruit a la fermeture du GUI
```

### horse.png redesign conseille

```
[  ICONE METIER (slot 0)  ]  [  PREVIEW CHEVAL (invisible)  ]  [  TEXTE BAKE PNG  ]
[  PRESTIGE (slot 1)     ]  [      ou texture humanoide     ]  [  "COMPETENCES"   ]
[════════════════════════════════════════════════════════════════════════════════]
[  Competence 1  ][  Competence 2  ][  Competence 3  ][  Competence 4  ][  5  ]
[  Quete 1       ][  Quete 2       ][  Quete 3       ][  Quete 4       ][  5  ]
[  Nav: Shop     ][  Nav: Stats    ][  NAV: RETOUR   ][  Classement    ][  5  ]
[════════════════════════════════════════════════════════════════════════════════]
                          INVENTAIRE JOUEUR
```

Les labels "COMPETENCES", "QUETES", "NAVIGATION" sont bakes dans le PNG (statiques).  
Les valeurs (nom de la competence, progression) sont dans le lore des items (dynamiques via `PacketPlayOutSetSlot`).
