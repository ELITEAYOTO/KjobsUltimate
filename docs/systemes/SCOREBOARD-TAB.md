# KjobUltimate — Scoreboard / Tab List

> Décision : Module intégré dans **KjobUltimate** avec hook optionnel vers Kchat.

---

## 1. Décision Architecturale

### Option A : Module dans KjobUltimate (recommandé)
- ✅ Pas de dépendance à Kchat
- ✅ KjobUltimate est autonome
- ✅ Si Kchat est absent, le scoreboard fonctionne quand même
- ✅ Le module peut être désactivé (`scoreboard.enabled: false`)
- ⚠️ Si Kchat gère aussi un tab, les deux peuvent entrer en conflit

### Option B : Intégration dans Kchat
- ✅ Cohérence avec la gestion du chat/tab existante dans Kchat
- ✅ Un seul endroit pour configurer le tab
- ❌ Dépendance obligatoire à Kchat pour le scoreboard jobs
- ❌ Nécessite de modifier Kchat pour chaque évolution du système jobs

### Choix Recommandé : **Option A + Hook de compatibilité Kchat**
- KjobUltimate gère son propre scoreboard
- Si Kchat est détecté, il peut désactiver son propre tab pour laisser la place
- Config : `hooks.kchat.disable_kchat_tab: true`

---

## 2. Technique — Implémentation 1.8.8

En 1.8.8, le "tab list" est affiché via :
- **Header/Footer** : `PacketPlayOutPlayerListHeaderFooter`
- **Noms de joueurs colorés** : `PacketPlayOutScoreboardTeam` (les équipes changent la couleur/prefix des noms dans le tab)

Pour afficher du contenu textuel arbitraire dans le tab (comme des sections Staff/Serveur/Jobs), on utilise une **fake scoreboard** avec des joueurs factices.

**⚠️ Limitation importante en 1.8.8** : Le tab ne peut pas afficher de colonnes arbitraires comme en 1.9+. Il affiche les noms des joueurs connectés classés automatiquement. Pour avoir des "sections" avec du texte libre, on ajoute des **fake players** (entrées de tab fictives via `PacketPlayOutPlayerInfo`) avec des noms colorés comme si c'étaient des joueurs.

---

## 3. Architecture ScoreboardManager

### Approche Header/Footer (simple et efficace)

Pour les infos Serveur et Jobs, la solution la plus propre sur 1.8.8 est d'utiliser le **header et footer du tab** (2 zones de texte multilignes, entièrement libres).

```
┌─────────────────────────────────────────────────────────┐
│              §6§lSparrowMC §8— §7Factions PvP            │  ← Header ligne 1
│           §8En ligne : §f42§8/§f500                      │  ← Header ligne 2
├─────────────────────────────────────────────────────────┤
│  §c[STAFF]  Pseudo1         §7[JOUEURS] Pseudo10         │
│             Pseudo2                     Pseudo11         │
│             Pseudo3                                      │
│                                                          │
├─────────────────────────────────────────────────────────┤
│  §b§lMon Job  §fMineur Lv.7 ████░░ 45%                  │  ← Footer ligne 1
│               §aFarmer Lv.3 ██░░░░ 20%                  │  ← Footer ligne 2
│  §eArgent : §f$12,450                                    │  ← Footer ligne 3
│  §7IP : §fplay.sparrowmc.fr                              │  ← Footer ligne 4
└─────────────────────────────────────────────────────────┘
```

### Approche Fake Players (pour section Staff séparée)

Pour la liste "Staff en ligne" distincte des joueurs normaux, on utilise `PacketPlayOutPlayerInfo` pour ajouter/retirer des entrées fictives avec des noms colorés.

Cependant, cette approche est fragile sur 1.8.8 (les clients voient les faux joueurs comme des vrais). **Recommandation** : utiliser uniquement header/footer pour le contenu textuel, et les équipes Scoreboard pour les couleurs/prefixes des joueurs réels dans la liste.

---

## 4. Packets NMS Utilisés

### 4.1 Header/Footer

```java
// PacketPlayOutPlayerListHeaderFooter
// Classe NMS : net.minecraft.server.v1_8_R3.PacketPlayOutPlayerListHeaderFooter

public void sendHeaderFooter(Player player, String header, String footer) {
    try {
        // Construire le packet via réflexion (champs obfusqués)
        Object packet = PacketPlayOutPlayerListHeaderFooter.class
            .getDeclaredConstructor().newInstance();

        // Champs : a = header, b = footer (noms obfusqués en 1.8.8)
        IChatBaseComponent headerComp = ChatSerializer.a(
            "{\"text\":\"" + ColorUtil.translate(header).replace("\"","\\\"") + "\"}"
        );
        IChatBaseComponent footerComp = ChatSerializer.a(
            "{\"text\":\"" + ColorUtil.translate(footer).replace("\"","\\\"") + "\"}"
        );

        Field headerField = packet.getClass().getDeclaredField("a");
        Field footerField = packet.getClass().getDeclaredField("b");
        headerField.setAccessible(true);
        footerField.setAccessible(true);
        headerField.set(packet, headerComp);
        footerField.set(packet, footerComp);

        ((CraftPlayer) player).getHandle().playerConnection
            .sendPacket((Packet<?>) packet);

    } catch (Exception e) {
        plugin.getLogger().warning("[ScoreboardManager] Erreur header/footer : " + e.getMessage());
    }
}
```

### 4.2 Équipes Scoreboard (couleur des joueurs dans le tab)

```java
// Donner un prefix de rang au joueur dans le tab (via scoreboard team)
public void setPlayerTabPrefix(Player player, String prefix) {
    Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
    String teamName = "job_" + player.getName().substring(0, Math.min(8, player.getName().length()));

    Team team = board.getTeam(teamName);
    if (team == null) team = board.registerNewTeam(teamName);

    team.setPrefix(ColorUtil.translate(prefix)); // ex: "§b[Mineur] "
    team.addPlayer(player);
}
```

---

## 5. ScoreboardManager.java — Structure

```java
public class ScoreboardManager {

    private final KjobUltimate plugin;
    private final ScoreboardConfig config;
    private final PlayerDataManager playerDataManager;
    private final JobManager jobManager;
    private final VaultHook vaultHook;

    /**
     * Initialise le scoreboard pour un joueur (appelé au join).
     */
    public void init(Player player) {
        if (!config.isEnabled()) return;
        refresh(player);
    }

    /**
     * Met à jour header/footer pour un joueur.
     * Appelé par le scheduler toutes les 40 ticks (2 secondes).
     */
    public void refresh(Player player) {
        String header = buildHeader(player);
        String footer = buildFooter(player);
        sendHeaderFooter(player, header, footer);
    }

    /**
     * Construit le header multilignes.
     */
    private String buildHeader(Player player) {
        List<String> lines = new ArrayList<>();
        for (String templateLine : config.getHeaderLines()) {
            lines.add(resolvePlaceholders(player, templateLine));
        }
        return String.join("\n", lines);
    }

    /**
     * Construit le footer multilignes avec :
     * - Section Staff (joueurs avec permission kjob.display.staff)
     * - Infos Serveur
     * - Infos Jobs
     */
    private String buildFooter(Player player) {
        List<String> lines = new ArrayList<>();

        for (ScoreboardSection section : config.getSections()) {
            // Titre de section
            lines.add(ColorUtil.translate(section.getTitle()));

            switch (section.getType()) {
                case STAFF_LIST:
                    buildStaffSection(lines, section);
                    break;
                case SERVER_INFO:
                    buildServerInfoSection(lines, player, section);
                    break;
                case JOBS_INFO:
                    buildJobsInfoSection(lines, player, section);
                    break;
                case CUSTOM:
                    for (String line : section.getLines()) {
                        lines.add(resolvePlaceholders(player, line));
                    }
                    break;
            }
            lines.add(""); // séparateur entre sections
        }

        return String.join("\n", lines);
    }

    private void buildStaffSection(List<String> lines, ScoreboardSection section) {
        String perm = section.getPermission();
        int count = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission(perm)) {
                String format = section.getFormat()
                    .replace("{player_name}", online.getName());
                lines.add(ColorUtil.translate(format));
                count++;
                if (count >= section.getMaxEntries()) break;
            }
        }
        if (count == 0) {
            lines.add(ColorUtil.translate(section.getEmptyText()));
        }
    }

    private void buildJobsInfoSection(List<String> lines, Player player,
                                       ScoreboardSection section) {
        PlayerData data = playerDataManager.get(player);
        for (String jobId : jobManager.getJobIds()) {
            int level = data.getLevel(jobId);
            if (section.isShowOnlyActive() && level == 0) continue;

            Job job = jobManager.getJob(jobId);
            int xp = data.getXP(jobId);
            int xpNext = Math.max(1, job.getXpForLevel(level));
            float pct = (float) xp / xpNext;

            String bar = BarRenderer.render(
                section.getProgressBarConfig(),
                pct
            );

            String line = section.getJobFormat()
                .replace("{job_short_name}", job.getShortName())
                .replace("{level}", String.valueOf(level))
                .replace("{progress_bar}", bar)
                .replace("{progress_pct}", String.valueOf((int)(pct * 100)));

            lines.add(ColorUtil.translate(line));
        }
    }

    /**
     * Remplace les placeholders internes + PAPI si disponible.
     */
    private String resolvePlaceholders(Player player, String text) {
        PlayerData data = playerDataManager.get(player);

        text = text
            .replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()))
            .replace("{max_online}", String.valueOf(Bukkit.getMaxPlayers()))
            .replace("{player_name}", player.getName());

        if (vaultHook.isEnabled()) {
            double balance = vaultHook.getEconomy().getBalance(player);
            text = text.replace("{money}", String.format("%.0f", balance));
        }

        // PAPI hook si disponible
        if (placeHolderApiHook.isEnabled()) {
            text = placeHolderApiHook.setPlaceholders(player, text);
        }

        return ColorUtil.translate(text);
    }
}
```

---

## 6. Intégration avec le Scheduler Global

```java
// Dans le scheduler global toutes les 40 ticks
if (config.isScoreboardEnabled()) {
    for (Player player : Bukkit.getOnlinePlayers()) {
        scoreboardManager.refresh(player);
    }
}
```

---

## 7. scoreboard.yml — Config Complète

(Voir [CONFIG-REFERENCE.md](CONFIG-REFERENCE.md) section `scoreboard.yml`)

---

## 8. Hook Kchat — Désactiver son Tab

Si Kchat gère aussi un header/footer de tab, il faut éviter le conflit :

```java
// Dans KchatHook.java
public void disableKchatTab() {
    Plugin kchat = Bukkit.getPluginManager().getPlugin("Kchat");
    if (kchat == null) return;

    // Via réflexion, accéder au configManager de Kchat et désactiver le tab
    try {
        Method getConfig = kchat.getClass().getMethod("getConfigManager");
        Object configMgr = getConfig.invoke(kchat);
        // Chercher une méthode setTabEnabled(false)
        Method setTab = configMgr.getClass().getMethod("setTabEnabled", boolean.class);
        setTab.invoke(configMgr, false);
        plugin.getLogger().info("[KjobUltimate] Kchat tab désactivé, KjobUltimate gère le tab.");
    } catch (Exception e) {
        plugin.getLogger().warning("[KjobUltimate] Impossible de désactiver le tab Kchat. Conflit possible.");
    }
}
```

---

## 9. Performance

| Opération | Fréquence | Coût |
|---|---|---|
| Build header string | 1x / 40 ticks / joueur | Minimal |
| Build footer string (avec boucles) | 1x / 40 ticks / joueur | Moyen |
| `sendHeaderFooter` (1 packet NMS) | 1x / 40 ticks / joueur | Minimal |
| Scoreboard team update (rank prefix) | 1x / connexion | Minimal |

Avec 600 joueurs :
- 600 × 1 packet header/footer toutes les 2 secondes = **300 packets/s**
- Chaque packet contient ~200-500 bytes de texte
- **~75-150 KB/s réseau** côté envoi serveur — acceptable

Si c'est trop, augmenter `refresh_ticks: 60` (toutes les 3 secondes).
