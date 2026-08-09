package me.krunsh.kjobultimate.hooks;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.util.KjobLogger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Centralise la détection et l'initialisation de tous les hooks vers les plugins externes.
 */
public final class HookManager implements Listener, AutoCloseable {

    private final KjobUltimate plugin;

    private VaultHook    vaultHook;
    private PAPIHook     papiHook;
    private KguiHook     kguiHook;
    private KcraftHook   kcraftHook;
    private KfactionHook kfactionHook;
    private KstackerHook kstackerHook;

    private int registeredProviders = 0;
    private boolean started;

    public HookManager(KjobUltimate plugin) {
        this.plugin = plugin;
    }

    public void setupAll() {
        if (!started) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            started = true;
        }
        setupVault();
        setupPAPI();
        setupKgui();
        setupKcraft();
        setupKfaction();
        setupKstacker();
    }

    private void setupVault() {
        if (!plugin.getConfigManager().getMainConfig().getBoolean("hooks.vault.enabled", true)) return;
        if (plugin.getServer().getPluginManager().getPlugin("Vault") != null) {
            vaultHook = new VaultHook(plugin);
            if (vaultHook.setup()) {
                KjobLogger.success("Vault connecté — économie disponible.");
            } else {
                KjobLogger.warn("Vault présent mais aucun plugin d'économie trouvé.");
                vaultHook = null;
            }
        } else {
            KjobLogger.warn("Vault introuvable — les récompenses en rewards configurees seront désactivées.");
        }
    }

    private void setupPAPI() {
        if (!plugin.getConfigManager().getMainConfig().getBoolean("hooks.placeholderapi.enabled", true)) return;
        if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            papiHook = new PAPIHook(plugin);
            papiHook.register();
            KjobLogger.success("PlaceholderAPI enregistré — %kjob_*% disponibles.");
        } else {
            KjobLogger.warn("PlaceholderAPI introuvable — les placeholders %kjob_*% ne fonctionneront pas.");
        }
    }

    private void setupKgui() {
        if (!plugin.getConfigManager().getMainConfig().getBoolean("hooks.kgui.enabled", true)) return;
        Plugin candidate = plugin.getServer().getPluginManager().getPlugin("Kgui");
        if (candidate == null || !candidate.isEnabled()) {
            KjobLogger.warn("Kgui introuvable — le GUI interne Kjobs reste actif.");
            return;
        }
        try {
            kguiHook = new KguiHook(plugin);
            registeredProviders = kguiHook.getRegisteredProviders();
            KjobLogger.success("Kgui V2 connecté — " + registeredProviders + " ContentProviders enregistrés.");
        } catch (RuntimeException failure) {
            kguiHook = null;
            registeredProviders = 0;
            KjobLogger.warn("Kgui présent mais API V2 indisponible: " + failure.getMessage());
        }
    }

    private void setupKcraft() {
        if (!plugin.getConfigManager().getMainConfig().getBoolean("hooks.kcraft.enabled", true)) return;
        if (plugin.getServer().getPluginManager().getPlugin("Kcraft") != null) {
            kcraftHook = new KcraftHook(plugin);
            kcraftHook.register();
            KjobLogger.success("Kcraft connecté — listener KcraftPostCraftEvent actif.");
        } else {
            KjobLogger.warn("Kcraft introuvable — le job Artisan ne fonctionnera pas.");
        }
    }

    private void setupKfaction() {
        if (!plugin.getConfigManager().getMainConfig().getBoolean("hooks.kfaction.enabled", true)) return;
        Plugin candidate = plugin.getServer().getPluginManager().getPlugin("Kfaction");
        if (candidate != null && candidate.isEnabled()) {
            try {
                kfactionHook = new KfactionHook();
                KjobLogger.success("Kfaction API 2.x connectée — relations PvP Prétorien actives.");
            } catch (Throwable ex) {
                kfactionHook = null;
                KjobLogger.warn("Kfaction present mais hook indisponible: " + ex.getMessage());
            }
        } else {
            KjobLogger.info("Kfaction introuvable - relations PvP Pretorien ignorees.");
        }
    }

    private void setupKstacker() {
        if (plugin.getServer().getPluginManager().getPlugin("KStacker") != null) {
            kstackerHook = new KstackerHook(plugin);
            kstackerHook.register();
            KjobLogger.success("KStacker connecté — multiplicateur kill actif.");
        } else {
            KjobLogger.info("KStacker introuvable — kills comptés sans multiplicateur de stack.");
        }
    }

    // ─── Accesseurs ─────────────────────────────────────────

    public VaultHook    getVaultHook()    { return vaultHook; }
    public PAPIHook     getPAPIHook()     { return papiHook; }
    public KguiHook     getKguiHook()     { return kguiHook; }
    public KcraftHook   getKcraftHook()   { return kcraftHook; }
    public KfactionHook getKfactionHook() { return kfactionHook; }
    public KstackerHook getKstackerHook() { return kstackerHook; }

    public boolean isVaultEnabled()    { return vaultHook != null; }
    public boolean isPAPIEnabled()     { return papiHook != null; }
    public boolean isKguiEnabled()     { return kguiHook != null; }
    public boolean isKcraftEnabled()   { return kcraftHook != null; }
    public boolean isKfactionEnabled() { return kfactionHook != null; }
    public boolean isKstackerEnabled() { return kstackerHook != null; }

    public int getRegisteredProviders() { return registeredProviders; }

    public void invalidateKgui(UUID playerId, String reason, String... menuIds) {
        if (kguiHook != null) kguiHook.invalidate(playerId, reason, menuIds);
    }

    public boolean openKgui(org.bukkit.entity.Player player, String menuId,
                            java.util.Map<String, String> arguments) {
        return kguiHook != null && kguiHook.openMenu(player, menuId, arguments);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnable(PluginEnableEvent event) {
        if (event == null || event.getPlugin() == null) return;
        String name = event.getPlugin().getName();
        if ("Kgui".equalsIgnoreCase(name) && kguiHook == null) setupKgui();
        if ("Kfaction".equalsIgnoreCase(name) && kfactionHook == null) setupKfaction();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPluginDisable(PluginDisableEvent event) {
        if (event == null || event.getPlugin() == null) return;
        String name = event.getPlugin().getName();
        if ("Kgui".equalsIgnoreCase(name)) closeKgui();
        if ("Kfaction".equalsIgnoreCase(name)) kfactionHook = null;
    }

    private void closeKgui() {
        KguiHook current = kguiHook;
        kguiHook = null;
        registeredProviders = 0;
        if (current == null) return;
        try {
            current.close();
        } catch (RuntimeException failure) {
            KjobLogger.warn("Arrêt du hook Kgui incomplet: " + failure.getMessage());
        }
    }

    @Override
    public void close() {
        closeKgui();
        kfactionHook = null;
        if (started) HandlerList.unregisterAll(this);
        started = false;
    }
}
