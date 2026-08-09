# KjobsUltimate - Objectif Tab en colonnes virtuelles

Date: 04/07/2026  
Contexte: objectif tab type Staff / Jobs / Faction avec fake joueurs, compatible PandaSpigot/KhopeSpigot 1.8.8/1.8.9.

## Verdict

Oui, l'objectif est faisable, mais pas avec le header/footer seul.

Le tab actuel de KjobsUltimate gere deja:

- Header/footer NMS configurable.
- Sections header/footer.
- Groupes staff configurables.
- Placeholders jobs, argent Vault, serveur.
- Nom joueur dans la liste tab optionnel.

Pour obtenir des colonnes dans la zone centrale du tab, il faut ajouter un systeme de fake entries via `PacketPlayOutPlayerInfo`.

## Maquette cible

```text
                      Volkaria
|      Staff      |      Jobs       |      Faction       |
----------------------------------------------------------
|      Krunsh_    |  Mineur: lvl 1  |   faction: Skypea  |
|      Testamp    |                 |      Joueurs :     |
|                 |                 |  Krunsh_ (Leader)  |
|                 |                 |  Brioche (Co-Lead) |
|                 |                 |  Akapio (Membre)   |
----------------------------------------------------------
                Argent: 100M | Rank: Gerant
                Joueurs en ligne: 200/320
                     Volkaria.fr
```

## Limites Minecraft 1.8.x

| Sujet | Limite |
|---|---|
| Header/footer | Libre, stable, multi-lignes. |
| Corps du tab | Pas une vraie grille configurable. |
| Fake joueurs | Necessaires pour afficher du texte arbitraire dans la liste. |
| Tri | A forcer par noms techniques et/ou scoreboard teams. |
| Longueur | Les GameProfile names doivent rester courts. |
| Vrais joueurs | Ils restent visibles sauf si on fait une gestion tab beaucoup plus invasive. |
| NPC | Les NPC d'un autre plugin peuvent apparaitre si ce plugin ne les retire pas du tab. |
| Tetes a gauche | Non supprimable en tab vanilla 1.8 pour des fake players; on peut seulement limiter le nombre de fake lignes ou utiliser un autre rendu. |

## Architecture recommandee

```text
me.krunsh.kjobultimate.tab
  TabManager.java
    - header/footer actuel
    - sections actuelles

  VirtualTabManager.java
    - active seulement si virtual_layout.enabled=true
    - cree/update/remove les fake entries
    - gere un cache par viewer

  VirtualTabLayout.java
    - transforme columns + placeholders en lignes finales

  VirtualTabEntry.java
    - uuid stable
    - technical name court
    - display text
    - ping/gamemode optionnels

  VirtualTabPacketAdapter.java
    - NMS 1.8 PacketPlayOutPlayerInfo
    - ADD_PLAYER / UPDATE_DISPLAY_NAME / REMOVE_PLAYER
```

## Modes de rendu retenus

Deux modes existent maintenant dans `tab.yml`:

| Mode | Usage | Avantage | Limite |
|---|---|---|---|
| `ROW_LINES` | Ancien prototype | Peu de fake joueurs, donc peu de tetes. | Tout est rendu dans une seule entree par ligne, donc les colonnes peuvent sembler serrees ou decalees. |
| `PACKED_COLUMNS` | Objectif actuel type `x = tete joueur` | Chaque cellule devient une entree fake, le client 1.8 forme alors de vraies colonnes naturellement. | Les tetes fake restent visibles; il faut assez d'entrees pour forcer plusieurs colonnes. |

Decision actuelle: utiliser `PACKED_COLUMNS` pour se rapprocher de la maquette envoyee:

```text
x| Staff        x| Jobs          x| Faction
x| Krunsh_      x| Mineur Lv.1   x| Skypea
x| Testamp      x|               x| Joueurs:
```

En 1.8, le client remplit la tablist par colonnes quand il y a assez d'entrees. Il compte aussi les vrais joueurs visibles dans la tablist.

Correction appliquee apres test:

- `42` fake entries semblaient logiques pour `3 x 14`, mais le vrai joueur ajoute une entree et le client a decale les colonnes.
- La config par defaut passe donc a `rows: 15`, `reserve_real_entries: 1`, `max_entries: 44`.
- Resultat vise: `44 fake entries + 1 vrai joueur = 45 entrees`, donc 3 colonnes de 15 lignes cote client.

Important: les vrais joueurs connectes et les NPC peuvent encore influencer le rendu final, car le client trie/affiche toutes les entrees de tab. Ce mode est donc un objectif/prototype a valider en conditions serveur.

## Tetes / skins fake

Les tetes a gauche ne sont pas supprimables proprement avec une fake entry 1.8 vanilla: pour le client, chaque entree tab est un joueur.

Ce qui est possible:

- Mettre moins de fake entries avec `ROW_LINES`.
- Utiliser `PACKED_COLUMNS` et accepter les tetes comme separateurs visuels.
- Configurer `virtual_layout.fake_skin.texture_hash` avec le hash d'une tete Minecraft.
- Configurer `virtual_layout.fake_skin.texture_url` avec une URL `textures.minecraft.net`.
- Configurer `virtual_layout.fake_skin.value/signature` avec un couple Mojang signe pour une fiabilite maximale.

Ce qui n'est pas fiable:

- Une vraie tete transparente. Les skins Minecraft restent des textures de joueur et le rendu client peut afficher un fallback Steve/Alex si la texture n'est pas valide/signee.
- Supprimer uniquement l'icone de tete sans supprimer l'entree.

## Config cible

```yaml
virtual_layout:
  enabled: false
  layout_mode: PACKED_COLUMNS
  columns_enabled: true
  update_interval_ticks: 60
  max_rows: 20
  remove_on_disable: true
  debug: false
  bottom_lines_enabled: false
  faction_members_limit: 8

  ordering:
    technical_name_prefix: "!kjt_"
    stable_uuid_seed: "kjobsultimate-tab"
    start_index: 1

  render:
    separator: " &8| "
    empty_cell: ""
    truncate_cells: true
    pixel_alignment: true
    cell_width_default: 24
    cell_width_pixels_default: 132

  packed_columns:
    columns: 3
    rows: 15
    max_entries: 44
    force_client_rows: true
    reserve_real_entries: 1
    blank_text: "&8"
    cell_prefix: "&8| "
    cell_suffix: ""

  fake_skin:
    enabled: false
    texture_hash: ""
    texture_url: ""
    value: ""
    signature: ""

  columns:
    staff:
      enabled: true
      title: "&bStaff"
      width: 24
      width_pixels: 126
      lines:
        - "%staff_admin_online%"
        - "%staff_modo_online%"
        - "%staff_helper_online%"

    jobs:
      enabled: true
      title: "&6Jobs"
      width: 26
      width_pixels: 144
      lines:
        - "%kjob_display_job_name%: Lv.%kjob_level_%kjob_display_job%%"
        - "%kjob_active_jobs_lines%"

    faction:
      enabled: true
      title: "&aFaction"
      width: 30
      width_pixels: 168
      lines:
        - "Faction: %kfaction_name%"
        - "Role: %kfaction_role%"
        - "Joueurs:"
        - "%kfaction_members_lines%"

  bottom_lines:
    - "&6Argent: &e%vault_balance% &8| &7Rank: %rank_name%"
    - "&7Joueurs en ligne: &f%server_online%&7/&f%server_max_players%"
    - "&eVolkaria.fr"
```

## Strategie packet

### Ajouter une ligne

1. Creer un `GameProfile` avec UUID stable par viewer + slot.
2. Nom technique court, par exemple `!kjt_001`.
3. Envoyer `PacketPlayOutPlayerInfo ADD_PLAYER`.
4. Envoyer `PacketPlayOutPlayerInfo UPDATE_DISPLAY_NAME` si besoin.

### Mettre a jour une ligne

1. Comparer ancien texte et nouveau texte dans un cache.
2. Si identique, ne rien envoyer.
3. Si different, envoyer uniquement `UPDATE_DISPLAY_NAME`.

### Retirer une ligne

1. Si une ligne n'existe plus dans le layout, envoyer `REMOVE_PLAYER`.
2. Sur `/kjobs reload`, `PlayerQuitEvent`, `onDisable`, nettoyer toutes les entries `!kjt_`.

## Regles anti-conflit

| Regle | Pourquoi |
|---|---|
| Prefix technique dedie `!kjt_` | Ne jamais supprimer une entry qui ne vient pas de KjobsUltimate. |
| UUID stable par viewer/slot | Eviter flicker et duplication. |
| Cache par viewer | Eviter spam packets. |
| Update toutes les 60 ticks par defaut | Stable pour 700 joueurs. |
| Cleanup on reload/disable | Eviter lignes fantomes. |
| Pas de scan Protocol global au debut | Garder simple et controlable. |

## Interaction avec ServerNPC

Le plugin NPC audite utilise lui aussi `PacketPlayOutPlayerInfo` pour spawner ses NPC joueurs:

- `ADD_PLAYER` avant spawn.
- `REMOVE_PLAYER` seulement sur hide ou logique `hideName`.

Donc il peut laisser des NPC visibles dans le tab. KjobsUltimate ne doit pas corriger cela en supprimant des profils inconnus, sauf module special de compat.

Decision recommandee:

- Corriger ServerNPC pour qu'il retire ses entries NPC 2 a 5 ticks apres spawn.
- KjobsUltimate gere seulement ses fake entries `!kjt_`.

## Performance cible 700 joueurs

Budget recommande:

| Element | Valeur |
|---|---:|
| Update virtual tab | 60 ticks par defaut |
| Max fake rows | 20 a 60 selon test |
| PAPI par viewer | Desactive par defaut sur virtual layout |
| Native placeholders | Pre-caches par tick |
| Faction members | Cache 2-5 sec |
| Staff groups | Snapshot global par update |
| Packets | Diff uniquement |

Exemple:

- 700 joueurs.
- 20 fake rows.
- 1 update toutes les 3 secondes.
- Sans diff: 14 000 updates/3 sec, trop haut.
- Avec diff: uniquement les lignes changees, acceptable.

## Commandes debug proposees

```text
/kjobs tabclearall
/kjobs tabclear <joueur>
/kjobs tabdebug [joueur]
/kjobs tabrender <joueur>
```

Sortie debug souhaitee:

```text
VirtualTab: enabled=true rows=20 interval=60
Viewer: Krunsh_
Entries cached: 20
Last render ms: 1.8
Packets last tick: add=0 update=2 remove=0
Mode: COLUMNS
```

## Checklist implementation

| Etape | Etat | Notes |
|---|---:|---|
| Audit ServerNPC tab packets | OK | Voir `C:\Users\timot\Desktop\npc-plugin\SERVERNPC-AUDIT.md`. |
| Confirmer faisabilite 1.8.8 | OK | `PacketPlayOutPlayerInfo` existe et ServerNPC l'utilise deja. |
| Config cible `virtual_layout` | OK | Ajoute dans `tab.yml`, desactive par defaut. |
| Mode `ROW_LINES` | OK prototype | Une entree fake par ligne complete, moins lourd mais moins propre visuellement. |
| Mode `PACKED_COLUMNS` | OK prototype | Une entree fake par cellule, plus proche de la maquette `x| colonne`. |
| Anti-decalage colonnes | OK prototype | `rows: 15` + `reserve_real_entries: 1` pour compenser le vrai joueur visible. |
| Skin fake configurable | OK prototype | `texture_hash`, `texture_url`, ou `value/signature`; le signe Mojang reste le plus fiable. |
| `VirtualTabManager` | OK prototype | Nouveau module `me.krunsh.kjobultimate.tab.VirtualTabManager`. |
| NMS packet adapter | OK prototype | Reflection `PacketPlayOutPlayerInfo` ADD/UPDATE_DISPLAY_NAME/REMOVE. Signature 1.8.8 verifiee avec `javap`. |
| Cache/diff par viewer | OK prototype | Envoie uniquement add/update/remove selon changement de ligne. |
| Cleanup reload/disable | OK prototype | `clearAll()` au shutdown si `remove_on_disable=true`; commandes admin de clear. |
| Hook Kfaction | OK prototype | `%kfaction_name%`, `%kfaction_role%`, `%kfaction_members%`, `%kfaction_members_lines%`. |
| Debug admin | OK prototype | `/kjobs tabdebug`, `/kjobs tabrender`, `/kjobs tabclear`, `/kjobs tabclearall`. |
| Test avec ServerNPC actif | A faire | Verifier que les entries ne se melangent pas. |
| Fallback scoreboard teams | A faire si besoin | Necessaire seulement si le client/fork ignore `displayName` dans la tablist. |
| Supprimer les tetes fake player | Impossible vanilla 1.8 | Une fake tab entry est rendue comme un joueur par le client. Alternative: skin sombre/neutre. |

## Recommandation de prochaine etape

La prochaine etape propre est de tester le prototype avec `virtual_layout.enabled: true` sur serveur de dev:

1. Copier/merger le bloc `virtual_layout` dans le `tab.yml` serveur si le fichier existe deja.
2. Mettre `virtual_layout.enabled: true`.
3. Garder `virtual_layout.layout_mode: PACKED_COLUMNS` pour tester le rendu en colonnes naturelles.
4. `/kjobs reload`.
5. `/kjobs tabclearall`.
6. `/kjobs tabdebug`.
7. `/kjobs tabrender`.
8. Verifier si le client affiche bien environ 3 colonnes.
9. Si les colonnes se redecalent, ajuster `packed_columns.reserve_real_entries`.
10. Si les tetes genent trop, tester une texture sombre via `fake_skin` ou repasser en `ROW_LINES`.
