package me.krunsh.kjobultimate.hooks;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.util.KjobLogger;
import org.bukkit.entity.Entity;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/**
 * Accès optionnel au résultat de kill vérifié par KStacker, sans dépendance
 * compile-time sur son JAR. Une API incomplète désactive entièrement le hook.
 */
public final class KstackerHook implements Listener {

    private final KjobUltimate plugin;
    private Plugin kstacker;
    private MethodHandle getStackKillResult;
    private MethodHandle isLegitimateStackKill;
    private MethodHandle getUnitsConsumed;
    private boolean failureLogged;

    public KstackerHook(KjobUltimate plugin) {
        this.plugin = plugin;
    }

    public boolean register() {
        try {
            Plugin target = plugin.getServer()
                .getPluginManager()
                .getPlugin("KStacker");

            if (target == null || !target.isEnabled()) {
                return false;
            }

            Method method = target.getClass()
                .getMethod("getStackKillResult", Entity.class);
            Class<?> resultType = method.getReturnType();
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();

            getStackKillResult = lookup.unreflect(method);
            isLegitimateStackKill = lookup.unreflect(
                resultType.getMethod("isLegitimateStackKill")
            );
            getUnitsConsumed = lookup.unreflect(
                resultType.getMethod("getUnitsConsumed")
            );
            kstacker = target;
            return true;
        } catch (Throwable failure) {
            clear();
            warnOnce(failure);
            return false;
        }
    }

    /**
     * Retourne true si l'entité est un ghost Kstacker (résultat d'un mob stacké tué).
     */
    public boolean isGhostEntity(Entity entity) {
        Object result = result(entity);
        if (result == null) {
            return false;
        }

        try {
            return Boolean.TRUE.equals(
                isLegitimateStackKill.invoke(result)
            );
        } catch (Throwable failure) {
            warnOnce(failure);
            return false;
        }
    }

    /**
     * Retourne le multiplicateur de kill positionné par Kstacker sur un ghost.
     * La clé de metadata est "kstacker-multiplier" (MobStackService.META_KILL_MULTIPLIER).
     * Si l'entité n'est pas un ghost ou si la metadata est absente, retourne 1.
     */
    public int getKillMultiplier(Entity entity) {
        Object result = result(entity);
        if (result == null) {
            return 1;
        }

        try {
            Object value = getUnitsConsumed.invoke(result);
            return value instanceof Number
                ? Math.max(1, ((Number) value).intValue())
                : 1;
        } catch (Throwable failure) {
            warnOnce(failure);
            return 1;
        }
    }

    public boolean isReady() {
        return kstacker != null
            && getStackKillResult != null
            && isLegitimateStackKill != null
            && getUnitsConsumed != null;
    }

    private Object result(Entity entity) {
        if (!isReady() || entity == null) {
            return null;
        }

        try {
            return getStackKillResult.invoke(kstacker, entity);
        } catch (Throwable failure) {
            warnOnce(failure);
            return null;
        }
    }

    private void clear() {
        kstacker = null;
        getStackKillResult = null;
        isLegitimateStackKill = null;
        getUnitsConsumed = null;
    }

    private void warnOnce(Throwable failure) {
        if (failureLogged) {
            return;
        }

        failureLogged = true;
        KjobLogger.warn(
            "Hook KStacker inactif : "
                + failure.getClass().getSimpleName()
                + ": "
                + String.valueOf(failure.getMessage())
        );
    }
}
