package me.krunsh.kjobultimate.hooks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.bukkit.entity.Player;

import me.krunsh.kfaction.api.v2.FactionView;
import me.krunsh.kfaction.api.v2.KfactionApiCompatibility;
import me.krunsh.kfaction.api.v2.KfactionApiV2;
import me.krunsh.kfaction.api.v2.KfactionApis;
import me.krunsh.kfaction.api.v2.MemberView;
import me.krunsh.kfaction.api.v2.PlayerView;

/** Lecture Kfaction 2.x strictement limitée aux snapshots publics immuables. */
public final class KfactionHook {

    private final KfactionApiV2 api;

    public KfactionHook() {
        KfactionApiCompatibility compatibility = KfactionApiCompatibility.evaluate(KfactionApis.get());
        if (!compatibility.isReady() || compatibility.getApi() == null) {
            throw new IllegalStateException("API Kfaction 2.x indisponible: " + compatibility.getStatus());
        }
        this.api = compatibility.getApi();
    }

    public String getRelationName(Player first, Player second) {
        if (first == null || second == null) return "NEUTRAL";
        FactionView firstFaction = api.getPlayerFaction(first.getUniqueId());
        FactionView secondFaction = api.getPlayerFaction(second.getUniqueId());
        if (firstFaction == null || secondFaction == null) return "NEUTRAL";
        if (firstFaction.getId().equals(secondFaction.getId())) return "MEMBER";
        String relation = api.getRelation(firstFaction.getId(), secondFaction.getId());
        return relation == null ? "NEUTRAL" : relation;
    }

    public String getFactionName(Player player, String fallback) {
        FactionView faction = faction(player);
        return faction == null ? fallback : safe(faction.getName(), fallback);
    }

    public int getFactionMembers(Player player) {
        FactionView faction = faction(player);
        return faction == null ? 0 : faction.getMembers().size();
    }

    public String getFactionMembersLines(Player player, int limit, String fallback) {
        FactionView faction = faction(player);
        if (faction == null) return fallback;

        List<MemberView> members = new ArrayList<MemberView>(faction.getMembers());
        members.sort(new Comparator<MemberView>() {
            @Override
            public int compare(MemberView first, MemberView second) {
                int role = Integer.compare(roleWeight(second.getRole()), roleWeight(first.getRole()));
                String firstName = safe(first.getName(), first.getUuid().toString());
                String secondName = safe(second.getName(), second.getUuid().toString());
                return role != 0 ? role : firstName.compareToIgnoreCase(secondName);
            }
        });

        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (MemberView member : members) {
            if (limit > 0 && count >= limit) break;
            if (builder.length() > 0) builder.append('\n');
            builder.append(safe(member.getName(), shortUuid(member)))
                    .append(" (").append(displayRole(member.getRole())).append(')');
            count++;
        }
        return builder.length() == 0 ? fallback : builder.toString();
    }

    public String getFactionRole(Player player, String fallback) {
        if (player == null) return fallback;
        PlayerView view = api.getPlayer(player.getUniqueId());
        return view == null || !view.hasFaction() ? fallback : displayRole(view.getRole());
    }

    private FactionView faction(Player player) {
        return player == null ? null : api.getPlayerFaction(player.getUniqueId());
    }

    private static int roleWeight(String role) {
        if ("LEADER".equalsIgnoreCase(role)) return 5;
        if ("COLEADER".equalsIgnoreCase(role)) return 4;
        if ("MODERATOR".equalsIgnoreCase(role)) return 3;
        if ("MEMBER".equalsIgnoreCase(role)) return 2;
        if ("RECRUIT".equalsIgnoreCase(role)) return 1;
        return 0;
    }

    private static String displayRole(String role) {
        if ("LEADER".equalsIgnoreCase(role)) return "Leader";
        if ("COLEADER".equalsIgnoreCase(role)) return "Co-Lead";
        if ("MODERATOR".equalsIgnoreCase(role)) return "Modo";
        if ("RECRUIT".equalsIgnoreCase(role)) return "Recrue";
        return role == null || role.trim().isEmpty() ? "Membre" : role;
    }

    private static String shortUuid(MemberView member) {
        if (member == null || member.getUuid() == null) return "inconnu";
        String value = member.getUuid().toString();
        return value.substring(0, Math.min(8, value.length()));
    }

    private static String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
