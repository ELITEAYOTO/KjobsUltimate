package me.krunsh.kjobultimate.tab;

import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.util.KjobLogger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Prototype de tab virtuel 1.8: lignes fake via PacketPlayOutPlayerInfo.
 *
 * Le module ne touche que les entrees dont le nom technique commence par le
 * prefix configure. Il reste desactive par defaut tant que le rendu n'est pas
 * valide en test avec le fork et les clients utilises.
 */
public final class VirtualTabManager {

    private final KjobUltimate plugin;
    private final TabManager tabManager;
    private final String nms;
    private final Map<UUID, List<VirtualEntry>> cache = new HashMap<UUID, List<VirtualEntry>>();

    private BukkitTask task;
    private boolean enabled;
    private long intervalTicks;
    private int maxRows;
    private String layoutMode;
    private boolean removeOnDisable;
    private boolean debug;
    private String technicalPrefix;
    private String uuidSeed;
    private int startIndex;
    private String separator;
    private String emptyCell;
    private boolean truncateCells;
    private int defaultCellWidth;
    private boolean pixelAlignment;
    private int defaultCellWidthPixels;
    private boolean bottomLinesEnabled;
    private int packedColumns;
    private int packedRows;
    private int packedMaxEntries;
    private int packedReservedRealEntries;
    private boolean packedForceClientRows;
    private boolean hideRealPlayers;
    private String packedBlankText;
    private String packedCellPrefix;
    private String packedCellSuffix;
    private boolean fakeSkinEnabled;
    private String fakeSkinValue;
    private String fakeSkinSignature;
    private String fakeSkinTextureHash;
    private String fakeSkinTextureUrl;
    private String fakeSkinCacheKey;
    private String fakeSkinSource;
    private List<TabColumn> columns = Collections.emptyList();
    private List<String> bottomLines = Collections.emptyList();
    private long lastRenderMs;
    private int lastAdds;
    private int lastUpdates;
    private int lastRemoves;

    public VirtualTabManager(KjobUltimate plugin, TabManager tabManager, String nms) {
        this.plugin = plugin;
        this.tabManager = tabManager;
        this.nms = nms;
    }

    public void reload() {
        boolean wasEnabled = enabled;
        boolean wasHidingRealPlayers = hideRealPlayers;
        enabled = plugin.getConfigManager().getTabConfig().getBoolean("virtual_layout.enabled", false)
            && plugin.getConfigManager().getTabConfig().getBoolean("enabled", true)
            && tabManager.isEnabled();
        intervalTicks = Math.max(20L, plugin.getConfigManager().getTabConfig().getLong("virtual_layout.update_interval_ticks", 60L));
        maxRows = Math.max(1, plugin.getConfigManager().getTabConfig().getInt("virtual_layout.max_rows", 20));
        layoutMode = plugin.getConfigManager().getTabConfig().getString("virtual_layout.layout_mode", "ROW_LINES").toUpperCase();
        removeOnDisable = plugin.getConfigManager().getTabConfig().getBoolean("virtual_layout.remove_on_disable", true);
        debug = plugin.getConfigManager().getTabConfig().getBoolean("virtual_layout.debug", false);
        technicalPrefix = sanitizeTechnicalPrefix(plugin.getConfigManager().getTabConfig().getString("virtual_layout.ordering.technical_name_prefix", "!kjt_"));
        uuidSeed = plugin.getConfigManager().getTabConfig().getString("virtual_layout.ordering.stable_uuid_seed", "kjobsultimate-tab");
        startIndex = Math.max(0, plugin.getConfigManager().getTabConfig().getInt("virtual_layout.ordering.start_index", 1));
        separator = plugin.getConfigManager().getTabConfig().getString("virtual_layout.render.separator", " &8| ");
        emptyCell = plugin.getConfigManager().getTabConfig().getString("virtual_layout.render.empty_cell", "");
        truncateCells = plugin.getConfigManager().getTabConfig().getBoolean("virtual_layout.render.truncate_cells", true);
        defaultCellWidth = Math.max(1, plugin.getConfigManager().getTabConfig().getInt("virtual_layout.render.cell_width_default", 18));
        pixelAlignment = plugin.getConfigManager().getTabConfig().getBoolean("virtual_layout.render.pixel_alignment", true);
        defaultCellWidthPixels = Math.max(16, plugin.getConfigManager().getTabConfig().getInt("virtual_layout.render.cell_width_pixels_default", 132));
        bottomLinesEnabled = plugin.getConfigManager().getTabConfig().getBoolean("virtual_layout.bottom_lines_enabled", false);
        packedColumns = Math.max(1, plugin.getConfigManager().getTabConfig().getInt("virtual_layout.packed_columns.columns", 3));
        packedRows = Math.min(20, Math.max(1, plugin.getConfigManager().getTabConfig().getInt("virtual_layout.packed_columns.rows", 15)));
        packedMaxEntries = Math.max(1, plugin.getConfigManager().getTabConfig().getInt("virtual_layout.packed_columns.max_entries", 44));
        packedReservedRealEntries = Math.max(0, plugin.getConfigManager().getTabConfig().getInt("virtual_layout.packed_columns.reserve_real_entries", 1));
        packedForceClientRows = plugin.getConfigManager().getTabConfig().getBoolean("virtual_layout.packed_columns.force_client_rows", true);
        hideRealPlayers = plugin.getConfigManager().getTabConfig().getBoolean("virtual_layout.packed_columns.hide_real_players", false);
        packedBlankText = plugin.getConfigManager().getTabConfig().getString("virtual_layout.packed_columns.blank_text", "&8");
        packedCellPrefix = plugin.getConfigManager().getTabConfig().getString("virtual_layout.packed_columns.cell_prefix", "&8| ");
        packedCellSuffix = plugin.getConfigManager().getTabConfig().getString("virtual_layout.packed_columns.cell_suffix", "");
        fakeSkinEnabled = plugin.getConfigManager().getTabConfig().getBoolean("virtual_layout.fake_skin.enabled", false);
        fakeSkinValue = plugin.getConfigManager().getTabConfig().getString("virtual_layout.fake_skin.value", "");
        fakeSkinSignature = plugin.getConfigManager().getTabConfig().getString("virtual_layout.fake_skin.signature", "");
        fakeSkinTextureHash = plugin.getConfigManager().getTabConfig().getString("virtual_layout.fake_skin.texture_hash", "");
        fakeSkinTextureUrl = plugin.getConfigManager().getTabConfig().getString("virtual_layout.fake_skin.texture_url", "");
        fakeSkinValue = resolveFakeSkinValue();
        fakeSkinCacheKey = resolveFakeSkinCacheKey();
        columns = loadColumns();
        bottomLines = plugin.getConfigManager().getTabConfig().getStringList("virtual_layout.bottom_lines");

        stopTask();
        if (wasHidingRealPlayers && (!enabled || !hideRealPlayers)) {
            showRealPlayersToAllViewers();
        }
        if (!enabled) {
            if (wasEnabled && removeOnDisable) clearAll();
            KjobLogger.info("VirtualTabManager inactif - virtual_layout.enabled=false.");
            return;
        }
        if (columns.isEmpty() && bottomLines.isEmpty()) {
            KjobLogger.warn("[TAB] virtual_layout actif mais aucune colonne/bottom_lines configuree.");
        }
        if (debug) {
            KjobLogger.info("[TAB-VIRTUAL] fake_skin enabled=" + fakeSkinEnabled
                + " source=" + fakeSkinSource
                + " valueLen=" + (fakeSkinValue == null ? 0 : fakeSkinValue.length())
                + " signature=" + yn(fakeSkinSignature != null && !fakeSkinSignature.trim().isEmpty())
                + " cacheKey=" + fakeSkinCacheKey);
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                tick();
            }
        }, 20L, intervalTicks);
        KjobLogger.success("VirtualTabManager actif (" + nms + ") - interval=" + intervalTicks + " ticks, maxRows=" + maxRows + ".");
    }

    public void shutdown() {
        stopTask();
        if (removeOnDisable) clearAll();
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long getIntervalTicks() {
        return intervalTicks;
    }

    public int getCachedViewers() {
        return cache.size();
    }

    public String statusLine(Player viewer) {
        int cachedRows = 0;
        if (viewer != null) {
            List<VirtualEntry> entries = cache.get(viewer.getUniqueId());
            cachedRows = entries == null ? 0 : entries.size();
        }
        return "virtual=" + yn(enabled)
            + " interval=" + intervalTicks + "t"
            + " maxRows=" + maxRows
            + " mode=" + layoutMode
            + ("PACKED_COLUMNS".equals(layoutMode)
                ? " packed=" + packedColumns + "x" + packedRows + " reserve=" + packedReservedRealEntries
                    + " hideReal=" + yn(hideRealPlayers)
                : "")
            + " skin=" + yn(fakeSkinEnabled && fakeSkinValue != null && !fakeSkinValue.trim().isEmpty())
            + "(" + fakeSkinSource + ",len=" + (fakeSkinValue == null ? 0 : fakeSkinValue.length())
            + ",signed=" + yn(fakeSkinSignature != null && !fakeSkinSignature.trim().isEmpty())
            + ",key=" + fakeSkinCacheKey + ")"
            + " columns=" + columns.size()
            + " viewers=" + cache.size()
            + " viewerRows=" + cachedRows
            + " lastMs=" + lastRenderMs
            + " packets(add=" + lastAdds + ", update=" + lastUpdates + ", remove=" + lastRemoves + ")";
    }

    public List<String> preview(Player viewer) {
        return renderRows(viewer);
    }

    public void clear(Player player) {
        if (player == null) return;
        List<VirtualEntry> old = cache.remove(player.getUniqueId());
        if (old != null) {
            for (VirtualEntry entry : old) {
                sendRemove(player, entry);
            }
        }
        if (hideRealPlayers) showRealPlayers(player);
    }

    public void clearAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            clear(player);
        }
        cache.clear();
    }

    /** À appeler après un join pour ne pas attendre le prochain cycle virtuel. */
    public void refreshRealPlayerVisibility() {
        if (!enabled || !hideRealPlayers || !"PACKED_COLUMNS".equals(layoutMode)) return;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            sendRealPlayerAction(viewer, "REMOVE_PLAYER");
        }
    }

    private void showRealPlayers(Player viewer) {
        sendRealPlayerAction(viewer, "ADD_PLAYER");
    }

    private void showRealPlayersToAllViewers() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            showRealPlayers(viewer);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void sendRealPlayerAction(Player viewer, String actionName) {
        try {
            Class<?> packetClass = Class.forName("net.minecraft.server." + nms + ".PacketPlayOutPlayerInfo");
            Class<?> actionClass = Class.forName("net.minecraft.server." + nms
                + ".PacketPlayOutPlayerInfo$EnumPlayerInfoAction");
            Object action = Enum.valueOf((Class<Enum>) actionClass.asSubclass(Enum.class), actionName);

            Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit." + nms + ".entity.CraftPlayer");
            List<Object> handles = new ArrayList<Object>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                handles.add(craftPlayerClass.getMethod("getHandle").invoke(online));
            }
            if (handles.isEmpty()) return;

            Object packet = null;
            for (Constructor<?> constructor : packetClass.getConstructors()) {
                Class<?>[] types = constructor.getParameterTypes();
                if (types.length != 2 || !types[0].equals(actionClass)) continue;
                if (types[1].isArray()) {
                    Object array = Array.newInstance(types[1].getComponentType(), handles.size());
                    for (int i = 0; i < handles.size(); i++) Array.set(array, i, handles.get(i));
                    packet = constructor.newInstance(action, array);
                    break;
                }
                if (Iterable.class.isAssignableFrom(types[1])) {
                    packet = constructor.newInstance(action, handles);
                    break;
                }
            }
            if (packet == null) throw new NoSuchMethodException("constructeur PlayerInfo réel introuvable");
            sendPacket(viewer, packet);
        } catch (Throwable throwable) {
            KjobLogger.warn("[TAB-VIRTUAL] Visibilité des joueurs réels impossible pour "
                + viewer.getName() + ": " + throwable.getClass().getSimpleName()
                + " " + throwable.getMessage());
        }
    }

    private void tick() {
        if (!enabled) return;
        lastAdds = 0;
        lastUpdates = 0;
        lastRemoves = 0;
        long start = System.nanoTime();
        purgeOfflineCache();
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
        lastRenderMs = Math.max(0L, (System.nanoTime() - start) / 1000000L);
        if (debug) {
            KjobLogger.info("[TAB-VIRTUAL] tick viewers=" + Bukkit.getOnlinePlayers().size()
                + " cache=" + cache.size() + " ms=" + lastRenderMs
                + " packets=" + lastAdds + "/" + lastUpdates + "/" + lastRemoves);
        }
    }

    private void update(Player player) {
        if (hideRealPlayers && "PACKED_COLUMNS".equals(layoutMode)) {
            sendRealPlayerAction(player, "REMOVE_PLAYER");
        }
        List<String> rows = renderRows(player);
        List<VirtualEntry> previous = cache.get(player.getUniqueId());
        if (previous == null) previous = Collections.emptyList();

        List<VirtualEntry> next = new ArrayList<VirtualEntry>();
        int max = Math.max(previous.size(), rows.size());
        for (int i = 0; i < max; i++) {
            VirtualEntry old = i < previous.size() ? previous.get(i) : null;
            String text = i < rows.size() ? rows.get(i) : null;
            if (text == null) {
                if (old != null) {
                    sendRemove(player, old);
                    lastRemoves++;
                }
                continue;
            }

            VirtualEntry entry = old != null ? old : createEntry(player, i);
            if (old == null) {
                entry.text = text;
                sendAdd(player, entry);
                lastAdds++;
            } else if (!text.equals(old.text)) {
                entry.text = text;
                sendUpdateDisplayName(player, entry);
                lastUpdates++;
            }
            next.add(entry);
        }
        cache.put(player.getUniqueId(), next);
    }

    private List<String> renderRows(Player player) {
        if ("PACKED_COLUMNS".equals(layoutMode)) {
            return renderPackedColumns(player);
        }

        List<List<String>> renderedColumns = new ArrayList<List<String>>();
        int height = 0;
        for (TabColumn column : columns) {
            List<String> lines = new ArrayList<String>();
            if (column.title != null && !column.title.trim().isEmpty()) lines.add(column.title);
            for (String rawLine : column.lines) {
                List<String> rendered = tabManager.renderVirtualLines(player, Collections.singletonList(rawLine));
                for (String renderedLine : rendered) {
                    addSplitLines(lines, renderedLine);
                }
            }
            height = Math.max(height, lines.size());
            renderedColumns.add(lines);
        }

        List<String> rows = new ArrayList<String>();
        for (int row = 0; row < height && rows.size() < maxRows; row++) {
            List<String> cells = new ArrayList<String>();
            for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                TabColumn column = columns.get(columnIndex);
                List<String> lines = renderedColumns.get(columnIndex);
                String cell = row < lines.size() ? lines.get(row) : emptyCell;
                cells.add(fitCell(cell, column.width, column.widthPixels));
            }
            rows.add(color(join(cells, separator)));
        }

        if (bottomLinesEnabled && !bottomLines.isEmpty()) {
            List<String> rendered = tabManager.renderVirtualLines(player, bottomLines);
            for (String line : rendered) {
                if (rows.size() >= maxRows) break;
                addSplitRows(rows, line);
            }
        }
        return rows;
    }

    private List<String> renderPackedColumns(Player player) {
        List<List<String>> renderedColumns = buildColumnLines(player);
        int columnCount = Math.min(packedColumns, renderedColumns.size());
        if (columnCount <= 0) return Collections.emptyList();

        int rowCount = packedRows;
        for (int i = 0; i < columnCount; i++) {
            rowCount = Math.max(rowCount, renderedColumns.get(i).size());
        }
        rowCount = Math.min(20, rowCount);

        int entryLimit = PackedTabSizing.fakeEntryLimit(packedForceClientRows,
            columnCount, rowCount, packedMaxEntries, packedReservedRealEntries,
            Bukkit.getOnlinePlayers().size(), hideRealPlayers);

        List<String> entries = new ArrayList<String>();
        for (int column = 0; column < columnCount; column++) {
            List<String> lines = renderedColumns.get(column);
            for (int row = 0; row < rowCount && entries.size() < entryLimit; row++) {
                String value = row < lines.size() ? lines.get(row) : packedBlankText;
                if (value == null || value.trim().isEmpty()) value = packedBlankText;
                entries.add(color(packedCellPrefix + value + packedCellSuffix));
            }
        }
        return entries;
    }

    private List<List<String>> buildColumnLines(Player player) {
        List<List<String>> renderedColumns = new ArrayList<List<String>>();
        for (TabColumn column : columns) {
            List<String> lines = new ArrayList<String>();
            if (column.title != null && !column.title.trim().isEmpty()) lines.add(column.title);
            for (String rawLine : column.lines) {
                List<String> rendered = tabManager.renderVirtualLines(player, Collections.singletonList(rawLine));
                for (String renderedLine : rendered) {
                    addSplitLines(lines, renderedLine);
                }
            }
            renderedColumns.add(lines);
        }
        return renderedColumns;
    }

    private void addSplitLines(List<String> lines, String value) {
        if (value == null) {
            lines.add("");
            return;
        }
        String[] split = value.split("\\n", -1);
        for (String line : split) lines.add(line);
    }

    private void addSplitRows(List<String> rows, String value) {
        if (value == null) {
            rows.add("");
            return;
        }
        String[] split = value.split("\\n", -1);
        for (String line : split) {
            if (rows.size() >= maxRows) return;
            rows.add(color(line));
        }
    }

    private List<TabColumn> loadColumns() {
        if (!plugin.getConfigManager().getTabConfig().getBoolean("virtual_layout.columns_enabled", true)) {
            return Collections.emptyList();
        }
        ConfigurationSection parent = plugin.getConfigManager().getTabConfig().getConfigurationSection("virtual_layout.columns");
        if (parent == null) return Collections.emptyList();
        List<TabColumn> loaded = new ArrayList<TabColumn>();
        for (String id : parent.getKeys(false)) {
            ConfigurationSection section = parent.getConfigurationSection(id);
            if (section == null || !section.getBoolean("enabled", true)) continue;
            loaded.add(new TabColumn(
                id,
                section.getString("title", id),
                Math.max(1, section.getInt("width", defaultCellWidth)),
                Math.max(16, section.getInt("width_pixels", defaultCellWidthPixels)),
                section.getStringList("lines")
            ));
        }
        return loaded;
    }

    private VirtualEntry createEntry(Player viewer, int row) {
        int index = startIndex + row;
        String technicalName = technicalPrefix + leftPad(index, 3);
        if (technicalName.length() > 16) technicalName = technicalName.substring(0, 16);
        UUID uuid = UUID.nameUUIDFromBytes((uuidSeed + ":" + fakeSkinCacheKey + ":" + viewer.getUniqueId() + ":" + row).getBytes(StandardCharsets.UTF_8));
        return new VirtualEntry(row, uuid, technicalName, "");
    }

    private void sendAdd(Player player, VirtualEntry entry) {
        sendPlayerInfo(player, "ADD_PLAYER", entry, entry.text);
    }

    private void sendUpdateDisplayName(Player player, VirtualEntry entry) {
        sendPlayerInfo(player, "UPDATE_DISPLAY_NAME", entry, entry.text);
    }

    private void sendRemove(Player player, VirtualEntry entry) {
        sendPlayerInfo(player, "REMOVE_PLAYER", entry, null);
    }

    private void sendPlayerInfo(Player player, String actionName, VirtualEntry entry, String displayName) {
        try {
            Object packet = createPlayerInfoPacket(actionName, entry, displayName);
            sendPacket(player, packet);
        } catch (Throwable t) {
            KjobLogger.warn("[TAB-VIRTUAL] Packet " + actionName + " impossible pour " + player.getName() + ": "
                + t.getClass().getSimpleName() + " " + t.getMessage());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object createPlayerInfoPacket(String actionName, VirtualEntry entry, String displayName) throws Exception {
        Class<?> packetClass = Class.forName("net.minecraft.server." + nms + ".PacketPlayOutPlayerInfo");
        Object packet = newInstance(packetClass);

        Class<?> actionClass = Class.forName("net.minecraft.server." + nms + ".PacketPlayOutPlayerInfo$EnumPlayerInfoAction");
        Object action = Enum.valueOf((Class<Enum>) actionClass.asSubclass(Enum.class), actionName);
        Field actionField = findField(packetClass, actionClass, "a");
        actionField.setAccessible(true);
        actionField.set(packet, action);

        Field listField = findListField(packetClass, "b");
        listField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Object> data = (List<Object>) listField.get(packet);
        data.clear();
        data.add(createPlayerInfoData(packet, entry, displayName));
        return packet;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object createPlayerInfoData(Object packet, VirtualEntry entry, String displayName) throws Exception {
        Class<?> profileClass = Class.forName("com.mojang.authlib.GameProfile");
        Object profile = profileClass.getConstructor(UUID.class, String.class).newInstance(entry.uuid, entry.technicalName);
        applyFakeSkin(profile);

        Class<?> gamemodeClass = Class.forName("net.minecraft.server." + nms + ".WorldSettings$EnumGamemode");
        Object gamemode = Enum.valueOf((Class<Enum>) gamemodeClass.asSubclass(Enum.class), "SURVIVAL");
        Object component = displayName == null ? null : chatComponent(displayName);

        Class<?> dataClass = Class.forName("net.minecraft.server." + nms + ".PacketPlayOutPlayerInfo$PlayerInfoData");
        for (Constructor<?> constructor : dataClass.getDeclaredConstructors()) {
            Class<?>[] types = constructor.getParameterTypes();
            if (types.length == 5) {
                constructor.setAccessible(true);
                return constructor.newInstance(packet, profile, Integer.valueOf(0), gamemode, component);
            }
            if (types.length == 4) {
                constructor.setAccessible(true);
                return constructor.newInstance(profile, Integer.valueOf(0), gamemode, component);
            }
        }
        throw new NoSuchMethodException("PlayerInfoData constructor introuvable");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void applyFakeSkin(Object profile) {
        if (!fakeSkinEnabled || fakeSkinValue == null || fakeSkinValue.trim().isEmpty()) return;
        try {
            Method getProperties = profile.getClass().getMethod("getProperties");
            Object properties = getProperties.invoke(profile);
            Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
            Object property;
            if (fakeSkinSignature != null && !fakeSkinSignature.trim().isEmpty()) {
                property = propertyClass.getConstructor(String.class, String.class, String.class)
                    .newInstance("textures", fakeSkinValue, fakeSkinSignature);
            } else {
                property = propertyClass.getConstructor(String.class, String.class)
                    .newInstance("textures", fakeSkinValue);
            }
            Method put = properties.getClass().getMethod("put", Object.class, Object.class);
            put.invoke(properties, "textures", property);
        } catch (Throwable t) {
            if (debug) {
                KjobLogger.warn("[TAB-VIRTUAL] fake_skin ignoree: " + t.getClass().getSimpleName() + " " + t.getMessage());
            }
        }
    }

    private String resolveFakeSkinValue() {
        fakeSkinSource = "none";
        if (fakeSkinValue != null && !fakeSkinValue.trim().isEmpty()) {
            fakeSkinSource = "value";
            return fakeSkinValue.trim();
        }
        String url = fakeSkinTextureUrl == null ? "" : fakeSkinTextureUrl.trim();
        String hash = fakeSkinTextureHash == null ? "" : fakeSkinTextureHash.trim();
        if (url.isEmpty() && !hash.isEmpty()) {
            url = "http://textures.minecraft.net/texture/" + hash;
            fakeSkinSource = "hash";
        } else if (!url.isEmpty()) {
            fakeSkinSource = "url";
        }
        if (url.isEmpty()) return "";
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + escapeJson(url) + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private String resolveFakeSkinCacheKey() {
        String configured = plugin.getConfigManager().getTabConfig().getString("virtual_layout.fake_skin.cache_key", "");
        if (configured != null && !configured.trim().isEmpty()) return sanitizeCacheKey(configured.trim());
        if (!fakeSkinEnabled || fakeSkinValue == null || fakeSkinValue.trim().isEmpty()) return "noskin";
        return sanitizeCacheKey(fakeSkinSource + "-" + Integer.toHexString(fakeSkinValue.hashCode()));
    }

    private Object chatComponent(String text) throws Exception {
        Class<?> serializer = Class.forName("net.minecraft.server." + nms + ".IChatBaseComponent$ChatSerializer");
        Method parse = serializer.getMethod("a", String.class);
        return parse.invoke(null, "{\"text\":\"" + escapeJson(text) + "\"}");
    }

    private void sendPacket(Player player, Object packet) throws Exception {
        Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit." + nms + ".entity.CraftPlayer");
        Class<?> packetClass = Class.forName("net.minecraft.server." + nms + ".Packet");
        Object handle = craftPlayerClass.getMethod("getHandle").invoke(player);
        Object connection = handle.getClass().getField("playerConnection").get(handle);
        connection.getClass().getMethod("sendPacket", packetClass).invoke(connection, packet);
    }

    private Object newInstance(Class<?> type) throws Exception {
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private Field findField(Class<?> owner, Class<?> expectedType, String fallbackName) throws NoSuchFieldException {
        try {
            Field field = owner.getDeclaredField(fallbackName);
            if (expectedType.isAssignableFrom(field.getType())) return field;
        } catch (NoSuchFieldException ignored) {
            // Fallback by type below.
        }
        for (Field field : owner.getDeclaredFields()) {
            if (expectedType.isAssignableFrom(field.getType())) return field;
        }
        throw new NoSuchFieldException(fallbackName);
    }

    private Field findListField(Class<?> owner, String fallbackName) throws NoSuchFieldException {
        try {
            Field field = owner.getDeclaredField(fallbackName);
            if (List.class.isAssignableFrom(field.getType())) return field;
        } catch (NoSuchFieldException ignored) {
            // Fallback by type below.
        }
        for (Field field : owner.getDeclaredFields()) {
            if (List.class.isAssignableFrom(field.getType())) return field;
        }
        throw new NoSuchFieldException(fallbackName);
    }

    private void purgeOfflineCache() {
        Set<UUID> online = new HashSet<UUID>();
        for (Player player : Bukkit.getOnlinePlayers()) online.add(player.getUniqueId());
        List<UUID> toRemove = new ArrayList<UUID>();
        for (UUID uuid : cache.keySet()) {
            if (!online.contains(uuid)) toRemove.add(uuid);
        }
        for (UUID uuid : toRemove) cache.remove(uuid);
    }

    private String fitCell(String value, int width, int widthPixels) {
        String raw = value == null ? "" : value;
        if (pixelAlignment) {
            if (truncateCells && pixelWidth(raw) > widthPixels) {
                raw = truncatePixels(raw, widthPixels);
            }
            StringBuilder builder = new StringBuilder(raw);
            while (pixelWidth(builder.toString()) < widthPixels) {
                builder.append(' ');
            }
            return builder.toString();
        }

        if (truncateCells && visibleLength(raw) > width) {
            raw = truncateVisibleChars(raw, width);
        }
        int missing = width - visibleLength(raw);
        if (missing <= 0) return raw;
        StringBuilder builder = new StringBuilder(raw);
        for (int i = 0; i < missing; i++) builder.append(' ');
        return builder.toString();
    }

    private int visibleLength(String text) {
        return ChatColor.stripColor(color(text)).length();
    }

    private String truncateVisibleChars(String text, int maxVisible) {
        String colored = color(text);
        StringBuilder builder = new StringBuilder();
        int visible = 0;
        for (int i = 0; i < colored.length() && visible < maxVisible; i++) {
            char c = colored.charAt(i);
            if (c == ChatColor.COLOR_CHAR && i + 1 < colored.length()) {
                builder.append(c).append(colored.charAt(++i));
                continue;
            }
            builder.append(c);
            visible++;
        }
        return builder.toString();
    }

    private String truncatePixels(String text, int maxPixels) {
        String colored = color(text);
        StringBuilder builder = new StringBuilder();
        int pixels = 0;
        for (int i = 0; i < colored.length(); i++) {
            char c = colored.charAt(i);
            if (c == ChatColor.COLOR_CHAR && i + 1 < colored.length()) {
                builder.append(c).append(colored.charAt(++i));
                continue;
            }
            int width = charWidth(c);
            if (pixels + width > maxPixels) break;
            builder.append(c);
            pixels += width;
        }
        return builder.toString();
    }

    private int pixelWidth(String text) {
        String colored = color(text);
        int width = 0;
        for (int i = 0; i < colored.length(); i++) {
            char c = colored.charAt(i);
            if (c == ChatColor.COLOR_CHAR && i + 1 < colored.length()) {
                i++;
                continue;
            }
            width += charWidth(c);
        }
        return width;
    }

    private int charWidth(char c) {
        switch (c) {
            case ' ': return 4;
            case '!':
            case '.':
            case ',':
            case ':':
            case ';':
            case 'i':
            case '|':
            case '\'':
                return 2;
            case 'l':
            case '`':
                return 3;
            case 'I':
            case '[':
            case ']':
            case 't':
                return 4;
            case 'f':
            case 'k':
            case '{':
            case '}':
            case '<':
            case '>':
            case '"':
            case '*':
            case '(':
            case ')':
                return 5;
            case '@':
            case '~':
                return 7;
            default:
                return 6;
        }
    }

    private String sanitizeTechnicalPrefix(String input) {
        String prefix = input == null || input.trim().isEmpty() ? "!kjt_" : input.trim();
        if (prefix.length() > 12) prefix = prefix.substring(0, 12);
        return prefix;
    }

    private String sanitizeCacheKey(String input) {
        String raw = input == null || input.trim().isEmpty() ? "default" : input.trim();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < raw.length() && builder.length() < 32; i++) {
            char c = raw.charAt(i);
            if ((c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || c == '-' || c == '_') {
                builder.append(c);
            }
        }
        return builder.length() == 0 ? "default" : builder.toString();
    }

    private String color(String text) {
        return text == null ? "" : text.replace("&", "\u00A7");
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private String join(List<String> values, String sep) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) builder.append(sep);
            builder.append(value == null ? "" : value);
        }
        return builder.toString();
    }

    private String leftPad(int value, int size) {
        String raw = String.valueOf(value);
        StringBuilder builder = new StringBuilder();
        for (int i = raw.length(); i < size; i++) builder.append('0');
        builder.append(raw);
        return builder.toString();
    }

    private String yn(boolean value) {
        return value ? "ON" : "OFF";
    }

    private static final class TabColumn {
        private final String id;
        private final String title;
        private final int width;
        private final int widthPixels;
        private final List<String> lines;

        private TabColumn(String id, String title, int width, int widthPixels, List<String> lines) {
            this.id = id;
            this.title = title;
            this.width = width;
            this.widthPixels = widthPixels;
            this.lines = lines == null ? Collections.<String>emptyList() : new ArrayList<String>(lines);
        }
    }

    private static final class VirtualEntry {
        private final int row;
        private final UUID uuid;
        private final String technicalName;
        private String text;

        private VirtualEntry(int row, UUID uuid, String technicalName, String text) {
            this.row = row;
            this.uuid = uuid;
            this.technicalName = technicalName;
            this.text = text;
        }
    }
}
