package dev.serantis.battle;

import dev.ahrivaea.creature.Influence;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * Type Mastery: creatures of each influence a player has gained since this was added, from battles, trades,
 * Wandering Traders and the Black Market. Every 10 of one influence earns a Type Mastery Trophy and the
 * "<Influence> Influence Master" achievement (the trophies are added with their models).
 */
public final class TypeMastery {
    public static final int PER_TROPHY = 10;

    /** Added to players: creatures gained per influence, and trophies already given per influence. */
    public interface Holder {
        Map<String, Integer> serantis$typeGained();
        Map<String, Integer> serantis$typeTrophies();
    }

    private TypeMastery() {}

    /** A creature of this influence joined the player (recruited, traded for or bought). */
    public static void gained(ServerPlayer p, Influence influence) {
        if (p == null) return;
        String key = influence == null ? "none" : influence.getSerializedName();
        ((Holder) p).serantis$typeGained().merge(key, 1, Integer::sum);
    }

    public static String encode(Map<String, Integer> m) {
        StringBuilder sb = new StringBuilder();
        m.forEach((k, v) -> sb.append(sb.isEmpty() ? "" : ",").append(k).append(':').append(v));
        return sb.toString();
    }

    public static void decode(Map<String, Integer> m, String s) {
        m.clear();
        if (s == null) return;
        for (String part : s.split(",")) {
            String[] kv = part.split(":");
            if (kv.length == 2) try { m.put(kv[0].trim(), Integer.parseInt(kv[1].trim())); } catch (NumberFormatException ignored) { }
        }
    }
}
