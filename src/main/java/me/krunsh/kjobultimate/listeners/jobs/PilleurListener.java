package me.krunsh.kjobultimate.listeners.jobs;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.jobs.JobDefinition;
import me.krunsh.kjobultimate.jobs.LevelUpResult;
import me.krunsh.kjobultimate.util.ConfiguredItemMatcher;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Job Pilleur V1.
 *
 * Garde en RAM le proprietaire des TNT posees, puis attribue l'XP a l'explosion.
 * Les compteurs anti-abus TNT sont aussi RAM-only, comme les cooldowns existants.
 */
public final class PilleurListener implements Listener {

    private static final String JOB_ID = "pilleur";
    private static final String TNT_EXPLODE = "TNT_EXPLODE";
    private static final String DYNAMITE_EXPLODE = "DYNAMITE_EXPLODE";
    private static final String TNT_CRAFT = "TNT_CRAFT";

    private final KjobUltimate plugin;
    private final Map<String, PlacedExplosive> placedExplosives = new HashMap<String, PlacedExplosive>();
    private final Map<UUID, PlacedExplosive> primedExplosives = new HashMap<UUID, PlacedExplosive>();
    private final Map<UUID, WindowCounter> tntExplosionCounters = new HashMap<UUID, WindowCounter>();

    public PilleurListener(KjobUltimate plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.TNT) return;

        String actionKey = isDynamiteItem(event.getItemInHand()) ? DYNAMITE_EXPLODE : TNT_EXPLODE;
        placedExplosives.put(locationKey(event.getBlockPlaced().getLocation()),
            new PlacedExplosive(event.getPlayer().getUniqueId(), actionKey, System.currentTimeMillis() + 3_600_000L));
        cleanupPlacedExplosives();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (event.getEntityType() != EntityType.PRIMED_TNT) return;
        PlacedExplosive placed = findPlacedExplosive(event.getLocation());
        if (placed != null) {
            primedExplosives.put(event.getEntity().getUniqueId(), placed);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        if (entity == null || entity.getType() != EntityType.PRIMED_TNT) return;

        PlacedExplosive placed = primedExplosives.remove(entity.getUniqueId());
        if (placed == null) placed = findPlacedExplosive(entity.getLocation());
        if (placed == null) return;

        Player player = Bukkit.getPlayer(placed.owner);
        if (player == null || !player.isOnline()) return;

        if (TNT_EXPLODE.equals(placed.actionKey)) {
            if (!consumeTntExplosionAllowance(player.getUniqueId())) return;
        }
        grantXp(player, placed.actionKey, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (event.getRecipe() == null || event.getRecipe().getResult() == null) return;
        ItemStack result = event.getRecipe().getResult();

        Player player = (Player) event.getWhoClicked();
        if (isDynamiteItem(result)) {
            grantXp(player, "DYNAMITE_CRAFT", Math.max(1, result.getAmount()));
        } else if (result.getType() == Material.TNT) {
            grantXp(player, TNT_CRAFT, Math.max(1, result.getAmount()));
        }
    }

    private void grantXp(Player player, String actionKey, int amount) {
        if (player.getGameMode() == GameMode.CREATIVE && plugin.getConfigManager().isBlockXpCreative()) return;
        if (player.getGameMode() == GameMode.SPECTATOR && plugin.getConfigManager().isBlockXpSpectator()) return;

        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data == null) return;
        if (!plugin.getSlotManager().isJobActive(data, JOB_ID)) return;

        JobDefinition job = plugin.getJobRegistry().getJob(JOB_ID);
        if (job == null) return;
        JobDefinition.ActionReward action = job.getAction(actionKey);
        if (action == null) return;

        plugin.getXpManager().checkDailyReset(data, JOB_ID);
        if (plugin.getXpManager().isDailyCapReached(data, JOB_ID)) {
            player.sendMessage(plugin.getConfigManager().getMessage("anti_abuse.daily_cap_reached")
                .replace("{job}", job.getDisplayName()));
            return;
        }

        int baseXp = Math.max(1, amount) * action.getXp();
        LevelUpResult xpResult = plugin.getXpManager().addXP(player, data, JOB_ID, baseXp);
        if (xpResult.isLeveledUp()) {
            plugin.getXpManager().handleLevelUp(player, data, JOB_ID, xpResult);
        }
        if (plugin.getHudManager() != null) {
            plugin.getHudManager().onXpGain(player, data, JOB_ID, xpResult.getXpActual(), xpResult);
        }
        if (plugin.getQuestManager() != null) {
            String target = actionKey.startsWith("DYNAMITE") ? "DYNAMITE" : "TNT";
            plugin.getQuestManager().progress(player, actionKey, target, Math.max(1, amount));
        }
    }

    private boolean consumeTntExplosionAllowance(UUID uuid) {
        if (!plugin.getConfigManager().getMainConfig().getBoolean("pilleur.tnt_xp_limit.enabled", true)) return true;

        int amount = plugin.getConfigManager().getMainConfig().getInt("pilleur.tnt_xp_limit.amount", 128);
        long windowMs = plugin.getConfigManager().getMainConfig().getLong("pilleur.tnt_xp_limit.window_seconds", 28800L) * 1000L;
        long now = System.currentTimeMillis();

        WindowCounter counter = tntExplosionCounters.get(uuid);
        if (counter == null || now >= counter.resetAt) {
            counter = new WindowCounter(now + windowMs);
            tntExplosionCounters.put(uuid, counter);
        }
        if (counter.count >= amount) return false;
        counter.count++;
        return true;
    }

    private boolean isDynamiteItem(ItemStack item) {
        return ConfiguredItemMatcher.matches(plugin, item, "pilleur.dynamite_item")
            || ConfiguredItemMatcher.matches(plugin, item, "pilleur.dynamite_nbt_example");
    }

    private PlacedExplosive findPlacedExplosive(Location location) {
        cleanupPlacedExplosives();
        int baseX = location.getBlockX();
        int baseY = location.getBlockY();
        int baseZ = location.getBlockZ();
        String world = location.getWorld().getName();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    PlacedExplosive placed = placedExplosives.remove(world + ":" + (baseX + x) + ":" + (baseY + y) + ":" + (baseZ + z));
                    if (placed != null) return placed;
                }
            }
        }
        return null;
    }

    private void cleanupPlacedExplosives() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, PlacedExplosive>> it = placedExplosives.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().expiresAt <= now) it.remove();
        }
    }

    private String locationKey(Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private static final class PlacedExplosive {
        final UUID owner;
        final String actionKey;
        final long expiresAt;

        PlacedExplosive(UUID owner, String actionKey, long expiresAt) {
            this.owner = owner;
            this.actionKey = actionKey;
            this.expiresAt = expiresAt;
        }
    }

    private static final class WindowCounter {
        final long resetAt;
        int count;

        WindowCounter(long resetAt) {
            this.resetAt = resetAt;
        }
    }
}
