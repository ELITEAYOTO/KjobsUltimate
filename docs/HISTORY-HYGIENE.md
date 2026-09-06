# Hygiène de l'historique

Le 6 septembre 2026, l'historique de KjobsUltimateV2 a été réécrit pour retirer
18 chemins de JAR tiers. Les branches `main`, `refactor/v3-foundation` et
`archive/github-old-main`, ainsi que les tags
`archive/kjobsultimate-dev-20260809` et `archive/pre-v3-20260815`, ont changé de SHA.

Les dépendances nécessaires au build doivent être obtenues auprès de leurs
sources autorisées et conservées localement, hors Git. La purge a préservé les
fichiers de travail locaux et n'a remplacé aucun JAR de release ni déployé le
plugin. Elle ne constitue pas une vérification complète des licences.

Pour reprendre le travail, un clone neuf est recommandé. Conserver séparément
les modifications non publiées d'un ancien clone, puis reporter uniquement les
changements source vérifiés. Ne pas fusionner ni pousser ses anciennes branches
ou ses anciens tags : ils peuvent réintroduire les dépendances retirées.

Ne jamais ajouter de secrets, configurations privées ou sauvegardes au dépôt.
Des copies de l'ancien historique peuvent subsister dans les clones, forks,
caches ou références de pull requests de GitHub.


## Contrôle de non-réintroduction

Le workflow `Repository hygiene` récupère les branches et tags et vérifie les
chemins de JAR dans tous leurs historiques accessibles, pas seulement au dernier
commit. Il inspecte les arbres Git sans lire les blobs ni exécuter les dépendances.
Un historique incomplet ou une erreur Git fait échouer le contrôle.

Ce contrôle est distinct du build Java, des tests fonctionnels et d'un audit de
secrets/licences. Il détecte une réintroduction après un push ; rendre son succès
obligatoire avant fusion nécessite une règle de protection de branche distincte.
Le `.gitignore` limite les ajouts accidentels, mais un ajout forcé reste possible.

## Références Maven après purge

Les références figées suivent les commits équivalents après réécriture.
Le contenu des modules API concernés est strictement identique :

- KFaction `659a90e` → `517f5713880a326391e00103146dd2ab528e3912`.
- KGUI `31a4d39` → `fe79e27`, résolu par JitPack au commit complet
  `fe79e27d4999f58ee24bac6fb6a67a5073445f9c`.

Pour KGUI, les POM et le JAR sont disponibles sous le SHA abrégé ci-dessus.
Lors du contrôle du 6 septembre, les POM sous SHA complet répondaient 404,
même après la fin du build JitPack ; ces coordonnées incomplètes ne sont pas utilisées.

JitPack doit fournir ces nouvelles coordonnées. Ne pas réinstaller un ancien
JAR sous leur nom pour masquer un problème de résolution. Une modification
ultérieure de l'API nécessite sa propre validation ; la purge seule ne la change pas.
