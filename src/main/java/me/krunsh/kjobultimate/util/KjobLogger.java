package me.krunsh.kjobultimate.util;

import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Logger console de KjobsUltimate.
 *
 * Compatible KhopeSpigot / PandaSpigot 1.8.8.
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

    private static final String PREFIX =
        CYAN + "[KjobsUltimate] " + RESET;

    private static Logger logger;

    private KjobLogger() {
    }

    /**
     * Initialise le logger global du serveur.
     *
     * Le logger global évite le double préfixe automatique Bukkit :
     * [KjobsUltimate] [KjobsUltimate] ...
     */
    public static void init(
            Plugin plugin) {

        logger = Bukkit.getLogger();
    }

    public static void info(
            String message) {

        ensureInitialized();

        logger.info(
            PREFIX
                + WHITE
                + safe(message)
                + RESET
        );
    }

    public static void success(
            String message) {

        ensureInitialized();

        logger.info(
            PREFIX
                + GREEN
                + "✔ "
                + safe(message)
                + RESET
        );
    }

    public static void warn(
            String message) {

        ensureInitialized();

        logger.warning(
            PREFIX
                + YELLOW
                + "⚠ "
                + safe(message)
                + RESET
        );
    }

    public static void error(
            String message) {

        ensureInitialized();

        logger.severe(
            PREFIX
                + RED
                + "✖ "
                + safe(message)
                + RESET
        );
    }

    public static void error(
            String message,
            Throwable throwable) {

        ensureInitialized();

        String cause =
            throwable == null
                ? "cause inconnue"
                : throwable.getMessage();

        logger.severe(
            PREFIX
                + RED
                + "✖ "
                + safe(message)
                + " — "
                + safe(cause)
                + RESET
        );
    }

    public static void reload(
            String message) {

        ensureInitialized();

        logger.info(
            PREFIX
                + PURPLE
                + "↻ "
                + safe(message)
                + RESET
        );
    }

    public static void printStartupBanner(
            String version) {

        ensureInitialized();

        System.out.println(
            CYAN
                + BOLD
                + "  ╔══════════════════════════════════════════╗"
                + RESET
        );

        System.out.println(
            CYAN
                + BOLD
                + "  ║"
                + RESET
                + "  "
                + YELLOW
                + BOLD
                + "✦ KjobsUltimate"
                + RESET
                + "  "
                + WHITE
                + "v"
                + safe(version)
                + RESET
                + CYAN
                + BOLD
                + "                      ║"
                + RESET
        );

        System.out.println(
            CYAN
                + BOLD
                + "  ║"
                + RESET
                + "  "
                + WHITE
                + "Volkaria — Système de Jobs 1.8.8"
                + RESET
                + CYAN
                + BOLD
                + "        ║"
                + RESET
        );

        System.out.println(
            CYAN
                + BOLD
                + "  ║"
                + RESET
                + "  "
                + GREEN
                + "KhopeSpigot / PandaSpigot"
                + RESET
                + CYAN
                + BOLD
                + "                ║"
                + RESET
        );

        System.out.println(
            CYAN
                + BOLD
                + "  ╚══════════════════════════════════════════╝"
                + RESET
        );
    }

    public static void printLoadSummary(
            int jobsLoaded,
            int providersRegistered,
            long startupMs) {

        success(
            "Démarrage complet en "
                + WHITE
                + Math.max(0L, startupMs)
                + "ms"
                + RESET
        );

        info(
            GREEN
                + Math.max(0, jobsLoaded)
                + " jobs chargés"
                + WHITE
                + " | "
                + GREEN
                + Math.max(0, providersRegistered)
                + " providers Kgui"
                + RESET
        );
    }

    private static void ensureInitialized() {

        if (logger == null) {
            logger = Bukkit.getLogger();
        }
    }

    private static String safe(
            String value) {

        return value == null
            ? ""
            : value;
    }
}
