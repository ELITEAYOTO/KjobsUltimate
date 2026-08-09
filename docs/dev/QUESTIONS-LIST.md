# KjobUltimate — Liste Exhaustive des Questions Avant Développement

> Chaque question doit avoir une réponse avant de coder la section concernée.
> Statut : [ ] = sans réponse | [x] = répondu | [?] = à vérifier en test

---

## 1. Général / Technique

- [x] **Version serveur cible** : KhopeSpigot (PandaSpigot 1.8.8) confirmé ✅ Java 8.
- [x] **Vault obligatoire** : Hook soft — le plugin fonctionne sans Vault, rewards money ignorées si absent.
- [x] **PlaceholderAPI** : Hook soft — activé automatiquement si PAPI est présent.
- [x] **Stockage données joueurs** : **SQLite** ✅ — 1 fichier `data/kjobultimate.db`, WAL mode, driver sqlite-jdbc shadé dans le jar. Migration KJob2 via `/kjobadmin migrate-kjob2`. Voir DONNEES-JOUEUR-SCHEMA.md.
- [x] **Intégration Kgui** : **Kgui via ContentProviderAPI** ✅ — GUI délégués à Kgui. Providers `kjob_quests_list` + `kjob_jobs_overview` enregistrés au démarrage. Voir INTEGRATION-MAP.md.
- [x] **Intégration Kfaction** : **Non en V1** ✅ — Pas de lien entre Kfaction et KjobsUltimate. Reporté V2 si nécessaire. Kfaction n'est pas une dépendance de KjobsUltimate.
- [x] **Multi-serveur / BungeeCord** : Non prévu en V1 — SQLite local uniquement.
- [x] **AutoSave** : Intervalle configurable (`storage.autosave_interval`), async. Crash = perte max 1 intervalle.
- [x] **Commande admin reload** : `/kjobadmin reload` recharge tous les fichiers à chaud ✅
- [x] **Compatibilité WorldGuard** : **Non inclus en V1** ✅ — Désactivé. Pas de dépendance WorldGuard. Peut être ajouté ultérieurement via soft hook.

---

## 2. Jobs — Définition

- [x] **Jobs définitifs** : **5 jobs en V1** ✅ — Mineur, Hunter, Prétorien, Farmer, Artisant. Le système est extensible (ajout d'un job = 1 fichier YAML + 1 Listener), mais aucun job supplémentaire n'est prévu en V1. Voir EDGE-CASES.md §13.
- [x] **Nombre max de jobs par joueur** : **Système de slots configurable** ✅ — Défaut: 1 slot. Déblocage progressif (slot 2 au level 5 du job principal, etc.). Désactivable (`job_slots.enabled: false` = tous les jobs actifs). Voir JOB-SLOTS-SYSTEM.md.
- [x] **Jobs par défaut au premier join** : **Configurable** ✅ — Défaut SparrowMC: `default_all_jobs: false` → joueur choisit son premier job (GUI s'ouvre au premier join). Option `true` = tous les jobs assignés auto.
- [x] **Niveau max par job** : **Individuel par job** ✅ — `max_level` dans chaque `jobs/<jobId>.yml`. Défaut: 50.
- [x] **Prestige / Reborn** : Reporté en V2 ✅ — Champ `prestige_count` réservé dans la DB SQLite pour migration facile plus tard.
- [x] **XP cap quotidien** : **Configurable par job** ✅ — `daily_xp_cap: 0` (désactivé) dans chaque `jobs/<jobId>.yml`. Reset à minuit. Voir DONNEES-JOUEUR-SCHEMA.md.
- [x] **Multiplicateurs XP** : **3 types** ✅ — Permissions (`kjob.xp.vip: 1.25`), événement serveur (`/kjobadmin event 2.0`), pas de malus par niveau en V1. Voir CONFIG-FICHIERS-STRUCTURE.md → `xp_multipliers`.
- [x] **Job "Hunter"** : Mobs uniquement (EntityDeathEvent) ✅ — Le PvP est géré par le job Prétorien séparément.
- [x] **Job "Hunter" — Mobs spawners** : **Même XP** ✅ — Pas de réduction pour les mobs issus de spawners (Kspawners). Voir ANTI-ABUSE.md pour la protection anti-farm.
- [x] **Job "Prétorien"** : XP via **plusieurs sources configurables** ✅ — (1) Kill PvP (`PlayerDeathEvent`), (2) Consommation d'items spéciaux (`PlayerItemConsumeEvent`). Liste `consume_list` dans `jobs/pretorien.yml` avec support CIT (NBT). Cooldown anti-abuse par UUID cible. Voir CONFIG-FICHIERS-STRUCTURE.md.
- [x] **Job "Artisant"** : XP via crafts définis dans `jobs/artisant.yml` ✅ — 2 sections : `vanilla_actions` (Material → XP, CraftItemEvent) + `kcraft_actions` (recipeId Kcraft → XP, via hook Kcraft). La liste est entièrement configurable. Voir INTEGRATION-MAP.md A5.
- [x] **Partage XP en groupe** : Pas de partage en V1 ✅ — Chaque joueur gagne son XP indépendamment.

---

## 3. Quêtes

- [x] **Attribution des quêtes** : **3 aléatoires depuis pool fixe** ✅ — Chaque joueur reçoit 3 quêtes daily tirées aléatoirement dans le pool, et 5 weekly. Les permanentes (20-30 par job) sont toutes visibles. Voir QUETES-SYSTEM.md.
- [x] **Système de difficulté** : **Facile / Avancé / Difficile** ✅ — Pas de rareté, mais une difficulté croissante avec couleurs (`§a` vert / `§6` or / `§c` rouge). Voir QUETES-SYSTEM.md.
- [x] **Quantités par job** : **3 daily + 5 weekly + 20-30 permanentes** ✅
- [x] **Types de quêtes disponibles** : mining, farming, hunting, pvp, crafting, fishing — pas d'exploration ni custom en V1 ✅
- [x] **Lien quête ↔ job** : Toujours liée à 1 seul job ✅ (`job: mineur` dans la config quête)
- [x] **Reset des quêtes** : **24H à partir du join du job** ✅ — Pas de reset à minuit fixe. Le timer est par joueur par job, ancré sur `job_join_timestamp`. Reset daily = toutes les 24H depuis ce timestamp. Reset weekly = toutes les 7×24H.
- [x] **Prérequis de niveau** : Oui, `min_level` dans chaque quête ✅
- [x] **Claim physique** : XP immédiat à la complétion. Items/money/cmds → clic dans le GUI pour claim. Inventaire plein → drop au sol ✅
- [x] **Quêtes en chaîne** : **Reporté V2** ✅ — Non implémenté en V1.
- [x] **Quêtes de groupe** : **Non en V1** ✅ — Reporté V2.

---

## 4. HUD — BossBar

- [x] **Couleur de la bossbar** : **Configurable par job** ✅ — `bossbar.color` dans chaque `jobs/<jobId>.yml` (BLUE, GREEN, RED, PURPLE, YELLOW...). Voir CONFIG-FICHIERS-STRUCTURE.md.
- [x] **Style bossbar** : **Configurable par job** ✅ — `bossbar.style` dans `jobs/<jobId>.yml` (SOLID, SEGMENTED_6, SEGMENTED_10...). Défaut : SEGMENTED_10.
- [x] **Toujours visible ou disparaît** : **Disparaît après timer configurable** ✅ — `bossbar_timing_reset: 7` (secondes) dans `hud.yml`. Si le joueur n'a gagné d'XP dans AUCUN job pendant X secondes, la bossbar disparaît. Elle réapparaît instantanément au prochain gain d'XP. Voir HUD-BOSSBAR-ACTIONBAR.md.
- [x] **Un job actif ou plusieurs** : **Toujours UNE seule bossbar** ✅ — La bossbar affiche TOUJOURS le **dernier job ayant donné de l'XP**. Pas de sélection manuelle. Si le joueur mine ET fait un craft, la bossbar bascule automatiquement vers le job du dernier gain. Voir HUD-BOSSBAR-ACTIONBAR.md section 1.
- [x] **Job affiché sélectionnable** : **Non — automatique** ✅ — `displayJob` = dernière jobId ayant produit du XP. Pas de commande `/jobs select` pour la bossbar. Le clic droit dans le GUI ne change pas la bossbar (ancienne doc corrigée).
- [x] **Format du titre bossbar** : **Configurable** ✅ — `bossbar.title_format` dans `hud.yml`. Placeholders : `{job}`, `{level}`, `{xp}`, `{xp_next}`, `{percent}`. Voir CONFIG-FICHIERS-STRUCTURE.md → hud.yml.
- [x] **Toggle HUD joueur** : **`/jobs hud`** ✅ — Toggle ON/OFF pour bossbar + actionbar. Retour en chat. Accessible aussi dans le GUI `/jobs`. Persistant (stocké dans PlayerData). Voir ARCHITECTURE-GLOBALE.md.

---

## 5. HUD — ActionBar

- [x] **Comportement accumulation XP** : **Configurable** ✅ — `accumulate: true/false` dans `hud.yml → actionbar`. Si `true` : gains accumulés dans la fenêtre affichés comme un seul message `+Xxp`. Si `false` : chaque gain affiché séparément (remplace le précédent). L'ActionBar affiche toujours le **dernier job ayant donné de l'XP** (même logique que la BossBar). Voir HUD-BOSSBAR-ACTIONBAR.md section 2.
- [x] **Durée d'affichage** : **Configurable** ✅ — `display_duration: 3` (secondes) dans `hud.yml → actionbar`. Le message disparaît X secondes après le dernier gain d'XP.
- [x] **Format configurable** : **Oui** ✅ — `format: "§a+{xp}xp §7({job})"` dans `hud.yml → actionbar`. Placeholders : `{xp}`, `{job}`. Voir CONFIG-FICHIERS-STRUCTURE.md → hud.yml.
- [x] **Priorité vs autres plugins** : **ActionBar simple, pas de queue** ✅ — KjobsUltimate envoie ses messages actionbar normalement. Si Kfaction envoie une actionbar au même moment, le dernier envoyé gagne (comportement 1.8.8 standard). Acceptable car l'actionbar jobs est cosmétique (pas critique). Pas de queue en V1.

---

## 6. Achievement / Notification de Level Up

- [x] **Achievement à chaque niveau** ✅ — Popup à chaque level up, configurable (`achievement_popup.enabled`)
- [x] **Item affiché dans le popup** : Item différent par job (icon du job) via CIT ✅
- [x] **Text du popup** : Configurable par job dans `messages.yml` ✅ — Placeholders {job}, {level}
- [x] **Title/Subtitle en complément** : Oui, configurable (`title_levelup.enabled`) ✅
- [x] **Son configurable par job** : Oui, dans `sounds.yml → levelup.<jobId>` ✅
- [x] **Config par niveau** : **Non en V1** ✅ — Pas d'effets spéciaux spécifiques à des niveaux paliers. Tous les niveaux déclenchent le même séquencement (popup + title + son). Les récompenses configurées dans `level_rewards.<N>` de `jobs/<jobId>.yml` permettent déjà d'exécuter des commandes particulières à certains niveaux (ex: firework via /firework). Reporté en V2.

---

## 7. Sons Custom

- [x] **Déclaration des sons** : Dans `sounds.yml` séparé ✅ — Volume + pitch configurables
- [x] **Événements sonores** : levelup, quest_complete, quest_claim, slot_unlocked, xp_gain (désactivé par défaut) ✅
- [x] **Son par job** : Oui, `sounds.yml → levelup.<jobId>`, fallback sur `default` ✅

---

## 8. Scoreboard / Tab

- [x] **Décision architecturale** : **Option A — Module dans KjobUltimate** ✅ — Header/footer NMS géré par KjobsUltimate. Kchat gère les équipes/prefixes (pas de conflit). Hook opt pour désactiver header/footer Kchat si besoin. Voir SCOREBOARD-TAB.md.
- [x] **Header/Footer** : Configurable multilignes avec placeholders dans `tab.yml` ✅
- [x] **Refresh rate** : Toutes les 40 ticks (configurable `tab.update_interval`) ✅

---

## 9. GUI — Interface Graphique

- [x] **Kgui** : GUI délégués à Kgui ContentProviderAPI ✅ — Providers enregistrés au démarrage. Voir INTEGRATION-MAP.md.
- [x] **GUI global** : 54 slots, 5 jobs avec progression. Voir GUI-VUE-GLOBALE.md ✅
- [x] **Items interactifs** : Clic gauche = ouvrir détail ✅ (le clic droit ne sélectionne PAS pour la bossbar — displayJob est automatique)
- [x] **GUI quêtes** : Paginé via Kgui, filtrable par job ✅
- [x] **Claim** : Items/money/cmds = clic dans GUI. XP = immédiat ✅

---

## 10. Anti-Abuse

- [x] **Silk Touch** : Bloquer XP uniquement sur les blocs marqués `silktouch: true` dans la config job ✅
- [x] **Cultures immatures** : Oui, via `CropUtil.isMature()` pour tous les types ✅
- [x] **Fortune** : Pas de bonus XP en V1 — XP fixe par action ✅
- [x] **Cooldown par position** : HashMap<String, Long> "x,y,z,world" → timestamp expiration ✅ — Configurable (`block_position_cooldown: 300s`)
- [x] **Mode créatif/spectateur** : Bloqué ✅ — Configurable (`block_creative`, `block_spectator`)
- [x] **XP Cap daily** : Configurable par job, désactivé par défaut ✅
- [x] **Prétorien PvP anti-abuse** : Cooldown par UUID cible ✅ — Configurable (`pvp_target_cooldown: 60s`)
- [x] **AFK Farm mobs** : **Cooldown par EntityUUID** ✅ — Chaque EntityUUID enregistré dans une Map avec timestamp. Si la même entité est tuée deux fois (spawner régénère le même UUID parfois) dans la fenêtre, pas d'XP la 2ème fois. Config : `mob_entity_cooldown: 30` (secondes, 0 = désactivé) dans `anti_abuse.yml`. Désactivé par défaut.
- [x] **Mobs de spawners** : **Même XP que les mobs naturels** ✅ — Pas de réduction en V1. La protection anti-farm est gérée via `mob_entity_cooldown` par EntityUUID. Voir ANTI-ABUSE.md.

---

## 11. Performance & Scalabilité

- [x] **Cible confirmée** : **600 joueurs simultanés** ✅ — Cible SparrowMC. L'architecture intègre des optimisations spécifiques pour cette charge (cleanup cooldowns, spy on-demand, wither skip si HUD invisible). Voir EDGE-CASES.md §18.
- [x] **Données en RAM** : **Oui, toutes les données des joueurs connectés en RAM** ✅ — `ConcurrentHashMap<UUID, PlayerData>`. Flush async à la déconnexion et autosave périodique. Voir DONNEES-JOUEUR-SCHEMA.md.
- [x] **Scheduler global** : **1 seul BukkitRunnable pour tous les joueurs** ✅ — Boucle sur tous les joueurs connectés toutes les 40 ticks. Inclut cleanup `blockCooldowns`/`mobCooldowns` expirés toutes les 60 ticks. Voir EDGE-CASES.md §18.
- [x] **Dirty flag bossbar** : **Dirty flag** ✅ — Bossbar mise à jour uniquement quand `dirty=true` (positionné dans `onXpGain`). Timer disparition géré via `lastXpTimestamp`. Pas de mise à jour inutile toutes les X ticks.
- [x] **Stockage quêtes** : **SQLite (table `quest_progress`)** ✅ — 1 row par (joueur, questId). Voir DONNEES-JOUEUR-SCHEMA.md.
- [x] **Cache en mémoire** : **Oui** ✅ — Configs jobs/quêtes chargées au `onEnable()` et au `/kjob reload`. Jamais relues pendant le jeu.
- [x] **Log spam** : **Debug désactivé par défaut** ✅ — `debug: false` dans `config.yml`. Modes granulaires : `debug_xp`, `debug_quest`, `debug_hud`. Aucun log en production par défaut.
