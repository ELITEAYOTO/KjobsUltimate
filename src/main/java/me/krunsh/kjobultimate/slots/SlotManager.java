package me.krunsh.kjobultimate.slots;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.jobs.JobDefinition;
import me.krunsh.kjobultimate.util.KjobLogger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gestion des jobs debloques.
 *
 * Phase de transition V1 : on reutilise la table job_slots comme liste de jobs
 * debloques. Un slot rempli = un job que le joueur peut XP. Le champ
 * displayJob sert de job favori pour /jobs, le HUD et les futurs affichages tab.
 */
public final class SlotManager {

    private static final long CONFIRM_TIMEOUT_MS = 30_000L;

    private final KjobUltimate plugin;
    private final Map<UUID, PendingChange> pendingChanges = new HashMap<UUID, PendingChange>();
    private final Map<UUID, PendingLeave> pendingLeaves = new HashMap<UUID, PendingLeave>();

    public SlotManager(KjobUltimate plugin) {
        this.plugin = plugin;
    }

    public List<String> getActiveJobs(PlayerData data) {
        List<String> active = new ArrayList<String>();
        int max = Math.min(data.getUnlockedSlots(), plugin.getConfigManager().getMaxSlots());
        for (int i = 1; i <= max; i++) {
            String jobId = data.getJobInSlot(i);
            if (jobId != null && !active.contains(jobId)) active.add(jobId);
        }
        return active;
    }

    public boolean isJobActive(PlayerData data, String jobId) {
        return jobId != null && getActiveJobs(data).contains(jobId.toLowerCase());
    }

    public int getFirstFreeSlot(PlayerData data) {
        int max = Math.min(data.getUnlockedSlots(), plugin.getConfigManager().getMaxSlots());
        for (int i = 1; i <= max; i++) {
            if (data.getJobInSlot(i) == null) return i;
        }
        return -1;
    }

    public int getGlobalLevel(PlayerData data) {
        int total = 0;
        for (String jobId : getActiveJobs(data)) {
            total += Math.max(0, data.getLevel(jobId));
        }
        return total;
    }

    public boolean assignJobToFreeSlot(Player player, PlayerData data, String jobId) {
        jobId = normalize(jobId);
        if (isJobActive(data, jobId)) {
            setFavoriteJob(player, data, jobId);
            return true;
        }

        int slot = getFirstFreeSlot(data);
        if (slot == -1) return false;
        assignJobToSlot(player, data, slot, jobId);
        return true;
    }

    public void assignJobToSlot(Player player, PlayerData data, int slot, String jobId) {
        jobId = normalize(jobId);
        if (!data.hasJob(jobId)) {
            data.setLevel(jobId, 0);
            data.setXP(jobId, 0);
        }
        data.setJobInSlot(slot, jobId);
        if (data.getDisplayJob() == null) data.setDisplayJob(jobId);
        data.markDirty();

        if (plugin.getConfigManager().isDebugSlots()) {
            KjobLogger.info("[Slots] " + player.getName() + " unlock " + jobId + " slot " + slot);
        }
    }

    public boolean setFavoriteJob(Player player, PlayerData data, String jobId) {
        jobId = normalize(jobId);
        if (!isJobActive(data, jobId)) return false;
        data.setDisplayJob(jobId);
        JobDefinition def = plugin.getJobRegistry().getJob(jobId);
        send(player, "slots.favorite_set", "{prefix}\u00A77Job favori: \u00A7e{job}", "{job}", displayName(def, jobId), "{job_id}", jobId);
        return true;
    }

    /**
     * Ancien flux de remplacement garde uniquement pour compat admin/interne.
     */
    public boolean requestJobChange(Player player, PlayerData data, int slot, String newJobId) {
        newJobId = normalize(newJobId);
        String oldJobId = data.getJobInSlot(slot);
        if (oldJobId == null) {
            assignJobToSlot(player, data, slot, newJobId);
            return true;
        }
        if (oldJobId.equals(newJobId)) return true;

        boolean allow = plugin.getConfigManager().getMainConfig().getBoolean("job_slots.allow_job_change", true);
        if (!allow) {
            send(player, "misc.no_permission", "{prefix}\u00A7cAction refusee.");
            return false;
        }

        if (!canChangeJob(player, data, false)) return false;

        pendingChanges.put(player.getUniqueId(), new PendingChange(slot, oldJobId, newJobId,
            System.currentTimeMillis() + CONFIRM_TIMEOUT_MS));
        JobDefinition oldDef = plugin.getJobRegistry().getJob(oldJobId);
        JobDefinition newDef = plugin.getJobRegistry().getJob(newJobId);
        send(player, "job_change.replace_warning", "{prefix}\u00A7cAttention: remplacer {old_job} supprimera sa progression.",
            "{old_job}", displayName(oldDef, oldJobId), "{new_job}", displayName(newDef, newJobId), "{slot}", String.valueOf(slot));
        send(player, "job_change.confirm_prompt", "{prefix}\u00A7eTape \u00A7f/jobs confirmer \u00A7epour confirmer ou \u00A7f/jobs annuler \u00A7epour annuler.");
        return false;
    }

    public boolean requestLeaveJob(Player player, PlayerData data, String jobId) {
        jobId = normalize(jobId);
        if (!isJobActive(data, jobId)) {
            send(player, "job_change.not_unlocked", "{prefix}\u00A7cTu n'as pas debloque ce job.");
            return false;
        }

        boolean allow = plugin.getConfigManager().getMainConfig().getBoolean("job_slots.allow_job_change", true);
        if (!allow) {
            send(player, "misc.no_permission", "{prefix}\u00A7cAction refusee.");
            return false;
        }

        if (!canChangeJob(player, data, false)) return false;

        pendingLeaves.put(player.getUniqueId(), new PendingLeave(jobId, System.currentTimeMillis() + CONFIRM_TIMEOUT_MS));
        pendingChanges.remove(player.getUniqueId());

        JobDefinition def = plugin.getJobRegistry().getJob(jobId);
        send(player, "job_change.leave_warning", "{prefix}\u00A7cAttention: quitter \u00A7e{job} \u00A7ceffacera toute sa progression.",
            "{job}", displayName(def, jobId), "{job_id}", jobId);
        send(player, "job_change.confirm_prompt", "{prefix}\u00A7eTape \u00A7f/jobs confirmer \u00A7epour confirmer ou \u00A7f/jobs annuler \u00A7epour annuler.");
        return true;
    }

    public boolean confirmChange(Player player, PlayerData data) {
        PendingLeave pendingLeave = pendingLeaves.remove(player.getUniqueId());
        if (pendingLeave != null) {
            if (System.currentTimeMillis() > pendingLeave.expiresAt) {
                send(player, "job_change.confirm_expired", "{prefix}\u00A7cLa confirmation a expire.");
                return false;
            }
            return confirmLeave(player, data, pendingLeave.jobId);
        }

        PendingChange pending = pendingChanges.remove(player.getUniqueId());
        if (pending == null) {
            send(player, "job_change.no_pending", "{prefix}\u00A77Aucune action en attente.");
            return false;
        }
        if (System.currentTimeMillis() > pending.expiresAt) {
            send(player, "job_change.confirm_expired", "{prefix}\u00A7cLa confirmation a expire.");
            return false;
        }

        resetJobProgress(data, pending.oldJobId);
        assignJobToSlot(player, data, pending.slot, pending.newJobId);
        data.setDisplayJob(pending.newJobId);
        data.setLastJobChangeAt(System.currentTimeMillis());
        send(player, "job_change.confirmed", "{prefix}\u00A7aChangement confirme.");
        return true;
    }

    public void cancelChange(Player player) {
        boolean hadPending = pendingChanges.remove(player.getUniqueId()) != null;
        hadPending = pendingLeaves.remove(player.getUniqueId()) != null || hadPending;
        if (hadPending) {
            send(player, "job_change.cancelled", "{prefix}\u00A77Action annulee.");
        } else {
            send(player, "job_change.no_pending", "{prefix}\u00A77Aucune action en attente.");
        }
    }

    public boolean hasPendingChange(UUID uuid) {
        return pendingChanges.containsKey(uuid) || pendingLeaves.containsKey(uuid);
    }

    public void checkAndUnlockSlots(Player player, PlayerData data, String jobId, int newLevel) {
        boolean slotsEnabled = plugin.getConfigManager().getMainConfig().getBoolean("job_slots.enabled", true);
        if (!slotsEnabled) return;

        int currentUnlocked = data.getUnlockedSlots();
        int maxSlots = plugin.getConfigManager().getMaxSlots();
        if (currentUnlocked >= maxSlots) return;

        String condition = plugin.getConfigManager().getMainConfig().getString("job_slots.unlock_condition", "TOTAL_LEVEL");
        int levelForCheck = getLevelForCondition(data, jobId, condition);

        ConfigurationSection thresholds = plugin.getConfigManager().getMainConfig()
            .getConfigurationSection("job_slots.unlock_thresholds");
        if (thresholds == null) return;

        int highestEligible = currentUnlocked;
        for (String key : thresholds.getKeys(false)) {
            try {
                int slotNum = Integer.parseInt(key);
                int required = thresholds.getInt(key);
                if (slotNum > highestEligible && slotNum <= maxSlots && levelForCheck >= required) {
                    highestEligible = slotNum;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        if (highestEligible > currentUnlocked) {
            data.setUnlockedSlots(highestEligible);
            notifySlotUnlocked(player);
            if (plugin.getConfigManager().isDebugSlots()) {
                KjobLogger.info("[Slots] " + player.getName() + " unlocked slots "
                    + currentUnlocked + " -> " + highestEligible + " (level " + levelForCheck + ")");
            }
        }
    }

    private boolean confirmLeave(Player player, PlayerData data, String jobId) {
        int slot = data.getSlotOfJob(jobId);
        if (slot == -1) {
            send(player, "job_change.no_longer_unlocked", "{prefix}\u00A7cCe job n'est plus debloque.");
            return false;
        }

        data.setJobInSlot(slot, null);
        resetJobProgress(data, jobId);

        if (jobId.equals(data.getDisplayJob())) {
            List<String> active = getActiveJobs(data);
            data.setDisplayJob(active.isEmpty() ? null : active.get(0));
        }
        data.setLastJobChangeAt(System.currentTimeMillis());
        data.markDirty();

        JobDefinition def = plugin.getJobRegistry().getJob(jobId);
        send(player, "job_change.leave_confirmed", "{prefix}\u00A7aTu as quitte \u00A7e{job}\u00A7a. Progression remise a zero.",
            "{job}", displayName(def, jobId), "{job_id}", jobId);
        return true;
    }

    private void resetJobProgress(PlayerData data, String jobId) {
        data.setLevel(jobId, 0);
        data.setXP(jobId, 0);
        data.getDailyXPMap().remove(jobId);
        data.getDailyXpResetTimeMap().remove(jobId);
    }

    public boolean forceLeaveJob(Player player, PlayerData data, String jobId, boolean resetProgress) {
        jobId = normalize(jobId);
        int slot = data.getSlotOfJob(jobId);
        if (slot == -1) return false;

        data.setJobInSlot(slot, null);
        if (resetProgress) resetJobProgress(data, jobId);
        if (jobId.equals(data.getDisplayJob())) {
            List<String> active = getActiveJobs(data);
            data.setDisplayJob(active.isEmpty() ? null : active.get(0));
        }
        data.markDirty();
        return true;
    }

    public void clearJobChangeCooldown(PlayerData data) {
        data.setLastJobChangeAt(0L);
    }

    private boolean canChangeJob(Player player, PlayerData data, boolean bypassCooldown) {
        if (bypassCooldown) return true;
        long cooldownMs = getChangeCooldownMs();
        if (cooldownMs <= 0L) return true;

        long remaining = (data.getLastJobChangeAt() + cooldownMs) - System.currentTimeMillis();
        if (remaining <= 0L) return true;

        send(player, "job_change.cooldown", "{prefix}\u00A7cTu dois attendre encore \u00A7e{time} \u00A7cavant de changer/quitter un job.",
            "{time}", formatDuration(remaining));
        return false;
    }

    private long getChangeCooldownMs() {
        long seconds = plugin.getConfigManager().getMainConfig().getLong("job_slots.change_cooldown", 0L);
        if (seconds <= 0L) return 0L;
        return seconds * 1000L;
    }

    private String formatDuration(long millis) {
        long totalSeconds = Math.max(0L, (millis + 999L) / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) return hours + "h " + minutes + "m";
        if (minutes > 0L) return minutes + "m " + seconds + "s";
        return seconds + "s";
    }

    private int getLevelForCondition(PlayerData data, String jobId, String condition) {
        if ("HIGHEST_JOB_LEVEL".equalsIgnoreCase(condition)) {
            int max = 0;
            for (String activeJob : getActiveJobs(data)) {
                max = Math.max(max, data.getLevel(activeJob));
            }
            return max;
        }
        if ("MAIN_JOB_LEVEL".equalsIgnoreCase(condition)) {
            String favorite = data.getDisplayJob();
            if (favorite != null && isJobActive(data, favorite)) return data.getLevel(favorite);
            String mainJob = data.getJobInSlot(1);
            return mainJob == null ? data.getLevel(jobId) : data.getLevel(mainJob);
        }
        return getGlobalLevel(data);
    }

    private void notifySlotUnlocked(Player player) {
        boolean notify = plugin.getConfigManager().getMainConfig().getBoolean("job_slots.notify_unlock", true);
        if (!notify) return;

        String msg = message("slots.unlocked", "{prefix}\u00A7aNouveau slot de job debloque !");
        if (!msg.isEmpty()) player.sendMessage(msg);
        playSoundForEvent(player, "slot_unlocked");
    }

    private void playSoundForEvent(Player player, String soundKey) {
        try {
            boolean enabled = plugin.getConfigManager().getSoundsConfig().getBoolean(soundKey + ".enabled", true);
            if (!enabled) return;
            String soundName = plugin.getConfigManager().getSoundsConfig().getString(soundKey + ".sound", "LEVEL_UP");
            float volume = (float) plugin.getConfigManager().getSoundsConfig().getDouble(soundKey + ".volume", 1.0);
            float pitch = (float) plugin.getConfigManager().getSoundsConfig().getDouble(soundKey + ".pitch", 1.0);
            org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (Exception ignored) {
        }
    }

    private String normalize(String jobId) {
        return jobId == null ? null : jobId.toLowerCase();
    }

    private String displayName(JobDefinition def, String fallback) {
        return def == null ? fallback : def.getDisplayName();
    }

    private void send(Player player, String key, String fallback, String... replacements) {
        String msg = message(key, fallback, replacements);
        if (!msg.isEmpty()) player.sendMessage(msg);
    }

    private String message(String key, String fallback, String... replacements) {
        String msg = plugin.getConfigManager().getMessage(key, fallback);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            msg = msg.replace(replacements[i], replacements[i + 1] == null ? "" : replacements[i + 1]);
        }
        return msg;
    }

    private static final class PendingChange {
        final int slot;
        final String oldJobId;
        final String newJobId;
        final long expiresAt;

        PendingChange(int slot, String oldJobId, String newJobId, long expiresAt) {
            this.slot = slot;
            this.oldJobId = oldJobId;
            this.newJobId = newJobId;
            this.expiresAt = expiresAt;
        }
    }

    private static final class PendingLeave {
        final String jobId;
        final long expiresAt;

        PendingLeave(String jobId, long expiresAt) {
            this.jobId = jobId;
            this.expiresAt = expiresAt;
        }
    }
}
