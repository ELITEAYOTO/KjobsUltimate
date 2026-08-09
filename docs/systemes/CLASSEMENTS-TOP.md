# Classements /jobs top

## Etat actuel

- [x] `/jobs top` affiche le classement global.
- [x] `/jobs top global` affiche le classement global.
- [x] `/jobs top <job>` affiche le classement d'un job precis.
- [x] Les requetes DB sont executees en async.
- [x] Le top est compatible SQLite et MySQL.
- [x] Un cache RAM court evite de refaire la meme requete si la commande est spammee.
- [x] Le rang personnel du joueur est affiche sous le classement.
- [x] Le GUI top possede un selecteur global/jobs.
- [x] Le GUI top charge les donnees DB en async avec item de chargement.
- [x] Le GUI top est pagine et configurable dans `gui/home.yml`.

## Configuration

```yaml
top:
  chat_limit: 10
  gui_limit: 50
  cache_seconds: 30
```

`chat_limit` limite le nombre de lignes envoyees dans le chat. La limite interne est bornee entre 1 et 50.

`gui_limit` limite le nombre de lignes chargees pour le GUI. La limite interne est bornee entre 1 et 50.

`cache_seconds` garde le resultat par filtre (`global`, `mineur`, `farmer`, etc.) en RAM pendant quelques secondes. Mettre `0` desactive le cache.

## GUI configurable

Le GUI est configure dans `gui/home.yml`, section `top`.

Structure principale:

```yaml
top:
  selector_title: "&6Classements Jobs"
  size: 27
  job_slots: [10, 11, 12, 13, 14, 15]
  items:
    global: {}
    job: {}
    back: {}
  ranking:
    title: "&6Top {target} &8- Page {page}"
    size: 54
    items:
      loading: {}
      empty: {}
      entry:
        slots: "0-44"
      own_rank: {}
      previous: {}
      next: {}
      refresh: {}
      selector: {}
      back: {}
```

Action GUI ajoutee:

- `[top] global`: ouvre le top global.
- `[top] {job_id}`: ouvre le top du job courant.
- `[top] previous`: page precedente.
- `[top] next`: page suivante.
- `[top] refresh`: recharge la page actuelle.
- `[top] selector`: retourne au choix global/jobs.

## Calcul du classement

### Global

Le classement global additionne tous les niveaux du joueur, puis tous les XP restants.

Ordre:

1. Somme des niveaux, du plus haut au plus bas.
2. Somme des XP, du plus haut au plus bas.

### Par job

Le classement par job utilise uniquement la ligne `job_data` du job demande.

Ordre:

1. Niveau du job, du plus haut au plus bas.
2. XP courant du job, du plus haut au plus bas.

## Donnees utilisees

Tables lues:

- `job_data`
- `players` indirectement via les donnees joueur existantes

Le nom joueur affiche est resolu via Bukkit `OfflinePlayer`. Si le nom est indisponible, le plugin affiche les 8 premiers caracteres de l'UUID.

## Messages configurables

Dans `messages.yml`:

```yaml
player_command:
  top:
    global_name: "global"
    loading: "{prefix}&7Chargement du classement &e{target}&7..."
    invalid_filter: "{prefix}&cClassement inconnu: &e{job_id}"
    error: "{prefix}&cImpossible de charger le classement pour le moment."
    header: "&6&l====== Top {target} ====== &8({count})"
    line: "&e#{position} &f{name} &8- &7Niv &a{level} &8- &7XP &b{xp}"
    empty: "{prefix}&7Aucune donnee de classement pour le moment."
    own_rank: "{prefix}&7Ton classement {target}: &e{rank}"
    unranked: "non classe"
```

Placeholders disponibles:

- `{target}`: nom affiche du classement.
- `{target_id}`: `global` ou id du job.
- `{count}`: nombre d'entrees affichees.
- `{cached}`: `true` si le resultat vient du cache.
- `{position}`: position dans le top affiche.
- `{name}`: nom joueur ou fallback UUID court.
- `{uuid}`: UUID complet.
- `{job_id}`: id du job de la ligne.
- `{level}`: niveau ou total de niveaux.
- `{xp}`: XP du job ou total XP global.
- `{rank}`: rang personnel du joueur.
- `{page}`: page affichee, commence a 1.
- `{page_index}`: index technique, commence a 0.
- `{previous_page}`: numero de page precedente affiche.
- `{next_page}`: numero de page suivante affiche.
- `{total}`: nombre total d'entrees chargees pour le filtre.

## Suite prevue

- [ ] Mutualiser le cache chat et GUI dans un service `RankingManager` si le top est tres utilise.
- [ ] Ajouter un stockage de `last_name` en DB si la resolution Bukkit OfflinePlayer devient insuffisante.
