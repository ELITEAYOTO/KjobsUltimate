package me.krunsh.kjobultimate.hooks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import me.krunsh.kgui.api.InvalidationRequest;
import me.krunsh.kgui.api.KguiApi;
import me.krunsh.kgui.api.MenuArguments;
import me.krunsh.kgui.api.MenuOpenRequest;
import me.krunsh.kgui.api.MenuOpenResult;
import me.krunsh.kgui.api.OwnedRegistration;
import me.krunsh.kjobultimate.KjobUltimate;
import me.krunsh.kjobultimate.integration.kgui.KjobsActions;
import me.krunsh.kjobultimate.integration.kgui.KjobsContentProviders;
import me.krunsh.kjobultimate.integration.kgui.KjobsRequirements;

/** Extension Kgui V2 possédée et nettoyée par KjobsUltimate. */
public final class KguiHook implements AutoCloseable {

    public static final Set<String> PACK_MENUS = Collections.unmodifiableSet(
            new LinkedHashSet<String>(Arrays.asList("kjobs_main", "kjobs_detail",
                    "kjobs_quests", "kjobs_top", "kjobs_settings", "kjobs_confirm_leave")));

    private final KjobUltimate plugin;
    private final KguiApi api;
    private final KjobsContentProviders contentProviders;
    private final List<OwnedRegistration> registrations = new ArrayList<OwnedRegistration>();
    private boolean closed;

    public KguiHook(KjobUltimate plugin) {
        if (plugin == null) throw new IllegalArgumentException("plugin must not be null");
        RegisteredServiceProvider<KguiApi> service = Bukkit.getServicesManager().getRegistration(KguiApi.class);
        KguiApi resolved = service == null ? null : service.getProvider();
        if (resolved == null || resolved.getApiMajor() != 2) {
            throw new IllegalStateException("API Kgui majeure 2 indisponible");
        }
        this.plugin = plugin;
        this.api = resolved;
        this.contentProviders = new KjobsContentProviders(plugin, this);

        try {
            contentProviders.register(api, registrations);
            new KjobsActions(plugin, this).register(api, registrations);
            new KjobsRequirements(plugin).register(api, registrations);
            registrations.add(api.registerMenuPack(plugin, "kjobsultimate:volkaria", PACK_MENUS));
        } catch (RuntimeException failure) {
            close();
            throw failure;
        }
    }

    public int getRegisteredProviders() {
        return contentProviders.getProviderCount();
    }

    public boolean openMenu(Player player, String menuId, Map<String, String> arguments) {
        if (closed || player == null || !player.isOnline() || menuId == null) return false;
        Map<String, String> safe = arguments == null
                ? Collections.<String, String>emptyMap()
                : new LinkedHashMap<String, String>(arguments);
        MenuOpenResult result = api.openMenu(new MenuOpenRequest(
                player.getUniqueId(), menuId, 0, new MenuArguments(safe)));
        return result == MenuOpenResult.OPENED;
    }

    public boolean openMenu(Player player, String menuId) {
        return openMenu(player, menuId, Collections.<String, String>emptyMap());
    }

    public void invalidate(UUID playerId, String reason, String... menuIds) {
        if (closed || playerId == null) return;
        contentProviders.invalidateRevision();
        if (menuIds == null || menuIds.length == 0) {
            api.invalidate(InvalidationRequest.player(playerId, reason));
            return;
        }
        for (String menuId : menuIds) {
            if (menuId == null || !PACK_MENUS.contains(menuId)) continue;
            api.invalidate(InvalidationRequest.playerMenu(playerId, menuId,
                    Collections.<String>emptySet(), reason));
        }
    }

    public void clearRankingCache(String reason) {
        if (closed) return;
        contentProviders.clearRankingCache();
        api.invalidate(InvalidationRequest.menu("kjobs_top", reason));
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        contentProviders.close();
        for (int index = registrations.size() - 1; index >= 0; index--) {
            try {
                registrations.get(index).close();
            } catch (RuntimeException ignored) {
            }
        }
        registrations.clear();
        try {
            api.unregisterAll(plugin);
        } catch (RuntimeException ignored) {
        }
    }
}
