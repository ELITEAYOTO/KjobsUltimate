# KjobsUltimate - Suivi PACKED_COLUMNS

Date: 04/07/2026

## Objectif

Rapprocher le tab de la maquette envoyee:

```text
x| Staff        x| Jobs          x| Faction
x| Krunsh_      x| Mineur Lv.1   x| Skypea
x| Testamp      x|               x| Joueurs:
```

Ici, `x` correspond a une tete de fake joueur dans la tablist Minecraft 1.8.

## Etat

| Element | Etat | Notes |
|---|---:|---|
| Mode `ROW_LINES` | OK | Ancien prototype: une ligne fake contient toutes les colonnes. |
| Mode `PACKED_COLUMNS` | OK | Une fake entry par cellule pour forcer les colonnes naturelles du client 1.8. |
| Config `layout_mode` | OK | `virtual_layout.layout_mode: PACKED_COLUMNS`. |
| Config `packed_columns` | OK | `columns`, `rows`, `max_entries`, `blank_text`, `cell_prefix`, `cell_suffix`. |
| Anti-decalage client | OK | `rows: 15`, `force_client_rows: true`, `reserve_real_entries: 1`. |
| Config `fake_skin` | OK | Support `texture_hash`, `texture_url`, ou `value/signature`. |
| Cache-buster skin | OK | La cle `cache_key` et le hash de texture changent les UUID fake pour forcer le client a recharger. |
| Build Maven | OK | `mvn -q clean package` valide. |

## Limites confirmees

- Les tetes des fake players ne sont pas supprimables proprement en vanilla 1.8.
- Une skin sombre/neutre est possible avec `texture_hash` ou `texture_url`; le couple signe `value/signature` reste le plus fiable.
- Une tete item peut accepter une texture non signee alors qu'une fake player tab peut rester en Steve/Alex selon le client/fork.
- Si `/kjobs tabdebug` affiche `skin=ON(hash,len=...)` mais que les tetes restent Steve/Alex, la texture est bien injectee cote serveur mais probablement refusee/cachee cote client.
- Une vraie texture transparente n'est pas garantie: le client peut afficher Steve/Alex si la texture est invalide.
- Les vrais joueurs, NPC et autres plugins de tab peuvent encore influencer la mise en colonnes.

## Test recommande

1. Remplacer le jar serveur par `target/KjobsUltimate-1.0.0-SNAPSHOT.jar`.
2. Verifier que le `tab.yml` serveur contient le nouveau bloc `virtual_layout`.
3. Mettre `virtual_layout.enabled: true`.
4. Garder `virtual_layout.layout_mode: PACKED_COLUMNS`.
5. Redemarrer ou `/kjobs reload`.
6. Lancer `/kjobs tabclearall`.
7. Lancer `/kjobs tabdebug`.
8. Lancer `/kjobs tabrender`.

## Reglages importants

```yaml
virtual_layout:
  enabled: true
  layout_mode: PACKED_COLUMNS

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
    cache_key: ""
    texture_hash: ""
    texture_url: ""
    value: ""
    signature: ""
```

Exemple avec hash:

```yaml
fake_skin:
  enabled: true
  cache_key: "dark-v1"
  texture_hash: "fd37c7c9b321cfbf250a434bacce897e5ce993ffd42344a0134841e95d5ca759"
  texture_url: ""
  value: ""
  signature: ""
```

Apres changement de skin:

1. Modifier `texture_hash` ou incrementer `cache_key`, par exemple `dark-v2`.
2. `/kjobs reload`
3. `/kjobs tabclearall`
4. Fermer/rouvrir le tab, voire reconnecter le joueur si le client garde encore le cache.

Pour augmenter la hauteur des colonnes, monter `packed_columns.rows` et `packed_columns.max_entries`.

Exemples:

| Objectif | columns | rows | max_entries |
|---|---:|---:|---:|
| 3 colonnes avec 1 vrai joueur visible | 3 | 15 | 44 |
| 3 colonnes sans vrai joueur reserve | 3 | 15 | 45 |
| 3 colonnes plus longues avec 1 vrai joueur visible | 3 | 18 | 53 |
| 4 colonnes avec 1 vrai joueur visible | 4 | 15 | 59 |

`reserve_real_entries` sert a compenser les entrees non gerees par KjobsUltimate, par exemple ton vrai pseudo dans la tab.

## Decision actuelle

Le mode `PACKED_COLUMNS` est le meilleur prototype pour ton idee `x = tete joueur`.

On garde `ROW_LINES` disponible en fallback si les tetes prennent trop de place ou si la tab est trop influencee par les vrais joueurs/NPC.
