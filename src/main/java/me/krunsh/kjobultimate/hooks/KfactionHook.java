package me.krunsh.kjobultimate.hooks;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.api.KfactionAPI;
import me.krunsh.kfaction.data.Faction;
import me.krunsh.kfaction.data.FactionRole;
import me.krunsh.kfaction.data.Relation;
import me.krunsh.kjobultimate.KjobUltimate;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Soft-hook Kfaction utilise par Pretorien pour filtrer les kills farmables.
 */
public final class KfactionHook {

    private final KfactionAPI api;

    public KfactionHook(KjobUltimate plugin) {
        Plugin external = plugin.getServer().getPluginManager().getPlugin("Kfaction");
        if (!(external instanceof Kfaction)) {
            throw new IllegalStateException("Plugin Kfaction introuvable ou type inattendu.");
        }
        this.api = ((Kfaction) external).getAPI();
        if (this.api == null) {
            throw new IllegalStateException("API Kfaction non initialisee.");
        }
    }

    public String getRelationName(Player first, Player second) {
        Relation relation = api.getRelation(first, second);
        return relation == null ? "NEUTRAL" : relation.name();
    }

    public String getFactionName(Player player, String fallback) {
        Faction faction = api.getPlayerFaction(player);
        return faction == null ? fallback : faction.getName();
    }

    public int getFactionMembers(Player player) {
        Faction faction = api.getPlayerFaction(player);
        return faction == null ? 0 : faction.getMemberCount();
    }

    public String getFactionMembersLines(Player player, int limit, String fallback) {
        Faction faction = api.getPlayerFaction(player);
        if (faction == null) return fallback;

        List<MemberLine> members = new ArrayList<MemberLine>();
        for (UUID uuid : faction.getMembers()) {
            FactionRole role = faction.getRole(uuid);
            String name = resolveName(uuid);
            members.add(new MemberLine(name, role));
        }
        members.sort(new Comparator<MemberLine>() {
            @Override
            public int compare(MemberLine first, MemberLine second) {
                int role = Integer.compare(roleWeight(second.role), roleWeight(first.role));
                return role != 0 ? role : first.name.compareToIgnoreCase(second.name);
            }
        });

        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (MemberLine member : members) {
            if (limit > 0 && count >= limit) break;
            if (builder.length() > 0) builder.append('\n');
            builder.append(member.name).append(" (").append(displayRole(member.role)).append(")");
            count++;
        }
        return builder.length() == 0 ? fallback : builder.toString();
    }

    public String getFactionRole(Player player, String fallback) {
        Faction faction = api.getPlayerFaction(player);
        if (faction == null) return fallback;
        return displayRole(faction.getRole(player.getUniqueId()));
    }

    private String resolveName(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        return offline != null && offline.getName() != null ? offline.getName() : uuid.toString().substring(0, 8);
    }

    private int roleWeight(FactionRole role) {
        if (role == null) return 0;
        switch (role) {
            case LEADER: return 5;
            case COLEADER: return 4;
            case MODERATOR: return 3;
            case MEMBER: return 2;
            case RECRUIT: return 1;
            default: return 0;
        }
    }

    private String displayRole(FactionRole role) {
        if (role == null) return "Membre";
        switch (role) {
            case LEADER: return "Leader";
            case COLEADER: return "Co-Lead";
            case MODERATOR: return "Modo";
            case MEMBER: return "Membre";
            case RECRUIT: return "Recrue";
            default: return role.name();
        }
    }

    private static final class MemberLine {
        private final String name;
        private final FactionRole role;

        private MemberLine(String name, FactionRole role) {
            this.name = name;
            this.role = role;
        }
    }
}
