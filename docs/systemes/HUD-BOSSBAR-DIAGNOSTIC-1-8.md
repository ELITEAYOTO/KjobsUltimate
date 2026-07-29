# HUD Bossbar 1.8 - diagnostic

## Probleme observe

La bossbar fake Wither ne s'affichait pas correctement sur PandaSpigot/KhopeSpigot 1.8.8.

Le code envoyait bien un `PacketPlayOutSpawnEntityLiving`, mais le fake Wither etait positionne a `Y + 1000`. En 1.8, le client n'affiche la bossbar que si le boss reste dans une range proche du joueur. A cette distance, le bossbar peut etre ignoree cote client.

## Correctifs appliques

- Le fake Wither est maintenant positionne proche du joueur.
- Offset configurable via `bossbar.entity_offset_y`, defaut `-30.0`.
- Suivi joueur via `PacketPlayOutEntityTeleport` au refresh si `bossbar.follow_player=true`.
- `bossbar.invisible_entity` configurable, true par defaut maintenant que `WITHER + FRONT + invisible` est valide.
- `bossbar.minimum_progress` configurable, defaut `0.05`.
- `bossbar_timing_reset: 0` signifie maintenant bossbar persistante, et non disparition immediate.
- Alternative ajoutee: `bossbar.entity_type: ENDER_DRAGON` pour tester la methode dragon si le client/fork ignore les fake Withers.
- Level-up ajoute un fallback configurable via `achievement.mode`, par defaut `TITLE_AND_CHAT`.
- Diagnostic renforce: `bossbar.position_mode` permet de tester BELOW/ABOVE/FRONT/EYE_FRONT/PLAYER.
- Resultat test serveur: `/kjobs testhud wither front visible` affiche bien le Wither et la bossbar. Le probleme vient donc du placement par defaut `BELOW`, pas du packet spawn/metadata.
- Resultat final valide: `/kjobs testhud wither front invisible` affiche la bossbar sans rendre le Wither visible. Profil prod retenu: `WITHER + FRONT + invisible`.
- `/kjobs testhud` accepte maintenant des arguments sans changer le YAML: `wither|dragon`, `below|above|front|eye_front|player`, `visible|invisible`.

## Options ajoutees dans hud.yml

```yaml
bossbar:
  test_duration_seconds: 8
  position_mode: FRONT
  entity_offset_y: -30.0
  entity_forward_offset: 24.0
  follow_player: true
  invisible_entity: true
  minimum_progress: 0.05
  entity_type: WITHER
  max_health: 300.0
achievement:
  mode: TITLE_AND_CHAT
  reset_before_send: true
  actionbar: "&6&lNIVEAU {level} &8- &b{job}"
  chat: "&6&lNIVEAU {level} &8- &b{job}"
```

## Test conseille

1. Mettre `debug_hud: true` dans `config.yml`.
2. Redemarrer ou faire `/kjobs reload`.
3. En jeu, executer `/kjobs testhud`.
4. Verifier la console pour les logs `[HUD-DEBUG]`.
5. La bossbar de test disparait automatiquement apres `bossbar.test_duration_seconds`.

Si la bossbar ne s'affiche toujours pas:

- tester `/kjobs testhud wither front invisible`;
- tester `/kjobs testhud wither front visible`;
- tester `/kjobs testhud dragon front visible`;
- tester `/kjobs testhud wither eye_front visible` en regardant vers une zone degagee;
- tester `/kjobs testhud dragon eye_front visible`;
- tester `/kjobs testhud wither above visible`;
- comparer Lunar Client et client vanilla 1.8.9.

## Hypothese actuelle

Les paquets observes dans les logs sont coherents: `PacketPlayOutSpawnEntityLiving`, type 64 pour Wither, type 63 pour EnderDragon, DataWatcher avec nom et sante. Le test `WITHER + FRONT + visible` confirme que le client 1.8 affiche la barre lorsque le boss est charge/rendu correctement. Le test `WITHER + FRONT + invisible` confirme que le flag invisible ne bloque pas la bossbar sur Panda/KhopeSpigot + client vanilla/Lunar.

Decision: utiliser `FRONT + invisible` en prod, avec `follow_player=true`, une distance de 24 blocs, et un timer normal via `bossbar_timing_reset`.
