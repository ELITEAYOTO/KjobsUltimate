# KjobsUltimate

Système de métiers de Volkaria pour KHopeSpigot 1.8.8, compilé en bytecode
Java 8. La branche `refactor/v3-foundation` porte la refonte V3 actuellement
en validation.

## État de la version

- version courante : `3.16.3` ;
- build : Maven, sans JAR local ni `systemPath` ;
- stockage de développement : SQLite WAL ;
- stockage de production prévu : MySQL/HikariCP ;
- GUI : Kgui ;
- HUD : ActionBar et BossBar packet-only ;
- cible d'architecture : 500 joueurs, marge de conception à 700.

La correction Dragon V3.16.3 est testée automatiquement, mais doit encore
passer la matrice visuelle Vanilla/OptiFine/Lunar avant fusion dans `main` et
création du tag. Voir
[docs/V3.16.3-DRAGON-BOSSBAR-FIX.md](docs/V3.16.3-DRAGON-BOSSBAR-FIX.md).

## Compiler

Prérequis : JDK 8 ou JDK 17 et Maven. Le résultat reste ciblé Java 8.

```powershell
mvn clean verify
```

Artefact produit : `target/KjobsUltimate-3.16.3.jar`.

Les plugins de serveur ne doivent jamais être ajoutés au dépôt. Les
dépendances de compilation sont déclarées dans `pom.xml`; les intégrations
optionnelles sont résolues au runtime par leurs contrats publics.

## Documentation

- `docs/architecture/` : architecture et flux ;
- `docs/config/` : références YAML ;
- `docs/gui/` : intégration Kgui ;
- `docs/systemes/` : comportements fonctionnels ;
- `docs/dev/` : décisions et matrices de test.

## Licence

Code source visible, tous droits réservés. Voir [LICENSE](LICENSE).

---

## Résumé du Plugin

**KjobsUltimate** est le système de métiers principal de Volkaria. Il gère :
- **6 jobs** : Mineur, Farmer, Hunter, Prétorien, Artisan et Pilleur
- **Système de slots configurable** : 1 slot par défaut, déblocage progressif au level 5, 10, 15... (désactivable)
- **Quêtes permanentes V1** avec progression et récompenses transactionnelles
- **HUD temps réel** : BossBar NMS packet-only, ActionBar XP, Achievement level up
- **Tab Scoreboard** : Header/Footer NMS configurable dans `tab.yml`
- **GUI via Kgui** : ContentProviderAPI — menus paginés délégués à Kgui
- **Anti-abuse** : SilkTouch, cultures immatures, cooldown position, anti-PvP farm
- **100% configurable** : Config splitée par responsabilité (1 fichier par job, 1 fichier par quêtes-job)

---

## État d'implémentation

Ce tableau distingue volontairement le code présent des systèmes seulement
décrits dans les documents de conception.

| Système | État réel |
|---|---|
| Six métiers et gains d'XP actuels | Implémenté ; validation en jeu continue |
| Slots de métiers | Implémenté et configurable |
| HUD BossBar/ActionBar, achievement, TAB et GUI KGUI | Implémenté ; validation multi-client/menus requise |
| Quêtes permanentes configurées dans `quests.yml` | Implémenté, y compris les cibles `materiau:data` 1.8 (`PRISMARINE:2`) |
| Récompenses de quêtes transactionnelles | Implémenté et testé sur SQLite |
| Quêtes daily/weekly et resets automatiques | Retiré du cahier des charges Volkaria |
| Chaînes séquentielles de quêtes | Implémenté et testé : une étape active par chaîne, chaînes parallèles, pause métier et conditions de niveau |
| Nouveaux types non présents dans `quests.yml` | Planifié |
| SQLite réel | Testé localement et dans le profil intégré de 44 JAR ; charge 750 joueurs à réaliser |
| MySQL/Hikari réel | Compile uniquement ; aucune base MySQL réelle validée |
| Intégration KStacker | API `StackKillResult` explicite ; seules les metadata appartenant à KStacker sont acceptées |

## Contrats inter-plugins Volkaria

- Kminerai publie une casse réellement terminée via `CustomOreMinedEvent`.
  Mineur résout `KMINERAI:<oreId>`, puis
  `KMINERAI_DROP:<actualDropId>`, puis le matériau vanilla. Une seule voie
  applique XP, argent, HUD, quête, Silk Touch et anti-farm.
- Kenchantement publie un `AutoSellActionEvent` par action logique. Kjobs
  agrège le nombre d'objets et la valeur dans son ActionBar existante ; aucun
  second HUD n'est envoyé.
- Kcraft et KStacker restent optionnels et sont liés à leur contrat public au
  démarrage. Le build ne dépend d'aucun JAR placé dans un dossier voisin.
- Kgui reste la seule dépendance runtime obligatoire et Kfaction est consommé
  uniquement par son API publique.

## Décisions Techniques Actées

| Décision | Choix | Raison |
|---|---|---|
| Stockage par défaut | **SQLite WAL** | Simple à exploiter, écritures sérialisées et I/O disque maîtrisées |
| GUI | **Kgui ContentProviderAPI** | Config YAML menus, pas de code GUI custom |
| Slots jobs | **Configurable (défaut=1)** | Déblocage progressif, désactivable |
| Premier join | **Choix joueur** | GUI sélection s'ouvre automatiquement |
| Config files | **1 fichier par job + 1 par quêtes-job** | Lisible, maintenable, hot-reload |
| Kchat tab | **KjobsUltimate propriétaire header/footer** | Kchat gère équipes/prefixes — pas de conflit |
| Kstacker kills | **META_KILL_MULTIPLIER = min(3, stackCount)** | Anti-dupe cap absolu à 3 |
| Console logging | **KjobLogger + ANSI** | Affichage coloré premium dans PandaSpigot |

---

## Chaînes de quêtes

Une chaîne explicite contient des étapes ordonnées. Une seule étape progresse
dans chaque chaîne, tandis que plusieurs chaînes différentes restent actives
en parallèle. La complétion déverrouille automatiquement l'étape suivante et
la progression des étapes terminées est conservée.

Le catalogue YAML est construit et validé intégralement avant de remplacer le
snapshot actif. Un doublon de clé, une collision de casse, un stage invalide,
une clé inconnue ou un type custom non déclaré fait refuser le candidat sans
interrompre le catalogue déjà chargé.

Une action ne reporte pas son surplus vers l'étape suivante pendant le même
événement. Les sauvegardes ordinaires fusionnent les compteurs de manière
monotone afin qu'une ancienne écriture asynchrone ne puisse pas diminuer une
progression plus récente.

Les chaînes actuelles sont `mineur_stone` et `farmer_wheat`, chacune avec trois
étapes. Les autres quêtes historiques restent des chaînes unitaires pour
préserver leur comportement.

---

## Récompenses de quête transactionnelles

La récupération d'une récompense ne repose plus uniquement sur le booléen
`quest_progress.claimed`. Une table durable `quest_reward_claims`, unique par
couple joueur/quête, suit les états :

`PREPARED → DISTRIBUTING → DISTRIBUTED`

Une erreur pendant ou après une commande produit l'état `FAILED`. Les états
`PREPARED`, `DISTRIBUTING` et `FAILED` ne sont jamais rejoués
automatiquement : une commande de console peut avoir eu un effet avant
l'erreur, et la relancer aveuglément créerait une duplication.

Ordre d'une récupération normale :

1. verrou RAM contre les doubles clics ;
2. réservation SQL atomique ;
3. passage SQL en `DISTRIBUTING` ;
4. fermeture de l'état RAM `claimed` ;
5. attribution de l'XP et exécution des commandes sur le thread principal ;
6. écriture transactionnelle de `quest_progress.claimed=1` et
   `quest_reward_claims.status=DISTRIBUTED`.

Au démarrage, toute ligne non terminale est signalée dans la console pour
vérification staff. Le reset administratif explicite d'une quête efface le
registre et la progression dans une même transaction ; il est refusé tant
qu'une distribution du même joueur et de la même quête est en cours.

Requête d'audit utile :

```sql
SELECT uuid, quest_id, player_name, status, reserved_at, updated_at, last_error
FROM quest_reward_claims
WHERE status <> 'DISTRIBUTED';
```

---

## Sons — Durée Côté Client

La durée d'un son est entièrement déterminée par le fichier `.ogg`. Le serveur envoie juste le nom du son et le joueur joue le fichier en entier.
