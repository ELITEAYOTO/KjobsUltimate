package me.krunsh.kjobultimate.gui;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.util.KjobLogger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Fonctions optionnelles appliquées aux items des GUI KjobsUltimate.
 *
 * Compatibilité :
 * - Java 8 / Bukkit 1.8.8 ;
 * - HeadDatabase optionnel, appelé par réflexion ;
 * - aucun code HeadDatabase n'est inclus dans le jar final.
 */
public final class GuiItemFeatures {

    private static final String[] HEAD_ID_KEYS = {
        "head_database",
        "head-database",
        "hdb",
        "hdb_id",
        "head_id"
    };

    private static final Set<String> WARNED_HEAD_IDS =
        Collections.synchronizedSet(new HashSet<String>());

    private static volatile Object headDatabaseApi;
    private static volatile Method getItemHeadMethod;
    private static volatile boolean headDatabaseLookupFailed;

    private GuiItemFeatures() {
    }

    /**
     * Construit l'item de base.
     *
     * Quand un ID HDB est configuré et HeadDatabase est disponible, la tête
     * HDB devient l'item de base. Le nom, le lore, le NBT et le glow sont
     * ensuite appliqués normalement par GuiManager.
     */
    public static ItemStack createBaseItem(
            KjobUltimate plugin,
            ConfigurationSection section,
            Material fallbackMaterial,
            int amount,
            short data) {

        int safeAmount = Math.max(1, amount);
        String headId = readHeadId(section);

        if (!headId.isEmpty()) {
            ItemStack head = loadHead(plugin, headId);

            if (head != null && head.getType() != Material.AIR) {
                ItemStack copy = head.clone();
                copy.setAmount(safeAmount);
                return copy;
            }

            // Un HDB sans material explicite retombe proprement sur une tête.
            if (section == null || !section.contains("material")) {
                return new ItemStack(
                    Material.SKULL_ITEM,
                    safeAmount,
                    (short) 3);
            }
        }

        Material material =
            fallbackMaterial == null
                ? Material.STONE
                : fallbackMaterial;

        return new ItemStack(
            material,
            safeAmount,
            data);
    }

    /**
     * Ajoute le faux enchantement visuel sans afficher sa ligne dans le lore.
     */
    public static ItemStack applyGlow(
            ItemStack item,
            ConfigurationSection section) {

        if (item == null
                || section == null
                || !section.getBoolean("glow", false)) {
            return item;
        }

        try {
            item.addUnsafeEnchantment(
                Enchantment.DURABILITY,
                1);

            ItemMeta meta = item.getItemMeta();

            if (meta != null) {
                meta.addItemFlags(
                    ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(meta);
            }
        } catch (Throwable failure) {
            KjobLogger.warn(
                "[GUI] Impossible d'appliquer glow: "
                    + failure.getMessage());
        }

        return item;
    }

    public static boolean hasHeadDatabaseId(
            ConfigurationSection section) {
        return !readHeadId(section).isEmpty();
    }

    private static String readHeadId(
            ConfigurationSection section) {

        if (section == null) return "";

        for (String key : HEAD_ID_KEYS) {
            if (!section.contains(key)) continue;

            Object raw = section.get(key);

            if (raw == null) continue;

            String id = String.valueOf(raw).trim();

            if (!id.isEmpty()) return id;
        }

        return "";
    }

    private static ItemStack loadHead(
            KjobUltimate plugin,
            String headId) {

        Plugin hdb =
            Bukkit.getPluginManager()
                .getPlugin("HeadDatabase");

        if (hdb == null || !hdb.isEnabled()) {
            warnHeadOnce(
                headId,
                "[GUI] HeadDatabase absent ou inactif; "
                    + "fallback utilisé pour HDB "
                    + headId
                    + ".");
            return null;
        }

        try {
            initializeHeadDatabaseReflection();

            if (headDatabaseApi == null
                    || getItemHeadMethod == null) {
                return null;
            }

            Object result =
                getItemHeadMethod.invoke(
                    headDatabaseApi,
                    headId);

            return result instanceof ItemStack
                ? (ItemStack) result
                : null;
        } catch (Throwable failure) {
            warnHeadOnce(
                headId,
                "[GUI] Impossible de charger la tête HDB "
                    + headId
                    + ": "
                    + rootMessage(failure));
            return null;
        }
    }

    private static synchronized void initializeHeadDatabaseReflection()
            throws Exception {

        if (headDatabaseApi != null
                && getItemHeadMethod != null) {
            return;
        }

        if (headDatabaseLookupFailed) return;

        try {
            Class<?> apiClass =
                Class.forName(
                    "me.arcaniax.hdb.api.HeadDatabaseAPI");

            Constructor<?> constructor =
                apiClass.getConstructor();

            Object api = constructor.newInstance();

            Method method =
                apiClass.getMethod(
                    "getItemHead",
                    String.class);

            headDatabaseApi = api;
            getItemHeadMethod = method;
        } catch (Exception failure) {
            headDatabaseLookupFailed = true;
            throw failure;
        }
    }

    private static void warnHeadOnce(
            String headId,
            String message) {

        if (headId == null) return;

        if (WARNED_HEAD_IDS.add(headId)) {
            KjobLogger.warn(message);
        }
    }

    private static String rootMessage(
            Throwable failure) {

        Throwable current = failure;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        String message = current.getMessage();

        return message == null
            ? current.getClass().getSimpleName()
            : message;
    }
}