package me.krunsh.kjobultimate.hooks;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

import org.bukkit.Location;
import org.bukkit.Material;
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
 * Abonnement optionnel au contrat post-casse Kminerai, sans dépendance
 * compile-time sur son JAR.
 */
public final class KmineraiJobBridge implements Listener {

    private static final String EVENT_CLASS =
        "me.krunsh.kminerai.api.events.CustomOreMinedEvent";

    private final KjobUltimate plugin;

    private MethodHandle getPlayer;
    private MethodHandle getLocation;
    private MethodHandle getBlockMaterial;
    private MethodHandle getBlockData;
    private MethodHandle getOreId;
    private MethodHandle getDroppedItemId;
    private boolean failureLogged;

    public KmineraiJobBridge(KjobUltimate plugin) {
        this.plugin = plugin;
    }

    public boolean register() {
        Plugin target =
            plugin.getServer()
                .getPluginManager()
                .getPlugin("Kminerai");

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
            getLocation = handle(lookup, raw, "getLocation");
            getBlockMaterial =
                handle(lookup, raw, "getBlockMaterial");
            getBlockData = handle(lookup, raw, "getBlockData");
            getOreId = handle(lookup, raw, "getOreId");
            getDroppedItemId =
                handle(lookup, raw, "getDroppedItemId");

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

                            onCustomOreMined(event);
                        }
                    },
                    plugin,
                    false
                );

            KjobLogger.success(
                "Hook Kminerai actif (post-casse, sans double comptage)."
            );
            return true;

        } catch (Throwable failure) {
            warnOnce(failure);
            return false;
        }
    }

    private void onCustomOreMined(Event event) {
        try {
            Player player =
                (Player) getPlayer.invoke(event);
            Location location =
                (Location) getLocation.invoke(event);
            Material material =
                (Material) getBlockMaterial.invoke(event);
            Number data =
                (Number) getBlockData.invoke(event);
            String oreId =
                (String) getOreId.invoke(event);
            String droppedItemId =
                (String) getDroppedItemId.invoke(event);

            if (player == null
                    || location == null
                    || location.getWorld() == null
                    || material == null
                    || data == null) {

                return;
            }

            plugin.getMiningActionService()
                .apply(
                    player,
                    location.getBlock(),
                    material,
                    data.intValue(),
                    oreId,
                    droppedItemId
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
            "Hook Kminerai inactif : "
                + (failure == null
                    ? "contrat absent"
                    : failure.getClass().getSimpleName()
                        + ": "
                        + String.valueOf(failure.getMessage()))
        );
    }
}
