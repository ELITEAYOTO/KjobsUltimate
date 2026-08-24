package me.krunsh.kjobultimate.hooks;

import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.util.KjobLogger;

/**
 * Centralise les intégrations externes de KjobsUltimate.
 *
 * V3 :
 * - Kgui V2 est obligatoire ;
 * - Vault, PlaceholderAPI, Kcraft, Kfaction et KStacker restent optionnels
 *   selon leur configuration / présence ;
 * - aucun fallback vers un GUI interne n'existe plus.
 */
public final class HookManager implements Listener, AutoCloseable {

    private final KjobUltimate plugin;

    private VaultHook vaultHook;
    private PAPIHook papiHook;

    /*
     * Obligatoire en V3.
     */
    private KguiHook kguiHook;

    private KcraftHook kcraftHook;
    private KfactionHook kfactionHook;
    private KstackerHook kstackerHook;

    private int registeredProviders;
    private boolean started;

    public HookManager(KjobUltimate plugin) {

        if (plugin == null) {
            throw new IllegalArgumentException(
                "KjobUltimate ne peut pas être null."
            );
        }

        this.plugin = plugin;
    }

    /**
     * Initialise d'abord Kgui pour échouer immédiatement si le contrat runtime
     * obligatoire n'est pas disponible.
     */
    public void setupAll() {

        if (!started) {

            plugin.getServer()
                .getPluginManager()
                .registerEvents(
                    this,
                    plugin
                );

            started = true;
        }

        setupRequiredKgui();

        setupVault();
        setupPAPI();
        setupKcraft();
        setupKfaction();
        setupKstacker();
    }

    // -------------------------------------------------------------------------
    // KGUI — OBLIGATOIRE
    // -------------------------------------------------------------------------

    private void setupRequiredKgui() {

        if (kguiHook != null) {
            return;
        }

        Plugin candidate =
            plugin.getServer()
                .getPluginManager()
                .getPlugin("Kgui");

        if (candidate == null
                || !candidate.isEnabled()) {

            throw new IllegalStateException(
                "Kgui est une dépendance obligatoire mais n'est pas actif."
            );
        }

        try {

            kguiHook =
                new KguiHook(plugin);

            registeredProviders =
                kguiHook.getRegisteredProviders();

            KjobLogger.success(
                "Kgui V2 connecté — "
                    + registeredProviders
                    + " ContentProviders enregistrés."
            );

        } catch (RuntimeException failure) {

            kguiHook = null;
            registeredProviders = 0;

            throw new IllegalStateException(
                "Kgui est présent mais son API V2 est indisponible ou incompatible.",
                failure
            );
        }
    }

    // -------------------------------------------------------------------------
    // HOOKS OPTIONNELS
    // -------------------------------------------------------------------------

    private void setupVault() {

        if (!plugin.getConfigManager()
                .getMainConfig()
                .getBoolean(
                    "hooks.vault.enabled",
                    true
                )) {

            return;
        }

        if (plugin.getServer()
                .getPluginManager()
                .getPlugin("Vault") == null) {

            KjobLogger.warn(
                "Vault introuvable — les récompenses économie seront désactivées."
            );

            return;
        }

        vaultHook =
            new VaultHook(plugin);

        if (vaultHook.setup()) {

            KjobLogger.success(
                "Vault connecté — économie disponible."
            );

        } else {

            KjobLogger.warn(
                "Vault présent mais aucun plugin d'économie trouvé."
            );

            vaultHook = null;
        }
    }

    private void setupPAPI() {

        if (!plugin.getConfigManager()
                .getMainConfig()
                .getBoolean(
                    "hooks.placeholderapi.enabled",
                    true
                )) {

            return;
        }

        if (plugin.getServer()
                .getPluginManager()
                .getPlugin("PlaceholderAPI") == null) {

            KjobLogger.warn(
                "PlaceholderAPI introuvable — les placeholders %kjob_*% ne fonctionneront pas."
            );

            return;
        }

        papiHook =
            new PAPIHook(plugin);

        papiHook.register();

        KjobLogger.success(
            "PlaceholderAPI enregistré — %kjob_*% disponibles."
        );
    }

    private void setupKcraft() {

        if (!plugin.getConfigManager()
                .getMainConfig()
                .getBoolean(
                    "hooks.kcraft.enabled",
                    true
                )) {

            return;
        }

        if (plugin.getServer()
                .getPluginManager()
                .getPlugin("Kcraft") == null) {

            KjobLogger.warn(
                "Kcraft introuvable — les crafts custom du job Artisan seront ignorés."
            );

            return;
        }

        kcraftHook =
            new KcraftHook(plugin);

        if (kcraftHook.register()) {
            KjobLogger.success(
                "Kcraft connecté — listener KcraftPostCraftEvent actif."
            );
        } else {
            kcraftHook = null;
        }
    }

    private void setupKfaction() {

        if (!plugin.getConfigManager()
                .getMainConfig()
                .getBoolean(
                    "hooks.kfaction.enabled",
                    true
                )) {

            return;
        }

        Plugin candidate =
            plugin.getServer()
                .getPluginManager()
                .getPlugin("Kfaction");

        if (candidate == null
                || !candidate.isEnabled()) {

            KjobLogger.info(
                "Kfaction introuvable - relations PvP Pretorien ignorees."
            );

            return;
        }

        try {

            kfactionHook =
                new KfactionHook();

            KjobLogger.success(
                "Kfaction API 2.x connectée — relations PvP Prétorien actives."
            );

        } catch (Throwable failure) {

            kfactionHook = null;

            KjobLogger.warn(
                "Kfaction présent mais hook indisponible: "
                    + failure.getMessage()
            );
        }
    }

    private void setupKstacker() {

        if (plugin.getServer()
                .getPluginManager()
                .getPlugin("KStacker") == null) {

            KjobLogger.info(
                "KStacker introuvable — kills comptés sans multiplicateur de stack."
            );

            return;
        }

        kstackerHook =
            new KstackerHook(plugin);

        if (kstackerHook.register()) {
            KjobLogger.success(
                "KStacker connecté — multiplicateur kill actif."
            );
        } else {
            kstackerHook = null;
        }
    }

    // -------------------------------------------------------------------------
    // ACCESSEURS
    // -------------------------------------------------------------------------

    public VaultHook getVaultHook() {
        return vaultHook;
    }

    public PAPIHook getPAPIHook() {
        return papiHook;
    }

    public KguiHook getKguiHook() {
        return kguiHook;
    }

    public KcraftHook getKcraftHook() {
        return kcraftHook;
    }

    public KfactionHook getKfactionHook() {
        return kfactionHook;
    }

    public KstackerHook getKstackerHook() {
        return kstackerHook;
    }

    public boolean isVaultEnabled() {
        return vaultHook != null;
    }

    public boolean isPAPIEnabled() {
        return papiHook != null;
    }

    public boolean isKguiEnabled() {
        return kguiHook != null;
    }

    public boolean isKcraftEnabled() {
        return kcraftHook != null;
    }

    public boolean isKfactionEnabled() {
        return kfactionHook != null;
    }

    public boolean isKstackerEnabled() {
        return kstackerHook != null;
    }

    public int getRegisteredProviders() {
        return registeredProviders;
    }

    // -------------------------------------------------------------------------
    // KGUI
    // -------------------------------------------------------------------------

    public void invalidateKgui(
            UUID playerId,
            String reason,
            String... menuIds) {

        if (kguiHook != null) {

            kguiHook.invalidate(
                playerId,
                reason,
                menuIds
            );
        }
    }

    public boolean openKgui(
            Player player,
            String menuId,
            Map<String, String> arguments) {

        return kguiHook != null
            && kguiHook.openMenu(
                player,
                menuId,
                arguments
            );
    }

    /**
     * Kfaction peut encore être chargé après Kjobs, car il reste optionnel.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnable(
            PluginEnableEvent event) {

        if (event == null
                || event.getPlugin() == null) {

            return;
        }

        String name =
            event.getPlugin()
                .getName();

        if ("Kfaction".equalsIgnoreCase(name)
                && kfactionHook == null) {

            setupKfaction();
        }
    }

    /**
     * Si Kgui disparaît pendant que Kjobs est actif, Kjobs se désactive
     * immédiatement : continuer sans son moteur GUI obligatoire laisserait le
     * plugin dans un état partiellement fonctionnel.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPluginDisable(
            PluginDisableEvent event) {

        if (event == null
                || event.getPlugin() == null) {

            return;
        }

        String name =
            event.getPlugin()
                .getName();

        if ("Kgui".equalsIgnoreCase(name)) {

            closeKgui();

            if (plugin.isEnabled()) {

                KjobLogger.warn(
                    "Kgui a été désactivé — arrêt de KjobsUltimate."
                );

                plugin.getServer()
                    .getPluginManager()
                    .disablePlugin(plugin);
            }

            return;
        }

        if ("Kfaction".equalsIgnoreCase(name)) {
            kfactionHook = null;
        }
    }

    private void closeKgui() {

        KguiHook current =
            kguiHook;

        kguiHook = null;
        registeredProviders = 0;

        if (current == null) {
            return;
        }

        try {
            current.close();
        } catch (RuntimeException failure) {

            KjobLogger.warn(
                "Arrêt du hook Kgui incomplet: "
                    + failure.getMessage()
            );
        }
    }

    @Override
    public void close() {

        closeKgui();

        kfactionHook = null;

        if (started) {
            HandlerList.unregisterAll(this);
        }

        started = false;
    }
}
