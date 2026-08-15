package me.krunsh.kjobultimate.listeners.jobs;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.action.CraftUnitResolver;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.jobs.JobDefinition;
import me.krunsh.kjobultimate.util.ConfiguredItemMatcher;

/**
 * Job Pilleur.
 *
 * V3.14 :
 * - JobActionService centralise XP / money / HUD / quêtes ;
 * - les crafts vanilla utilisent le même accounting shift-click que Artisan ;
 * - nettoyage des TNT posées amorti au lieu d'un scan complet à chaque event.
 */
public final class PilleurListener implements Listener {

    private static final String JOB_ID = "pilleur";
    private static final String TNT_EXPLODE = "TNT_EXPLODE";
    private static final String DYNAMITE_EXPLODE = "DYNAMITE_EXPLODE";
    private static final String TNT_CRAFT = "TNT_CRAFT";
    private static final String DYNAMITE_CRAFT = "DYNAMITE_CRAFT";

    private static final long PLACED_EXPLOSIVE_TTL_MS = 3_600_000L;
    private static final int PLACED_CLEANUP_INTERVAL = 64;

    private final KjobUltimate plugin;
    private final Map<String, PlacedExplosive> placedExplosives =
        new HashMap<String, PlacedExplosive>();
    private final Map<UUID, PlacedExplosive> primedExplosives =
        new HashMap<UUID, PlacedExplosive>();
    private final Map<UUID, WindowCounter> tntExplosionCounters =
        new HashMap<UUID, WindowCounter>();

    private int placedOperations;

    public PilleurListener(KjobUltimate plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.TNT) {
            return;
        }

        String actionKey = isDynamiteItem(event.getItemInHand())
            ? DYNAMITE_EXPLODE
            : TNT_EXPLODE;

        long now = System.currentTimeMillis();
        placedExplosives.put(
            locationKey(event.getBlockPlaced().getLocation()),
            new PlacedExplosive(
                event.getPlayer().getUniqueId(),
                actionKey,
                safeAdd(now, PLACED_EXPLOSIVE_TTL_MS)));

        cleanupPlacedExplosivesMaybe(now);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (event.getEntityType() != EntityType.PRIMED_TNT) {
            return;
        }

        PlacedExplosive placed = findPlacedExplosive(event.getLocation());
        if (placed != null) {
            primedExplosives.put(event.getEntity().getUniqueId(), placed);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        if (entity == null || entity.getType() != EntityType.PRIMED_TNT) {
            return;
        }

        PlacedExplosive placed = primedExplosives.remove(entity.getUniqueId());
        if (placed == null) {
            placed = findPlacedExplosive(entity.getLocation());
        }
        if (placed == null) {
            return;
        }

        Player player = Bukkit.getPlayer(placed.owner);
        if (player == null || !player.isOnline()) {
            return;
        }

        if (TNT_EXPLODE.equals(placed.actionKey)
                && !consumeTntExplosionAllowance(player.getUniqueId())) {
            return;
        }

        grantAction(player, placed.actionKey, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player)
                || event.getRecipe() == null
                || event.getRecipe().getResult() == null) {
            return;
        }

        ItemStack result = event.getRecipe().getResult();
        if (result.getType() == null || result.getType() == Material.AIR) {
            return;
        }

        String actionKey;
        if (isDynamiteItem(result)) {
            actionKey = DYNAMITE_CRAFT;
        } else if (result.getType() == Material.TNT) {
            actionKey = TNT_CRAFT;
        } else {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data == null || !plugin.getSlotManager().isJobActive(data, JOB_ID)) {
            return;
        }

        JobDefinition job = plugin.getJobRegistry().getJob(JOB_ID);
        if (job == null) {
            return;
        }

        JobDefinition.ActionReward action = job.getAction(actionKey);
        if (action == null) {
            return;
        }

        int units = CraftUnitResolver.resolveVanilla(event, action);
        if (units <= 0) {
            return;
        }

        grantResolvedAction(
            player,
            data,
            job,
            action,
            actionKey,
            units);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        tntExplosionCounters.remove(playerId);
    }

    private void grantAction(Player player, String actionKey, int units) {
        if (!isGameModeAllowed(player)) {
            return;
        }

        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data == null || !plugin.getSlotManager().isJobActive(data, JOB_ID)) {
            return;
        }

        JobDefinition job = plugin.getJobRegistry().getJob(JOB_ID);
        if (job == null) {
            return;
        }

        JobDefinition.ActionReward action = job.getAction(actionKey);
        if (action == null) {
            return;
        }

        grantResolvedAction(player, data, job, action, actionKey, units);
    }

    private void grantResolvedAction(
            Player player,
            PlayerData data,
            JobDefinition job,
            JobDefinition.ActionReward action,
            String actionKey,
            int units) {

        if (!isGameModeAllowed(player) || units <= 0) {
            return;
        }

        String target = actionKey.startsWith("DYNAMITE")
            ? "DYNAMITE"
            : "TNT";

        plugin.getJobActionService().apply(
            player,
            data,
            job,
            action,
            units,
            actionKey,
            target);
    }

    private boolean isGameModeAllowed(Player player) {
        if (player == null) {
            return false;
        }

        if (player.hasPermission("kjobsultimate.bypass.gamemodecheck")) {
            return true;
        }

        if (player.getGameMode() == GameMode.CREATIVE
                && plugin.getConfigManager().isBlockXpCreative()) {
            return false;
        }

        return player.getGameMode() != GameMode.SPECTATOR
            || !plugin.getConfigManager().isBlockXpSpectator();
    }

    private boolean consumeTntExplosionAllowance(UUID uuid) {
        if (!plugin.getConfigManager().getMainConfig().getBoolean(
                "pilleur.tnt_xp_limit.enabled",
                true)) {
            return true;
        }

        int amount = Math.max(
            0,
            plugin.getConfigManager().getMainConfig().getInt(
                "pilleur.tnt_xp_limit.amount",
                128));
        if (amount <= 0) {
            return false;
        }

        long seconds = Math.max(
            1L,
            plugin.getConfigManager().getMainConfig().getLong(
                "pilleur.tnt_xp_limit.window_seconds",
                28800L));

        long now = System.currentTimeMillis();
        long windowMs = seconds >= Long.MAX_VALUE / 1000L
            ? Long.MAX_VALUE
            : seconds * 1000L;

        WindowCounter counter = tntExplosionCounters.get(uuid);
        if (counter == null || now >= counter.resetAt) {
            counter = new WindowCounter(safeAdd(now, windowMs));
            tntExplosionCounters.put(uuid, counter);
        }

        if (counter.count >= amount) {
            return false;
        }

        counter.count++;
        return true;
    }

    private boolean isDynamiteItem(ItemStack item) {
        return ConfiguredItemMatcher.matches(
            plugin,
            item,
            "pilleur.dynamite_item");
    }

    private PlacedExplosive findPlacedExplosive(Location location) {
        long now = System.currentTimeMillis();
        cleanupPlacedExplosivesMaybe(now);

        int baseX = location.getBlockX();
        int baseY = location.getBlockY();
        int baseZ = location.getBlockZ();
        String world = location.getWorld().getName();

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    PlacedExplosive placed = placedExplosives.remove(
                        world + ":" + (baseX + x)
                            + ":" + (baseY + y)
                            + ":" + (baseZ + z));

                    if (placed == null) {
                        continue;
                    }

                    if (placed.expiresAt > now) {
                        return placed;
                    }
                }
            }
        }

        return null;
    }

    private void cleanupPlacedExplosivesMaybe(long now) {
        placedOperations++;
        if (placedOperations < PLACED_CLEANUP_INTERVAL
                && placedExplosives.size() < 512) {
            return;
        }

        placedOperations = 0;
        Iterator<Map.Entry<String, PlacedExplosive>> iterator =
            placedExplosives.entrySet().iterator();

        while (iterator.hasNext()) {
            PlacedExplosive explosive = iterator.next().getValue();
            if (explosive == null || explosive.expiresAt <= now) {
                iterator.remove();
            }
        }
    }

    private String locationKey(Location location) {
        return location.getWorld().getName()
            + ":" + location.getBlockX()
            + ":" + location.getBlockY()
            + ":" + location.getBlockZ();
    }

    private static long safeAdd(long first, long second) {
        if (second > 0L && first > Long.MAX_VALUE - second) {
            return Long.MAX_VALUE;
        }
        return first + second;
    }

    private static final class PlacedExplosive {
        private final UUID owner;
        private final String actionKey;
        private final long expiresAt;

        private PlacedExplosive(UUID owner, String actionKey, long expiresAt) {
            this.owner = owner;
            this.actionKey = actionKey;
            this.expiresAt = expiresAt;
        }
    }

    private static final class WindowCounter {
        private final long resetAt;
        private int count;

        private WindowCounter(long resetAt) {
            this.resetAt = resetAt;
        }
    }
}
