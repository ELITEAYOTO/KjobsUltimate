# Faisabilite - Systeme de Jobs 1.8.8 (KhopeSpigot / PandaSpigot)

> Analyse basee sur le code decompile de PandaSpigot (`decompiled_panda/net/minecraft/server/v1_8_R3/`).
> Tout ce qui est marque **FAISABLE** fonctionne avec un client vanilla 1.8.8 non modifie.

---

## 1. GUI Cheval detournee en menu Jobs

**FAISABLE — Niveau de difficulte : moyen**

### Ce que fait le client vanilla

Le client ouvre une fenetre speciale quand il recoit `PacketPlayOutOpenWindow` avec le type `"EntityHorse"` et un entity ID valide. La texture chargee cote client est `textures/gui/container/horse.png`. Cette texture est entierement remplacable via resource pack.

### Comment faire

Dans `EntityPlayer.openHorseInventory(EntityHorse, IInventory)` (ligne ~703 du decompile), le packet envoye est :

```java
new PacketPlayOutOpenWindow(containerCounter, "EntityHorse", title, slots, entityHorse.getId())
```

Pour detourner cela depuis un plugin, il faut :

1. Spawner un `EntityHorse` fake invisible en NMS (type mule avec `hasChest = true` pour avoir 17 slots utiles).
2. Lui assigner un `IInventory` custom.
3. Appeler `((CraftPlayer) player).getHandle().openHorseInventory(fakeHorse, customInventory)`.
4. Le cheval fake ne doit jamais etre visible : on le met a `setInvisible(true)`, on bloque son spawn packet cote tracker, ou on le supprime du monde tout en gardant la reference.

### Slots disponibles dans ContainerHorse

Selon `ContainerHorse.java` analyse :

- Slot 0 : selle (bait slot saddle)
- Slot 1 : armure du cheval
- Slots 2..16 : grille 5x3 du coffre du cheval (`hasChest = true`)
- Slots joueur 9..35 : inventaire principal
- Slots joueur 0..8 : barre d'action

**Total exploitable pour le menu Jobs : 17 slots custom + inventaire du joueur masquable via glass panes.**

### Redesign visuel complet via resource pack

Remplacer `assets/minecraft/textures/gui/container/horse.png` (256x256) dans le pack de textures. Le client vanilla accepte n'importe quelle image PNG tant que les zones de slots sont respectees. Tu peux dessiner un panneau RPG complet, un arbre de competences, un ecran de classe, etc.

### Limite

Le GUI cheval exige qu'une entite cheval soit "vivante" cote NMS (le client verifie que `entity.isAlive()` dans `ContainerHorse.a(EntityHuman)`). Si l'entite est tuee, le GUI se ferme automatiquement. Il faut garder le fake horse en vie (invincible) pendant toute la session de menu.

---

## 2. Fake NPC avec skin de joueur devant le GUI

**FAISABLE — Niveau de difficulte : eleve**

### Principe

Spawner une entite `EntityPlayer` NMS fake (packet-only) devant le joueur au moment ou le GUI s'ouvre. Le joueur la voit pendant quelques secondes, puis le GUI prend le focus. L'entite est detruite quand le GUI se ferme.

### Packets necessaires

```
PacketPlayOutPlayerInfo  (ADD_PLAYER, avec GameProfile + skin textures)
PacketPlayOutNamedEntitySpawn  (spawn l'entite a une position devant le joueur)
PacketPlayOutEntityHeadRotation  (pour qu'il regarde le joueur)
PacketPlayOutEntityDestroy  (cleanup a la fermeture du GUI)
```

Le `GameProfile` peut contenir n'importe quelle texture valide (skin custom) encodee en base64 dans la propriete `textures`. Tu peux utiliser les textures de Mojang pour des skins predetermines (Mineur, Forgeron, Pirate, etc.) en les cachant en base64 au demarrage du plugin.

### Contrainte importante

`PacketPlayOutPlayerInfo` avec `ADD_PLAYER` ajoute le joueur fake a la liste de joueurs visible dans le TAB. Il faut envoyer un second `PacketPlayOutPlayerInfo` avec `REMOVE_PLAYER` juste apres le spawn (avec un delai de 2-3 ticks) pour le retirer de la liste, tout en gardant l'entite visible dans le monde.

---

## 3. Achievements 1.8.8 — Comment ca marche reellement

### Mecanisme vanilla

Dans `EntityPlayer.l()` (la boucle tick du joueur), les achievements sont enregistres via `EntityPlayer.b(Achievement achievement)`. Cela envoie automatiquement :

- Une mise a jour des statistiques cote client (`PacketPlayOutStatistic`)
- Le popup d'achievement natif (rendu entierement cote client)

Le popup achievement vanilla en 1.8.8 :
- S'affiche en haut a droite
- Montre un item et un nom
- N'est PAS personnalisable en layout sans mod client
- Le texte affiché est la chaine `achievement.xxx.name` de la langue active

### Ce qu'on peut modifier

**Le texte** : en remplacant les fichiers de langue dans le resource pack (`assets/minecraft/lang/fr_FR.lang`), on peut changer le texte de n'importe quel achievement vanilla. Exemple :

```properties
achievement.mineWood=Nouveau metier debloques !
achievement.mineWood.desc=Tu es desormais Bucherons niveau 1
```

**L'item affiche** : en remplacant la texture de l'item utilise par l'achievement via CIT (`pack/assets/minecraft/optifine/cit/`).

**Le son** : en remplacant `sounds/random/levelup.ogg` ou le son specific achievement.

### Fake achievements (recommande)

Plutot que d'utiliser les vrais achievements vanilla (limites et peu flexibles), la methode propre est de simuler l'effet visuel avec packets :

```java
// Titre central (LEVEL UP)
PacketPlayOutTitle titlePacket = new PacketPlayOutTitle(
    PacketPlayOutTitle.EnumTitleAction.TITLE,
    new ChatComponentText("§6⛏ Mineur §e↑ Niveau 12")
);
player.playerConnection.sendPacket(titlePacket);

// Sous-titre
PacketPlayOutTitle subtitlePacket = new PacketPlayOutTitle(
    PacketPlayOutTitle.EnumTitleAction.SUBTITLE,
    new ChatComponentText("§7+5% vitesse de minage")
);
player.playerConnection.sendPacket(subtitlePacket);

// Duree
PacketPlayOutTitle timesPacket = new PacketPlayOutTitle(3, 40, 15);
player.playerConnection.sendPacket(timesPacket);

// Son achievement
player.playerConnection.sendPacket(
    new PacketPlayOutNamedSoundEffect("random.levelup", x, y, z, 1.0f, 1.0f)
);
```

Resultat : ressemble exactement a un "level up MMO" sans aucun mod client.

---

## 4. Action Bar pour progression en temps reel

**FAISABLE — Niveau de difficulte : faible**

`PacketPlayOutChat` avec `byte 2` (channel action bar) affiche un texte sous le crosshair. Pratique pour une barre de progression metier persistante.

```java
// Envoi toutes les 4 secondes pour garder la barre affichee
PacketPlayOutChat actionBar = new PacketPlayOutChat(
    new ChatComponentText("§8[§6⛏ Mineur §7Lv.15§8] §a███████████░░░ §e73%"),
    (byte) 2
);
player.playerConnection.sendPacket(actionBar);
```

**Attention** : l'action bar disparait apres ~3 secondes si pas renvoye. Il faut un scheduler qui renvoie toutes les 40-60 ticks uniquement quand la valeur a change.

---

## 5. Bossbar fake (Wither / EnderDragon)

**FAISABLE — Niveau de difficulte : moyen**

En 1.8.8, la bossbar est rendue cote client uniquement quand un `EntityWither` ou `EntityEnderDragon` est dans la range du joueur. La technique classique :

1. Spawner un `EntityWither` invisible packet-only tres haut dans l'air (Y=255) ou sous le sol.
2. Lui donner un nom avec `setCustomName()`.
3. Modifier sa sante pour controler la progression de la barre.

```java
EntityWither wither = new EntityWither(worldServer);
wither.setInvisible(true);
wither.setCustomName("§6Mineur §eLv.20 §8| §a████████████░░ §782%");
wither.setHealth(wither.getMaxHealth() * 0.82f); // 82% de la barre

// Envoyer uniquement a ce joueur (pas via tracker global)
PacketPlayOutSpawnEntityLiving spawnPacket = new PacketPlayOutSpawnEntityLiving(wither);
player.playerConnection.sendPacket(spawnPacket);
```

**Contrainte** : range de detection de la bossbar = ~256 blocs. Il faut maintenir le fake wither dans cette range ou utiliser la technique "teleport constant" pour le coller au joueur via scheduler.

---

## 6. Barre XP vanilla detournee

**FAISABLE — Niveau de difficulte : tres faible**

`PacketPlayOutExperience` controle directement la barre XP affichee cote client, independamment de la vraie XP du joueur.

```java
// Barre a 68%, niveau affiche = niveau metier
player.playerConnection.sendPacket(
    new PacketPlayOutExperience(0.68f, totalXPMetier, niveauMetier)
);
```

**Important** : si le joueur gagne de la vraie XP, PandaSpigot renvoie automatiquement un packet XP dans `EntityPlayer.l()` qui ecrase le fake. Il faut soit intercepter cet envoi dans le fork (modifier `if (this.expTotal != this.lastSentExp)`), soit resynchroniser le fake apres chaque vrai gain XP via l'event `PlayerExpChangeEvent`.

---

## 7. GUI animees par packets

**FAISABLE — Niveau de difficulte : moyen**

Les slots d'un inventaire ouvert peuvent etre modifies en temps reel via `PacketPlayOutSetSlot`. Le client accepte les mises a jour instantanement.

Exemple d'animation de pulsation :

```java
// Scheduler toutes les 10 ticks
int frame = (tickCounter / 10) % frames.length;
ItemStack frameItem = buildProgressFrame(frame, jobProgress);
player.playerConnection.sendPacket(
    new PacketPlayOutSetSlot(windowId, slotIndex, frameItem)
);
```

Permet : items qui clignotent, barre de progression animee dans les slots, icones qui changent selon l'etat.

---

## 8. Limites reelles et blocages

| Fonctionnalite | Limite |
|---|---|
| Background GUI different selon le contexte | Limite aux types de container vanilla (voir doc GUI-BACKGROUNDS-CUSTOM.md) |
| NPC dans le GUI cheval | Impossible nativement — le client rend uniquement le cheval |
| Modifier le layout du popup achievement | Impossible sans mod client |
| Plus de 17 slots dans le GUI cheval | Impossible — limite du ContainerHorse vanilla |
| Faire pivoter le NPC fake en temps reel | Possible mais couteux (packet EntityHeadRotation toutes les 2 ticks) |
| Garder la bossbar sans scheduler | Impossible — wither disparait si hors range |
| Empecher le joueur de poser des items dans les slots du GUI | Possible via `InventoryClickEvent` en Bukkit ou via `Slot.isAllowed()` en NMS |
