package me.krunsh.kjobultimate.hud;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.Achievement;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.util.KjobLogger;

/**
 * Adaptateur NMS du HUD.
 *
 * V3.16 :
 * - toute la reflexion est resolue une seule fois au demarrage ;
 * - aucun Class.forName/getMethod/getField sur les refresh HUD ;
 * - centralise ActionBar, Title, BossBar et toast statistic.
 */
public final class HudNmsAdapter {

    private final KjobUltimate plugin;
    private final String nms;

    private boolean available;
    private Throwable initFailure;

    private Class<?> packetClass;
    private Class<?> entityClass;
    private Class<?> entityLivingClass;
    private Class<?> dataWatcherClass;
    private Class<?> chatBaseClass;
    private Class<?> titleActionClass;
    private Class<?> entityWitherClass;
    private Class<?> entityDragonClass;
    private Class<?> achievementListClass;

    private Method craftPlayerGetHandle;
    private Method craftWorldGetHandle;
    private Field playerConnectionField;
    private Method connectionSendPacket;

    private Method entitySetId;
    private Method entitySetPosition;
    private Method entitySetCustomName;
    private Method entitySetInvisible;
    private Method entityGetDataWatcher;
    private Method entityLivingSetHealth;
    private Method witherSetInvul;

    private Method chatSerializerParse;
    private Method craftChatFromString;

    private Constructor<?> witherConstructor;
    private Constructor<?> dragonConstructor;
    private Constructor<?> spawnLivingConstructor;
    private Constructor<?> metadataConstructor;
    private Constructor<?> teleportConstructor;
    private Constructor<?> destroyConstructor;
    private Constructor<?> chatConstructor;
    private Constructor<?> titleBasicConstructor;
    private Constructor<?> titleTimedConstructor;
    private Constructor<?> statisticConstructor;

    private final AtomicLong packets = new AtomicLong();
    private final AtomicLong actionBarPackets = new AtomicLong();
    private final AtomicLong titlePackets = new AtomicLong();
    private final AtomicLong bossSpawnPackets = new AtomicLong();
    private final AtomicLong bossMetadataPackets = new AtomicLong();
    private final AtomicLong bossTeleportPackets = new AtomicLong();
    private final AtomicLong bossDestroyPackets = new AtomicLong();
    private final AtomicLong statisticPackets = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();
    private final AtomicLong reflectionResolutions = new AtomicLong();

    public HudNmsAdapter(KjobUltimate plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("plugin ne peut pas etre null.");
        }

        this.plugin = plugin;

        String pkg =
            Bukkit.getServer()
                .getClass()
                .getPackage()
                .getName();

        this.nms =
            pkg.substring(pkg.lastIndexOf('.') + 1);

        resolve();
    }

    private void resolve() {
        try {
            Class<?> craftPlayerClass =
                cls("org.bukkit.craftbukkit." + nms + ".entity.CraftPlayer");

            Class<?> craftWorldClass =
                cls("org.bukkit.craftbukkit." + nms + ".CraftWorld");

            packetClass =
                cls("net.minecraft.server." + nms + ".Packet");

            entityClass =
                cls("net.minecraft.server." + nms + ".Entity");

            entityLivingClass =
                cls("net.minecraft.server." + nms + ".EntityLiving");

            Class<?> worldClass =
                cls("net.minecraft.server." + nms + ".World");

            dataWatcherClass =
                cls("net.minecraft.server." + nms + ".DataWatcher");

            entityWitherClass =
                cls("net.minecraft.server." + nms + ".EntityWither");

            entityDragonClass =
                cls("net.minecraft.server." + nms + ".EntityEnderDragon");

            Class<?> spawnClass =
                cls("net.minecraft.server." + nms
                    + ".PacketPlayOutSpawnEntityLiving");

            Class<?> metadataClass =
                cls("net.minecraft.server." + nms
                    + ".PacketPlayOutEntityMetadata");

            Class<?> teleportClass =
                cls("net.minecraft.server." + nms
                    + ".PacketPlayOutEntityTeleport");

            Class<?> destroyClass =
                cls("net.minecraft.server." + nms
                    + ".PacketPlayOutEntityDestroy");

            Class<?> chatPacketClass =
                cls("net.minecraft.server." + nms
                    + ".PacketPlayOutChat");

            chatBaseClass =
                cls("net.minecraft.server." + nms
                    + ".IChatBaseComponent");

            Class<?> chatSerializerClass =
                cls("net.minecraft.server." + nms
                    + ".IChatBaseComponent$ChatSerializer");

            Class<?> craftChatClass =
                cls("org.bukkit.craftbukkit." + nms
                    + ".util.CraftChatMessage");

            Class<?> titleClass =
                cls("net.minecraft.server." + nms
                    + ".PacketPlayOutTitle");

            titleActionClass =
                cls("net.minecraft.server." + nms
                    + ".PacketPlayOutTitle$EnumTitleAction");

            Class<?> statisticClass =
                cls("net.minecraft.server." + nms
                    + ".PacketPlayOutStatistic");

            achievementListClass =
                cls("net.minecraft.server." + nms
                    + ".AchievementList");

            craftPlayerGetHandle =
                method(craftPlayerClass, "getHandle");

            Class<?> entityPlayerClass =
                craftPlayerGetHandle.getReturnType();

            playerConnectionField =
                field(entityPlayerClass, "playerConnection");

            connectionSendPacket =
                method(
                    playerConnectionField.getType(),
                    "sendPacket",
                    packetClass
                );

            craftWorldGetHandle =
                method(craftWorldClass, "getHandle");

            entitySetId =
                method(entityClass, "d", int.class);

            entitySetPosition =
                method(
                    entityClass,
                    "setPosition",
                    double.class,
                    double.class,
                    double.class
                );

            entitySetCustomName =
                method(entityClass, "setCustomName", String.class);

            entitySetInvisible =
                method(entityClass, "setInvisible", boolean.class);

            entityGetDataWatcher =
                method(entityClass, "getDataWatcher");

            entityLivingSetHealth =
                method(entityLivingClass, "setHealth", float.class);

            witherSetInvul =
                optionalMethod(entityWitherClass, "r", int.class);

            witherConstructor =
                constructor(entityWitherClass, worldClass);

            dragonConstructor =
                constructor(entityDragonClass, worldClass);

            spawnLivingConstructor =
                constructor(spawnClass, entityLivingClass);

            metadataConstructor =
                constructor(
                    metadataClass,
                    int.class,
                    dataWatcherClass,
                    boolean.class
                );

            teleportConstructor =
                constructor(teleportClass, entityClass);

            destroyConstructor =
                constructor(destroyClass, int[].class);

            chatSerializerParse =
                method(chatSerializerClass, "a", String.class);

            chatConstructor =
                constructor(
                    chatPacketClass,
                    chatBaseClass,
                    byte.class
                );

            craftChatFromString =
                method(craftChatClass, "fromString", String.class);

            titleBasicConstructor =
                constructor(
                    titleClass,
                    titleActionClass,
                    chatBaseClass
                );

            titleTimedConstructor =
                constructor(
                    titleClass,
                    titleActionClass,
                    chatBaseClass,
                    int.class,
                    int.class,
                    int.class
                );

            statisticConstructor =
                constructor(statisticClass, Map.class);

            available = true;

        } catch (Throwable failure) {
            initFailure = failure;
            available = false;

            KjobLogger.error(
                "[HUD] Initialisation HudNmsAdapter impossible (" + nms + ")",
                failure
            );
        }
    }

    public String getNms() {
        return nms;
    }

    public boolean isAvailable() {
        return available;
    }

    public String getInitFailureName() {
        return initFailure == null
            ? ""
            : initFailure.getClass().getSimpleName();
    }

    public BossHandle spawnBoss(
            Player player,
            String entityType,
            int entityId,
            double x,
            double y,
            double z,
            float health,
            String title,
            boolean invisible) {

        if (!ready(player)) {
            return null;
        }

        String normalized =
            normalizeEntityType(entityType);

        try {
            Object nmsWorld =
                craftWorldGetHandle.invoke(player.getWorld());

            Object entity =
                ("ENDER_DRAGON".equals(normalized)
                    ? dragonConstructor
                    : witherConstructor)
                    .newInstance(nmsWorld);

            entitySetId.invoke(entity, entityId);

            entitySetPosition.invoke(
                entity,
                x,
                y,
                z
            );

            applyCommonBossState(
                entity,
                normalized,
                health,
                title,
                invisible
            );

            Object spawnPacket =
                spawnLivingConstructor.newInstance(entity);

            sendPacket(player, spawnPacket);
            bossSpawnPackets.incrementAndGet();

            sendMetadata(
                player,
                entityId,
                entity
            );

            return new BossHandle(
                entity,
                entityId,
                normalized
            );

        } catch (Throwable failure) {
            fail("Boss spawn", failure);
            return null;
        }
    }

    public boolean updateBoss(
            Player player,
            BossHandle handle,
            double x,
            double y,
            double z,
            boolean teleport,
            float health,
            String title,
            boolean invisible) {

        if (!ready(player)
                || handle == null
                || handle.entity == null) {

            return false;
        }

        try {
            if (teleport) {
                entitySetPosition.invoke(
                    handle.entity,
                    x,
                    y,
                    z
                );

                Object teleportPacket =
                    teleportConstructor.newInstance(handle.entity);

                sendPacket(player, teleportPacket);
                bossTeleportPackets.incrementAndGet();
            }

            applyCommonBossState(
                handle.entity,
                handle.entityType,
                health,
                title,
                invisible
            );

            sendMetadata(
                player,
                handle.entityId,
                handle.entity
            );

            return true;

        } catch (Throwable failure) {
            fail("Boss update", failure);
            return false;
        }
    }

    public void destroyBoss(
            Player player,
            BossHandle handle) {

        if (!ready(player)
                || handle == null
                || handle.entityId < 0) {

            return;
        }

        try {
            Object packet =
                destroyConstructor.newInstance(
                    (Object) new int[] {
                        handle.entityId
                    }
                );

            sendPacket(player, packet);
            bossDestroyPackets.incrementAndGet();

        } catch (Throwable failure) {
            fail("Boss destroy", failure);
        }
    }

    private void applyCommonBossState(
            Object entity,
            String entityType,
            float health,
            String title,
            boolean invisible)
            throws Exception {

        if ("WITHER".equals(entityType)
                && witherSetInvul != null) {

            /*
             * Supprime l'animation d'invulnerabilite/spawn supplementaire.
             * Les particules normales/armored restent client-side : HudManager
             * les masque par un placement particle-safe.
             */
            witherSetInvul.invoke(entity, 0);
        }

        entitySetInvisible.invoke(entity, invisible);

        entitySetCustomName.invoke(
            entity,
            title == null ? "" : title
        );

        entityLivingSetHealth.invoke(
            entity,
            Math.max(1.0F, health)
        );
    }

    private void sendMetadata(
            Player player,
            int entityId,
            Object entity)
            throws Exception {

        Object watcher =
            entityGetDataWatcher.invoke(entity);

        Object packet =
            metadataConstructor.newInstance(
                entityId,
                watcher,
                true
            );

        sendPacket(player, packet);
        bossMetadataPackets.incrementAndGet();
    }

    public boolean sendActionBar(
            Player player,
            String message) {

        if (!ready(player)) {
            return false;
        }

        try {
            Object component =
                chatSerializerParse.invoke(
                    null,
                    "{\"text\":\""
                        + escapeJson(message == null ? "" : message)
                        + "\"}"
                );

            Object packet =
                chatConstructor.newInstance(
                    component,
                    (byte) 2
                );

            sendPacket(player, packet);
            actionBarPackets.incrementAndGet();

            return true;

        } catch (Throwable failure) {
            fail("ActionBar", failure);
            return false;
        }
    }

    public boolean sendTitle(
            Player player,
            String title,
            String subtitle,
            int fadeIn,
            int stay,
            int fadeOut,
            boolean resetBefore) {

        if (!ready(player)) {
            return false;
        }

        try {
            if (resetBefore) {
                Object resetPacket =
                    titleBasicConstructor.newInstance(
                        enumAction("RESET"),
                        null
                    );

                sendTitlePacket(player, resetPacket);
            }

            Object timesPacket =
                titleTimedConstructor.newInstance(
                    enumAction("TIMES"),
                    null,
                    Math.max(0, fadeIn),
                    Math.max(0, stay),
                    Math.max(0, fadeOut)
                );

            sendTitlePacket(player, timesPacket);

            sendTitlePart(
                player,
                "TITLE",
                title,
                fadeIn,
                stay,
                fadeOut
            );

            if (subtitle != null
                    && !subtitle.isEmpty()) {

                sendTitlePart(
                    player,
                    "SUBTITLE",
                    subtitle,
                    fadeIn,
                    stay,
                    fadeOut
                );
            }

            return true;

        } catch (Throwable failure) {
            fail("Title", failure);
            return false;
        }
    }

    private void sendTitlePart(
            Player player,
            String actionName,
            String text,
            int fadeIn,
            int stay,
            int fadeOut)
            throws Exception {

        Object raw =
            craftChatFromString.invoke(
                null,
                text == null ? "" : text
            );

        Object[] components =
            (Object[]) raw;

        if (components == null
                || components.length == 0) {

            return;
        }

        Object packet =
            titleTimedConstructor.newInstance(
                enumAction(actionName),
                components[0],
                Math.max(0, fadeIn),
                Math.max(0, stay),
                Math.max(0, fadeOut)
            );

        sendTitlePacket(player, packet);
    }

    private void sendTitlePacket(
            Player player,
            Object packet)
            throws Exception {

        sendPacket(player, packet);
        titlePackets.incrementAndGet();
    }

    public boolean sendAchievementStatistic(
            Player player,
            Achievement achievement) {

        if (!ready(player)
                || achievement == null) {

            return false;
        }

        String fieldName =
            achievementField(achievement);

        if (fieldName == null) {
            return false;
        }

        try {
            Object nmsAchievement =
                achievementListClass
                    .getField(fieldName)
                    .get(null);

            Map<Object, Integer> stats =
                new HashMap<Object, Integer>();

            stats.put(
                nmsAchievement,
                Integer.valueOf(1)
            );

            Object packet =
                statisticConstructor.newInstance(stats);

            sendPacket(player, packet);
            statisticPackets.incrementAndGet();

            return true;

        } catch (Throwable failure) {
            fail("Achievement statistic", failure);
            return false;
        }
    }

    private Object enumAction(
            String name)
            throws Exception {

        return titleActionClass
            .getField(name)
            .get(null);
    }

    private void sendPacket(
            Player player,
            Object packet)
            throws Exception {

        Object handle =
            craftPlayerGetHandle.invoke(player);

        Object connection =
            playerConnectionField.get(handle);

        connectionSendPacket.invoke(
            connection,
            packet
        );

        packets.incrementAndGet();
    }

    private boolean ready(Player player) {
        return available
            && player != null
            && player.isOnline();
    }

    private void fail(
            String feature,
            Throwable failure) {

        failures.incrementAndGet();

        Throwable cause =
            unwrap(failure);

        KjobLogger.warn(
            "[HUD] "
                + feature
                + " NMS "
                + cause.getClass().getSimpleName()
                + " : "
                + cause.getMessage()
        );

        if (plugin.getConfigManager().isDebugHud()) {
            cause.printStackTrace();
        }
    }

    private Class<?> cls(String name)
            throws Exception {

        reflectionResolutions.incrementAndGet();
        return Class.forName(name);
    }

    private Method method(
            Class<?> owner,
            String name,
            Class<?>... args)
            throws Exception {

        reflectionResolutions.incrementAndGet();

        Class<?> type = owner;

        while (type != null) {
            try {
                Method method =
                    type.getDeclaredMethod(name, args);

                method.setAccessible(true);
                return method;

            } catch (NoSuchMethodException ignored) {
                type = type.getSuperclass();
            }
        }

        throw new NoSuchMethodException(
            owner.getName() + "#" + name
        );
    }

    private Method optionalMethod(
            Class<?> owner,
            String name,
            Class<?>... args) {

        try {
            return method(owner, name, args);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Field field(
            Class<?> owner,
            String name)
            throws Exception {

        reflectionResolutions.incrementAndGet();

        Class<?> type = owner;

        while (type != null) {
            try {
                Field field =
                    type.getDeclaredField(name);

                field.setAccessible(true);
                return field;

            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }

        throw new NoSuchFieldException(
            owner.getName() + "#" + name
        );
    }

    private Constructor<?> constructor(
            Class<?> owner,
            Class<?>... args)
            throws Exception {

        reflectionResolutions.incrementAndGet();

        Constructor<?> constructor =
            owner.getDeclaredConstructor(args);

        constructor.setAccessible(true);
        return constructor;
    }

    private static String normalizeEntityType(
            String raw) {

        String value =
            raw == null
                ? "WITHER"
                : raw.trim()
                    .toUpperCase()
                    .replace('-', '_');

        if ("DRAGON".equals(value)) {
            value = "ENDER_DRAGON";
        }

        return "ENDER_DRAGON".equals(value)
            ? "ENDER_DRAGON"
            : "WITHER";
    }

    private static Throwable unwrap(
            Throwable failure) {

        Throwable current = failure;

        while (current != null
                && current.getCause() != null
                && (current instanceof java.lang.reflect.InvocationTargetException
                    || current instanceof ExceptionInInitializerError)) {

            current = current.getCause();
        }

        return current == null
            ? failure
            : current;
    }

    private static String escapeJson(String text) {
        StringBuilder out =
            new StringBuilder(text.length() + 16);

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            switch (c) {
                case '\\':
                    out.append("\\\\");
                    break;
                case '"':
                    out.append("\\\"");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        out.append(
                            String.format(
                                "\\u%04x",
                                (int) c
                            )
                        );
                    } else {
                        out.append(c);
                    }
                    break;
            }
        }

        return out.toString();
    }

    private static String achievementField(
            Achievement achievement) {

        switch (achievement) {
            case OPEN_INVENTORY: return "f";
            case MINE_WOOD: return "g";
            case BUILD_WORKBENCH: return "h";
            case BUILD_PICKAXE: return "i";
            case BUILD_FURNACE: return "j";
            case ACQUIRE_IRON: return "k";
            case BUILD_HOE: return "l";
            case MAKE_BREAD: return "m";
            case BAKE_CAKE: return "n";
            case BUILD_BETTER_PICKAXE: return "o";
            case COOK_FISH: return "p";
            case ON_A_RAIL: return "q";
            case BUILD_SWORD: return "r";
            case KILL_ENEMY: return "s";
            case KILL_COW: return "t";
            case FLY_PIG: return "u";
            case SNIPE_SKELETON: return "v";
            case GET_DIAMONDS: return "w";
            case DIAMONDS_TO_YOU: return "x";
            case NETHER_PORTAL: return "y";
            case GHAST_RETURN: return "z";
            case GET_BLAZE_ROD: return "A";
            case BREW_POTION: return "B";
            case END_PORTAL: return "C";
            case THE_END: return "D";
            case ENCHANTMENTS: return "E";
            case OVERKILL: return "F";
            case BOOKCASE: return "G";
            case BREED_COW: return "H";
            case SPAWN_WITHER: return "I";
            case KILL_WITHER: return "J";
            case FULL_BEACON: return "K";
            case EXPLORE_ALL_BIOMES: return "L";
            case OVERPOWERED: return "M";
            default: return null;
        }
    }

    public long getPacketCount() {
        return packets.get();
    }

    public long getActionBarPackets() {
        return actionBarPackets.get();
    }

    public long getTitlePackets() {
        return titlePackets.get();
    }

    public long getBossSpawnPackets() {
        return bossSpawnPackets.get();
    }

    public long getBossMetadataPackets() {
        return bossMetadataPackets.get();
    }

    public long getBossTeleportPackets() {
        return bossTeleportPackets.get();
    }

    public long getBossDestroyPackets() {
        return bossDestroyPackets.get();
    }

    public long getStatisticPackets() {
        return statisticPackets.get();
    }

    public long getFailureCount() {
        return failures.get();
    }

    public long getReflectionResolutions() {
        return reflectionResolutions.get();
    }

    public static final class BossHandle {

        private final Object entity;
        private final int entityId;
        private final String entityType;

        private BossHandle(
                Object entity,
                int entityId,
                String entityType) {

            this.entity = entity;
            this.entityId = entityId;
            this.entityType = entityType;
        }

        public int getEntityId() {
            return entityId;
        }

        public String getEntityType() {
            return entityType;
        }
    }
}
