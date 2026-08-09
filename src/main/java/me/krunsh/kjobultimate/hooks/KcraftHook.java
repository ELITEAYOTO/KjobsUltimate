package me.krunsh.kjobultimate.hooks;

import me.krunsh.kcraft.api.events.KcraftPostCraftEvent;
import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.jobs.JobDefinition;
import me.krunsh.kjobultimate.jobs.LevelUpResult;
import me.krunsh.kjobultimate.util.KjobLogger;
import me.krunsh.kjobultimate.util.ConfiguredItemMatcher;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

/**
 * Hook Kcraft - ecoute KcraftPostCraftEvent pour les crafts custom.
 * Enregistre Artisan, puis Pilleur si l'item correspond a la dynamite NBT configuree.
 */
public final class KcraftHook implements Listener {

    private final KjobUltimate plugin;

    public KcraftHook(KjobUltimate plugin) {
        this.plugin = plugin;
    }

    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        KjobLogger.info("Kcraft hook enregistre - listener KcraftPostCraftEvent actif.");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onKcraftPost(KcraftPostCraftEvent event) {
        if (!event.isSuccess()) return;

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;
        if (player.getGameMode() == GameMode.SPECTATOR) return;

        ItemStack craftResult = event.getResult();
        if (craftResult == null) return;

        handleArtisanCraft(player, craftResult);
        handlePilleurDynamiteCraft(player, craftResult);
    }

    private void handleArtisanCraft(Player player, ItemStack craftResult) {
        JobDefinition job = plugin.getJobRegistry().getJob("artisan");
        if (job == null) return;

        JobDefinition.ActionReward action = job.getAction(craftResult.getType().name());
        if (action == null) return;

        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data == null) return;
        if (!plugin.getSlotManager().isJobActive(data, "artisan")) return;

        plugin.getXpManager().checkDailyReset(data, "artisan");
        if (plugin.getXpManager().isDailyCapReached(data, "artisan")) {
            player.sendMessage(plugin.getConfigManager().getMessage("anti_abuse.daily_cap_reached")
                .replace("{job}", job.getDisplayName()));
            return;
        }

        LevelUpResult result = plugin.getXpManager().addXP(player, data, "artisan", action.getXp());
        if (result.isLeveledUp()) plugin.getXpManager().handleLevelUp(player, data, "artisan", result);
        if (plugin.getHudManager() != null) plugin.getHudManager().onXpGain(player, data, "artisan", action.getXp(), result);
        if (plugin.getQuestManager() != null) {
            plugin.getQuestManager().progress(player, "CRAFT", craftResult.getType().name(), Math.max(1, craftResult.getAmount()));
        }
    }

    private void handlePilleurDynamiteCraft(Player player, ItemStack craftResult) {
        if (!isDynamiteItem(craftResult)) return;

        JobDefinition job = plugin.getJobRegistry().getJob("pilleur");
        if (job == null) return;
        JobDefinition.ActionReward action = job.getAction("DYNAMITE_CRAFT");
        if (action == null) return;

        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data == null) return;
        if (!plugin.getSlotManager().isJobActive(data, "pilleur")) return;

        plugin.getXpManager().checkDailyReset(data, "pilleur");
        if (plugin.getXpManager().isDailyCapReached(data, "pilleur")) {
            player.sendMessage(plugin.getConfigManager().getMessage("anti_abuse.daily_cap_reached")
                .replace("{job}", job.getDisplayName()));
            return;
        }

        LevelUpResult result = plugin.getXpManager().addXP(player, data, "pilleur", action.getXp() * Math.max(1, craftResult.getAmount()));
        if (result.isLeveledUp()) plugin.getXpManager().handleLevelUp(player, data, "pilleur", result);
        if (plugin.getHudManager() != null) plugin.getHudManager().onXpGain(player, data, "pilleur", result.getXpActual(), result);
        if (plugin.getQuestManager() != null) {
            plugin.getQuestManager().progress(player, "DYNAMITE_CRAFT", "DYNAMITE", Math.max(1, craftResult.getAmount()));
        }
    }

    private boolean isDynamiteItem(ItemStack item) {
        return ConfiguredItemMatcher.matches(plugin, item, "pilleur.dynamite_item")
            || ConfiguredItemMatcher.matches(plugin, item, "pilleur.dynamite_nbt_example");
    }
}
