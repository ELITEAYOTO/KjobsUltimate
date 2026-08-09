package me.krunsh.kjobultimate.hooks;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.util.KjobLogger;
import org.bukkit.event.Listener;

/**
 * Hook KStacker — accès au registre de mobs stackés pour calculer le bon nombre de kills.
 * KStacker expose déjà META_KILL_MULTIPLIER et getExtraKillNbtTag() via ConfigManager.
 * Ce hook stocke une référence au plugin KStacker pour y accéder depuis les listeners.
 */
public final class KstackerHook implements Listener {

    private final KjobUltimate plugin;
    private me.krunsh.kstacker.KStacker kstacker;

    public KstackerHook(KjobUltimate plugin) {
        this.plugin = plugin;
    }

    public void register() {
        try {
            kstacker = (me.krunsh.kstacker.KStacker) plugin.getServer().getPluginManager().getPlugin("KStacker");
            KjobLogger.info("KStacker hook initialisé.");
        } catch (Exception e) {
            KjobLogger.warn("KStacker hook : impossible de caster le plugin — " + e.getMessage());
        }
    }

    /**
     * Retourne true si l'entité est un ghost Kstacker (résultat d'un mob stacké tué).
     */
    public boolean isGhostEntity(org.bukkit.entity.Entity entity) {
        return kstacker != null
            && kstacker.getStackKillResult(entity).isLegitimateStackKill();
    }

    /**
     * Retourne le multiplicateur de kill positionné par Kstacker sur un ghost.
     * La clé de metadata est "kstacker-multiplier" (MobStackService.META_KILL_MULTIPLIER).
     * Si l'entité n'est pas un ghost ou si la metadata est absente, retourne 1.
     */
    public int getKillMultiplier(org.bukkit.entity.Entity entity) {
        if (kstacker == null) return 1;
        return kstacker.getStackKillResult(entity).getUnitsConsumed();
    }

    public boolean isReady() {
        return kstacker != null;
    }
}
