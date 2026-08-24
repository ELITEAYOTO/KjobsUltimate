# Audit d'intégration écosystème

Date de validation : 2026-08-24.

## Résultat

- 70 sources principales et 13 sources de tests ;
- 42 tests, 0 failure, 0 error ;
- aucun `systemPath` ou JAR voisin requis par Maven ;
- SHA-256 du JAR :
  `703CFD27AB8F076F53394D502FF69B5169B5F863CAC6834347E08D82FECE8CF0`.

## Frontières

- Kgui est la seule dépendance runtime obligatoire et son API publique est la
  seule surface compilée.
- Kfaction est consommé par son API publique.
- Kcraft, KStacker, Kminerai et Kenchantement sont des hooks optionnels liés
  une fois au démarrage. Une API absente ou incomplète désactive uniquement le
  hook concerné.
- Le métier Mineur possède un chemin d'accounting unique pour Bukkit et
  Kminerai, avec le même cooldown, Silk Touch, XP, argent, HUD et quêtes.
- Les gains AutoSell sont agrégés dans le HUD Kjobs existant.

SQLite, MySQL, Hikari, Protobuf et SLF4J sont isolés dans l'espace de noms du
plugin. Les déclarations des deux pilotes JDBC sont fusionnées dans le JAR et
leur chargement a été vérifié après shading.
