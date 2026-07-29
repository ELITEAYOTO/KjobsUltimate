package me.krunsh.kjobultimate.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import java.util.logging.Logger;

/**
 * Logger coloré pour la console KhopeSpigot/PandaSpigot.
 * Utilise les codes ANSI interprétés nativement par Log4j2.
 * Les codes ANSI n'apparaissent pas dans les fichiers .log (comportement correct).
 */
public final class KjobLogger {

    private static final String RESET  = "\u001B[0m";
    private static final String BOLD   = "\u001B[1m";
    private static final String CYAN   = "\u001B[0;36m";
    private static final String WHITE  = "\u001B[0;37m";
    private static final String YELLOW = "\u001B[0;33m";
    private static final String RED    = "\u001B[0;31m";
    private static final String GREEN  = "\u001B[0;32m";
    private static final String PURPLE = "\u001B[0;35m";

    private static final String PREFIX = CYAN + "[KjobsUltimate] " + RESET;

    private static Logger logger;

    private KjobLogger() {}

    public static void init(Plugin plugin) {
        // Utilise le logger global du serveur pour éviter le double préfixe
        // plugin.getLogger() ajoute automatiquement [PluginName] en plus du PREFIX
        logger = Bukkit.getLogger();
    }

    public static void info(String message) {
        logger.info(PREFIX + WHITE + message + RESET);
    }

    public static void success(String message) {
        logger.info(PREFIX + GREEN + "\u2714 " + message + RESET);
    }

    public static void warn(String message) {
        logger.warning(PREFIX + YELLOW + "\u26A0 " + message + RESET);
    }

    public static void error(String message) {
        logger.severe(PREFIX + RED + "\u2716 " + message + RESET);
    }

    public static void error(String message, Throwable t) {
        logger.severe(PREFIX + RED + "\u2716 " + message + " — " + t.getMessage() + RESET);
    }

    public static void reload(String message) {
        logger.info(PREFIX + PURPLE + "\u21BB " + message + RESET);
    }

    public static void printStartupBanner(String version) {
        System.out.println(CYAN + BOLD + "  \u2554\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2557" + RESET);
        System.out.println(CYAN + BOLD + "  \u2551" + RESET + "  " + YELLOW + BOLD + "\u2726 KjobsUltimate" + RESET + "  " + WHITE + "v" + version + RESET + CYAN + BOLD + "                      \u2551" + RESET);
        System.out.println(CYAN + BOLD + "  \u2551" + RESET + "  " + WHITE + "SparrowMC \u2014 Syst\u00e8me de Jobs 1.8.8" + RESET + CYAN + BOLD + "       \u2551" + RESET);
        System.out.println(CYAN + BOLD + "  \u2551" + RESET + "  " + GREEN + "KhopeSpigot / PandaSpigot" + RESET + CYAN + BOLD + "                \u2551" + RESET);
        System.out.println(CYAN + BOLD + "  \u255A\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u255D" + RESET);
    }

    public static void printLoadSummary(int jobsLoaded, int providersRegistered, long startupMs) {
        success("D\u00e9marrage complet en " + WHITE + startupMs + "ms" + RESET);
        info(GREEN + jobsLoaded + " jobs charg\u00e9s" + WHITE + " | " + GREEN + providersRegistered + " providers Kgui" + RESET);
    }
}
