# KjobsUltimate — Checklist Pré-Développement

> Ce document liste **dans l'ordre** tout ce qui doit être fait ou vérifié AVANT d'écrire la première ligne de code de KjobsUltimate.
> Chaque item est coché quand il est prêt.

---

## PARTIE A — Modifications dans les autres plugins

> Ces plugins doivent être modifiés, compilés et déployés AVANT que KjobsUltimate ne les utilise.

---

### A1. Kenchantement — Vérifier que Extra-Kill est fonctionnel

- [ ] Vérifier que l'enchant `Extra-Kill` écrit bien le NBT `kenchant_extra_kill` sur l'item quand appliqué à l'enclume
- [ ] Tester : `KenchantAPI.getEnchantLevel(item, "extra-kill")` retourne le niveau correct
- [ ] Vérifier que la permission `chasseur.level.X` bloque bien l'utilisation de l'item enchantmanté si pas la permission
- [ ] **Fichiers concernés** : `AnvilListener.java`, `KenchantAPI.java`, config `Kenchantement/config.yml` section Extra-Kill

**Pourquoi bloquant** : HunterListener de KjobsUltimate dépend du NBT `kenchant_extra_kill` pour les bonus kills.

---

### A2. Kstacker — Intégrer Extra-Kill dans MobLethalDamageListener

- [ ] Modifier `MobLethalDamageListener.java` : ajouter le cas `killedCount = min(extraKillLevel + 1, count)` si le joueur a l'enchant Extra-Kill (via lecture NBT directe)
- [ ] Le META_KILL_MULTIPLIER du ghost reflète le bon nombre (1, 2 ou 3)
- [ ] Tester : mob stacké x10, joueur avec Extra Kill niveau 1 → ghost avec multiplier=2, pas plus
- [ ] Tester : mob stacké x10, joueur sans Extra Kill → ghost avec multiplier=1
- [ ] Tester : shift-kill → multiplier = stackCount (comportement inchangé)
- [ ] **Fichiers concernés** : `Kstacker/src/main/java/me/krunsh/kstacker/listener/MobLethalDamageListener.java`

**Pourquoi bloquant** : Sans ça, HunterListener ne peut pas compter correctement les kills sur mobs stackés.

---

### A3. Kgui — Vérifier/Étendre ContentProviderAPI

- [ ] Vérifier que `DynamicContentProvider.getContent(player, args)` reçoit bien les args dynamiques passés à l'ouverture
- [ ] Vérifier que `Kgui.openMenu(player, menuId, Map<String, String> args)` existe ou l'ajouter
- [ ] Vérifier que les `DynamicItem` supportent un `onClick(player, item, clickType)` avec distinction gauche/droit (CLICK_LEFT vs CLICK_RIGHT)
- [ ] Vérifier que Kgui rafraîchit automatiquement le contenu d'un GUI ouvert OU accepter qu'il est statique à l'ouverture (acceptable pour V1)
- [ ] Si absent : ajouter `openMenu(player, menuId, args)` dans `KguiPlugin.java`
- [ ] **Fichiers concernés** : `Kgui/src/main/java/me/krunsh/kgui/KguiPlugin.java`, `ContentProviderManager.java`, `DynamicItem.java`

**Pourquoi bloquant** : KjobsUltimate délègue TOUS ses GUI à Kgui. Sans API compatible, on ne peut pas ouvrir les menus.

---

### A4. Kchat — Vérifier conflit Tab Header/Footer

- [ ] Chercher `PlayerListHeaderFooter` dans tous les fichiers Java de Kchat
- [ ] Si trouvé : ajouter `tab.header_footer.enabled: true` dans `Kchat/config.yml` + méthode `disableTabHeaderFooter()` dans `KchatPlugin.java`
- [ ] Si non trouvé : aucune action requise, pas de conflit
- [ ] **Fichiers concernés (si nécessaire)** : `KchatPlugin.java`, `ConfigManager.java` (Kchat)

**Pourquoi important** : Éviter que Kchat et KjobsUltimate se battent pour le header/footer du tab.

---

### A5. Kcraft — Ajouter `KcraftCraftCompleteEvent`

- [ ] Créer `Kcraft/src/main/java/me/krunsh/kcraft/events/KcraftCraftCompleteEvent.java` (voir INTEGRATION-MAP.md section 7)
- [ ] Ajouter l'appel `Bukkit.getPluginManager().callEvent(craftEvent)` dans `CraftGUIListener.handleCraftClick()`
- [ ] Ajouter l'appel dans `CraftGUIListener.handleMassCraft()` (avec la quantité réelle)
- [ ] Ajouter l'appel dans `VanillaCraftListener` pour les recettes avec `allowVanillaWorkbench: true`
- [ ] Tester : crafter une recette Kcraft → event reçu dans KjobsUltimate → XP donné
- [ ] **Fichiers concernés** : `CraftGUIListener.java`, `VanillaCraftListener.java`, nouveau `KcraftCraftCompleteEvent.java`

**Pourquoi bloquant** : Sans cet événement, `ArtisanListener` ne peut pas détecter les crafts Kcraft.

---

## PARTIE B — Décisions de Design Encore Ouvertes

> Ces décisions doivent être prises AVANT de coder les modules concernés.

---

### B1. Quêtes — Attribution

- [x] **Décidé** : **3 quêtes daily aléatoires tirées du pool** ✅ — Pool fixe de quêtes configurées par job. 3 sont tirées au sort pour l'attribution daily du joueur. 5 pour le weekly. Les permanentes (20-30) sont toutes visibles.

### B2. Quêtes — Système de Difficulté

- [x] **Décidé** : **Facile / Avancé / Difficile** ✅ — Pas de rareté. 3 niveaux de difficulté avec couleurs (vert/or/rouge). Configurable dans chaque quête YAML. Voir QUETES-SYSTEM.md section 3.

### B3. Hunter — Mobs de spawners vs mobs sauvages

- [x] **Décidé** : **Même XP** ✅ — Pas de réduction pour les mobs issus de spawners (Kspawners).

### B4. Artisant — Items donnant de l'XP

- [x] **Décidé** : **Liste définie dans `artisant.yml` via 2 sections** ✅ — `vanilla_actions` (Material → XP) pour les crafts table vanilla, `kcraft_actions` (recipeId Kcraft → XP) pour les crafts Kcraft custom. Liste entièrement configurable. Voir INTEGRATION-MAP.md section 7.

### B5. Prétorien — Conditions PvP

- [x] **Décidé** : **Tout kill PvP + items consommables** ✅ — XP pour tout kill joueur + XP pour consommation d'items de combat (pomme d'or, joints...). Liste `consume_list` dans `pretorien.yml` avec support CIT (NBT + texture). Voir CONFIG-FICHIERS-STRUCTURE.md.

### B6. Bossbar — Comportement multi-job

- [ ] **Confirmer** : 1 seule bossbar affichant le `displayJob`, changeable via `/jobs select <job>`. (Confirmé en décision mais à valider avant de coder `BossBarManager`)

### B7. Niveau Max Global

- [ ] **Décider** : Le niveau max est-il le même pour tous les jobs (ex: 50) ou configurable par job ?
  - Décision actuelle : configurable par job (`max_level` dans chaque `jobs/<jobId>.yml`)

### B8. Quêtes Chaînes

- [ ] **Décider** : Une quête permanente terminée peut-elle en débloquer une autre automatiquement ? Pour V1, ou reporté ?

### B9. Prestige / Reborn

- [ ] **Confirmer** : Reporté en V2. Aucune architecture à prévoir en V1 ? (Ou garder un champ `prestige_count` vide dans la DB pour pas avoir à migrer plus tard ?)

---

## PARTIE C — Setup Technique KjobsUltimate

> A faire dans l'ordre une fois les parties A et B terminées.

---

### C1. Créer le Projet Maven

- [ ] Copier le `pom.xml` de Kchat comme base
- [ ] Changer `artifactId: KjobUltimate`, `groupId: me.krunsh.kjobultimate`
- [ ] Ajouter la dépendance `sqlite-jdbc` (scope: compile, shaded)
- [ ] Ajouter les dépendances provided : SpigotAPI 1.8.8, Vault, PlaceholderAPI
- [ ] Configurer maven-shade-plugin pour le sqlite-jdbc
- [ ] Créer `plugin.yml` avec : name, version, main, commands, depend, softdepend

```yaml
# plugin.yml
name: KjobUltimate
version: 1.0.0
main: me.krunsh.kjobultimate.KjobUltimate
api-version: 1.8
depend: []
softdepend: [Vault, PlaceholderAPI, Kchat, Kgui, Kstacker, Kenchantement, Kcraft]
commands:
  # Commandes joueurs
  job:
    description: Ouvre le menu des jobs
    usage: /job [quests|hud|info <job>]
  jobs:
    description: Alias de /job
    usage: /jobs [quests|hud|info <job>]
    # /jobs hud = toggle HUD ON/OFF (bossbar + actionbar)
  # Commandes admin (toute commande avec k devant job)
  kjob:
    description: Administration des jobs
    usage: /kjob <addxp|removexp|setlvl|reset|seejobs|spy|addjob|bonus|migrate|questreset|questgive|cap|reload>
    permission: kjob.admin
  kjobs:
    description: Alias admin de /kjob
    permission: kjob.admin
  kjobsultimate:
    description: Alias admin de /kjob
    permission: kjob.admin
  kjobultimate:
    description: Alias admin de /kjob
    permission: kjob.admin
permissions:
  kjob.admin:
    description: Accès aux commandes admin
    default: op
  kjob.xp.vip:
    description: Multiplicateur XP VIP (x1.25)
    default: false
  kjob.xp.premium:
    description: Multiplicateur XP Premium (x1.5)
    default: false
```

### C2. Créer la Structure de Packages (tous vides)

- [ ] `me.krunsh.kjobultimate.KjobUltimate` (main)
- [ ] `me.krunsh.kjobultimate.api`
- [ ] `me.krunsh.kjobultimate.config`
- [ ] `me.krunsh.kjobultimate.jobs`
- [ ] `me.krunsh.kjobultimate.quests`
- [ ] `me.krunsh.kjobultimate.data`
- [ ] `me.krunsh.kjobultimate.hud`
- [ ] `me.krunsh.kjobultimate.scoreboard`
- [ ] `me.krunsh.kjobultimate.slots`
- [ ] `me.krunsh.kjobultimate.listeners`
- [ ] `me.krunsh.kjobultimate.gui`
- [ ] `me.krunsh.kjobultimate.commands`
- [ ] `me.krunsh.kjobultimate.hooks`
- [ ] `me.krunsh.kjobultimate.util`

### C3. Créer Tous les Fichiers de Config par Défaut (templates)

- [ ] `config.yml` (voir CONFIG-FICHIERS-STRUCTURE.md)
- [ ] `messages.yml`
- [ ] `sounds.yml`
- [ ] `hud.yml`
- [ ] `tab.yml`
- [ ] `jobs/mineur.yml`
- [ ] `jobs/farmer.yml`
- [ ] `jobs/hunter.yml`
- [ ] `jobs/pretorien.yml`
- [ ] `jobs/artisant.yml`
- [ ] `quests/quests_mineur.yml`
- [ ] `quests/quests_farmer.yml`
- [ ] `quests/quests_hunter.yml`
- [ ] `quests/quests_pretorien.yml`
- [ ] `quests/quests_artisant.yml`

Ces fichiers seront placés dans `src/main/resources/` et copiés avec `saveResource()` si absents.

### C4. Initialiser le Schéma SQLite

- [ ] Écrire `SQLiteStorage.java` avec les `CREATE TABLE IF NOT EXISTS` (voir DONNEES-JOUEUR-SCHEMA.md)
- [ ] Tester : `onEnable` → DB créée → tables créées sans erreur
- [ ] Tester : redémarrage → DB existante → tables inchangées (IF NOT EXISTS)

---

## PARTIE D — Ordre de Développement des Phases

> Une fois A, B, C terminés, suivre cet ordre strictement.

| # | Phase | Condition | Livrable test |
|---|---|---|---|
| 1 | Config Loaders + Modèles | C1+C2+C3 faits | Plugin démarre, charge les jobs, log en console |
| 2 | SQLite + PlayerData | Phase 1 OK | Join → données chargées. Quit → données sauvées |
| 3 | Job Slot System | Phase 2 OK | Premier join → GUI sélection. Level 5 → slot 2 débloqué |
| 4 | Listeners XP + Anti-Abuse | A1+A2 OK, Phase 3 OK | Miner → XP dans DB. SilkTouch → pas d'XP |
| 5 | HUD BossBar + ActionBar | Phase 4 OK | Bossbar visible. +Xxp en hotbar |
| 6 | Level Up (Achievement+Son+Title) | Phase 5 OK | Level up → popup + son + titre |
| 7 | GUI via Kgui | A3 OK, Phase 4 OK | `/jobs` → GUI ouvert, 5 jobs visibles |
| 8 | Système de Quêtes | Phase 4+7 OK | Quêtes progressent. Claim dans GUI fonctionne |
| 9 | Tab Header/Footer | A4 OK, Phase 2 OK | Tab affiche job + niveau + argent |
| 10 | Commandes Admin | Phase 2+4 OK | `/kjobadmin givexp`, `setlevel`, `reload` |
| 11 | PAPI + Vault Rewards | Phase 6 OK | `%kjob_level_mineur%` fonctionnel. Money donné au level up |
| 12 | Migration KJob2 | Phase 2 OK | `/kjobadmin migrate-kjob2` importe les données |
| 13 | Tests Complets + Polish | Toutes phases OK | 0 bug bloquant, config rechargeable à chaud |

---

## PARTIE E — Ressource Pack (Parallèle au développement)

> Peut être préparé en parallèle, pas bloquant pour le code.

- [ ] Créer/mettre à jour le resource pack SparrowMC avec :
  - Icônes des 5 jobs (CIT via NBT `sparrowmc-item`) pour les GUI
  - Texture custom `horse.png` pour le GUI cheval (si utilisé)
  - Override des textes d'achievements dans `assets/minecraft/lang/fr_FR.lang`
  - Sons custom si utilisés

---

## Récapitulatif des Dépendances en Arbre

```
KjobsUltimate peut être codé quand :
├── A1 (Kenchantement Extra-Kill) ✓
│     └── requis par → Phase 4 (HunterListener)
├── A2 (Kstacker Extra-Kill) ✓
│     └── requis par → Phase 4 (HunterListener)
├── A3 (Kgui API) ✓
│     └── requis par → Phase 7 (GUI)
├── A4 (Kchat vérification) ✓
│     └── requis par → Phase 9 (Tab)
├── B1-B9 (décisions design) ✓
│     └── requis avant → phases concernées
└── C1-C4 (setup technique) ✓
      └── requis par → Phase 1 (Config Loaders)
```
