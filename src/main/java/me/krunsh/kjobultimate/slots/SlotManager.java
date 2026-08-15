package me.krunsh.kjobultimate.slots;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.jobs.JobDefinition;
import me.krunsh.kjobultimate.util.KjobLogger;

/**
 * Gestion des slots de métiers.
 *
 * V3.14 :
 * - isJobActive() ne construit plus de List sur chaque event ;
 * - les calculs de niveau global / highest parcourent directement les 1..6 slots ;
 * - un même métier ne peut pas rester présent dans deux slots.
 */
public final class SlotManager {

    private static final long CONFIRM_TIMEOUT_MS = 30_000L;

    private final KjobUltimate plugin;
    private final Map<UUID, PendingChange> pendingChanges =
        new HashMap<UUID, PendingChange>();
    private final Map<UUID, PendingLeave> pendingLeaves =
        new HashMap<UUID, PendingLeave>();

    public SlotManager(KjobUltimate plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("plugin ne peut pas être null.");
        }
        this.plugin = plugin;
    }

    /**
     * Allocation volontaire uniquement pour les commandes / views qui ont
     * réellement besoin d'une liste. Les listeners doivent utiliser
     * isJobActive().
     */
    public List<String> getActiveJobs(PlayerData data) {
        List<String> active = new ArrayList<String>();
        if (data == null) {
            return active;
        }

        int max = activeSlotLimit(data);
        for (int slot = 1; slot <= max; slot++) {
            String jobId = data.getJobInSlot(slot);
            if (jobId != null) {
                active.add(jobId);
            }
        }
        return active;
    }

    /** Hot path : zéro List / HashSet temporaire. */
    public boolean isJobActive(PlayerData data, String jobId) {
        if (data == null || jobId == null) {
            return false;
        }

        int max = activeSlotLimit(data);
        for (int slot = 1; slot <= max; slot++) {
            String active = data.getJobInSlot(slot);
            if (active != null && active.equalsIgnoreCase(jobId)) {
                return true;
            }
        }
        return false;
    }

    public int getFirstFreeSlot(PlayerData data) {
        if (data == null) {
            return -1;
        }

        int max = activeSlotLimit(data);
        for (int slot = 1; slot <= max; slot++) {
            if (data.getJobInSlot(slot) == null) {
                return slot;
            }
        }
        return -1;
    }

    /** Hot path de certains placeholders/views : aucun getActiveJobs(). */
    public int getGlobalLevel(PlayerData data) {
        if (data == null) {
            return 0;
        }

        int total = 0;
        int max = activeSlotLimit(data);
        for (int slot = 1; slot <= max; slot++) {
            String jobId = data.getJobInSlot(slot);
            if (jobId != null) {
                total += Math.max(0, data.getLevel(jobId));
            }
        }
        return total;
    }

    public boolean assignJobToFreeSlot(
            Player player,
            PlayerData data,
            String jobId) {

        String normalized = normalize(jobId);
        if (normalized == null) {
            return false;
        }

        if (isJobActive(data, normalized)) {
            setFavoriteJob(player, data, normalized);
            return true;
        }

        int slot = getFirstFreeSlot(data);
        if (slot == -1) {
            return false;
        }

        assignJobToSlot(player, data, slot, normalized);
        return true;
    }

    public void assignJobToSlot(
            Player player,
            PlayerData data,
            int slot,
            String jobId) {

        if (player == null || data == null) {
            return;
        }

        String normalized = normalize(jobId);
        if (normalized == null) {
            return;
        }

        int maxSlots = plugin.getConfigManager().getMaxSlots();
        if (slot < 1 || slot > maxSlots) {
            throw new IllegalArgumentException(
                "slot hors limites : " + slot + " (1-" + maxSlots + ")");
        }

        /* Base V2 propre : un job ne peut exister que dans un slot. */
        int previousSlot = data.getSlotOfJob(normalized);
        if (previousSlot > 0 && previousSlot != slot) {
            data.setJobInSlot(previousSlot, null);
        }

        if (!data.hasJob(normalized)) {
            data.setLevel(normalized, 0);
            data.setXP(normalized, 0);
        }

        data.setJobInSlot(slot, normalized);
        if (data.getDisplayJob() == null) {
            data.setDisplayJob(normalized);
        }
        data.markDirty();

        plugin.notifyJobsUiChanged(
            player.getUniqueId(),
            "kjobs:job-assigned",
            "kjobs_main",
            "kjobs_detail",
            "kjobs_quests");

        if (plugin.getConfigManager().isDebugSlots()) {
            KjobLogger.info(
                "[Slots] " + player.getName()
                    + " assign " + normalized
                    + " slot " + slot);
        }
    }

    public boolean setFavoriteJob(
            Player player,
            PlayerData data,
            String jobId) {

        String normalized = normalize(jobId);
        if (normalized == null || !isJobActive(data, normalized)) {
            return false;
        }

        data.setDisplayJob(normalized);
        plugin.notifyJobsUiChanged(
            player.getUniqueId(),
            "kjobs:favorite",
            "kjobs_main",
            "kjobs_detail");

        JobDefinition def = plugin.getJobRegistry().getJob(normalized);
        send(
            player,
            "slots.favorite_set",
            "{prefix}§7Job favori: §e{job}",
            "{job}", displayName(def, normalized),
            "{job_id}", normalized);
        return true;
    }

    public boolean requestJobChange(
            Player player,
            PlayerData data,
            int slot,
            String newJobId) {

        String normalized = normalize(newJobId);
        if (normalized == null) {
            return false;
        }

        String oldJobId = data.getJobInSlot(slot);
        if (oldJobId == null) {
            assignJobToSlot(player, data, slot, normalized);
            return true;
        }
        if (oldJobId.equals(normalized)) {
            return true;
        }

        if (!plugin.getConfigManager().getMainConfig()
                .getBoolean("job_slots.allow_job_change", true)) {
            send(player, "misc.no_permission", "{prefix}§cAction refusee.");
            return false;
        }

        if (!canChangeJob(player, data, false)) {
            return false;
        }

        pendingChanges.put(
            player.getUniqueId(),
            new PendingChange(
                slot,
                oldJobId,
                normalized,
                System.currentTimeMillis() + CONFIRM_TIMEOUT_MS));

        JobDefinition oldDef = plugin.getJobRegistry().getJob(oldJobId);
        JobDefinition newDef = plugin.getJobRegistry().getJob(normalized);

        send(
            player,
            "job_change.replace_warning",
            "{prefix}§cAttention: remplacer {old_job} supprimera sa progression.",
            "{old_job}", displayName(oldDef, oldJobId),
            "{new_job}", displayName(newDef, normalized),
            "{slot}", String.valueOf(slot));
        send(
            player,
            "job_change.confirm_prompt",
            "{prefix}§eTape §f/jobs confirmer §epour confirmer ou §f/jobs annuler §epour annuler.");
        return false;
    }

    public boolean requestLeaveJob(
            Player player,
            PlayerData data,
            String jobId) {

        String normalized = normalize(jobId);
        if (normalized == null || !isJobActive(data, normalized)) {
            send(
                player,
                "job_change.not_unlocked",
                "{prefix}§cTu n'as pas debloque ce job.");
            return false;
        }

        if (!plugin.getConfigManager().getMainConfig()
                .getBoolean("job_slots.allow_job_change", true)) {
            send(player, "misc.no_permission", "{prefix}§cAction refusee.");
            return false;
        }

        if (!canChangeJob(player, data, false)) {
            return false;
        }

        pendingLeaves.put(
            player.getUniqueId(),
            new PendingLeave(
                normalized,
                System.currentTimeMillis() + CONFIRM_TIMEOUT_MS));
        pendingChanges.remove(player.getUniqueId());

        JobDefinition def = plugin.getJobRegistry().getJob(normalized);
        send(
            player,
            "job_change.leave_warning",
            "{prefix}§cAttention: quitter §e{job} §ceffacera toute sa progression.",
            "{job}", displayName(def, normalized),
            "{job_id}", normalized);
        send(
            player,
            "job_change.confirm_prompt",
            "{prefix}§eTape §f/jobs confirmer §epour confirmer ou §f/jobs annuler §epour annuler.");
        return true;
    }

    public boolean confirmChange(Player player, PlayerData data) {
        PendingLeave pendingLeave = pendingLeaves.remove(player.getUniqueId());
        if (pendingLeave != null) {
            if (System.currentTimeMillis() > pendingLeave.expiresAt) {
                send(
                    player,
                    "job_change.confirm_expired",
                    "{prefix}§cLa confirmation a expire.");
                return false;
            }
            return confirmLeave(player, data, pendingLeave.jobId);
        }

        PendingChange pending = pendingChanges.remove(player.getUniqueId());
        if (pending == null) {
            send(
                player,
                "job_change.no_pending",
                "{prefix}§7Aucune action en attente.");
            return false;
        }
        if (System.currentTimeMillis() > pending.expiresAt) {
            send(
                player,
                "job_change.confirm_expired",
                "{prefix}§cLa confirmation a expire.");
            return false;
        }

        resetJobProgress(data, pending.oldJobId);
        assignJobToSlot(player, data, pending.slot, pending.newJobId);
        data.setDisplayJob(pending.newJobId);
        data.setLastJobChangeAt(System.currentTimeMillis());
        send(player, "job_change.confirmed", "{prefix}§aChangement confirme.");
        return true;
    }

    public void cancelChange(Player player) {
        boolean hadPending =
            pendingChanges.remove(player.getUniqueId()) != null;
        hadPending =
            pendingLeaves.remove(player.getUniqueId()) != null || hadPending;

        send(
            player,
            hadPending ? "job_change.cancelled" : "job_change.no_pending",
            hadPending
                ? "{prefix}§7Action annulee."
                : "{prefix}§7Aucune action en attente.");
    }

    public boolean hasPendingChange(UUID uuid) {
        return pendingChanges.containsKey(uuid)
            || pendingLeaves.containsKey(uuid);
    }

    public void checkAndUnlockSlots(
            Player player,
            PlayerData data,
            String jobId,
            int newLevel) {

        if (!plugin.getConfigManager().getMainConfig()
                .getBoolean("job_slots.enabled", true)) {
            return;
        }

        int currentUnlocked = data.getUnlockedSlots();
        int maxSlots = plugin.getConfigManager().getMaxSlots();
        if (currentUnlocked >= maxSlots) {
            return;
        }

        String condition =
            plugin.getConfigManager().getMainConfig()
                .getString("job_slots.unlock_condition", "TOTAL_LEVEL");

        int levelForCheck =
            getLevelForCondition(data, jobId, condition);

        ConfigurationSection thresholds =
            plugin.getConfigManager().getMainConfig()
                .getConfigurationSection("job_slots.unlock_thresholds");
        if (thresholds == null) {
            return;
        }

        int highestEligible = currentUnlocked;
        for (String key : thresholds.getKeys(false)) {
            try {
                int slotNum = Integer.parseInt(key);
                int required = thresholds.getInt(key);
                if (slotNum > highestEligible
                        && slotNum <= maxSlots
                        && levelForCheck >= required) {
                    highestEligible = slotNum;
                }
            } catch (NumberFormatException ignored) {
                // ConfigValidator couvre cette erreur.
            }
        }

        if (highestEligible <= currentUnlocked) {
            return;
        }

        data.setUnlockedSlots(highestEligible);
        plugin.notifyJobsUiChanged(
            player.getUniqueId(),
            "kjobs:slot-unlocked",
            "kjobs_main",
            "kjobs_detail");
        notifySlotUnlocked(player);

        if (plugin.getConfigManager().isDebugSlots()) {
            KjobLogger.info(
                "[Slots] " + player.getName()
                    + " unlocked slots " + currentUnlocked
                    + " -> " + highestEligible
                    + " (level " + levelForCheck + ")");
        }
    }

    private boolean confirmLeave(
            Player player,
            PlayerData data,
            String jobId) {

        int slot = data.getSlotOfJob(jobId);
        if (slot == -1) {
            send(
                player,
                "job_change.no_longer_unlocked",
                "{prefix}§cCe job n'est plus debloque.");
            return false;
        }

        data.setJobInSlot(slot, null);
        resetJobProgress(data, jobId);

        if (jobId.equals(data.getDisplayJob())) {
            data.setDisplayJob(firstActiveJob(data));
        }

        data.setLastJobChangeAt(System.currentTimeMillis());
        data.markDirty();
        plugin.notifyJobsUiChanged(
            player.getUniqueId(),
            "kjobs:job-left",
            "kjobs_main",
            "kjobs_detail",
            "kjobs_quests");

        JobDefinition def = plugin.getJobRegistry().getJob(jobId);
        send(
            player,
            "job_change.leave_confirmed",
            "{prefix}§aTu as quitte §e{job}§a. Progression remise a zero.",
            "{job}", displayName(def, jobId),
            "{job_id}", jobId);
        return true;
    }

    private void resetJobProgress(PlayerData data, String jobId) {
        data.setLevel(jobId, 0);
        data.setXP(jobId, 0);
        data.getDailyXPMap().remove(jobId);
        data.getDailyXpResetTimeMap().remove(jobId);
        data.markDirty();
    }

    public boolean forceLeaveJob(
            Player player,
            PlayerData data,
            String jobId,
            boolean resetProgress) {

        String normalized = normalize(jobId);
        if (normalized == null) {
            return false;
        }

        int slot = data.getSlotOfJob(normalized);
        if (slot == -1) {
            return false;
        }

        data.setJobInSlot(slot, null);
        if (resetProgress) {
            resetJobProgress(data, normalized);
        }

        if (normalized.equals(data.getDisplayJob())) {
            data.setDisplayJob(firstActiveJob(data));
        }

        data.markDirty();
        plugin.notifyJobsUiChanged(
            player.getUniqueId(),
            "kjobs:job-force-left",
            "kjobs_main",
            "kjobs_detail",
            "kjobs_quests");
        return true;
    }

    public void clearJobChangeCooldown(PlayerData data) {
        if (data != null) {
            data.setLastJobChangeAt(0L);
        }
    }

    private boolean canChangeJob(
            Player player,
            PlayerData data,
            boolean bypassCooldown) {

        if (bypassCooldown) {
            return true;
        }

        long cooldownMs = getChangeCooldownMs();
        if (cooldownMs <= 0L) {
            return true;
        }

        long remaining =
            (data.getLastJobChangeAt() + cooldownMs)
                - System.currentTimeMillis();
        if (remaining <= 0L) {
            return true;
        }

        send(
            player,
            "job_change.cooldown",
            "{prefix}§cTu dois attendre encore §e{time} §cavant de changer/quitter un job.",
            "{time}", formatDuration(remaining));
        return false;
    }

    private long getChangeCooldownMs() {
        long seconds =
            plugin.getConfigManager().getMainConfig()
                .getLong("job_slots.change_cooldown", 0L);

        if (seconds <= 0L) {
            return 0L;
        }
        if (seconds > Long.MAX_VALUE / 1000L) {
            return Long.MAX_VALUE;
        }
        return seconds * 1000L;
    }

    private static String formatDuration(long millis) {
        long totalSeconds = Math.max(0L, (millis + 999L) / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0L) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }

    private int getLevelForCondition(
            PlayerData data,
            String jobId,
            String condition) {

        if ("HIGHEST_JOB_LEVEL".equalsIgnoreCase(condition)) {
            int highest = 0;
            int max = activeSlotLimit(data);
            for (int slot = 1; slot <= max; slot++) {
                String active = data.getJobInSlot(slot);
                if (active != null) {
                    highest = Math.max(highest, data.getLevel(active));
                }
            }
            return highest;
        }

        if ("MAIN_JOB_LEVEL".equalsIgnoreCase(condition)) {
            String favorite = data.getDisplayJob();
            if (favorite != null && isJobActive(data, favorite)) {
                return data.getLevel(favorite);
            }
            String mainJob = data.getJobInSlot(1);
            return mainJob == null
                ? data.getLevel(jobId)
                : data.getLevel(mainJob);
        }

        return getGlobalLevel(data);
    }

    private String firstActiveJob(PlayerData data) {
        int max = activeSlotLimit(data);
        for (int slot = 1; slot <= max; slot++) {
            String jobId = data.getJobInSlot(slot);
            if (jobId != null) {
                return jobId;
            }
        }
        return null;
    }

    private int activeSlotLimit(PlayerData data) {
        return Math.max(
            0,
            Math.min(
                data.getUnlockedSlots(),
                plugin.getConfigManager().getMaxSlots()));
    }

    private void notifySlotUnlocked(Player player) {
        if (!plugin.getConfigManager().getMainConfig()
                .getBoolean("job_slots.notify_unlock", true)) {
            return;
        }

        String msg = message(
            "slots.unlocked",
            "{prefix}§aNouveau slot de job debloque !");
        if (!msg.isEmpty()) {
            player.sendMessage(msg);
        }
        playSoundForEvent(player, "slot_unlocked");
    }

    private void playSoundForEvent(Player player, String soundKey) {
        try {
            boolean enabled =
                plugin.getConfigManager().getSoundsConfig()
                    .getBoolean(soundKey + ".enabled", true);
            if (!enabled) {
                return;
            }

            String soundName =
                plugin.getConfigManager().getSoundsConfig()
                    .getString(soundKey + ".sound", "LEVEL_UP");
            float volume = (float) plugin.getConfigManager().getSoundsConfig()
                .getDouble(soundKey + ".volume", 1D);
            float pitch = (float) plugin.getConfigManager().getSoundsConfig()
                .getDouble(soundKey + ".pitch", 1D);

            org.bukkit.Sound sound = org.bukkit.Sound.valueOf(
                soundName.trim().toUpperCase(Locale.ROOT));
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (RuntimeException ignored) {
            // ConfigValidator / logs de démarrage couvrent la configuration.
        }
    }

    private static String normalize(String jobId) {
        if (jobId == null) {
            return null;
        }
        String normalized = jobId.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private static String displayName(JobDefinition def, String fallback) {
        return def == null ? fallback : def.getDisplayName();
    }

    private void send(
            Player player,
            String key,
            String fallback,
            String... replacements) {

        String msg = message(key, fallback, replacements);
        if (!msg.isEmpty()) {
            player.sendMessage(msg);
        }
    }

    private String message(
            String key,
            String fallback,
            String... replacements) {

        String msg = plugin.getConfigManager().getMessage(key, fallback);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            msg = msg.replace(
                replacements[i],
                replacements[i + 1] == null ? "" : replacements[i + 1]);
        }
        return msg;
    }

    private static final class PendingChange {
        private final int slot;
        private final String oldJobId;
        private final String newJobId;
        private final long expiresAt;

        private PendingChange(
                int slot,
                String oldJobId,
                String newJobId,
                long expiresAt) {
            this.slot = slot;
            this.oldJobId = oldJobId;
            this.newJobId = newJobId;
            this.expiresAt = expiresAt;
        }
    }

    private static final class PendingLeave {
        private final String jobId;
        private final long expiresAt;

        private PendingLeave(String jobId, long expiresAt) {
            this.jobId = jobId;
            this.expiresAt = expiresAt;
        }
    }
}
