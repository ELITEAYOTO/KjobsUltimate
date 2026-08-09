# KjobsUltimate — Avancement de Développement

## Journal recent - 04/07/2026

### HUD / Bossbar 1.8 debug
- [x] Diagnostic bossbar: l'ancien fake Wither etait place a `Y + 1000`, donc hors range client 1.8 et probablement invisible cote joueur.
- [x] Fix bossbar: position configurable proche du joueur via `bossbar.entity_offset_y` (defaut `-30.0`).
- [x] Fix suivi joueur: envoi `PacketPlayOutEntityTeleport` au refresh si `bossbar.follow_player=true`.
- [x] Option debug/test: `bossbar.invisible_entity` configurable, true par defaut maintenant que le profil invisible est valide.
- [x] Option visuelle: `bossbar.minimum_progress` configurable, defaut `0.05`.
- [x] Fix logique: `bossbar_timing_reset: 0` signifie maintenant bossbar persistante au lieu de disparition immediate.
- [x] Alternative bossbar ajoutee: `bossbar.entity_type: WITHER|ENDER_DRAGON` avec `bossbar.max_health`.
- [x] Achievement level-up: `achievement.mode` ajoute, defaut `TITLE_AND_CHAT`.
- [x] Title packet: envoi optionnel `RESET` avant `TIMES/TITLE/SUBTITLE` via `achievement.reset_before_send`.
- [x] Diagnostic bossbar avance: `bossbar.position_mode` ajoute (`BELOW`, `ABOVE`, `FRONT`, `EYE_FRONT`, `PLAYER`).
- [x] `/kjobs testhud` accepte des variantes temporaires: `wither|dragon`, `below|above|front|eye_front|player`, `visible|invisible`.
- [x] Logs HUD enrichis: type, maxHealth, positionMode, position, visibilite et health/maxHealth.
- [x] Bossbar 1.8 validee serveur: `WITHER + FRONT + invisible` affiche la bossbar sans mob visible.
- [x] Profil prod HUD applique dans `hud.yml`: `position_mode: FRONT`, `entity_forward_offset: 24.0`, `invisible_entity: true`.
- [x] `/kjobs testhud` auto-cache la bossbar de test via `bossbar.test_duration_seconds`.
- [x] Settings HUD separes: master HUD, BossBar seule, ActionBar seule.
- [x] DB joueurs migree: `bossbar_enabled` et `actionbar_enabled` persistants en SQLite/MySQL.
- [x] Commandes ajoutees: `/jobs hud bossbar`, `/jobs hud actionbar`, `/jobs hud on`, `/jobs hud off`.
- [x] Fix ActionBar OFF: cache interne purge + paquet ActionBar vide envoye au joueur.
- [x] Achievement level-up: `force_chat_fallback` ajoute pour garantir un message chat meme avec un ancien `hud.yml`.
- [x] Achievement toast 1.8 valide: le mode `BUKKIT` affiche le toast, le mode `PACKET` reste experimental car le client peut ignorer l'affichage.
- [x] `achievement.vanilla_toast.method` ajoute: `BUKKIT`, `PACKET`, `PACKET_THEN_BUKKIT`.
- [x] Mapping achievement par job configurable dans `hud.yml` pour choisir l'icone vanilla.
- [x] Documentation ajoutee : [docs/systemes/HUD-ACHIEVEMENT-TOAST-1-8.md](docs/systemes/HUD-ACHIEVEMENT-TOAST-1-8.md).

### GUI interne configurable - base Kgui-like
- [x] `GuiManager` supporte maintenant `slot` et `slots`.
- [x] `slots` accepte les formats `0-8`, `0-8,45-53`, listes YAML et nombres uniques.
- [x] Les items acceptent `name` ou `display_name`.
- [x] Actions YAML ajoutees : `click_actions`, `left_click_actions`, `right_click_actions`, `shift_click_actions`, `shift_left_click_actions`, `shift_right_click_actions`, `middle_click_actions`.
- [x] Actions internes supportees : `[open]`, `[back]`, `[close]`, `[message]`, `[player]`, `[joueur]`, `[console]`, `[command]`, `[sound]`, `[refresh]`, `[favorite]`, `[unlock]`, `[join]`, `[leave_confirm]`, `[leave_confirmed]`, `[toggle_hud]`, `[hud_toggle]`.
- [x] Les menus par defaut `gui/home.yml` et `gui/jobs.yml` contiennent maintenant des actions explicites.
- [x] Fallback conserve : si un bouton n'a aucune action YAML, l'ancien handler interne continue de fonctionner.
- [x] Validation au chargement/reload : material inconnu, slot invalide et action inconnue log en console.
- [x] Documentation ajoutee : [docs/gui/GUI-CONFIG-ACTIONS.md](docs/gui/GUI-CONFIG-ACTIONS.md).

### Build
- [x] `mvn -q clean package` OK.
- [x] Jar genere : `target/KjobsUltimate-1.0.0-SNAPSHOT.jar`.

### Reste ouvert
- [x] Ajouter CIT/NBT direct dans les GUI internes pour se rapprocher encore plus de Kgui.
- [x] Ajouter requirements/cooldowns par item si utile.
- [ ] Continuer la refonte Tab vers un format ultra configurable : header/footer existe deja, mais il faut encore structurer staff/economie/jobs/joueurs.

### Tab configurable V2
- [x] `TabManager` supporte maintenant un mode sections via `sections.enabled`.
- [x] `tab.yml` remplace l'ancien fichier par une version propre et documentee.
- [x] Sections configurables pour `header` et `footer`.
- [x] Conditions par section : `always`, `has_jobs`, `no_jobs`, `staff_online`, `no_staff_online`, `vault`, `staff_group_online:<id>`.
- [x] Permission optionnelle par section.
- [x] Placeholders natifs ajoutes : `%vault_balance%`, `%vault_balance_raw%`, `%staff_online%`, `%staff_count%`, `%kjob_active_jobs_inline%`, `%kjob_active_jobs_lines%`.
- [x] Snapshot staff calcule une fois par tick Tab.
- [x] Groupes staff configurables via `staff_groups` : admin/modo/helper ou autres permissions.
- [x] Placeholders groupes staff : `%staff_groups_lines%`, `%staff_groups_inline%`, `%staff_<id>_online%`, `%staff_<id>_count%`.
- [x] `placeholderapi_per_player` permet de couper PAPI pour economiser de la charge.
- [x] Permission `kjobsultimate.staff` ajoutee et incluse dans `kjobsultimate.admin`.
- [x] Documentation ajoutee : [docs/systemes/TAB-CONFIG-SECTIONS.md](docs/systemes/TAB-CONFIG-SECTIONS.md).

### Tab player-list name optionnel
- [x] Ajout de `player_list_name.enabled` dans `tab.yml`.
- [x] Desactive par defaut pour eviter les conflits avec Kchat/scoreboard.
- [x] Support format joueur et format staff : `format`, `staff_format`.
- [x] Permission staff configurable : `staff_permission`.
- [x] Troncature legacy 1.8 configurable avec `truncate_to_legacy_limit` et `max_length`.
- [x] Reset propre des noms quand l'option est desactivee ou quand le plugin s'arrete.

### Correctifs status / validation
- [x] `/kjobs status` parse maintenant les couleurs injectees par placeholders (`{gui_status}`, `{hud_status}`, `{tab_status}`, `{storage_status}`).
- [x] `/kjobs status` affiche les details Tab : interval, sections, player-list names, PAPI par joueur.
- [x] Validation Tab au reload : conditions inconnues et sections vides log en console.

### Tab virtuel - colonnes fake entries
- [x] Audit ServerNPC decompile ajoute : `C:\Users\timot\Desktop\npc-plugin\SERVERNPC-AUDIT.md`.
- [x] Documentation objectif ajoutee : [docs/systemes/TAB-VIRTUAL-COLONNES-OBJECTIF.md](docs/systemes/TAB-VIRTUAL-COLONNES-OBJECTIF.md).
- [x] `VirtualTabManager` ajoute, desactive par defaut via `virtual_layout.enabled: false`.
- [x] Config `virtual_layout` ajoutee dans `tab.yml` : colonnes, widths, bottom_lines, prefix technique, max_rows, interval.
- [x] Packets NMS 1.8 `PacketPlayOutPlayerInfo` ajoutes via reflection : `ADD_PLAYER`, `UPDATE_DISPLAY_NAME`, `REMOVE_PLAYER`.
- [x] Cache/diff par viewer : add/update/remove seulement quand une ligne change.
- [x] Cleanup sur reload/disable via `remove_on_disable` et commandes admin.
- [x] Commandes debug ajoutees : `/kjobs tabdebug [joueur]`, `/kjobs tabrender [joueur]`, `/kjobs tabclear [joueur]`, `/kjobs tabclearall`.
- [x] `/kjobs status` affiche maintenant l'etat du tab virtuel.
- [x] Signature locale `patched_1.8.8.jar` verifiee avec `javap` pour `PacketPlayOutPlayerInfo$PlayerInfoData`.
- [x] Alignement pixel-width ajoute (`virtual_layout.render.pixel_alignment`) pour reduire le decalage des colonnes.
- [x] Largeurs colonnes agrandies via `width_pixels`.
- [x] `bottom_lines_enabled: false` ajoute pour eviter de transformer les infos footer en fake joueurs avec tetes.
- [x] Placeholders natifs ajoutes : `%kfaction_name%`, `%kfaction_role%`, `%kfaction_members%`, `%kfaction_members_lines%`, `%rank_name%`.
- [x] Limite documentee : les tetes a gauche des fake entries ne sont pas supprimables en tab vanilla 1.8.
- [ ] Test runtime en jeu avec `virtual_layout.enabled: true` sur serveur de dev.
- [ ] Si le client ignore le displayName en ADD/UPDATE, ajouter fallback scoreboard-team prefix/suffix.
- [ ] Ajouter un vrai hook rank/permissions si besoin au lieu du fallback permission simple.

### Classements DB /jobs top
- [x] Ajout de `RankingEntry` pour transporter une ligne de classement.
- [x] `DatabaseManager#getTop(jobId, limit)` compatible SQLite/MySQL.
- [x] `DatabaseManager#getRank(uuid, jobId)` compatible SQLite/MySQL.
- [x] `/jobs top`, `/jobs top global` et `/jobs top <job>` executent les requetes en async.
- [x] Cache RAM par filtre via `top.cache_seconds`.
- [x] Limite chat configurable via `top.chat_limit`, bornee a 50.
- [x] Messages top configurables dans `messages.yml`.
- [x] Le formatter de `/jobs` parse maintenant les couleurs apres remplacement des placeholders.
- [x] Documentation ajoutee : [docs/systemes/CLASSEMENTS-TOP.md](docs/systemes/CLASSEMENTS-TOP.md).
- [x] GUI top dynamique branche sur la meme couche DB.
- [x] Selecteur GUI global/jobs via `top.items.global`, `top.items.job` et `top.job_slots`.
- [x] Page de chargement GUI avant retour DB async.
- [x] Pagination GUI via `top.ranking.items.previous` et `top.ranking.items.next`.
- [x] Item de rang personnel via `top.ranking.items.own_rank`.
- [x] Action GUI ajoutee : `[top] global|{job_id}|previous|next|refresh|selector`.
- [x] `top.gui_limit` ajoute dans `config.yml`, borne a 50.
- [x] Formatter GUI corrige : couleurs parsees apres placeholders.
- [x] Les configs GUI chargees utilisent les defaults internes en memoire pour eviter les cles manquantes apres une ancienne generation.

### GUI items CIT / NBT
- [x] Les items GUI internes supportent maintenant `cit` et `cit_key`.
- [x] `cit`/`cit_key` ecrit le tag NBT `sparrowmc-item`, compatible avec le pattern Kgui/SparrowMC.
- [x] Les items GUI internes supportent `nbt:` key/value simples.
- [x] Types NBT supportes : string, int, long, bool, double.
- [x] `hide_attributes` supporte pour les items decoratifs/CIT.
- [x] Validation GUI : `cit` vide, `cit_key` vide, NBT imbrique non supporte.
- [x] Documentation mise a jour : [docs/gui/GUI-CONFIG-ACTIONS.md](docs/gui/GUI-CONFIG-ACTIONS.md).

### GUI click requirements / cooldowns
- [x] `click_requirements` supporte des conditions AND avant execution.
- [x] Requirements supportes : permission, no_permission, has_jobs, no_jobs, job_unlocked, job_locked, favorite_job, global_level_min, slots_min, level_min, level_max.
- [x] `deny_actions` execute des actions si les requirements echouent.
- [x] `cooldown` par joueur et par item, en secondes.
- [x] `cooldown_deny_actions` optionnel, fallback vers `deny_actions`.
- [x] Validation GUI : requirements inconnus, actions deny inconnues, cooldown negatif.
- [x] Defaults `home.yml` et `jobs.yml` renforces sur leave/confirm/unlock/favorite/toggle HUD.
- [x] Cooldowns GUI nettoyes a la deconnexion joueur.

### Quetes permanentes V1 - socle
- [x] Ajout de `quests.yml` avec quetes permanentes par job.
- [x] Ajout de `QuestDefinition` et `QuestManager`.
- [x] Chargement/reload des quetes via boot plugin et `/kjobs reload`.
- [x] Progression sauvegardee en DB via `quest_progress`.
- [x] `DatabaseManager` recharge `quest_progress` au join et sauvegarde les quetes au save joueur.
- [x] Progression branchee sur Mineur (`MINE`), Farmer (`HARVEST`), Hunter (`KILL`), Pretorien (`PVP_KILL`), Artisan (`CRAFT`), Pilleur (`TNT_EXPLODE`, `DYNAMITE_EXPLODE`, `TNT_CRAFT`, `DYNAMITE_CRAFT`).
- [x] Kcraft branche sur les quetes `CRAFT` et `DYNAMITE_CRAFT`.
- [x] API `QuestManager.claimReward(player, questId)` prete pour le GUI de claim.
- [x] GUI quetes branche dans `home.yml` avec pagination.
- [x] Action GUI `[quest_claim]` / `[claim_quest]` ajoutee.
- [x] Action GUI `[quests]` / `[quest]` ajoutee pour navigation quetes.
- [x] Raccourci joueur `/jobs quests` / `/jobs quetes` ouvre le GUI quetes.
- [x] Documentation GUI mise a jour pour les actions et placeholders quetes.
- [x] Commandes admin de test ajoutees : `/kjobs questcomplete <joueur> <questId>` et alias `/kjobs questgive`.
- [x] Commandes admin de reset ajoutees : `/kjobs questreset <joueur> <questId|all>` et alias `/kjobs resetquest`.
- [x] Les commandes admin rendent une quete claimable/reset mais ne claim pas les rewards, qui restent recuperables uniquement via GUI.
- [x] Filtres GUI quetes par job ajoutes : `filter_all`, `filter_job`, templates selectionnes et `filter_job_slots`.
- [x] Listener quetes generiques ajoute : `EAT`, `CONSUME`, `SMELT`, `FISH`, `FISH_ENTITY`, `ENCHANT`, `ENCHANT_LEVELS`, `PLACE`, `TAME`.
- [x] Validation reload des quetes : type inconnu et target probablement invalide en warning console.
- [x] Documentation actuelle ajoutee : [docs/systemes/QUETES-CONFIG-V1.md](docs/systemes/QUETES-CONFIG-V1.md).

> Fichier de suivi en temps réel. Mis à jour à chaque étape complétée.
> Architecture complète → [docs/architecture/ARCHITECTURE-GLOBALE.md](docs/architecture/ARCHITECTURE-GLOBALE.md)
> Plan d'implémentation → [docs/dev/PLAN-IMPLEMENTATION.md](docs/dev/PLAN-IMPLEMENTATION.md)

---

## 📊 État Global

```
Phase 0  — Préparation terrain          ████████████████████ 100%  ✅ TERMINÉ
Phase 1  — Core (Maven + DB + Config)   ████████████████████ 100%  ✅ TERMINÉ
Phase 2  — Système jobs de base         ████████████████████ 100%  ✅ TERMINÉ
Phase 3  — XP + Levels                  ████████████████████ 100%  ✅ TERMINÉ
Phase 4  — Listeners métiers            ████████████████████ 100%  ✅ TERMINÉ
Phase 5  — HUD (bossbar + actionbar)    ░░░░░░░░░░░░░░░░░░░░   0%  ← SUIVANT
Phase 6  — Anti-abuse + données         ░░░░░░░░░░░░░░░░░░░░   0%
Phase 7  — GUI Kgui                     ░░░░░░░░░░░░░░░░░░░░   0%
Phase 8  — Commandes + permissions      ░░░░░░░░░░░░░░░░░░░░   0%
Phase 9  — Scoreboard + Tab             ░░░░░░░░░░░░░░░░░░░░   0%
Phase 10 — Quêtes                       ░░░░░░░░░░░░░░░░░░░░   0%
Phase 11 — Tests + déploiement          ░░░░░░░░░░░░░░░░░░░░   0%
```

---

## ✅ Phase 0 — Préparation du terrain (Hooks + Doc)

### Documentation (terminée)
- [x] Architecture globale rédigée
- [x] Schéma BDD documenté (4 tables : `job_data`, `job_slots`, `quest_data`, `player_money`)
- [x] Tous les systèmes documentés (GUI, HUD, Quêtes, Slots, Anti-abus, Tab, Scoreboard)
- [x] Décisions de design arrêtées (voir [TODO-AVANT-CODE.md](TODO-AVANT-CODE.md))
- [x] Console logging documenté (`KjobLogger` avec ANSI)
- [x] Dossiers docs/ organisés par thème

### Hooks plugins (terminés)

#### ✅ A1 — Kenchantement : enchant Extra-Kill
| Fichier | Modification | État |
|---|---|---|
| `config.yml` | Section `Extra-Kill` déjà présente (sword, max-level 3, nbt-tag=`kenchant_extra_kill`) | ✅ |
| `nbt/NbtKeys.java` | Ajout `ENCHANT_EXTRA_KILL="extra-kill"` et `EXTRA_KILL="kenchant_extra_kill"` | ✅ |
| `listeners/AnvilListener.java` | Ajout `case "extra-kill"` dans `customIdToNbtKey()` | ✅ |

**Résultat** : appliquer un livre Extra-Kill niveau N sur une épée écrit `kenchant_extra_kill = N` (Integer NBT). Kstacker lit ce tag automatiquement.

#### ✅ A2 — Kstacker : hook Extra-Kill  
| Fichier | Modification | État |
|---|---|---|
| `listener/MobLethalDamageListener.java` | Logique Extra-Kill déjà implémentée | ✅ pré-existant |
| `config/ConfigManager.java` | Champs `extraKillEnabled`, `extraKillNbtTag`, `extraKillFixed` déjà présents | ✅ pré-existant |

**Résultat** : Kstacker lit déjà `kenchant_extra_kill` via `hooks.kenchantement.extra-kill.nbt-tag` (défaut = `kenchant_extra_kill`). Aucune modification requise.

#### ✅ A3 — Kgui : openMenu avec args runtime
| Fichier | Modification | État |
|---|---|---|
| `gui/GuiManager.java` | Ajout `Map<UUID, Map<String,String>> runtimeArgs` | ✅ |
| `gui/GuiManager.java` | Ajout `openMenu(Player, String, Map<String,String>)` | ✅ |
| `gui/GuiManager.java` | `loadProviderItems()` fusionne YAML args + runtime args | ✅ |

**Résultat** : depuis KjobsUltimate, appeler `kgui.getGuiManager().openMenu(player, "kjobs_detail", Map.of("job_id","mineur"))` passe les args au ContentProvider lors du rendu.

#### ✅ A4 — Kchat : conflit Tab
| Vérification | Résultat |
|---|---|
| `PlayerListHeaderFooter` dans sources Kchat | Aucune occurrence trouvée |

**Résultat** : Kchat ne touche pas au Tab header/footer. KjobsUltimate peut gérer le Tab sans conflit.

#### ✅ A5 — Kcraft : déclencher KcraftPostCraftEvent
| Fichier | Modification | État |
|---|---|---|
| `managers/CraftManager.java` | Import `KcraftPostCraftEvent` ajouté | ✅ |
| `managers/CraftManager.java` | `executeCraft()` : TODO remplacé par `callEvent(new KcraftPostCraftEvent(...))` | ✅ |

**Résultat** : tout craft via GUI Kcraft ou table vanilla déclenche `KcraftPostCraftEvent`. KjobsUltimate peut écouter `@EventHandler void onKcraftPost(KcraftPostCraftEvent e)` pour le job Artisan.

---

## ✅ Phase 1 — Core (Maven + DB + Config) — TERMINÉE

### C1 — Projet Maven ✅
- [x] `pom.xml` créé
  - groupId: `me.krunsh`, artifactId: `kjobultimate`, version: `1.0.0-SNAPSHOT`
  - deps provided: SpigotAPI 1.8.8, VaultAPI 1.7
  - deps system: PlaceholderAPI, Kgui, Kcraft, Kstacker, Kfaction
  - dep compile+shade: `sqlite-jdbc:3.42.0.0` → reloc `me.krunsh.kjobultimate.libs.sqlite`
  - dep compile+shade: `item-nbt-api:2.11.1` → reloc `me.krunsh.kjobultimate.nbtapi`

### C2 — Classes Java Core ✅
- [x] `KjobUltimate.java` — point d'entrée (onEnable/onDisable, ordre boot, accesseurs)
- [x] `util/KjobLogger.java` — logger ANSI (banner, success, warn, error, reload)
- [x] `config/ConfigManager.java` — chargement YAML (main + messages + sounds + hud)
- [x] `data/PlayerData.java` — modèle RAM joueur (XP, levels, slots, cooldowns)
- [x] `data/DatabaseManager.java` — SQLite WAL (5 tables + CRUD complet)
- [x] `data/PlayerDataManager.java` — cache ConcurrentHashMap + async load/save + autosave
- [x] `hooks/HookManager.java` — détection et initialisation centralisée
- [x] `hooks/VaultHook.java` — économie Vault
- [x] `hooks/PAPIHook.java` — placeholders %kjob_*%
- [x] `hooks/KguiHook.java` — openMenu() + ContentProviders (Phase 7)
- [x] `hooks/KcraftHook.java` — KcraftPostCraftEvent (Phase 4) **+ handler artisant complet ajouté**
- [x] `hooks/KstackerHook.java` — multiplicateur kills stackés **+ fix metadata key + isGhostEntity()**
- [x] `jobs/JobRegistry.java` — chargement jobs/*.yml
- [x] `jobs/JobDefinition.java` — modèle job (XP table, actions, rewards)

### C3 — Resources ✅
- [x] `src/main/resources/plugin.yml`
- [x] `src/main/resources/config.yml`
- [x] `src/main/resources/messages.yml`
- [x] `src/main/resources/sounds.yml`
- [x] `src/main/resources/hud.yml`
- [x] `src/main/resources/tab.yml`
- [x] `src/main/resources/jobs/mineur.yml`

---

## ✅ Phase 2 — Système jobs de base — TERMINÉE

### Jobs YML ✅
- [x] `src/main/resources/jobs/farmer.yml` — 14 actions (cultures, bois, feuilles)
- [x] `src/main/resources/jobs/hunter.yml` — 16 mobs (zombie, squelette, creeper, enderman…)
- [x] `src/main/resources/jobs/pretorien.yml` — PvP (`pvp_kill`) + anti-abuse cooldown cible
- [x] `src/main/resources/jobs/artisant.yml` — 30 crafts (bois → enchantement)

### Classes Java ✅
- [x] `jobs/LevelUpResult.java` — résultat d'un addXP (leveledUp, levelsGained, newLevel, remainingXP, atMaxLevel)
- [x] `jobs/XpManager.java` — calcul XP + multiplicateurs (permission + event) + récompenses niveau (commandes console)
- [x] `slots/SlotManager.java` — getActiveJobs, assignJob, requestJobChange (avec confirmation), checkAndUnlockSlots
- [x] `data/QuestData.java` — modèle RAM progression quête (progress, completed, claimed, completedAt)
- [x] `listeners/PlayerConnectionListener.java` — onLogin (load async) + onJoin (premier join) + onQuit (save async)
- [x] `KjobUltimate.java` — XpManager + SlotManager ajoutés, PlayerConnectionListener enregistré

---

## ✅ Phase 3 — XP + Levels — TERMINÉE

### Corrections d'audit appliquées ✅
- [x] **farmer.yml** : `NETHER_WART` → `NETHER_WARTS` (nom exact enum Bukkit 1.8.8 du bloc planté)
- [x] **KstackerHook.java** : metadata key `"kill_multiplier"` → `"kstacker-multiplier"` + ajout `isGhostEntity()`
- [x] **PlayerData.java** : ajout cache RAM `bonusMultipliers` + `getBonusMultiplier(jobId)` + `setBonusMultiplier()` + `getBonusMultipliers()`
- [x] **DatabaseManager.java** : ajout `loadBonusMultipliers(UUID)` → charge la table `bonus_multipliers`
- [x] **PlayerDataManager.java** : charge les bonus multipliers dans `loadAsync()` après `cache.put()`
- [x] **XpManager.java** : applique `data.getBonusMultiplier(jobId)` dans la formule `addXP()`
- [x] **XpManager.java** : ajout `handleLevelUp(Player, PlayerData, String, LevelUpResult)` (message + son + slots)
- [x] **XpManager.java** : ajout `checkDailyReset(PlayerData, String)` + `isDailyCapReached(PlayerData, String)`
- [x] **XpManager.java** : ajout privés `getJobDisplayName(String)` + `playSoundForKey(Player, String)`
- [x] **PAPIHook.java** : correction `register()` retourne `boolean` (override correct)
- [x] **messages.yml** : ajout `anti_abuse.pvp_cooldown`
- [x] **pom.xml** : correction path JAR Kcraft (`kcraft-1.0.0-SNAPSHOT.jar` → `Kcraft-1.0.0.jar`)

### Utils ✅
- [x] `util/LevelUtil.java` — `getProgressPercent()`, `formatXP()`, `formatProgress()`
- [x] `util/CropUtil.java` — `isMature(Block)` + `isFarmingCrop(Material)` (Bukkit 1.8.8 enum)

---

## ✅ Phase 4 — Listeners Métiers — TERMINÉE

### Listeners ✅
- [x] `listeners/jobs/MinerListener.java` — `BlockBreakEvent` + gates (créatif, silk touch, cooldown position, daily cap)
- [x] `listeners/jobs/FarmerListener.java` — `BlockBreakEvent` + vérif `isFarmingCrop()` + `isMature()` si activé
- [x] `listeners/jobs/HunterListener.java` — `EntityDeathEvent` + intégration Kstacker ghost multiplier (cap 3x)
- [x] `listeners/jobs/PretorienListener.java` — `PlayerDeathEvent` (PvP) + cooldown anti-farm par cible UUID
- [x] `listeners/jobs/ArtisantListener.java` — `CraftItemEvent` vanilla (Kcraft géré dans `KcraftHook.java`)
- [x] `KjobUltimate.java` — 5 listeners enregistrés dans `registerListeners()`

### Architecture anti-abuse (7 gates) ✅
- Gate 1 : GameMode.CREATIVE bloqué
- Gate 2 : GameMode.SPECTATOR bloqué
- Gate 3 : `isJobActive(data, JOB_ID)` — slot actif vérifié
- Gate 4 : `job.getAction(key)` — action déclarée dans YAML
- Gate 5 (mineur) : silk touch bloqué si `action.isSilkTouchBlocked()`
- Gate 6 (mineur/farmer) : `data.isBlockOnCooldown(locationKey)` — anti-farm position
- Gate 7 : `checkDailyReset()` + `isDailyCapReached()` — plafond quotidien

---

## ⬜ Phase 5 — HUD (SUIVANT)

> Non démarrées. Voir [docs/dev/PLAN-IMPLEMENTATION.md](docs/dev/PLAN-IMPLEMENTATION.md) pour le détail.

| Phase | Contenu | Dépend de |
|---|---|---|
| 2 | Jobs de base : chargement YAML, `JobRegistry`, `/jobs list` minimal | C1-C4 |
| 3 | Système XP + Levels : `LevelUtil`, formule XP, level-up event, DB save | Phase 2 |
| 4 | Listeners métiers : Mineur (bloc cassé), Bucheron, Pêcheur, Chasseur (Kstacker), Agriculteur, Artisan (Kcraft) | Phase 3 |
| 5 | HUD : BossBar + ActionBar | Phase 3 |
| 6 | Anti-abus : cooldown DB, zone-lock, tool-check | Phase 4 |
| 7 | GUI Kgui : vue principale, détail job, sélection, quêtes | Phase 3 |
| 8 | Commandes `/jobs` et `/job` complètes + permissions | Phase 7 |
| 9 | Scoreboard + Tab header/footer | Phase 4 |
| 10 | Quêtes : daily/weekly reset, attribution, progression | Phase 3 |
| 11 | Tests terrain + perf (600 joueurs simulés) | Phases 2-10 |

---

## 🔧 Dernières modifications apportées aux plugins dépendants

| Plugin | Fichier | Changement |
|---|---|---|
| Kcraft | `managers/CraftManager.java` | `KcraftPostCraftEvent` déclenché dans `executeCraft()` |
| Kenchantement | `nbt/NbtKeys.java` | Constantes `ENCHANT_EXTRA_KILL` + `EXTRA_KILL` |
| Kenchantement | `listeners/AnvilListener.java` | case `extra-kill` dans `customIdToNbtKey()` |
| Kgui | `gui/GuiManager.java` | `openMenu(Player,String,Map)` + `runtimeArgs` + merge dans `loadProviderItems` |

---

*Dernière mise à jour : Préparation terrain terminée — 4 hooks validés, 4 plugins modifiés.*
