package me.krunsh.kjobultimate.hooks;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.util.KjobLogger;

/**
 * Ajoute les ventes agrégées de Kenchantement au HUD Kjobs existant. Kjobs
 * reste l'unique propriétaire de l'ActionBar.
 */
public final class AutoSellHudBridge implements Listener {

    private static final String EVENT_CLASS =
        "me.krunsh.kenchantement.api.event.AutoSellActionEvent";

    private final KjobUltimate plugin;
    private MethodHandle getPlayer;
    private MethodHandle getSoldItems;
    private MethodHandle getSoldValue;
    private boolean failureLogged;

    public AutoSellHudBridge(KjobUltimate plugin) {
        this.plugin = plugin;
    }

    public boolean register() {
        Plugin target =
            plugin.getServer()
                .getPluginManager()
                .getPlugin("Kenchantement");

        if (target == null || !target.isEnabled()) {
            return false;
        }

        try {
            Class<?> raw =
                Class.forName(
                    EVENT_CLASS,
                    false,
                    target.getClass()
                        .getClassLoader()
                );

            if (!Event.class.isAssignableFrom(raw)) {
                throw new IllegalStateException(
                    "le contrat n'est pas un Event Bukkit"
                );
            }

            MethodHandles.Lookup lookup =
                MethodHandles.publicLookup();

            getPlayer = handle(lookup, raw, "getPlayer");
            getSoldItems = handle(lookup, raw, "getSoldItems");
            getSoldValue = handle(lookup, raw, "getSoldValue");

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

                            onAutoSell(event);
                        }
                    },
                    plugin,
                    false
                );

            KjobLogger.success(
                "Hook AutoSell actif (HUD Kjobs agrégé)."
            );
            return true;

        } catch (Throwable failure) {
            warnOnce(failure);
            return false;
        }
    }

    private void onAutoSell(Event event) {
        try {
            Player player =
                (Player) getPlayer.invoke(event);
            Number soldItems =
                (Number) getSoldItems.invoke(event);
            Number soldValue =
                (Number) getSoldValue.invoke(event);

            if (player == null
                    || soldItems == null
                    || soldValue == null
                    || plugin.getHudManager() == null) {

                return;
            }

            plugin.getHudManager()
                .onAutoSellGain(
                    player,
                    soldItems.longValue(),
                    soldValue.doubleValue()
                );

        } catch (Throwable failure) {
            warnOnce(failure);
        }
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
            "Hook AutoSell inactif : "
                + (failure == null
                    ? "contrat absent"
                    : failure.getClass().getSimpleName()
                        + ": "
                        + String.valueOf(failure.getMessage()))
        );
    }
}
