# KjobsUltimate - Quetes Config V1

Cette doc decrit le systeme actuellement implemente dans le plugin.

## Principe

- Les quetes sont permanentes.
- Elles sont declarees dans `quests.yml`.
- La progression est sauvegardee en DB dans `quest_progress`.
- Une recompense est claimable une seule fois.
- Le claim se fait uniquement via GUI (`/jobs quests`), pas via commande joueur.
- Une quete progresse seulement si le joueur a le job de la quete actif/debloque.

## Format Minimal

```yaml
quests:
  mon_id_unique:
    display_name: "&aNom affiche"
    job: mineur
    type: MINE
    target: STONE
    amount: 250
    min_level: 0
    rewards:
      xp: 500
      commands:
        - "[console] give {player} tripwire_hook 1"
```

## Types Supportes

| Type | Declencheur | Target attendu | Amount compte |
|---|---|---|---|
| `MINE` | Bloc casse par Mineur | `Material` casse | 1 par bloc |
| `HARVEST` | Recolte Farmer | `Material` recolte | 1 par bloc |
| `KILL` | Mob tue | `EntityType` | 1 ou multiplicateur KStacker |
| `PVP_KILL` | Kill joueur Pretorien | `PLAYER` ou `*` | 1 |
| `CRAFT` | Craft vanilla/Kcraft | `Material` resultat ou `*` | Quantite craft |
| `TNT_EXPLODE` | TNT posee par joueur explose | `TNT` ou `*` | Nombre TNT |
| `DYNAMITE_EXPLODE` | Dynamite custom explose | `DYNAMITE` ou `*` | Nombre dynamites |
| `TNT_CRAFT` | Craft TNT | `TNT` ou `*` | Quantite craft |
| `DYNAMITE_CRAFT` | Craft Kcraft dynamite | `DYNAMITE` ou `*` | Quantite craft |
| `EAT` | Item consomme | `Material` consomme | 1 |
| `CONSUME` | Alias large de consume | `Material` consomme | 1 |
| `SMELT` | Item retire d'un four | `Material` obtenu | Quantite retiree |
| `FISH` | Peche item ou entite | `Material`, `EntityType`, ou `*` | Quantite/1 |
| `FISH_ENTITY` | Entite attrapee a la canne | `EntityType` | 1 |
| `ENCHANT` | Item enchante | `Material` enchante | 1 |
| `ENCHANT_LEVELS` | Item enchante | `Material` enchante | Niveaux consommes |
| `PLACE` | Bloc pose | `Material` pose | 1 |
| `TAME` | Entite apprivoisee | `EntityType` | 1 |

`target: "*"` accepte tous les targets pour le type choisi.

## Exemples

### Smelt

```yaml
artisan_smelter_iron:
  display_name: "&6Artisan &8- &7Fonte du fer"
  job: artisan
  type: SMELT
  target: IRON_INGOT
  amount: 128
  min_level: 0
  rewards:
    xp: 600
    commands: []
```

### Eat

```yaml
hunter_eat_steak:
  display_name: "&2Chasseur &8- &7Repas de guerre"
  job: hunter
  type: EAT
  target: COOKED_BEEF
  amount: 32
  min_level: 0
  rewards:
    xp: 250
    commands: []
```

### Fish

```yaml
farmer_fish_any:
  display_name: "&aFarmer &8- &7Peche simple"
  job: farmer
  type: FISH
  target: "*"
  amount: 50
  min_level: 0
  rewards:
    xp: 300
    commands: []
```

### Enchant

```yaml
artisan_enchant_sword:
  display_name: "&6Artisan &8- &7Lames enchantees"
  job: artisan
  type: ENCHANT
  target: DIAMOND_SWORD
  amount: 5
  min_level: 0
  rewards:
    xp: 700
    commands: []
```

## Commandes Staff Utiles

- `/kjobs questcomplete <joueur> <questId>` rend la quete claimable.
- `/kjobs questgive <joueur> <questId>` alias de `questcomplete`.
- `/kjobs questreset <joueur> <questId|all>` reset une quete ou toutes.
- `/kjobs resetquest <joueur> <questId|all>` alias de `questreset`.

## Validation

Au demarrage et au `/kjobs reload`, le plugin log des warnings si:

- le type de quete n'est pas dans la liste supportee;
- le target ne correspond ni a un `Material` 1.8.8, ni a un `EntityType` 1.8.8, ni a un target custom connu.

Un target custom reste possible si un hook envoie exactement le meme `type + target`.
