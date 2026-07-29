package me.krunsh.kjobultimate.listeners.quests;

import me.krunsh.kjobultimate.KjobUltimate;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Actions de quetes generiques, independantes de l'XP metier.
 * Le QuestManager verifie ensuite job actif, niveau minimum et target.
 */
public final class QuestActionListener implements Listener {

    private final KjobUltimate plugin;

    public QuestActionListener(KjobUltimate plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (shouldIgnore(player)) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) return;
        progress(player, "EAT", item.getType().name(), 1);
        progress(player, "CONSUME", item.getType().name(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        Player player = event.getPlayer();
        if (shouldIgnore(player)) return;

        Material material = event.getItemType();
        if (material == null || material == Material.AIR) return;
        progress(player, "SMELT", material.name(), Math.max(1, event.getItemAmount()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        if (shouldIgnore(player)) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) return;
        progress(player, "ENCHANT", item.getType().name(), 1);
        progress(player, "ENCHANT_LEVELS", item.getType().name(), Math.max(1, event.getExpLevelCost()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        if (shouldIgnore(player)) return;
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH
            && event.getState() != PlayerFishEvent.State.CAUGHT_ENTITY) return;

        Entity caught = event.getCaught();
        if (caught instanceof Item) {
            ItemStack stack = ((Item) caught).getItemStack();
            if (stack == null || stack.getType() == Material.AIR) return;
            progress(player, "FISH", stack.getType().name(), Math.max(1, stack.getAmount()));
            return;
        }

        if (caught != null && caught.getType() != null) {
            progress(player, "FISH", caught.getType().name(), 1);
            progress(player, "FISH_ENTITY", caught.getType().name(), 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (shouldIgnore(player)) return;
        if (event.getBlockPlaced() == null || event.getBlockPlaced().getType() == Material.AIR) return;
        progress(player, "PLACE", event.getBlockPlaced().getType().name(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTame(EntityTameEvent event) {
        if (!(event.getOwner() instanceof Player)) return;
        Player player = (Player) event.getOwner();
        if (shouldIgnore(player)) return;
        if (event.getEntity() == null || event.getEntityType() == null) return;
        progress(player, "TAME", event.getEntityType().name(), 1);
    }

    private boolean shouldIgnore(Player player) {
        if (player == null || plugin.getQuestManager() == null || !plugin.getQuestManager().isEnabled()) return true;
        return player.getGameMode() == GameMode.CREATIVE && plugin.getConfigManager().isBlockXpCreative();
    }

    private void progress(Player player, String type, String target, int amount) {
        plugin.getQuestManager().progress(player, type, target, amount);
    }
}
