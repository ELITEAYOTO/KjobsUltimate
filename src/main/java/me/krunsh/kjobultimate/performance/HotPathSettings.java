package me.krunsh.kjobultimate.performance;

import org.bukkit.configuration.file.FileConfiguration;

import me.krunsh.kjobultimate.KjobUltimate;

/**
 * Snapshot immuable des réglages V3.14 utilisés sur les chemins chauds.
 *
 * Le YAML n'est donc jamais relu à chaque bloc/craft/kill : les services
 * consomment uniquement ce petit objet en RAM. /kjobs reload reconstruit le
 * snapshot via ConfigManager.loadAll().
 */
public final class HotPathSettings {

    private final int blockCooldownMaxEntriesPerPlayer;
    private final int blockCooldownCleanupEveryChecks;

    private final long permissionCacheTtlMs;
    private final int permissionCacheMaxPlayers;
    private final long eventMultiplierRefreshMs;
    private final long globalDailySweepIntervalMs;

    private final boolean uiInvalidationEnabled;
    private final int uiInvalidationFlushIntervalTicks;
    private final int uiInvalidationMaxPlayersPerFlush;

    private HotPathSettings(
            int blockCooldownMaxEntriesPerPlayer,
            int blockCooldownCleanupEveryChecks,
            long permissionCacheTtlMs,
            int permissionCacheMaxPlayers,
            long eventMultiplierRefreshMs,
            long globalDailySweepIntervalMs,
            boolean uiInvalidationEnabled,
            int uiInvalidationFlushIntervalTicks,
            int uiInvalidationMaxPlayersPerFlush) {

        this.blockCooldownMaxEntriesPerPlayer =
            blockCooldownMaxEntriesPerPlayer;
        this.blockCooldownCleanupEveryChecks =
            blockCooldownCleanupEveryChecks;
        this.permissionCacheTtlMs =
            permissionCacheTtlMs;
        this.permissionCacheMaxPlayers =
            permissionCacheMaxPlayers;
        this.eventMultiplierRefreshMs =
            eventMultiplierRefreshMs;
        this.globalDailySweepIntervalMs =
            globalDailySweepIntervalMs;
        this.uiInvalidationEnabled =
            uiInvalidationEnabled;
        this.uiInvalidationFlushIntervalTicks =
            uiInvalidationFlushIntervalTicks;
        this.uiInvalidationMaxPlayersPerFlush =
            uiInvalidationMaxPlayersPerFlush;
    }

    public static HotPathSettings load(
            KjobUltimate plugin) {

        if (plugin == null
                || plugin.getConfigManager() == null
                || plugin.getConfigManager().getMainConfig() == null) {

            return defaults();
        }

        FileConfiguration config =
            plugin.getConfigManager()
                .getMainConfig();

        int maxCooldownEntries =
            clamp(
                config.getInt(
                    "performance.hot_path.block_cooldowns.max_entries_per_player",
                    4096
                ),
                256,
                65536
            );

        int cleanupEveryChecks =
            clamp(
                config.getInt(
                    "performance.hot_path.block_cooldowns.cleanup_every_checks",
                    128
                ),
                8,
                8192
            );

        int permissionTtlTicks =
            clamp(
                config.getInt(
                    "performance.hot_path.xp.permission_cache_ttl_ticks",
                    100
                ),
                0,
                12000
            );

        int permissionMaxPlayers =
            clamp(
                config.getInt(
                    "performance.hot_path.xp.permission_cache_max_players",
                    2048
                ),
                128,
                20000
            );

        int eventRefreshTicks =
            clamp(
                config.getInt(
                    "performance.hot_path.xp.event_multiplier_refresh_ticks",
                    20
                ),
                1,
                1200
            );

        int dailySweepTicks =
            clamp(
                config.getInt(
                    "performance.hot_path.xp.global_daily_sweep_interval_ticks",
                    200
                ),
                20,
                12000
            );

        boolean uiEnabled =
            config.getBoolean(
                "performance.hot_path.ui_invalidation.enabled",
                true
            );

        int uiFlushTicks =
            clamp(
                config.getInt(
                    "performance.hot_path.ui_invalidation.flush_interval_ticks",
                    1
                ),
                1,
                20
            );

        int uiMaxPerFlush =
            clamp(
                config.getInt(
                    "performance.hot_path.ui_invalidation.max_players_per_flush",
                    250
                ),
                1,
                2000
            );

        return new HotPathSettings(
            maxCooldownEntries,
            cleanupEveryChecks,
            ticksToMillis(permissionTtlTicks),
            permissionMaxPlayers,
            ticksToMillis(eventRefreshTicks),
            ticksToMillis(dailySweepTicks),
            uiEnabled,
            uiFlushTicks,
            uiMaxPerFlush
        );
    }

    private static HotPathSettings defaults() {
        return new HotPathSettings(
            4096,
            128,
            5000L,
            2048,
            1000L,
            10000L,
            true,
            1,
            250
        );
    }

    private static long ticksToMillis(
            int ticks) {

        return Math.max(
            0L,
            (long) ticks * 50L
        );
    }

    private static int clamp(
            int value,
            int minimum,
            int maximum) {

        return Math.max(
            minimum,
            Math.min(
                maximum,
                value
            )
        );
    }

    public int getBlockCooldownMaxEntriesPerPlayer() {
        return blockCooldownMaxEntriesPerPlayer;
    }

    public int getBlockCooldownCleanupEveryChecks() {
        return blockCooldownCleanupEveryChecks;
    }

    public long getPermissionCacheTtlMs() {
        return permissionCacheTtlMs;
    }

    public int getPermissionCacheMaxPlayers() {
        return permissionCacheMaxPlayers;
    }

    public long getEventMultiplierRefreshMs() {
        return eventMultiplierRefreshMs;
    }

    public long getGlobalDailySweepIntervalMs() {
        return globalDailySweepIntervalMs;
    }

    public boolean isUiInvalidationEnabled() {
        return uiInvalidationEnabled;
    }

    public int getUiInvalidationFlushIntervalTicks() {
        return uiInvalidationFlushIntervalTicks;
    }

    public int getUiInvalidationMaxPlayersPerFlush() {
        return uiInvalidationMaxPlayersPerFlush;
    }
}
