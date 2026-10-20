package dev.serantis.battle;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.ahrivaea.party.Dex;
import dev.ahrivaea.party.PartySync;
import dev.ahrivaea.villager.Villagers;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Achievements: milestones in seven areas, each paying out golden apples (see Apples). They are checked
 * every few seconds and paid automatically; the party screen's Achievements page shows them all.
 *
 * Counts come from Minecraft's own statistics where it keeps them (blocks placed = block items used,
 * blocks walked = walking + sprinting + crouching distance, battles won and creatures recruited are the
 * creatures mod's statistics), from the creature dex (creatures seen), and from Serantis's own counters
 * (items smelted, taken out of a furnace by the player; experience gained).
 */
public final class Achievements {
    public record Track(String id, String name, String verb, long[] tiers, int[] apples) {}

    private static final int[] FIVE = {3, 6, 12, 24, 48};
    private static final int[] SIX = {2, 4, 8, 16, 32, 48};

    public static final List<Track> TRACKS = List.of(
            new Track("placed", "Blocks Placed", "blocks placed", new long[]{100, 1_000, 10_000, 100_000, 1_000_000}, FIVE),
            new Track("battles", "Battles Won", "battles won", new long[]{1, 10, 50, 100, 250, 500}, SIX),
            new Track("recruited", "Creatures Recruited", "creatures recruited", new long[]{5, 25, 100, 200, 300, 400}, SIX),
            new Track("seen", "Creatures Seen", "creatures seen", new long[]{5, 25, 50, 150, 300, 434}, SIX),
            new Track("smelted", "Items Smelted", "items smelted", new long[]{100, 1_000, 10_000, 100_000, 1_000_000}, FIVE),
            new Track("walked", "Blocks Walked", "blocks walked", new long[]{100, 1_000, 10_000, 100_000, 1_000_000}, FIVE),
            new Track("xp", "Experience Gained", "experience gained", new long[]{100, 1_000, 10_000, 100_000, 1_000_000}, FIVE));

    /** Set while XP is moved around rather than earned (spending XP gives back the rest). */
    public static final ThreadLocal<Boolean> NOT_GAINED = ThreadLocal.withInitial(() -> false);

    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI"};

    /** Added to players: Serantis's own counters and how many tiers of each track have been paid. */
    public interface Holder {
        long serantis$smelted();
        void serantis$addSmelted(long n);
        long serantis$xpGained();
        void serantis$addXpGained(long n);
        int serantis$claimed(String track);
        void serantis$setClaimed(String track, int tiers);
    }

    private Achievements() {}

    private static int blockItemsUsed(ServerStatsCounter stats) {
        long total = 0;
        for (Item item : BuiltInRegistries.ITEM) if (item instanceof BlockItem) total += stats.getValue(Stats.ITEM_USED, item);
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    public static long value(ServerPlayer p, String id) {
        ServerStatsCounter stats = p.getStats();
        Holder h = (Holder) p;
        return switch (id) {
            case "placed" -> blockItemsUsed(stats);
            case "battles" -> stats.getValue(Stats.CUSTOM, Villagers.BATTLES_WON);
            case "recruited" -> stats.getValue(Stats.CUSTOM, Villagers.CREATURES_RECRUITED);
            case "seen" -> Dex.count(p, 1);
            case "smelted" -> h.serantis$smelted();
            case "walked" -> ((long) stats.getValue(Stats.CUSTOM, Stats.WALK_ONE_CM) + stats.getValue(Stats.CUSTOM, Stats.SPRINT_ONE_CM)
                    + stats.getValue(Stats.CUSTOM, Stats.CROUCH_ONE_CM)) / 100;
            case "xp" -> h.serantis$xpGained();
            default -> 0;
        };
    }

    /** Pays out every tier the player has reached and not been paid for. */
    public static void check(ServerPlayer p) {
        Holder h = (Holder) p;
        boolean any = false;
        for (Track t : TRACKS) {
            long v = value(p, t.id());
            int claimed = h.serantis$claimed(t.id());
            while (claimed < t.tiers().length && v >= t.tiers()[claimed]) {
                int apples = t.apples()[claimed];
                give(p, apples);
                p.sendSystemMessage(Component.literal("Achievement: " + t.name() + " " + ROMAN[claimed] + " (" + String.format("%,d", t.tiers()[claimed])
                        + " " + t.verb() + ")! +" + apples + " golden apples.").withStyle(ChatFormatting.GOLD));
                claimed++;
                any = true;
            }
            h.serantis$setClaimed(t.id(), claimed);
        }
        if (any) {
            p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.2f);
            PartySync.sync(p);
        }
    }

    private static void give(ServerPlayer p, int count) {
        while (count > 0) {
            int n = Math.min(64, count);
            ItemStack stack = new ItemStack(Items.GOLDEN_APPLE, n);
            if (!p.getInventory().add(stack) && !stack.isEmpty())
                p.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(p.level(), p.getX(), p.getY(), p.getZ(), stack));
            count -= n;
        }
    }

    /** "serantis_achievements" in the party state, for the Achievements page. */
    public static void describe(ServerPlayer p, JsonObject state) {
        Holder h = (Holder) p;
        JsonArray out = new JsonArray();
        int earned = 0, total = 0;
        for (Track t : TRACKS) {
            JsonObject o = new JsonObject();
            o.addProperty("name", t.name());
            o.addProperty("verb", t.verb());
            o.addProperty("value", value(p, t.id()));
            int claimed = h.serantis$claimed(t.id());
            o.addProperty("claimed", claimed);
            JsonArray tiers = new JsonArray(), apples = new JsonArray();
            for (int i = 0; i < t.tiers().length; i++) {
                tiers.add(t.tiers()[i]);
                apples.add(t.apples()[i]);
                total += t.apples()[i];
                if (i < claimed) earned += t.apples()[i];
            }
            o.add("tiers", tiers);
            o.add("apples", apples);
            out.add(o);
        }
        state.add("serantis_achievements", out);
        state.addProperty("serantis_apples_earned", earned);
        state.addProperty("serantis_apples_total", total);
    }

    // ------------------------------------------------------------------ saving

    public static String encode(Holder h) {
        List<String> parts = new ArrayList<>();
        for (Track t : TRACKS) parts.add(t.id() + ":" + h.serantis$claimed(t.id()));
        return String.join(",", parts);
    }

    public static void decode(Holder h, String saved) {
        if (saved == null) return;
        for (String part : saved.split(",")) {
            String[] kv = part.split(":");
            if (kv.length != 2) continue;
            try { h.serantis$setClaimed(kv[0].trim(), Integer.parseInt(kv[1].trim())); } catch (NumberFormatException ignored) { }
        }
    }
}
