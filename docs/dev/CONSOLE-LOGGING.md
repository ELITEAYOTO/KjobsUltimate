# KjobUltimate — Console Logging Coloré

> Serveur cible : KhopeSpigot (PandaSpigot 1.8.8)
> PandaSpigot supporte nativement les codes ANSI dans la console (hérité de Paper/Spigot).
> L'objectif est un affichage premium reconnaissable dès le démarrage.

---

## 1. Principe

PandaSpigot utilise **Log4j2** sous le capot, avec un terminal ANSI activé.
Les codes d'échappement ANSI (`\u001B[Xm`) sont interprétés nativement dans la console du serveur.

Le `java.util.logging.Logger` de Bukkit (`plugin.getLogger()`) envoie ses messages via la pipeline log4j2 — les codes ANSI passent donc correctement.

> **Note :** Les codes ANSI ne s'affichent **pas** dans les logs fichiers (`.log`), uniquement dans la console interactive. C'est le comportement attendu et correct.

---

## 2. Palette de Couleurs KjobUltimate

```java
// Codes ANSI utilisés par KjobLogger
RESET   = "\u001B[0m"
CYAN    = "\u001B[0;36m"   // Préfixe [KjobUltimate]
WHITE   = "\u001B[0;37m"   // Messages INFO normaux
YELLOW  = "\u001B[0;33m"   // Avertissements (WARN)
RED     = "\u001B[0;31m"   // Erreurs (ERROR/SEVERE)
GREEN   = "\u001B[0;32m"   // Succès (chargement OK, démarrage)
PURPLE  = "\u001B[0;35m"   // Rechargement config, events spéciaux
BOLD    = "\u001B[1m"      // Texte gras (banner)
```

**Résultat dans la console :**
```
[14:32:01 INFO]: [KjobUltimate] ✔ 5 jobs chargés (mineur, farmer, hunter, pretorien, artisant)
[14:32:01 INFO]: [KjobUltimate] ✔ SQLite connecté — plugins/KjobUltimate/data/kjobultimate.db
[14:32:01 INFO]: [KjobUltimate] ✔ Kgui détecté — providers enregistrés
[14:32:01 INFO]: [KjobUltimate] ✔ Vault détecté — rewards monétaires activées
[14:32:01 WARN]: [KjobUltimate] ⚠ PlaceholderAPI absent — placeholders %kjob_xxx% désactivés
[14:32:01 INFO]: [KjobUltimate] ✔ Démarrage complet en 47ms
```

---

## 3. Classe `KjobLogger.java`

**Package :** `me.krunsh.kjobultimate.util`

```java
package me.krunsh.kjobultimate.util;

import org.bukkit.plugin.Plugin;
import java.util.logging.Logger;

/**
 * Logger coloré pour la console PandaSpigot/KhopeSpigot.
 * Utilise les codes ANSI interprétés nativement par Log4j2.
 * Les codes ANSI n'apparaissent pas dans les fichiers .log (comportement correct).
 */
public final class KjobLogger {

    // ─── Codes ANSI ────────────────────────────────────────────────────────────
    private static final String RESET    = "\u001B[0m";
    private static final String BOLD     = "\u001B[1m";
    private static final String CYAN     = "\u001B[0;36m";
    private static final String WHITE    = "\u001B[0;37m";
    private static final String YELLOW   = "\u001B[0;33m";
    private static final String RED      = "\u001B[0;31m";
    private static final String GREEN    = "\u001B[0;32m";
    private static final String PURPLE   = "\u001B[0;35m";

    // ─── Préfixe coloré ────────────────────────────────────────────────────────
    private static final String PREFIX   = CYAN + "[KjobUltimate] " + RESET;

    private static Logger logger;

    private KjobLogger() {}

    /** À appeler dans KjobUltimate.onEnable() avant tout log. */
    public static void init(Plugin plugin) {
        logger = plugin.getLogger();
    }

    /** Message informatif standard. */
    public static void info(String message) {
        logger.info(PREFIX + WHITE + message + RESET);
    }

    /** Message de succès (chargement OK, démarrage réussi). */
    public static void success(String message) {
        logger.info(PREFIX + GREEN + "✔ " + message + RESET);
    }

    /** Avertissement — hook manquant, config incomplète, etc. */
    public static void warn(String message) {
        logger.warning(PREFIX + YELLOW + "⚠ " + message + RESET);
    }

    /** Erreur récupérable — config mal formée, joueur introuvable, etc. */
    public static void error(String message) {
        logger.severe(PREFIX + RED + "✖ " + message + RESET);
    }

    /** Erreur critique avec exception — table DB manquante, NMS fail, etc. */
    public static void error(String message, Throwable t) {
        logger.severe(PREFIX + RED + "✖ " + message + " — " + t.getMessage() + RESET);
    }

    /** Rechargement de config ou événement spécial. */
    public static void reload(String message) {
        logger.info(PREFIX + PURPLE + "↻ " + message + RESET);
    }

    /**
     * Banner de démarrage affiché dans onEnable().
     * Imprime directement sur System.out pour éviter le préfixe date/level du logger.
     */
    public static void printStartupBanner(String version) {
        String c = CYAN;
        String g = GREEN;
        String w = WHITE;
        String r = RESET;
        String b = BOLD;

        System.out.println(c + b + "  ╔══════════════════════════════════════════╗" + r);
        System.out.println(c + b + "  ║" + r + "  " + YELLOW + b + "✦ KjobUltimate" + r
                + "  " + w + "v" + version + r
                + c + b + "                      ║" + r);
        System.out.println(c + b + "  ║" + r + "  " + w + "SparrowMC — Système de Jobs 1.8.8" + r
                + c + b + "       ║" + r);
        System.out.println(c + b + "  ║" + r + "  " + g + "KhopeSpigot / PandaSpigot" + r
                + c + b + "                ║" + r);
        System.out.println(c + b + "  ╚══════════════════════════════════════════╝" + r);
    }

    /**
     * Résumé de démarrage — affiché après le chargement complet.
     * Exemple : KjobLogger.printLoadSummary(5, 3, 47)
     */
    public static void printLoadSummary(int jobsLoaded, int providersRegistered, long startupMs) {
        success("Démarrage complet en " + WHITE + startupMs + "ms" + RESET);
        info(GREEN + jobsLoaded + " jobs chargés" + WHITE
                + " | " + GREEN + providersRegistered + " providers Kgui" + RESET);
    }
}
```

---

## 4. Intégration dans `KjobUltimate.java`

```java
@Override
public void onEnable() {
    long start = System.currentTimeMillis();

    // 1. Initialiser le logger EN PREMIER
    KjobLogger.init(this);
    KjobLogger.printStartupBanner(getDescription().getVersion());

    // 2. Charger les configs
    try {
        configManager = new ConfigManager(this);
        configManager.loadAll();
        KjobLogger.success("Configs chargées (" + configManager.getJobCount() + " jobs)");
    } catch (Exception e) {
        KjobLogger.error("Échec du chargement des configs", e);
        getServer().getPluginManager().disablePlugin(this);
        return;
    }

    // 3. Connecter SQLite
    try {
        storage = new SQLiteStorage(this);
        storage.initialize();
        KjobLogger.success("SQLite connecté — " + storage.getDbPath());
    } catch (Exception e) {
        KjobLogger.error("Impossible de connecter SQLite — désactivation", e);
        getServer().getPluginManager().disablePlugin(this);
        return;
    }

    // 4. Hooks plugins externes
    setupHooks();

    // 5. Enregistrer listeners, commandes, scheduler
    registerListeners();
    registerCommands();
    startSchedulers();

    long elapsed = System.currentTimeMillis() - start;
    KjobLogger.printLoadSummary(configManager.getJobCount(), guiHook.getRegisteredProviders(), elapsed);
}

private void setupHooks() {
    // Vault
    if (getServer().getPluginManager().getPlugin("Vault") != null) {
        vaultHook = new VaultHook(this);
        if (vaultHook.setup()) {
            KjobLogger.success("Vault détecté — rewards monétaires activées");
        } else {
            KjobLogger.warn("Vault présent mais Economy introuvable — rewards monétaires désactivées");
        }
    } else {
        KjobLogger.warn("Vault absent — rewards monétaires désactivées");
    }

    // PlaceholderAPI
    if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
        new KjobPapiExpansion(this).register();
        KjobLogger.success("PlaceholderAPI détecté — expansion %kjob_xxx% enregistrée");
    } else {
        KjobLogger.warn("PlaceholderAPI absent — placeholders %kjob_xxx% désactivés");
    }

    // Kgui
    if (getServer().getPluginManager().getPlugin("Kgui") != null) {
        guiHook = new KguiHook(this);
        guiHook.registerProviders();
        KjobLogger.success("Kgui détecté — " + guiHook.getRegisteredProviders() + " providers enregistrés");
    } else {
        KjobLogger.warn("Kgui absent — GUI seront remplacés par des menus chat");
        guiHook = new KguiFallbackHook(this);
    }

    // Kstacker
    if (getServer().getPluginManager().getPlugin("Kstacker") != null) {
        KjobLogger.success("Kstacker détecté — multiplicateur kills activé");
    } else {
        KjobLogger.warn("Kstacker absent — kills mobs stackés = 1 kill (pas de multiplicateur)");
    }

    // Kcraft
    if (getServer().getPluginManager().getPlugin("Kcraft") != null) {
        KjobLogger.success("Kcraft détecté — hook KcraftCraftCompleteEvent actif");
    } else {
        KjobLogger.warn("Kcraft absent — XP Artisan via crafts Kcraft désactivé (vanilla uniquement)");
    }
}
```

---

## 5. Exemples de Sortie Console

```
  ╔══════════════════════════════════════════╗
  ║  ✦ KjobUltimate  v1.0.0                      ║
  ║  SparrowMC — Système de Jobs 1.8.8       ║
  ║  KhopeSpigot / PandaSpigot                ║
  ╚══════════════════════════════════════════╝
[14:32:01 INFO]: [KjobUltimate] ✔ Configs chargées (5 jobs)
[14:32:01 INFO]: [KjobUltimate] ✔ SQLite connecté — plugins/KjobUltimate/data/kjobultimate.db
[14:32:01 INFO]: [KjobUltimate] ✔ Vault détecté — rewards monétaires activées
[14:32:01 INFO]: [KjobUltimate] ✔ PlaceholderAPI détecté — expansion %kjob_xxx% enregistrée
[14:32:01 INFO]: [KjobUltimate] ✔ Kgui détecté — 2 providers enregistrés
[14:32:01 INFO]: [KjobUltimate] ✔ Kstacker détecté — multiplicateur kills activé
[14:32:01 WARN]: [KjobUltimate] ⚠ Kcraft absent — XP Artisan via crafts Kcraft désactivé (vanilla uniquement)
[14:32:01 INFO]: [KjobUltimate] ✔ Démarrage complet en 47ms
[14:32:01 INFO]: [KjobUltimate] 5 jobs chargés | 2 providers Kgui

--- Sur /kjob reload ---
[14:45:12 INFO]: [KjobUltimate] ↻ Rechargement initié par Administrateur
[14:45:12 INFO]: [KjobUltimate] ↻ Configs rechargées en 12ms
```

---

## 6. Règles d'Usage

| Méthode | Quand l'utiliser |
|---|---|
| `KjobLogger.info()` | Messages informatifs neutres |
| `KjobLogger.success()` | Chargement OK, hook détecté, reload réussi |
| `KjobLogger.warn()` | Hook absent (non bloquant), config manquante avec fallback |
| `KjobLogger.error()` | Erreur récupérable qui n'arrête pas le plugin |
| `KjobLogger.error(msg, t)` | Exception attrapée — affiche le message de l'exception |
| `KjobLogger.reload()` | Toute opération de reload (config, commande admin) |
| `KjobLogger.printStartupBanner()` | Une seule fois dans `onEnable()` |

> **Règle :** Ne jamais utiliser `plugin.getLogger().info(...)` directement dans le code — toujours passer par `KjobLogger`.
> Exception : les messages des commandes joueurs (chat) n'utilisent pas le logger.
