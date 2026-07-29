# HUD Achievement Toast 1.8

## Etat terrain

Le toast achievement vanilla 1.8 est visible avec le mode `BUKKIT`.

Le mode `PACKET` envoie bien `PacketPlayOutStatistic`, mais le client peut ne rien afficher meme si le log indique `Achievement packet toast envoye`. Sur PandaSpigot/KhopeSpigot + client vanilla/Lunar, le packet seul doit donc rester un mode experimental.

## Configuration recommandee

Dans `plugins/KjobsUltimate/hud.yml`, sous `achievement.vanilla_toast` :

```yaml
achievement:
  vanilla_toast:
    enabled: true
    method: BUKKIT
    achievement: OPEN_INVENTORY
    fallback_achievement: OPEN_INVENTORY
    bukkit_after_packet_ticks: 2
    force_reaward: true
    restore_if_not_previously_awarded: true
    restore_after_ticks: 60
    mapping:
      mineur: BUILD_PICKAXE
      farmer: MAKE_BREAD
      hunter: KILL_ENEMY
      pretorien: BUILD_SWORD
      artisan: BUILD_WORKBENCH
      pilleur: OVERPOWERED
```

Modes supportes :

- `BUKKIT` : retire puis redonne l'achievement Bukkit. C'est le mode fiable pour afficher le toast.
- `PACKET` : envoie seulement le packet NMS `PacketPlayOutStatistic`. Plus propre, mais pas fiable visuellement.
- `PACKET_THEN_BUKKIT` : envoie le packet puis force Bukkit apres `bukkit_after_packet_ticks`.

## Texte et icone

Limite Minecraft 1.8 : le serveur ne peut pas envoyer un texte custom ni un ItemStack/NBT custom dans le toast. Il choisit seulement un achievement vanilla.

Le texte se modifie dans le resource pack/lang du client :

```properties
achievement.buildPickaxe=Mineur - Nouveau niveau !
achievement.buildPickaxe.desc=Tu progresses dans le job Mineur.
achievement.makeBread=Farmer - Nouveau niveau !
achievement.makeBread.desc=Tu progresses dans le job Farmer.
achievement.killEnemy=Chasseur - Nouveau niveau !
achievement.killEnemy.desc=Tu progresses dans le job Chasseur.
achievement.buildSword=Pretorien - Nouveau niveau !
achievement.buildSword.desc=Tu progresses dans le job Pretorien.
achievement.buildWorkBench=Artisan - Nouveau niveau !
achievement.buildWorkBench.desc=Tu progresses dans le job Artisan.
achievement.overpowered=Pilleur - Nouveau niveau !
achievement.overpowered.desc=Tu progresses dans le job Pilleur.
```

L'icone depend de l'achievement choisi. Pour la modifier, il faut remplacer la texture de l'item vanilla utilise par cet achievement dans le resource pack. Un CIT base sur NBT ne peut pas cibler ce toast directement, car le toast ne transporte pas d'ItemStack custom.

Si un achievement configure est refuse par Bukkit a cause des prerequis vanilla, `fallback_achievement` est utilise pour afficher quand meme un toast.

## Notes importantes

Un `hud.yml` deja genere n'est pas reecrit automatiquement par le plugin. Les nouvelles cles doivent donc etre ajoutees manuellement ou le fichier doit etre regenere en supprimant l'ancien.

Avec `BUKKIT`, le serveur peut log ou annoncer des messages vanilla du type `lost/earned achievement`. Si ce bruit devient genant, tester `PACKET_THEN_BUKKIT`, puis `PACKET`, mais `PACKET` peut ne pas afficher le toast.
