# Liste Complete de Creativite — KjobsUltimate sur SparrowMC 1.8.8

> Toutes les idees sont classees par faisabilite et difficulte.
> Base technique : PandaSpigot (KhopeSpigot fork), MCPatcher/CIT, client vanilla.

---

## CATEGORIE 1 — Systeme Jobs core

### 1.1 GUI Jobs via ContainerHorse redesigne
**FAISABLE ✅ | Difficulte : moyenne**

Ouvrir un GUI "EntityHorse" fakewith une `IInventory` custom. Remplacer `horse.png` dans le pack par un ecran RPG de selection de classe. Le joueur voit un menu Jobs complet (17 slots exploitables) sans aucune connexion avec un vrai cheval.

Jusqu'ou on peut aller : 17 slots actifs, arriere-plan totalement custom (256x256), boutons de navigation, icones de metier CIT-tagged, barre de progression simulee par items.

Limite : 17 slots max, client ferme le GUI si le fake cheval "meurt".

---

### 1.2 Arbre de competences en GUI 54 slots
**FAISABLE ✅ | Difficulte : moyenne**

Un inventaire 54 slots (`minecraft:container`) dont les slots sont remplis de `stained_glass_pane` custom CIT qui dessinent un arbre visuel. Les slots "cliquables" sont des items speciaux. Les slots de decoration sont non-cliquables via `InventoryClickEvent`.

```
Exemple Mineur :
Slot 13 : [TRONC] Mineur
Slot 22 : [BRANCHE] Fortune
Slot 24 : [BRANCHE] Speed Mining
Slot 31 : [FEUILLE] Explosion Expert
Slot 33 : [FEUILLE] Vein Miner
```

Jusqu'ou on peut aller : arbre a 5 niveaux, branches conditionnelles (deverrouillage progressif), preview du bonus au survol (via item lore dynamique), animations de deblocage (item qui change via `PacketPlayOutSetSlot`).

---

### 1.3 Selection de metier avec NPC fake
**FAISABLE ✅ | Difficulte : elevee**

Au `/jobs`, spawner un `EntityPlayer` NMS packet-only devant le joueur avec le skin du metier (Mineur, Forgeron, Pirate, etc.). L'entite regarde le joueur, tient un item caracteristique du metier, puis le GUI s'ouvre.

Jusqu'ou on peut aller : 5 a 10 skins de metier pre-caches en base64, rotation de tete dynamique, changement de skin selon le metier selectionne, destruction propre a la fermeture du GUI.

Limite : le NPC est dans le monde (pas a l'interieur du GUI). Le skin est fixe (pas de skin joueur temps-reel sans requete Mojang).

---

### 1.4 Metiers physiques dans le monde
**FAISABLE ✅ | Difficulte : faible a moyenne**

Des zones dans le monde donnent de l'XP de metier uniquement si le joueur a le bon metier actif :
- Zone mine = XP Mineur
- Foret protegee = XP Bucherons
- Lac = XP Pecheur
- Forge (bloc Furnace/Anvil) = XP Forgeron

Detection via `BlockBreakEvent`, `PlayerFishEvent`, `FurnaceSmeltEvent`, etc. Pas de NMS requis pour la partie XP.

Jusqu'ou on peut aller : zones delimitees par WorldGuard ou regions custom, multiplicateurs d'XP selon la zone, minerais custom (via systeme NBT CIT existant du fork) qui donnent plus d'XP metier.

---

### 1.5 Systeme de prestige metier
**FAISABLE ✅ | Difficulte : faible**

Apres le niveau max d'un metier, le joueur peut "prestigier" : retour au niveau 1 mais avec un badge permanent et un bonus passif persistant. Purement gere en donnees (pas de NMS).

Jusqu'ou on peut aller : jusqu'a 5 prestiges, chaque prestige change le prefixe affiché dans le TAB et au-dessus de la tete via `ScoreboardTeam`, deblocage de perks exclusifs par prestige.

---

## CATEGORIE 2 — HUD et feedback visuel temps reel

### 2.1 Action bar de progression metier
**FAISABLE ✅ | Difficulte : tres faible**

`PacketPlayOutChat` byte 2 affiche une barre sous le crosshair. Mise a jour toutes les 4 secondes ou uniquement quand la valeur change.

```
§8[§6⛏ Mineur §7Lv.15§8] §a███████████░░░ §e73%  §7(1460 / 2000 XP)
```

Jusqu'ou on peut aller : indicateur de combo actif, streak du jour, bonus actif, cooldown de competence. Tout tient dans une ligne de 80 caracteres environ.

---

### 2.2 Bossbar de metier (Wither fake)
**FAISABLE ✅ | Difficulte : moyenne**

Fake `EntityWither` invisible maintenu proche du joueur, nom affiche = nom du metier + progression. La barre de vie du wither = pourcentage d'XP metier.

```
§6Mineur §eLv.20  |  §a████████████░░  §782%
```

Jusqu'ou on peut aller : switcher entre la bossbar metier et la bossbar d'evenement (raid, boss), animer la barre lors d'un level up (montee et redesente), changer le nom en temps reel pour afficher les bonus actifs.

---

### 2.3 Barre XP detournee
**FAISABLE ✅ | Difficulte : faible (necessite patch fork)**

`PacketPlayOutExperience` avec le niveau et la progression du metier actif. L'interface vanilla affiche le niveau metier comme si c'etait le niveau XP du joueur.

Dans le fork KhopeSpigot, on peut conditionner l'envoi automatique dans `EntityPlayer.l()` pour ne pas ecraser le fake quand le joueur a un metier actif.

---

### 2.4 Hologrammes flottants d'XP
**FAISABLE ✅ | Difficulte : faible**

`ArmorStand` invisible avec `setCustomName("+15 XP §6Mineur")` et `setCustomNameVisible(true)`, spawne a la position du bloc mine et qui monte puis disparait. Remplace les particules d'XP vanilla par quelque chose de lisible.

Jusqu'ou on peut aller : couleur et icone selon le type de gain (+XP mine, +XP craft, bonus combo), hauteur de vol parametrable, grouper les gains rapides en un seul hologramme cumule.

---

### 2.5 Notification de level up cinematic
**FAISABLE ✅ | Difficulte : faible**

Combinaison de :
- `PacketPlayOutTitle` : titre central
- `PacketPlayOutTitle` subtitle : bonus gagne
- `PacketPlayOutNamedSoundEffect` : son `random.levelup`
- `PacketPlayOutWorldParticles` : particules autour du joueur
- `PacketPlayOutGameStateChange(3, 0.0f)` : flash de pluie (optionnel, effet "choc")

Jusqu'ou on peut aller : sequence de 3 secondes, titre qui change en 2 phases (flash du niveau -> affichage du bonus), son de fanfare custom via resource pack, pas de lag cote serveur.

---

### 2.6 Combo system de minage
**FAISABLE ✅ | Difficulte : faible**

Compter les blocs mines en chaine (moins de X secondes d'ecart). Afficher en action bar :

```
§c⚡ COMBO §ex15  §7(+25% XP pendant 10s)
```

Jusqu'ou on peut aller : paliers de combo (x5, x10, x20, x50), son de tick a chaque nouveau combo, particules rouges/orange/jaunes selon le palier, multiplier l'XP metier par le palier.

---

## CATEGORIE 3 — GUIs custom avancees

### 3.1 10 GUIs avec identite visuelle unique
**FAISABLE ✅ | Difficulte : faible (pack) + faible (code)**

En assignant un type de container different par categorie de menu et en redessinant chaque PNG :

| Menu | Type | Background |
|---|---|---|
| Jobs | `EntityHorse` | Panneau RPG metier |
| Shop | `minecraft:container` x54 | Vitrine medievale |
| Kits | `minecraft:crafting_table` | Table de preparation |
| Talents | `minecraft:beacon` | Cristal de competences |
| Faction | `minecraft:hopper` | Entonnoir/flux de ressources |
| Banque | `minecraft:anvil` | Coffre-fort forge |
| Marche | `minecraft:villager` | Marchand ambulant |
| Alchimie | `minecraft:brewing_stand` | Labo de potions |
| Enchantements | `minecraft:enchanting_table` | Runes magiques |
| Progression | `minecraft:furnace` | Forge de progression |

---

### 3.2 Slots de decoration CIT pour backgrounds differencies
**FAISABLE ✅ | Difficulte : moyenne (pack)**

Pour avoir plusieurs GUIs 54 slots avec des visuels distincts (ex : Shop Armes vs Shop Armures), remplir les slots de decoration avec des `stained_glass_pane` NBT-tagged dont la texture CIT dessine le fond voulu.

Chaque GUI a son propre "fond par items". Le vrai PNG `generic_54.png` est un cadre neutre.

---

### 3.3 GUI avec slots animes
**FAISABLE ✅ | Difficulte : moyenne**

Scheduler Bukkit toutes les 10 ticks, envoyer `PacketPlayOutSetSlot` pour changer les items de decoration selon une frame d'animation. Permet :
- Barre de progression qui se remplit visuellement slot par slot
- Icone de metier qui "pulse" (enchanted glow on/off)
- Compteur qui decompte visuellement

Limite : ne pas spammer plus de 5-6 slots par tick pour rester leger.

---

### 3.4 Journal de metier (livre ecrit custom)
**FAISABLE ✅ | Difficulte : faible**

Donner au joueur un `BookMeta` pre-rempli avec :
- Page 1 : description du metier
- Page 2 : liste des perks actifs
- Page 3 : objectifs et quetes en cours
- Page 4 : classement du metier

Regenere a chaque consultation via `/jobs journal`. Peut inclure des couleurs via `ChatColor` dans le contenu des pages.

---

### 3.5 Map custom comme "minimap de metier"
**FAISABLE ✅ (partiel) | Difficulte : elevee**

`PacketPlayOutMap` permet d'envoyer une image 128x128 sur une `MapView`. Utilisable pour afficher :
- Une carte des zones de metier
- Un "certificat de metier" avec le nom du joueur
- Un badge de prestige graphique

Limite : 128x128 pixels, 16 couleurs de palette fixe, pas de transparence. Rendu pixelise mais lisible.

---

## CATEGORIE 4 — Fake achievements et notifications

### 4.1 Fake achievement RPG overlay
**FAISABLE ✅ | Difficulte : tres faible**

Envoyer `PacketPlayOutTitle` + `PacketPlayOutNamedSoundEffect` pour simuler un "achievement" RPG :
- Titre : `§6⚒ Nouveau metier debloques !`
- Sous-titre : `§7Forgeron — Niveau 1`
- Son : `random.levelup` ou `mob.villager.yes`

Completement different du popup achievement vanilla (qui lui s'affiche en coin haut-droit). Cet overlay est au centre de l'ecran, plus visible et plus impressionnant.

---

### 4.2 Remplacer les textes d'achievements vanilla
**FAISABLE ✅ | Difficulte : tres faible**

Dans le resource pack, modifier `assets/minecraft/lang/fr_FR.lang` (ou `en_US.lang`) :

```properties
achievement.mineWood=Premier bois !
achievement.mineWood.desc=Tu viens de demarrer ta carriere de Bucherons
achievement.openInventory=Inventaire ouvert
achievement.openInventory.desc=Appuie sur Tab pour voir tes stats de metier
```

Utiliser les vrais achievements vanilla comme points de declenchement de la logique metier (`StatisticList`, `AchievementList` dans le decompile). Exemple : capter le premier clic sur `AchievementList.b` (craft une planche) pour declencher le deblocage du metier Bucherons.

---

### 4.3 Streak quotidienne metier
**FAISABLE ✅ | Difficulte : faible**

Tracker le dernier timestamp de login actif avec activite metier. Si le joueur joue au moins 15 minutes par jour avec son metier, incrementer le streak. Afficher dans l'action bar :

```
§6🔥 Streak x7 jours  §7(bonus XP x1.5 actif)
```

---

## CATEGORIE 5 — Effets environnementaux lies au metier

### 5.1 Particules de metier autour du joueur
**FAISABLE ✅ | Difficulte : tres faible**

`PacketPlayOutWorldParticles` envoye periodiquement. Quand le metier Mineur est actif et que le joueur mine :
- Particules `CRIT` dorées autour des mains
- Particules `SMOKE_NORMAL` quand un vein est detecte

---

### 5.2 Effets de potions passifs par metier
**FAISABLE ✅ | Difficulte : tres faible**

Appliquer des `PotionEffect` Bukkit selon le metier actif :
- Mineur : `FAST_DIGGING` (Haste II) permanent tant que sous Y=40
- Bucherons : `SPEED I` dans les biomes forestiers
- Forgeron : `FIRE_RESISTANCE` dans les zones de forge

Via `PlayerMoveEvent` + detection de biome/Y/region.

---

### 5.3 Son custom a l'activation d'un metier
**FAISABLE ✅ | Difficulte : tres faible**

`PacketPlayOutNamedSoundEffect` avec un son du resource pack custom. Le resource pack inclut des sons `.ogg` par metier (fanfare Mineur, cloche Forgeron, etc.) references dans `assets/minecraft/sounds.json`.

---

### 5.4 ArmorStand animee "enseigne" de metier
**FAISABLE ✅ | Difficulte : moyenne**

Une `ArmorStand` invisible positionnee devant un NPC de metier, avec un item dans la main (ex: pioche pour le Mineur) et un nom custom affiché. Un scheduler la fait tourner lentement (`yaw += 5` toutes les 2 ticks) pour un effet flottant.

---

## CATEGORIE 6 — Idees avancees / uniquement avec le fork

### 6.1 Nouveau type de container custom dans le fork
**FAISABLE avec fork ✅ | Client vanilla : NON**

Dans PandaSpigot source, ajouter un nouveau string de container type dans `PacketPlayOutOpenWindow` et une nouvelle texture dans le resource pack. Le client vanilla **ignorera** ou **plantera** sur un type inconnu.

Verdict : non viable pour client vanilla pur. Viable uniquement si le serveur impose un mod client leger.

---

### 6.2 Intercepter et modifier l'envoi XP dans le fork
**FAISABLE avec fork ✅ | Difficulte : faible dans la source**

Dans `EntityPlayer.l()`, modifier la condition d'envoi de `PacketPlayOutExperience` pour integrer une API Jobs :

```java
// Dans PandaSpigot-Server, EntityPlayer.l()
if (this.expTotal != this.lastSentExp) {
    this.lastSentExp = this.expTotal;
    // Si un metier actif est defini, envoyer les donnees metier
    if (JobsAPI.hasActiveJob(this)) {
        JobData job = JobsAPI.getJob(this);
        this.playerConnection.sendPacket(
            new PacketPlayOutExperience(job.getProgress(), job.getTotalXP(), job.getLevel())
        );
    } else {
        this.playerConnection.sendPacket(
            new PacketPlayOutExperience(this.exp, this.expTotal, this.expLevel)
        );
    }
}
```

---

### 6.3 Patch ContainerHorse pour plus de slots
**FAISABLE avec fork ✅ | Difficulte : moyenne dans la source**

Dans `ContainerHorse.java` du fork, retirer la contrainte `entityhorse.hasChest()` et forcer l'ajout de la grille 5x3 (ou 5x6 pour 30 slots). Le client accepte jusqu'a 15 slots extras dans le GUI cheval.

---

### 6.4 NBT metier sur les items custom (integration KhopeSpigot)
**FAISABLE avec fork ✅ | Difficulte : faible**

Les items custom existants du fork (`sparrowmc-item` NBT) peuvent recevoir un tag supplementaire `sparrowmc-job-xp` qui indique combien d'XP metier ils donnent quand mines/utilises. Integration directe avec le systeme NBT/CIT deja en place.

---

## RESUME RAPIDE — Ce qui est IMPOSSIBLE sans mod client

| Idee | Pourquoi impossible |
|---|---|
| Background completement different pour 2 GUIs du meme type | Client determine le PNG par type container uniquement |
| NPC rendu a l'interieur du GUI (comme un vrai render) | Client rend uniquement l'entite pointee par EntityHorse |
| Modifier le layout du popup achievement (coin haut-droit) | Rendu entierement cote client, non modifiable |
| GUI anime (fond qui change pendant l'ouverture) | PNG charge une seule fois a l'ouverture |
| Plus de 54 slots dans un chest GUI | Limite hard-codee cote client (9x6 max) |
| Textes flottants en dehors des zones vanilla | Pas de position arbitraire dans le GUI sans mod |
| Plusieurs bossbars simultanees | 1 seule bossbar active par joueur en 1.8.8 |
