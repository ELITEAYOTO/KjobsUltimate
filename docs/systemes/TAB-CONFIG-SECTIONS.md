# KjobsUltimate - Tab configurable par sections

> Etat au 04/07/2026 : Tab V2 ajoute au-dessus du header/footer NMS existant. La cible "colonnes virtuelles" est documentee dans `TAB-VIRTUAL-COLONNES-OBJECTIF.md`.

## Objectif

Garder la stabilite du packet `PacketPlayOutPlayerListHeaderFooter` en 1.8.8/1.8.9, tout en rendant le contenu ultra configurable depuis `tab.yml`.

Le plugin ne modifie pas encore l'ordre des joueurs dans les colonnes du tab. Cette partie sera traitee plus tard, car elle demande du scoreboard/team par joueur et doit etre testee avec soin sur 700 joueurs.

## Ce qui est fait

| Element | Etat | Notes |
|---|---:|---|
| Header/footer NMS legacy | OK | Toujours disponible via `header:` et `footer:`. |
| Mode sections | OK | Active avec `sections.enabled: true`. |
| Sections header | OK | `sections.header.<id>.lines`. |
| Sections footer | OK | `sections.footer.<id>.lines`. |
| Conditions par section | OK | `always`, `has_jobs`, `no_jobs`, `staff_online`, `no_staff_online`, `vault`. |
| Permission par section | OK | Champ optionnel `permission`. |
| PlaceholderAPI optionnel | OK | `placeholderapi_per_player: true/false`. |
| Snapshot staff par tick | OK | Staff calcule une fois par update, pas une fois par joueur. |
| Argent Vault natif | OK | `%vault_balance%` et `%vault_balance_raw%`. |
| Jobs actifs natifs | OK | Inline ou multi-lignes. |
| Permission staff | OK | `kjobsultimate.staff`, incluse dans `kjobsultimate.admin`. |
| Groupes staff configurables | OK | `staff_groups` separe admin/modo/helper ou tout autre groupe. |
| Nom joueur dans la liste Tab | OK optionnel | `player_list_name.enabled`, desactive par defaut. |
| Validation config Tab | OK partiel | Conditions inconnues et sections vides log au reload. |

## Configuration

```yaml
sections:
  enabled: true

  header:
    brand:
      enabled: true
      condition: always
      lines:
        - "&6&l* SparrowMC *"
        - "&7PvP Faction &8- &e%server_online%&7/&e%server_max_players%"

  footer:
    jobs:
      enabled: true
      condition: has_jobs
      lines:
        - "&6Jobs actifs"
        - "%kjob_active_jobs_lines%"

    staff:
      enabled: true
      condition: staff_online
      lines:
        - "&bStaff &8(&f%staff_count%&8)"
        - "%staff_groups_lines%"
```

## Conditions disponibles

| Condition | Affichage |
|---|---|
| `always` | Toujours. |
| `has_jobs` | Joueur avec au moins un job debloque. |
| `no_jobs` | Joueur sans job debloque. |
| `staff_online` | Au moins un staff en ligne. |
| `no_staff_online` | Aucun staff en ligne. |
| `vault` | Vault economie disponible. |
| `staff_group_online:<id>` | Groupe staff configure avec au moins un joueur. |

## Groupes Staff

`staff_groups` permet de separer les roles sans systeme scoreboard lourd.

```yaml
staff_groups:
  admin:
    enabled: true
    permission: "kjobsultimate.admin"
    format: "&c{player}"
    separator: "&8, "
    empty: "&7Aucun"
    line_format: "&cAdmin &8(&f{count}&8): &f{players}"

  helper:
    enabled: true
    permission: "kjobsultimate.staff.helper"
    format: "&b{player}"
    separator: "&8, "
    empty: "&7Aucun"
    line_format: "&bHelper &8(&f{count}&8): &f{players}"
```

Exemple de section conditionnelle :

```yaml
sections:
  footer:
    admins:
      enabled: true
      condition: staff_group_online:admin
      lines:
        - "%staff_admin_online%"
```

## Nom des joueurs dans la liste Tab

Cette option modifie uniquement le nom visible dans la liste des joueurs. Elle ne trie pas encore les joueurs et ne cree pas de colonnes.

Elle est desactivee par defaut pour eviter les conflits avec Kchat ou un autre plugin de scoreboard/nametag.

```yaml
player_list_name:
  enabled: false
  truncate_to_legacy_limit: true
  max_length: 16
  staff_permission: "kjobsultimate.staff"
  format: "&7%player_name%"
  staff_format: "&b%player_name%"
```

Notes 1.8.x :
- Garder `truncate_to_legacy_limit: true` tant que le fork n'a pas ete valide en production.
- Les couleurs comptent dans la limite de caracteres Bukkit.
- Si un autre plugin gere deja les noms Tab, laisser `enabled: false`.

## Placeholders natifs

| Placeholder | Description |
|---|---|
| `%server_online%` | Joueurs connectes. |
| `%server_max_players%` | Slots serveur. |
| `%player_name%` | Nom du joueur qui voit le tab. |
| `%vault_balance%` | Argent Vault formate. |
| `%vault_balance_raw%` | Argent Vault brut. |
| `%staff_online%` | Liste des staffs en ligne. |
| `%staff_count%` | Nombre de staffs en ligne. |
| `%staff_groups_inline%` | Tous les groupes staff sur une ligne. |
| `%staff_groups_lines%` | Tous les groupes staff en plusieurs lignes. |
| `%staff_<groupId>_online%` | Liste d'un groupe staff, ex: `%staff_admin_online%`. |
| `%staff_<groupId>_count%` | Nombre d'un groupe staff, ex: `%staff_admin_count%`. |
| `%kjob_display_job%` | ID du job favori/affiche. |
| `%kjob_display_job_name%` | Nom colore du job favori/affiche. |
| `%kjob_slots%` | Slots metiers debloques. |
| `%kjob_unlocked_jobs%` | Nombre de jobs debloques. |
| `%kjob_global_level%` | Niveau global jobs. |
| `%kjob_active_jobs_inline%` | Tous les jobs actifs sur une ligne. |
| `%kjob_active_jobs_lines%` | Tous les jobs actifs en plusieurs lignes. |
| `%kjob_level_<jobId>%` | Niveau d'un job. |
| `%kjob_xp_<jobId>%` | XP actuel d'un job. |
| `%kjob_xp_next_<jobId>%` | XP requis prochain niveau. |
| `%kjob_percent_<jobId>%` | Progression en pourcentage. |
| `%kjob_max_level_<jobId>%` | Niveau maximum du job. |

## Performance

- Le tick Tab est configurable avec `update_interval_ticks`.
- Le staff online est calcule une seule fois par tick.
- En mode sections, le header et le footer sont rendus une seule fois par joueur.
- Si PlaceholderAPI devient trop couteux, mettre `placeholderapi_per_player: false`.

## Reste a faire

| Sujet | Etat | Notes |
|---|---:|---|
| Colonnes virtuelles avec fake entries | A faire | Voir `TAB-VIRTUAL-COLONNES-OBJECTIF.md`; a faire avec cache/diff par viewer. |
| Groups configurables staff/helper/modo/admin | OK | Via `staff_groups`. |
| Cache PAPI intelligent | A faire | Distinguer placeholders globaux et placeholders joueur. |
| Validation config Tab avancee | A faire | Verifier formats trop longs, permissions vides, cout PAPI. |
