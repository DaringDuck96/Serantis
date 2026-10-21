package dev.serantis.battle;

import dev.ahrivaea.creature.Influence;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * Type Mastery: creatures of each influence a player has gained since this was added, from battles, trades,
 * Wandering Traders and the Black Market. Every 10 of one influence earns a Type Mastery Trophy and the
 * "<Influence> Influence Master" achievement, with no limit. The trophies are placeable, purely decorative
 * blocks (no XP or golden apples).
 */
public final class TypeMastery {
    public static final int PER_TROPHY = 10;
    public static final Map<String, net.minecraft.world.item.Item> TROPHIES = new java.util.LinkedHashMap<>();

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

    /** Hands out every trophy earned and not yet given (checked with the other achievements). */
    public static boolean check(ServerPlayer p) {
        Holder h = (Holder) p;
        boolean any = false;
        for (String inf : Masters.INFLUENCES) {
            int due = h.serantis$typeGained().getOrDefault(inf, 0) / PER_TROPHY;
            int have = h.serantis$typeTrophies().getOrDefault(inf, 0);
            var item = TROPHIES.get(inf);
            if (item == null) continue;
            while (have < due) {
                have++;
                net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item);
                if (!p.getInventory().add(stack) && !stack.isEmpty())
                    p.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(p.level(), p.getX(), p.getY(), p.getZ(), stack));
                String name = Masters.influenceName(inf);
                p.sendSystemMessage(net.minecraft.network.chat.Component.literal("Achievement unlocked: " + name + " Influence Master! ("
                        + have * PER_TROPHY + " " + name + " creatures gained) A " + name + " Type Mastery Trophy is in your inventory.")
                        .withStyle(net.minecraft.ChatFormatting.GOLD));
                any = true;
            }
            h.serantis$typeTrophies().put(inf, have);
        }
        return any;
    }

    /** One row per influence for the achievement log: progress to the next trophy, and how many so far. */
    public static void describe(ServerPlayer p, com.google.gson.JsonArray out) {
        Holder h = (Holder) p;
        for (String inf : Masters.INFLUENCES) {
            String name = Masters.influenceName(inf) + " Influence Master";
            int gained = h.serantis$typeGained().getOrDefault(inf, 0), have = h.serantis$typeTrophies().getOrDefault(inf, 0);
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            o.addProperty("name", name);
            o.addProperty("verb", Masters.influenceName(inf).toLowerCase() + " creatures gained");
            o.addProperty("icon", "serantis_battle:" + inf + "_mastery_trophy");
            o.addProperty("value", gained);
            com.google.gson.JsonArray tiers = new com.google.gson.JsonArray(), titles = new com.google.gson.JsonArray(), apples = new com.google.gson.JsonArray();
            if (have > 0) { tiers.add(have * PER_TROPHY); titles.add(name + " x" + have); apples.add(0); }
            tiers.add((have + 1) * PER_TROPHY);
            titles.add(name + " x" + (have + 1));
            apples.add(0);
            o.add("tiers", tiers);
            o.add("titles", titles);
            o.add("apples", apples);
            o.addProperty("claimed", have > 0 ? 1 : 0);
            com.google.gson.JsonArray badges = new com.google.gson.JsonArray();
            if (have > 0) badges.add("x" + have);
            badges.add("x" + (have + 1));
            o.add("badges", badges);
            o.addProperty("reward", "a " + Masters.influenceName(inf) + " Type Mastery Trophy (no limit)");
            out.add(o);
        }
    }
}
