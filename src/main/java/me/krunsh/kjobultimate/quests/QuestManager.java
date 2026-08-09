package me.krunsh.kjobultimate.quests;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.data.QuestData;
import me.krunsh.kjobultimate.data.QuestRewardClaimStore;
import me.krunsh.kjobultimate.jobs.JobDefinition;
import me.krunsh.kjobultimate.jobs.LevelUpResult;
import me.krunsh.kjobultimate.util.KjobLogger;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quetes V1 permanentes:
 * - pas de reset daily/weekly;
 * - progression sauvegardee en DB;
 * - recompense claimable une seule fois.
 */
public final class QuestManager {

    private final KjobUltimate plugin;
    private final Set<String> rewardClaimsInFlight =
        Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    private volatile Catalog catalog = Catalog.empty();

    private static final class Catalog {
        private final FileConfiguration config;
        private final boolean enabled;
        private final Map<String, QuestDefinition> quests;
        private final Map<String, QuestDefinition> questsByNormalizedId;
        private final Map<String, List<QuestDefinition>> questsByType;
        private final Map<String, QuestChainDefinition> chains;
        private final Set<String> customTypes;

        private Catalog(FileConfiguration config, boolean enabled,
                Map<String, QuestDefinition> quests,
                Map<String, QuestDefinition> questsByNormalizedId,
                Map<String, List<QuestDefinition>> questsByType,
                Map<String, QuestChainDefinition> chains,
                Set<String> customTypes) {
            this.config = config;
            this.enabled = enabled;
            this.quests = Collections.unmodifiableMap(
                    new LinkedHashMap<String, QuestDefinition>(quests));
            this.questsByNormalizedId = Collections.unmodifiableMap(
                    new LinkedHashMap<String, QuestDefinition>(questsByNormalizedId));

            LinkedHashMap<String, List<QuestDefinition>> immutableByType =
                    new LinkedHashMap<String, List<QuestDefinition>>();
            for (Map.Entry<String, List<QuestDefinition>> entry : questsByType.entrySet()) {
                immutableByType.put(entry.getKey(), Collections.unmodifiableList(
                        new ArrayList<QuestDefinition>(entry.getValue())));
            }
            this.questsByType = Collections.unmodifiableMap(immutableByType);
            this.chains = Collections.unmodifiableMap(
                    new LinkedHashMap<String, QuestChainDefinition>(chains));
            this.customTypes = Collections.unmodifiableSet(
                    new HashSet<String>(customTypes));
        }

        private static Catalog empty() {
            return new Catalog(new YamlConfiguration(), false,
                    Collections.<String, QuestDefinition>emptyMap(),
                    Collections.<String, QuestDefinition>emptyMap(),
                    Collections.<String, List<QuestDefinition>>emptyMap(),
                    Collections.<String, QuestChainDefinition>emptyMap(),
                    Collections.<String>emptySet());
        }
    }

    public QuestManager(KjobUltimate plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        File file = new File(plugin.getDataFolder(), "quests.yml");
        if (!file.exists()) plugin.saveResource("quests.yml", false);
        List<String> errors = new ArrayList<String>();
        try {
            errors.addAll(YamlDuplicateKeyScanner.scan(file));
        } catch (Exception failure) {
            errors.add("Lecture stricte impossible: " + failure.getMessage());
        }
        FileConfiguration candidateConfig = YamlConfiguration.loadConfiguration(file);
        boolean candidateEnabled = candidateConfig.getBoolean("enabled", true);
        LinkedHashMap<String, QuestDefinition> candidateQuests =
                new LinkedHashMap<String, QuestDefinition>();
        LinkedHashMap<String, QuestDefinition> candidateLookup =
                new LinkedHashMap<String, QuestDefinition>();
        LinkedHashMap<String, List<QuestDefinition>> candidateByType =
                new LinkedHashMap<String, List<QuestDefinition>>();
        LinkedHashMap<String, QuestChainIndex.Metadata> declaredChains =
                new LinkedHashMap<String, QuestChainIndex.Metadata>();
        validateKeys(candidateConfig, setOf(
                "enabled", "custom_types", "chains", "quests"),
                "quests.yml", errors);
        if (candidateConfig.contains("custom_types")
                && !candidateConfig.isList("custom_types")) {
            errors.add("custom_types: une liste YAML est attendue.");
        }
        Set<String> customTypes = new HashSet<String>();
        for (String customType : candidateConfig.getStringList("custom_types")) {
            String normalized = QuestDefinition.normalize(customType);
            if ("*".equals(normalized)) {
                errors.add("custom_types: valeur vide interdite.");
            } else {
                customTypes.add(normalized);
            }
        }

        ConfigurationSection chainsRoot =
                candidateConfig.getConfigurationSection("chains");
        if (candidateConfig.contains("chains") && chainsRoot == null) {
            errors.add("chains: une section YAML est attendue.");
        }
        if (chainsRoot != null) {
            for (String rawChainId : chainsRoot.getKeys(false)) {
                String chainId = normalizeId(rawChainId);
                if (chainId.isEmpty()) {
                    errors.add("chains: identifiant vide interdit.");
                    continue;
                }
                if (declaredChains.containsKey(chainId)) {
                    errors.add("chains." + rawChainId
                            + ": collision de casse avec une autre chaine.");
                    continue;
                }
                ConfigurationSection section =
                        chainsRoot.getConfigurationSection(rawChainId);
                if (section == null) {
                    errors.add("chains." + rawChainId
                            + ": la definition doit etre une section.");
                    continue;
                }
                validateKeys(section, setOf("display_name", "job"),
                        "chains." + rawChainId, errors);
                String display = color(section.getString(
                        "display_name", rawChainId));
                String jobId = normalizeId(section.getString("job", ""));
                if (plugin.getJobRegistry().getJob(jobId) == null) {
                    errors.add("chains." + rawChainId
                            + ".job: job inconnu " + jobId + ".");
                }
                declaredChains.put(chainId,
                        new QuestChainIndex.Metadata(display, jobId));
            }
        }

        ConfigurationSection root = candidateConfig.getConfigurationSection("quests");
        if (candidateConfig.contains("quests") && root == null) {
            errors.add("quests: une section YAML est attendue.");
        }
        if (root == null) {
            errors.add("Section quests manquante dans quests.yml.");
        } else {
            for (String id : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(id);
                if (section == null) {
                    errors.add("quests." + id
                            + ": la definition doit etre une section.");
                    continue;
                }

                QuestDefinition quest = parseQuest(id, section, errors);
                if (quest == null) continue;

                String normalizedId = normalizeId(quest.getId());
                if (candidateLookup.containsKey(normalizedId)) {
                    errors.add("quests." + id
                            + ": identifiant duplique sans tenir compte de la casse.");
                    continue;
                }
                candidateQuests.put(quest.getId(), quest);
                candidateLookup.put(normalizedId, quest);
                List<QuestDefinition> byType =
                        candidateByType.get(quest.getType());
                if (byType == null) {
                    byType = new ArrayList<QuestDefinition>();
                    candidateByType.put(quest.getType(), byType);
                }
                byType.add(quest);
            }
        }

        QuestChainIndex.Result chainResult =
                QuestChainIndex.build(candidateQuests.values(), declaredChains);
        errors.addAll(chainResult.getErrors());
        if (!errors.isEmpty()) {
            for (String error : errors) {
                KjobLogger.error("[Quests] " + error);
            }
            throw new IllegalStateException("quests.yml refuse: "
                    + errors.size() + " erreur(s). L'ancien catalogue reste actif.");
        }

        catalog = new Catalog(candidateConfig, candidateEnabled,
                candidateQuests, candidateLookup, candidateByType,
                chainResult.getChains(), customTypes);
        KjobLogger.success("Quetes chargees: " + candidateQuests.size()
                + " dans " + chainResult.getChains().size()
                + " chaine(s); catalogue remplace atomiquement.");
    }

    private QuestDefinition parseQuest(String id, ConfigurationSection section,
                                        List<String> errors) {
        if (id == null || id.trim().isEmpty()) {
            errors.add("quests: identifiant vide interdit.");
            return null;
        }
        validateKeys(section, setOf("display_name", "job", "type", "target",
                "chain", "stage", "amount", "min_level", "rewards",
                "reward_xp"), "quests." + id, errors);
        ConfigurationSection rewards =
                section.getConfigurationSection("rewards");
        if (section.contains("rewards") && rewards == null) {
            errors.add("quests." + id
                    + ".rewards: une section YAML est attendue.");
        }
        if (rewards != null) {
            validateKeys(rewards, setOf("xp", "commands"),
                    "quests." + id + ".rewards", errors);
        }
        String jobId = normalizeId(section.getString("job", ""));
        if (plugin.getJobRegistry().getJob(jobId) == null) {
            errors.add("quests." + id + ".job: job inconnu " + jobId + ".");
            return null;
        }

        String type = section.getString("type", "");
        if (type == null || type.trim().isEmpty()) {
            errors.add("quests." + id + ".type: valeur obligatoire.");
            return null;
        }
        String target = section.getString("target", "*");
        int amount = section.getInt("amount", 0);
        if (amount <= 0) {
            errors.add("quests." + id + ".amount: doit etre > 0.");
            return null;
        }

        String display = color(section.getString("display_name", id));
        int minLevel = section.getInt("min_level", 0);
        if (minLevel < 0) {
            errors.add("quests." + id + ".min_level: ne peut pas etre negatif.");
            return null;
        }
        int rewardXp = section.getInt("rewards.xp", section.getInt("reward_xp", 0));
        if (rewardXp < 0) {
            errors.add("quests." + id + ".rewards.xp: ne peut pas etre negatif.");
            return null;
        }
        List<String> commands = section.getStringList("rewards.commands");
        if (commands == null) commands = Collections.emptyList();

        boolean explicitChain = section.contains("chain");
        String chainId = explicitChain
                ? normalizeId(section.getString("chain", "")) : normalizeId(id);
        if (chainId.isEmpty()) {
            errors.add("quests." + id + ".chain: identifiant vide interdit.");
            return null;
        }
        int stage = section.getInt("stage", 1);
        if (stage <= 0) {
            errors.add("quests." + id + ".stage: doit etre >= 1.");
            return null;
        }
        if (!explicitChain && section.contains("stage") && stage != 1) {
            errors.add("quests." + id
                    + ".stage: une quete sans chain est une chaine autonome d'etape 1.");
            return null;
        }

        return new QuestDefinition(id, display, jobId, type, target, amount,
                minLevel, rewardXp, commands, chainId, stage, explicitChain);
    }

    public void progress(Player player, String type, String target, int amount) {
        Catalog current = catalog;
        if (!current.enabled || player == null || amount <= 0) return;

        String normalizedType = QuestDefinition.normalize(type);
        List<QuestDefinition> candidates =
                current.questsByType.get(normalizedType);
        if (candidates == null || candidates.isEmpty()) return;

        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data == null) return;

        // Instantane avant toute mutation: une action peut faire progresser
        // plusieurs chaines, mais jamais l'etape suivante de la meme chaine.
        Set<String> activeQuestIds = QuestChainPolicy.activeQuestIds(
                candidates, current.chains, data.getQuestProgress());

        for (QuestDefinition quest : candidates) {
            if (!activeQuestIds.contains(quest.getId())) continue;
            if (!quest.matches(type, target)) continue;
            if (!plugin.getSlotManager().isJobActive(data, quest.getJobId())) continue;
            if (data.getLevel(quest.getJobId()) < quest.getMinLevel()) continue;

            QuestData questData = data.getQuestProgress().get(quest.getId());
            if (questData == null) {
                questData = new QuestData(quest.getId());
                data.getQuestProgress().put(quest.getId(), questData);
            }
            if (questData.isCompleted()) continue;

            boolean completedNow = questData.addProgress(amount, quest.getAmount());
            data.markDirty();
            saveQuestAsync(data.getUuid(), questData);

            if (plugin.getConfigManager().isDebugQuest()) {
                KjobLogger.info("[Quest] " + player.getName() + " " + quest.getId()
                    + " " + questData.getProgress() + "/" + quest.getAmount());
            }

            if (completedNow) {
                notifyCompleted(player, quest);
            }
        }
    }

    public boolean claimReward(Player player, String questId) {
        if (!catalog.enabled || player == null || questId == null) return false;

        QuestDefinition quest = getQuest(questId);
        if (quest == null) {
            send(player, "quest.unknown", "{prefix}\u00A7cQuete inconnue: \u00A7e{quest_id}",
                "{quest_id}", questId);
            return false;
        }

        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data == null) return false;

        QuestData questData = data.getQuestProgress().get(quest.getId());
        if (questData == null || !questData.isCompleted() || questData.isClaimed()) {
            send(player, "quest.not_claimable", "{prefix}\u00A7cCette quete n'est pas prete a etre recuperee.",
                "{quest}", quest.getDisplayName(), "{quest_id}", quest.getId());
            return false;
        }

        final UUID uuid = player.getUniqueId();
        final String claimKey = claimKey(uuid, quest.getId());
        if (!rewardClaimsInFlight.add(claimKey)) {
            send(player, "quest.claim_processing",
                "{prefix}\u00A7eLa recuperation de cette recompense est deja en cours.",
                "{quest}", quest.getDisplayName(), "{quest_id}", quest.getId());
            return false;
        }

        final String playerName = player.getName();
        send(player, "quest.claim_processing",
            "{prefix}\u00A7eVerification de la recompense en cours...",
            "{quest}", quest.getDisplayName(), "{quest_id}", quest.getId());

        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    final QuestRewardClaimStore.ReservationResult reservation =
                        plugin.getDatabaseManager().reserveQuestReward(
                            uuid, quest.getId(), playerName);
                    Bukkit.getScheduler().runTask(plugin, new Runnable() {
                        @Override
                        public void run() {
                            if (reservation == QuestRewardClaimStore.ReservationResult.RESERVED) {
                                validateReservedClaim(uuid, quest, claimKey);
                            } else {
                                rejectAlreadyReservedClaim(uuid, quest, claimKey);
                            }
                        }
                    });
                } catch (final Exception failure) {
                    KjobLogger.error("[QuestReward] Reservation impossible pour "
                        + uuid + "/" + quest.getId(), failure);
                    Bukkit.getScheduler().runTask(plugin, new Runnable() {
                        @Override
                        public void run() {
                            rewardClaimsInFlight.remove(claimKey);
                            Player current = Bukkit.getPlayer(uuid);
                            if (current != null && current.isOnline()) {
                                send(current, "quest.claim_failed",
                                    "{prefix}\u00A7cLa recompense n'a pas ete distribuee: reservation impossible.",
                                    "{quest}", quest.getDisplayName(), "{quest_id}", quest.getId());
                            }
                        }
                    });
                }
            }
        });
        return true;
    }

    private void validateReservedClaim(final UUID uuid, final QuestDefinition quest,
                                       final String claimKey) {
        Player player = Bukkit.getPlayer(uuid);
        PlayerData data = plugin.getPlayerDataManager().get(uuid);
        QuestData questData = data == null ? null : data.getQuestProgress().get(quest.getId());

        if (player == null || !player.isOnline() || questData == null
                || !questData.isCompleted() || questData.isClaimed()) {
            cancelPreparedClaim(uuid, quest, claimKey,
                "joueur hors ligne ou etat de quete modifie avant distribution");
            return;
        }

        final int progress = questData.getProgress();
        final long completedAt = questData.getCompletedAt();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    final boolean started = plugin.getDatabaseManager()
                        .beginQuestRewardDistribution(uuid, quest.getId());
                    Bukkit.getScheduler().runTask(plugin, new Runnable() {
                        @Override
                        public void run() {
                            if (!started) {
                                rewardClaimsInFlight.remove(claimKey);
                                Player current = Bukkit.getPlayer(uuid);
                                if (current != null && current.isOnline()) {
                                    send(current, "quest.claim_locked",
                                        "{prefix}\u00A7cCette recompense est deja enregistree ou necessite une verification.",
                                        "{quest}", quest.getDisplayName(), "{quest_id}", quest.getId());
                                }
                                return;
                            }
                            distributeReservedReward(
                                uuid, quest, progress, completedAt, claimKey);
                        }
                    });
                } catch (final Exception failure) {
                    KjobLogger.error("[QuestReward] Passage en distribution impossible pour "
                        + uuid + "/" + quest.getId(), failure);
                    cancelPreparedClaim(uuid, quest, claimKey,
                        "erreur avant le debut de distribution: " + safeError(failure));
                }
            }
        });
    }

    private void distributeReservedReward(final UUID uuid, final QuestDefinition quest,
                                           final int progress, final long completedAt,
                                           final String claimKey) {
        Player player = Bukkit.getPlayer(uuid);
        PlayerData data = plugin.getPlayerDataManager().get(uuid);
        QuestData questData = data == null ? null : data.getQuestProgress().get(quest.getId());

        if (player == null || !player.isOnline() || questData == null
                || !questData.isCompleted() || questData.isClaimed()) {
            finishReservedReward(uuid, quest, progress, completedAt, claimKey,
                false, "etat joueur/quete invalide apres passage DISTRIBUTING");
            return;
        }

        // L'etat RAM est ferme avant toute recompense. Le registre SQL
        // DISTRIBUTING interdit deja tout second versement apres redemarrage.
        questData.markClaimed();
        data.markDirty();

        boolean success = false;
        String error = null;
        try {
            grantReward(player, data, quest);
            success = true;
        } catch (Exception failure) {
            error = safeError(failure);
            KjobLogger.error("[QuestReward] Distribution partielle ou echouee pour "
                + uuid + "/" + quest.getId() + "; aucun rejeu automatique", failure);
        }

        finishReservedReward(uuid, quest, questData.getProgress(),
            questData.getCompletedAt(), claimKey, success, error);
    }

    private void grantReward(Player player, PlayerData data, QuestDefinition quest) {
        if (quest.getRewardXp() > 0) {
            LevelUpResult result = plugin.getXpManager().addXP(
                player, data, quest.getJobId(), quest.getRewardXp());
            if (result.isLeveledUp()) {
                plugin.getXpManager().handleLevelUp(
                    player, data, quest.getJobId(), result);
            }
            if (plugin.getHudManager() != null) {
                plugin.getHudManager().onXpGain(
                    player, data, quest.getJobId(), result.getXpActual(), result);
            }
        }

        for (String command : quest.getRewardCommands()) {
            if (!executeRewardCommand(player, quest, command)) {
                throw new IllegalStateException(
                    "Commande de recompense refusee: " + command);
            }
        }
    }

    private void finishReservedReward(final UUID uuid, final QuestDefinition quest,
                                      final int progress, final long completedAt,
                                      final String claimKey, final boolean success,
                                      final String error) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                Exception persistenceFailure = null;
                try {
                    plugin.getDatabaseManager().finishQuestReward(
                        uuid, quest.getId(), progress, completedAt, success, error);
                } catch (Exception failure) {
                    persistenceFailure = failure;
                    KjobLogger.error("[QuestReward] Finalisation SQL impossible pour "
                        + uuid + "/" + quest.getId()
                        + "; le verrou durable reste non rejouable", failure);
                }

                final Exception finalPersistenceFailure = persistenceFailure;
                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        rewardClaimsInFlight.remove(claimKey);
                        Player current = Bukkit.getPlayer(uuid);
                        if (current == null || !current.isOnline()) return;

                        if (success && finalPersistenceFailure == null) {
                            send(current, "quest.claimed",
                                "{prefix}\u00A7aRecompense recuperee pour \u00A7f{quest}\u00A7a!",
                                "{quest}", quest.getDisplayName(), "{quest_id}", quest.getId());
                            playSound(current, "quest_reward_claimed");
                        } else {
                            send(current, "quest.claim_locked",
                                "{prefix}\u00A7cDistribution interrompue et verrouillee pour verification staff. Aucun rejeu automatique.",
                                "{quest}", quest.getDisplayName(), "{quest_id}", quest.getId());
                        }
                    }
                });
            }
        });
    }

    private void rejectAlreadyReservedClaim(UUID uuid, QuestDefinition quest,
                                            String claimKey) {
        rewardClaimsInFlight.remove(claimKey);
        PlayerData data = plugin.getPlayerDataManager().get(uuid);
        QuestData questData = data == null ? null : data.getQuestProgress().get(quest.getId());
        if (questData != null && !questData.isClaimed()) {
            questData.markClaimed();
            data.markDirty();
            saveQuestAsync(uuid, questData);
        }

        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            send(player, "quest.claim_locked",
                "{prefix}\u00A7cCette recompense est deja enregistree ou necessite une verification. Aucun nouveau versement.",
                "{quest}", quest.getDisplayName(), "{quest_id}", quest.getId());
        }
    }

    private void cancelPreparedClaim(final UUID uuid, final QuestDefinition quest,
                                     final String claimKey, final String reason) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    boolean cancelled = plugin.getDatabaseManager()
                        .cancelPreparedQuestReward(uuid, quest.getId());
                    if (!cancelled) {
                        KjobLogger.warn("[QuestReward] Reservation PREPARED non annulable pour "
                            + uuid + "/" + quest.getId() + ": " + reason);
                    }
                } catch (Exception failure) {
                    KjobLogger.error("[QuestReward] Annulation PREPARED impossible pour "
                        + uuid + "/" + quest.getId(), failure);
                } finally {
                    rewardClaimsInFlight.remove(claimKey);
                }
            }
        });
    }

    public Collection<QuestDefinition> getQuests() {
        return catalog.quests.values();
    }

    public QuestDefinition getQuest(String questId) {
        if (questId == null) return null;
        Catalog current = catalog;
        QuestDefinition exact = current.quests.get(questId);
        if (exact != null) return exact;
        return current.questsByNormalizedId.get(normalizeId(questId));
    }

    public List<String> getQuestIds() {
        List<String> ids = new ArrayList<String>(catalog.quests.keySet());
        Collections.sort(ids);
        return ids;
    }

    public List<QuestDefinition> getQuestsForJob(String jobId) {
        List<QuestDefinition> result = new ArrayList<QuestDefinition>();
        for (QuestDefinition quest : catalog.quests.values()) {
            if (quest.getJobId().equalsIgnoreCase(jobId)) result.add(quest);
        }
        return result;
    }

    public int countClaimable(PlayerData data) {
        int count = 0;
        for (QuestDefinition quest : catalog.quests.values()) {
            QuestData qd = data.getQuestProgress().get(quest.getId());
            if (qd != null && qd.isCompleted() && !qd.isClaimed()) count++;
        }
        return count;
    }

    public boolean isEnabled() {
        return catalog.enabled;
    }

    public boolean isCustomQuestTypeDeclared(String type) {
        return catalog.customTypes.contains(QuestDefinition.normalize(type));
    }

    public Collection<QuestChainDefinition> getChains() {
        return catalog.chains.values();
    }

    public QuestChainDefinition getChain(String chainId) {
        if (chainId == null) return null;
        return catalog.chains.get(normalizeId(chainId));
    }

    public QuestDefinition getActiveQuest(PlayerData data, String chainId) {
        if (data == null) return null;
        QuestChainDefinition chain = getChain(chainId);
        return chain == null ? null : QuestChainPolicy.firstIncomplete(
                chain, data.getQuestProgress());
    }

    public String getQuestState(PlayerData data, QuestDefinition quest) {
        if (data == null || quest == null) return QuestChainPolicy.LOCKED_CHAIN;
        QuestChainDefinition chain = getChain(quest.getChainId());
        if (chain == null) return QuestChainPolicy.LOCKED_CHAIN;
        boolean jobActive = plugin.getSlotManager()
                .isJobActive(data, quest.getJobId());
        return QuestChainPolicy.state(chain, quest, data.getQuestProgress(),
                jobActive, data.getLevel(quest.getJobId()));
    }

    public boolean forceComplete(Player player, String questId) {
        if (!catalog.enabled || player == null || questId == null) return false;
        QuestDefinition quest = getQuest(questId);
        if (quest == null) return false;

        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data == null) return false;
        String stateKey = claimKey(player.getUniqueId(), quest.getId());
        if (!rewardClaimsInFlight.add(stateKey)) {
            return false;
        }

        QuestData questData = data.getQuestProgress().get(quest.getId());
        if (questData == null) {
            questData = new QuestData(quest.getId());
            data.getQuestProgress().put(quest.getId(), questData);
        }

        questData.setProgress(quest.getAmount());
        questData.setCompleted(true);
        questData.setClaimed(false);
        questData.setCompletedAt(System.currentTimeMillis());
        data.markDirty();
        resetQuestStateAsync(data.getUuid(), questData, stateKey);
        notifyCompleted(player, quest);
        return true;
    }

    public boolean resetQuest(Player player, String questId) {
        if (!catalog.enabled || player == null || questId == null) return false;
        QuestDefinition quest = getQuest(questId);
        if (quest == null) return false;

        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data == null) return false;
        String stateKey = claimKey(player.getUniqueId(), quest.getId());
        if (!rewardClaimsInFlight.add(stateKey)) {
            return false;
        }

        QuestData questData = data.getQuestProgress().get(quest.getId());
        if (questData == null) {
            questData = new QuestData(quest.getId());
            data.getQuestProgress().put(quest.getId(), questData);
        } else {
            questData.reset();
        }

        data.markDirty();
        resetQuestStateAsync(data.getUuid(), questData, stateKey);
        return true;
    }

    public int resetAllQuests(Player player) {
        if (!catalog.enabled || player == null) return 0;
        int count = 0;
        for (QuestDefinition quest : catalog.quests.values()) {
            if (resetQuest(player, quest.getId())) count++;
        }
        return count;
    }

    private void notifyCompleted(Player player, QuestDefinition quest) {
        send(player, "quest.completed",
            "{prefix}\u00A7a\u00A7lQuete terminee ! \u00A7f{quest} \u00A77- Ouvre \u00A7e/jobs \u00A77pour recuperer ta recompense.",
            "{quest}", quest.getDisplayName(), "{quest_id}", quest.getId());
        playSound(player, "quest_complete");
    }

    private boolean executeRewardCommand(Player player, QuestDefinition quest, String raw) {
        if (raw == null || raw.trim().isEmpty()) return true;
        String command = raw.trim()
            .replace("{player}", player.getName())
            .replace("{uuid}", player.getUniqueId().toString())
            .replace("{quest}", quest.getDisplayName())
            .replace("{quest_id}", quest.getId())
            .replace("{job}", quest.getJobId());

        String lower = command.toLowerCase();
        if (lower.startsWith("[player]")) {
            return player.performCommand(
                stripSlash(command.substring("[player]".length()).trim()));
        } else if (lower.startsWith("[joueur]")) {
            return player.performCommand(
                stripSlash(command.substring("[joueur]".length()).trim()));
        } else if (lower.startsWith("[console]")) {
            return Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                stripSlash(command.substring("[console]".length()).trim()));
        } else if (lower.startsWith("[command]")) {
            return Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                stripSlash(command.substring("[command]".length()).trim()));
        } else {
            return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), stripSlash(command));
        }
    }

    private void saveQuestAsync(final UUID uuid, final QuestData questData) {
        final String questId = questData.getQuestId();
        final int progress = questData.getProgress();
        final boolean completed = questData.isCompleted();
        final boolean claimed = questData.isClaimed();
        final long completedAt = questData.getCompletedAt();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    plugin.getDatabaseManager().saveQuestProgress(uuid, questId,
                        progress, completed, claimed, completedAt);
                } catch (Exception e) {
                    KjobLogger.error("Impossible de sauvegarder la quete " + questId + " pour " + uuid, e);
                }
            }
        });
    }

    private void resetQuestStateAsync(final UUID uuid, final QuestData questData,
                                      final String stateKey) {
        final String questId = questData.getQuestId();
        final int progress = questData.getProgress();
        final boolean completed = questData.isCompleted();
        final long completedAt = questData.getCompletedAt();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    plugin.getDatabaseManager().resetQuestState(
                        uuid, questId, progress, completed, completedAt);
                } catch (Exception e) {
                    KjobLogger.error("Impossible de reinitialiser la quete "
                        + questId + " pour " + uuid, e);
                } finally {
                    rewardClaimsInFlight.remove(stateKey);
                }
            }
        });
    }

    private String claimKey(UUID uuid, String questId) {
        return uuid.toString() + ":" + questId.toLowerCase();
    }

    private String safeError(Throwable failure) {
        if (failure == null) return "erreur inconnue";
        String message = failure.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = failure.getClass().getName();
        }
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }

    private void playSound(Player player, String key) {
        FileConfiguration sounds = plugin.getConfigManager().getSoundsConfig();
        if (!sounds.getBoolean(key + ".enabled", true)) return;
        String soundName = sounds.getString(key + ".sound", "NOTE_PLING");
        float volume = (float) sounds.getDouble(key + ".volume", 1.0D);
        float pitch = (float) sounds.getDouble(key + ".pitch", 1.0D);
        try {
            player.playSound(player.getLocation(), Sound.valueOf(soundName), volume, pitch);
        } catch (IllegalArgumentException ignored) {
            KjobLogger.warn("[Quests] Son inconnu: " + soundName);
        }
    }

    private void send(Player player, String key, String fallback, String... replacements) {
        String msg = plugin.getConfigManager().getMessage(key, fallback);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            msg = msg.replace(replacements[i], replacements[i + 1] == null ? "" : replacements[i + 1]);
        }
        if (!msg.isEmpty()) player.sendMessage(color(msg));
    }

    private String color(String value) {
        return value == null ? "" : value.replace("&", "\u00A7");
    }

    private String stripSlash(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }

    private static String normalizeId(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static Set<String> setOf(String... values) {
        return new HashSet<String>(Arrays.asList(values));
    }

    private static void validateKeys(ConfigurationSection section,
            Set<String> allowed, String path, List<String> errors) {
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            if (!allowed.contains(key)) {
                errors.add(path + "." + key + ": cle inconnue.");
            }
        }
    }
}
