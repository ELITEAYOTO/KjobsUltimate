package me.krunsh.kjobultimate.performance;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.scheduler.BukkitTask;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.hooks.KguiHook;

/**
 * Fusionne les invalidations Kgui produites pendant un même intervalle.
 *
 * XP + quête + HUD d'un joueur dans le même tick ne doivent pas provoquer
 * plusieurs révisions globales de providers et plusieurs refresh API.
 */
public final class UiInvalidationQueue {

    private static final int MAIN = 1;
    private static final int DETAIL = 1 << 1;
    private static final int QUESTS = 1 << 2;
    private static final int TOP = 1 << 3;
    private static final int SETTINGS = 1 << 4;
    private static final int CONFIRM_LEAVE = 1 << 5;

    private final KjobUltimate plugin;

    private final LinkedHashMap<UUID, Pending> pending =
        new LinkedHashMap<UUID, Pending>();

    private volatile HotPathSettings settings;
    private BukkitTask task;
    private int ticks;

    private long dirtyMarks;
    private long flushes;
    private long playerInvalidations;
    private long coalescedMarks;

    public UiInvalidationQueue(
            KjobUltimate plugin) {

        if (plugin == null) {
            throw new IllegalArgumentException(
                "plugin ne peut pas être null."
            );
        }

        this.plugin = plugin;
        reloadSettings();
    }

    public void start() {

        if (task != null) {
            return;
        }

        task =
            plugin.getServer()
                .getScheduler()
                .runTaskTimer(
                    plugin,
                    this::tick,
                    1L,
                    1L
                );
    }

    public void reloadSettings() {
        HotPathSettings loaded =
            HotPathSettings.load(
                plugin
            );

        settings = loaded;

        if (!loaded.isUiInvalidationEnabled()) {
            pending.clear();
        }
    }

    public boolean isEnabled() {
        HotPathSettings current =
            settings;

        return current != null
            && current.isUiInvalidationEnabled();
    }

    public void mark(
            UUID playerId,
            String reason,
            String... menuIds) {

        if (playerId == null) {
            return;
        }

        dirtyMarks++;

        Pending entry =
            pending.get(
                playerId
            );

        if (entry == null) {
            entry =
                new Pending();
            pending.put(
                playerId,
                entry
            );
        } else {
            coalescedMarks++;
        }

        entry.reason =
            reason == null
                ? "kjobs:dirty"
                : reason;

        if (menuIds == null
                || menuIds.length == 0) {

            entry.allMenus = true;
            entry.menuMask = 0;
            return;
        }

        if (entry.allMenus) {
            return;
        }

        for (String menuId
                : menuIds) {

            entry.menuMask |=
                menuBit(
                    menuId
                );
        }
    }

    public void remove(
            UUID playerId) {

        if (playerId != null) {
            pending.remove(playerId);
        }
    }

    public void clear() {
        pending.clear();
    }

    public void shutdown() {

        if (task != null) {
            task.cancel();
            task = null;
        }

        pending.clear();
    }

    public int size() {
        return pending.size();
    }

    public long getDirtyMarks() {
        return dirtyMarks;
    }

    public long getCoalescedMarks() {
        return coalescedMarks;
    }

    public long getFlushes() {
        return flushes;
    }

    public long getPlayerInvalidations() {
        return playerInvalidations;
    }

    private void tick() {

        HotPathSettings current =
            settings;

        if (current == null
                || !current.isUiInvalidationEnabled()
                || pending.isEmpty()) {

            return;
        }

        ticks++;

        if (ticks < current
                .getUiInvalidationFlushIntervalTicks()) {

            return;
        }

        ticks = 0;
        flush(
            current.getUiInvalidationMaxPlayersPerFlush()
        );
    }

    private void flush(
            int maximumPlayers) {

        if (pending.isEmpty()
                || plugin.getHookManager() == null
                || plugin.getHookManager().getKguiHook() == null) {

            return;
        }

        KguiHook hook =
            plugin.getHookManager()
                .getKguiHook();

        hook.beginInvalidationBatch();

        flushes++;

        int processed = 0;

        Iterator<Map.Entry<UUID, Pending>> iterator =
            pending.entrySet()
                .iterator();

        while (iterator.hasNext()
                && processed < maximumPlayers) {

            Map.Entry<UUID, Pending> mapEntry =
                iterator.next();

            UUID playerId =
                mapEntry.getKey();

            Pending request =
                mapEntry.getValue();

            iterator.remove();
            processed++;

            if (request == null) {
                continue;
            }

            if (request.allMenus
                    || request.menuMask == 0) {

                hook.invalidatePlayerNoRevision(
                    playerId,
                    request.reason
                );

                playerInvalidations++;
                continue;
            }

            invalidateMask(
                hook,
                playerId,
                request
            );

            playerInvalidations++;
        }
    }

    private void invalidateMask(
            KguiHook hook,
            UUID playerId,
            Pending request) {

        int mask =
            request.menuMask;

        if ((mask & MAIN) != 0) {
            hook.invalidatePlayerMenuNoRevision(
                playerId,
                "kjobs_main",
                request.reason
            );
        }

        if ((mask & DETAIL) != 0) {
            hook.invalidatePlayerMenuNoRevision(
                playerId,
                "kjobs_detail",
                request.reason
            );
        }

        if ((mask & QUESTS) != 0) {
            hook.invalidatePlayerMenuNoRevision(
                playerId,
                "kjobs_quests",
                request.reason
            );
        }

        if ((mask & TOP) != 0) {
            hook.invalidatePlayerMenuNoRevision(
                playerId,
                "kjobs_top",
                request.reason
            );
        }

        if ((mask & SETTINGS) != 0) {
            hook.invalidatePlayerMenuNoRevision(
                playerId,
                "kjobs_settings",
                request.reason
            );
        }

        if ((mask & CONFIRM_LEAVE) != 0) {
            hook.invalidatePlayerMenuNoRevision(
                playerId,
                "kjobs_confirm_leave",
                request.reason
            );
        }
    }

    private static int menuBit(
            String menuId) {

        if ("kjobs_main".equals(menuId)) {
            return MAIN;
        }

        if ("kjobs_detail".equals(menuId)) {
            return DETAIL;
        }

        if ("kjobs_quests".equals(menuId)) {
            return QUESTS;
        }

        if ("kjobs_top".equals(menuId)) {
            return TOP;
        }

        if ("kjobs_settings".equals(menuId)) {
            return SETTINGS;
        }

        if ("kjobs_confirm_leave".equals(menuId)) {
            return CONFIRM_LEAVE;
        }

        return 0;
    }

    private static final class Pending {
        private boolean allMenus;
        private int menuMask;
        private String reason = "kjobs:dirty";
    }
}
