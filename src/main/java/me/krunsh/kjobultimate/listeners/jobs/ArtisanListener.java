package me.krunsh.kjobultimate.listeners.jobs;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.jobs.JobDefinition;
import me.krunsh.kjobultimate.jobs.LevelUpResult;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

/**
 * Listener Phase 4 — Job Artisan (craft vanilla Bukkit).
 * Accorde XP quand un joueur fabrique un objet défini dans Artisan.yml.
 *
 * NOTE : le craft Kcraft (KcraftPostCraftEvent) est géré séparément dans KcraftHook.java
 * pour éviter NoClassDefFoundError si le plugin Kcraft est absent au démarrage.
 */
public final class ArtisanListener implements Listener {

    private static final String JOB_ID = "artisan";

    private final KjobUltimate plugin;

    public ArtisanListener(KjobUltimate plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        HumanEntity human = event.getWhoClicked();
        if (!(human instanceof Player)) return;
        Player player = (Player) human;

        // Gate 1 : mode créatif (respecte la config anti_abuse.block_creative)
        if (player.getGameMode() == GameMode.CREATIVE
                && plugin.getConfigManager().isBlockXpCreative()) return;

        Recipe recipe = event.getRecipe();
        if (recipe == null) return;
        ItemStack craftResult = recipe.getResult();
        if (craftResult == null || craftResult.getType() == Material.AIR) return;

        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data == null) return;

        // Gate 3 : job actif dans un slot
        if (!plugin.getSlotManager().isJobActive(data, JOB_ID)) return;

        JobDefinition job = plugin.getJobRegistry().getJob(JOB_ID);
        if (job == null) return;

        // Gate 4 : objet déclaré dans la config du job
        JobDefinition.ActionReward action = job.getAction(craftResult.getType().name());
        if (action == null) return;

        // Gate 7 : plafond quotidien
        plugin.getXpManager().checkDailyReset(data, JOB_ID);
        if (plugin.getXpManager().isDailyCapReached(data, JOB_ID)) {
            player.sendMessage(plugin.getConfigManager().getMessage("anti_abuse.daily_cap_reached")
                .replace("{prefix}", plugin.getConfigManager().getPrefix())
                .replace("{job}", job.getDisplayName()));
            return;
        }

        // Attribution XP
        LevelUpResult result = plugin.getXpManager().addXP(player, data, JOB_ID, action.getXp());

        // Level up
        if (result.isLeveledUp()) {
            plugin.getXpManager().handleLevelUp(player, data, JOB_ID, result);
        }

        if (plugin.getHudManager() != null)
            plugin.getHudManager().onXpGain(player, data, JOB_ID, result.getXpActual(), result);
        if (plugin.getQuestManager() != null) {
            plugin.getQuestManager().progress(player, "CRAFT", craftResult.getType().name(), Math.max(1, craftResult.getAmount()));
        }
    }
}
