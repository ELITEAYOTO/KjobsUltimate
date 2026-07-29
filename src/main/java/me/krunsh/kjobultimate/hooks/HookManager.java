package me.krunsh.kjobultimate.hooks;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.util.KjobLogger;

/**
 * Centralise la détection et l'initialisation de tous les hooks vers les plugins externes.
 */
public final class HookManager {

    private final KjobUltimate plugin;

    private VaultHook    vaultHook;
    private PAPIHook     papiHook;
    private KguiHook     kguiHook;
    private KcraftHook   kcraftHook;
    private KfactionHook kfactionHook;
    private KstackerHook kstackerHook;

    private int registeredProviders = 0;

    public HookManager(KjobUltimate plugin) {
        this.plugin = plugin;
    }

    public void setupAll() {
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
        if (plugin.getServer().getPluginManager().getPlugin("Kgui") != null) {
            kguiHook = new KguiHook(plugin);
            registeredProviders = kguiHook.registerProviders();
            KjobLogger.success("Kgui connecté — " + registeredProviders + " ContentProviders enregistrés.");
        } else {
            KjobLogger.warn("Kgui introuvable — les menus GUI seront désactivés.");
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
        if (plugin.getServer().getPluginManager().getPlugin("Kfaction") != null) {
            try {
                kfactionHook = new KfactionHook(plugin);
                KjobLogger.success("Kfaction connecte - relations PvP Pretorien actives.");
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
}
