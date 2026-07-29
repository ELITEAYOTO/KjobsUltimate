package me.krunsh.kjobultimate.hooks;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.util.KjobLogger;
import org.bukkit.Bukkit;

/**
 * Hook Kgui — enregistre les ContentProviders des menus KjobUltimate.
 * Les providers sont enregistrés dans le ContentProviderManager de Kgui.
 * Cette classe est un squelette — les providers réels sont ajoutés en Phase 7 (GUI).
 */
public final class KguiHook {

    private final KjobUltimate plugin;

    public KguiHook(KjobUltimate plugin) {
        this.plugin = plugin;
    }

    /**
     * Enregistre tous les ContentProviders.
     * @return Nombre de providers enregistrés
     */
    public int registerProviders() {
        // TODO Phase 7 : enregistrer les ContentProviders pour :
        //   - "kjobs_main"      → menu principal des jobs
        //   - "kjobs_detail"    → détail d'un job (XP, quêtes, slots)
        //   - "kjobs_quests"    → liste des quêtes actives
        //   - "kjobs_hud"       → toggle HUD dans le menu
        KjobLogger.info("Kgui hook initialisé — providers GUI à enregistrer en Phase 7.");
        return 0;
    }

    /**
     * Ouvre un menu Kgui pour le joueur avec des arguments dynamiques.
     * @param player Joueur cible
     * @param menuId Identifiant du menu (doit exister dans menus/)
     * @param args   Arguments dynamiques pour le ContentProvider
     */
    public void openMenu(org.bukkit.entity.Player player, String menuId, java.util.Map<String, String> args) {
        try {
            me.krunsh.kgui.Kgui kgui = (me.krunsh.kgui.Kgui) Bukkit.getPluginManager().getPlugin("Kgui");
            if (kgui == null) {
                KjobLogger.warn("openMenu() appelé mais Kgui est null.");
                return;
            }
            kgui.getGuiManager().openMenu(player, menuId, args);
        } catch (Exception e) {
            KjobLogger.error("Erreur lors de l'ouverture du menu " + menuId, e);
        }
    }
}
