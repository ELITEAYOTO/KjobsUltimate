package me.krunsh.kjobultimate.listeners.jobs;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.jobs.JobDefinition;
import me.krunsh.kjobultimate.jobs.LevelUpResult;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Job Pretorien: XP sur kill PvP avec protections anti-farm faction.
 */
public final class PretorienListener implements Listener {

    private static final String JOB_ID = "pretorien";
    private static final String ACTION_KEY = "pvp_kill";

    private final KjobUltimate plugin;
    private final Map<UUID, CombatHit> lastHits = new HashMap<UUID, CombatHit>();
    private final Map<UUID, WindowCounter> dailyKillCounters = new HashMap<UUID, WindowCounter>();

    public PretorienListener(KjobUltimate plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player victim = (Player) event.getEntity();
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || attacker.equals(victim)) return;

        long durationMs = getLastHitSeconds() * 1000L;
        if (durationMs <= 0L) return;

        lastHits.put(victim.getUniqueId(), new CombatHit(attacker.getUniqueId(), System.currentTimeMillis() + durationMs));
        cleanupLastHits();
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = resolveKiller(victim);

        if (killer == null || killer.equals(victim)) return;
        if (isBlockedGameMode(killer)) return;
        if (isRelationBlocked(killer, victim)) return;
        if (isSameIpBlocked(killer, victim)) return;

        PlayerData data = plugin.getPlayerDataManager().get(killer);
        if (data == null) return;
        if (!plugin.getSlotManager().isJobActive(data, JOB_ID)) return;

        JobDefinition job = plugin.getJobRegistry().getJob(JOB_ID);
        if (job == null) return;

        JobDefinition.ActionReward action = job.getAction(ACTION_KEY);
        if (action == null) return;

        if (isVictimOnCooldown(data, killer, victim)) return;

        plugin.getXpManager().checkDailyReset(data, JOB_ID);
        if (plugin.getXpManager().isDailyCapReached(data, JOB_ID)) {
            sendIfNotEmpty(killer, "anti_abuse.daily_cap_reached",
                plugin.getConfigManager().getMessage("anti_abuse.daily_cap_reached")
                    .replace("{job}", job.getDisplayName()));
            return;
        }

        if (!consumeDailyKillAllowance(killer)) return;

        long cooldownMs = getVictimCooldownSeconds() * 1000L;
        if (cooldownMs > 0L) data.setPvpTargetCooldown(victim.getUniqueId(), cooldownMs);

        LevelUpResult result = plugin.getXpManager().addXP(killer, data, JOB_ID, action.getXp());

        if (action.getMoney() > 0 && plugin.getHookManager().isVaultEnabled()) {
            plugin.getHookManager().getVaultHook().deposit(killer.getName(), action.getMoney());
        }

        if (result.isLeveledUp()) {
            plugin.getXpManager().handleLevelUp(killer, data, JOB_ID, result);
        }
        if (plugin.getHudManager() != null) {
            plugin.getHudManager().onXpGain(killer, data, JOB_ID, result.getXpActual(), result);
        }
        if (plugin.getQuestManager() != null) {
            plugin.getQuestManager().progress(killer, "PVP_KILL", "PLAYER", 1);
        }
    }

    private Player resolveKiller(Player victim) {
        Player direct = victim.getKiller();
        if (direct != null) {
            lastHits.remove(victim.getUniqueId());
            return direct;
        }

        CombatHit hit = lastHits.remove(victim.getUniqueId());
        if (hit == null || hit.expiresAt <= System.currentTimeMillis()) return null;
        Player attacker = Bukkit.getPlayer(hit.attacker);
        return attacker != null && attacker.isOnline() ? attacker : null;
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player) return (Player) damager;
        if (damager instanceof Projectile) {
            ProjectileSource source = ((Projectile) damager).getShooter();
            if (source instanceof Player) return (Player) source;
        }
        return null;
    }

    private boolean isBlockedGameMode(Player killer) {
        boolean bypass = killer.hasPermission("kjobsultimate.bypass.gamemodecheck")
            || killer.hasPermission("kjob.bypass.gamemodecheck");
        if (bypass) return false;
        if (killer.getGameMode() == GameMode.CREATIVE && plugin.getConfigManager().isBlockXpCreative()) return true;
        return killer.getGameMode() == GameMode.SPECTATOR && plugin.getConfigManager().isBlockXpSpectator();
    }

    private boolean isRelationBlocked(Player killer, Player victim) {
        if (plugin.getHookManager() == null || !plugin.getHookManager().isKfactionEnabled()) return false;

        String relation = plugin.getHookManager().getKfactionHook().getRelationName(killer, victim);
        if ("MEMBER".equalsIgnoreCase(relation) && getConfig().getBoolean("anti_abuse.pvp.block_same_faction", true)) {
            sendIfNotEmpty(killer, "anti_abuse.pvp_relation_blocked",
                plugin.getConfigManager().getMessage("anti_abuse.pvp_relation_blocked")
                    .replace("{relation}", relation));
            return true;
        }
        if ("ALLY".equalsIgnoreCase(relation) && getConfig().getBoolean("anti_abuse.pvp.block_ally", true)) {
            sendIfNotEmpty(killer, "anti_abuse.pvp_relation_blocked",
                plugin.getConfigManager().getMessage("anti_abuse.pvp_relation_blocked")
                    .replace("{relation}", relation));
            return true;
        }
        if ("TRUCE".equalsIgnoreCase(relation) && getConfig().getBoolean("anti_abuse.pvp.block_truce", true)) {
            sendIfNotEmpty(killer, "anti_abuse.pvp_relation_blocked",
                plugin.getConfigManager().getMessage("anti_abuse.pvp_relation_blocked")
                    .replace("{relation}", relation));
            return true;
        }
        return false;
    }

    private boolean isSameIpBlocked(Player killer, Player victim) {
        if (!getConfig().getBoolean("anti_abuse.pvp.same_ip_block.enabled", false)) return false;
        String killerIp = getIp(killer);
        String victimIp = getIp(victim);
        if (killerIp == null || victimIp == null || !killerIp.equals(victimIp)) return false;

        sendIfNotEmpty(killer, "anti_abuse.pvp_same_ip_blocked",
            plugin.getConfigManager().getMessage("anti_abuse.pvp_same_ip_blocked")
                .replace("{player}", victim.getName()));
        return true;
    }

    private boolean isVictimOnCooldown(PlayerData data, Player killer, Player victim) {
        if (getVictimCooldownSeconds() <= 0L) return false;
        if (!data.isPvpTargetOnCooldown(victim.getUniqueId())) return false;

        sendIfNotEmpty(killer, "anti_abuse.pvp_cooldown",
            plugin.getConfigManager().getMessage("anti_abuse.pvp_cooldown")
                .replace("{player}", victim.getName()));
        return true;
    }

    private boolean consumeDailyKillAllowance(Player killer) {
        if (!getConfig().getBoolean("anti_abuse.pvp.daily_kill_cap.enabled", true)) return true;

        int amount = getConfig().getInt("anti_abuse.pvp.daily_kill_cap.amount", 80);
        long windowMs = getConfig().getLong("anti_abuse.pvp.daily_kill_cap.window_seconds", 86400L) * 1000L;
        long now = System.currentTimeMillis();

        WindowCounter counter = dailyKillCounters.get(killer.getUniqueId());
        if (counter == null || now >= counter.resetAt) {
            counter = new WindowCounter(now + windowMs);
            dailyKillCounters.put(killer.getUniqueId(), counter);
        }

        if (counter.count >= amount) {
            sendIfNotEmpty(killer, "anti_abuse.pvp_daily_kill_cap_reached",
                plugin.getConfigManager().getMessage("anti_abuse.pvp_daily_kill_cap_reached")
                    .replace("{amount}", String.valueOf(amount)));
            return false;
        }
        counter.count++;
        return true;
    }

    private int getLastHitSeconds() {
        return getConfig().getInt("anti_abuse.pvp.last_hit_seconds", 20);
    }

    private long getVictimCooldownSeconds() {
        if (getConfig().contains("anti_abuse.pvp.victim_cooldown_seconds")) {
            return getConfig().getLong("anti_abuse.pvp.victim_cooldown_seconds", 1200L);
        }
        return plugin.getConfigManager().getPvpTargetCooldown();
    }

    private String getIp(Player player) {
        InetSocketAddress address = player.getAddress();
        if (address == null || address.getAddress() == null) return null;
        return address.getAddress().getHostAddress();
    }

    private FileConfiguration getConfig() {
        return plugin.getConfigManager().getMainConfig();
    }

    private void sendIfNotEmpty(Player player, String key, String message) {
        if (message != null && !message.isEmpty()) player.sendMessage(message);
    }

    private void cleanupLastHits() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, CombatHit>> it = lastHits.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().expiresAt <= now) it.remove();
        }
    }

    private static final class CombatHit {
        final UUID attacker;
        final long expiresAt;

        CombatHit(UUID attacker, long expiresAt) {
            this.attacker = attacker;
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
