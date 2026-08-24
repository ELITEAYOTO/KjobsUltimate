package me.krunsh.kjobultimate.hooks;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.action.CraftUnitResolver;
import me.krunsh.kjobultimate.data.PlayerData;
import me.krunsh.kjobultimate.jobs.JobDefinition;
import me.krunsh.kjobultimate.util.ConfiguredItemMatcher;
import me.krunsh.kjobultimate.util.KjobLogger;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

/**
 * Hook Kcraft V3.13 sans dépendance compile-time sur le plugin.
 *
 * Ordre de résolution Artisan :
 * 1. action exacte "KCRAFT:<craftId>" ;
 * 2. fallback historique sur le Material du résultat.
 *
 * Les crafts forcés ne donnent rien par défaut :
 * allow_forced: true doit être explicite sur l'action retenue.
 */
public final class KcraftHook implements Listener {

    private static final String EVENT_CLASS =
        "me.krunsh.kcraft.api.events.KcraftPostCraftEvent";

    private static final String ARTISAN_JOB_ID =
        "artisan";

    private static final String PILLEUR_JOB_ID =
        "pilleur";

    private final KjobUltimate plugin;
    private MethodHandle getPlayer;
    private MethodHandle getResult;
    private MethodHandle getCraftId;
    private MethodHandle isSuccess;
    private MethodHandle wasForced;
    private boolean failureLogged;

    public KcraftHook(
            KjobUltimate plugin) {

        this.plugin =
            plugin;
    }

    public boolean register() {
        Plugin target = plugin.getServer()
            .getPluginManager()
            .getPlugin("Kcraft");

        if (target == null || !target.isEnabled()) {
            return false;
        }

        try {
            Class<?> raw = Class.forName(
                EVENT_CLASS,
                false,
                target.getClass().getClassLoader()
            );

            if (!Event.class.isAssignableFrom(raw)) {
                throw new IllegalStateException(
                    "le contrat n'est pas un Event Bukkit"
                );
            }

            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            getPlayer = handle(lookup, raw, "getPlayer");
            getResult = handle(lookup, raw, "getResult");
            getCraftId = handle(lookup, raw, "getCraftId");
            isSuccess = handle(lookup, raw, "isSuccess");
            wasForced = handle(lookup, raw, "wasForced");

            @SuppressWarnings("unchecked")
            Class<? extends Event> eventClass =
                (Class<? extends Event>) raw;

            plugin.getServer()
                .getPluginManager()
                .registerEvent(
                    eventClass,
                    this,
                    EventPriority.MONITOR,
                    new EventExecutor() {
                        @Override
                        public void execute(
                                Listener listener,
                                Event event)
                                throws EventException {

                            onKcraftPost(event);
                        }
                    },
                    plugin,
                    false
                );

            return true;
        } catch (Throwable failure) {
            warnOnce(failure);
            return false;
        }
    }

    public void onKcraftPost(
            Event event) {

        try {
            if (event == null
                    || !Boolean.TRUE.equals(
                        isSuccess.invoke(event)
                    )) {

                return;
            }

            Player player =
                (Player) getPlayer.invoke(event);
            ItemStack craftResult =
                (ItemStack) getResult.invoke(event);
            String craftId =
                (String) getCraftId.invoke(event);
            boolean forced = Boolean.TRUE.equals(
                wasForced.invoke(event)
            );

            handleCraft(
                player,
                craftResult,
                craftId,
                forced
            );
        } catch (Throwable failure) {
            warnOnce(failure);
        }
    }

    private void handleCraft(
            Player player,
            ItemStack craftResult,
            String craftId,
            boolean forced) {

        if (player == null
                || player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR) {

            return;
        }

        if (craftResult == null
                || craftResult.getType() == null
                || craftResult.getType() == Material.AIR) {

            return;
        }

        handleArtisanCraft(
            player,
            craftResult,
            craftId,
            forced
        );

        handlePilleurDynamiteCraft(
            player,
            craftResult,
            forced
        );
    }

    private static MethodHandle handle(
            MethodHandles.Lookup lookup,
            Class<?> owner,
            String name) throws IllegalAccessException,
            NoSuchMethodException {

        Method method = owner.getMethod(name);
        return lookup.unreflect(method);
    }

    private void warnOnce(Throwable failure) {
        if (failureLogged) {
            return;
        }

        failureLogged = true;
        KjobLogger.warn(
            "Hook Kcraft inactif : "
                + failure.getClass().getSimpleName()
                + ": "
                + String.valueOf(failure.getMessage())
        );
    }

    private void handleArtisanCraft(
            Player player,
            ItemStack craftResult,
            String craftId,
            boolean forced) {

        JobDefinition job =
            plugin.getJobRegistry()
                .getJob(
                    ARTISAN_JOB_ID
                );

        if (job == null) {
            return;
        }

        PlayerData data =
            plugin.getPlayerDataManager()
                .get(
                    player
                );

        if (data == null
                || !plugin.getSlotManager()
                    .isJobActive(
                        data,
                        ARTISAN_JOB_ID
                    )) {

            return;
        }

        JobDefinition.ActionReward action =
            resolveArtisanAction(
                job,
                craftId,
                craftResult
            );

        if (action == null) {
            return;
        }

        int units =
            CraftUnitResolver
                .resolveKcraft(
                    craftResult,
                    action
                );

        if (units <= 0) {
            return;
        }

        plugin.getJobActionService()
            .apply(
                player,
                data,
                job,
                action,
                units,
                "CRAFT",
                craftResult
                    .getType()
                    .name(),
                forced
            );
    }

    private JobDefinition.ActionReward resolveArtisanAction(
            JobDefinition job,
            String craftId,
            ItemStack craftResult) {

        if (craftId != null
                && !craftId.trim().isEmpty()) {

            JobDefinition.ActionReward exact =
                job.getAction(
                    "KCRAFT:"
                        + craftId
                );

            if (exact != null) {
                return exact;
            }
        }

        return job.getAction(
            craftResult
                .getType()
                .name()
        );
    }

    private void handlePilleurDynamiteCraft(
            Player player,
            ItemStack craftResult,
            boolean forced) {

        if (!isDynamiteItem(
                craftResult
            )) {

            return;
        }

        JobDefinition job =
            plugin.getJobRegistry()
                .getJob(
                    PILLEUR_JOB_ID
                );

        if (job == null) {
            return;
        }

        JobDefinition.ActionReward action =
            job.getAction(
                "DYNAMITE_CRAFT"
            );

        if (action == null) {
            return;
        }

        PlayerData data =
            plugin.getPlayerDataManager()
                .get(
                    player
                );

        if (data == null
                || !plugin.getSlotManager()
                    .isJobActive(
                        data,
                        PILLEUR_JOB_ID
                    )) {

            return;
        }

        /*
         * Pilleur compte les dynamites produites par item de résultat.
         * Cette quantité unique est ensuite utilisée par JobActionService.
         */
        int units =
            Math.max(
                1,
                craftResult.getAmount()
            );

        plugin.getJobActionService()
            .apply(
                player,
                data,
                job,
                action,
                units,
                "DYNAMITE_CRAFT",
                "DYNAMITE",
                forced
            );
    }

    private boolean isDynamiteItem(
            ItemStack item) {

        return ConfiguredItemMatcher
            .matches(
                plugin,
                item,
                "pilleur.dynamite_item"
            );
    }
}
