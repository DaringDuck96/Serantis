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
 * (items smelted, taken out of a furnace by the player; seeds planted, crops a player plants by hand;
 * experience gained).
 */
public final class Achievements {
    public record Track(String id, String name, String verb, long[] tiers, int[] apples, String[] titles) {}

    private static final int[] FIVE = {3, 6, 12, 24, 48};
    private static final int[] SIX = {2, 4, 8, 16, 32, 48};

    public static final List<Track> TRACKS = List.of(
            new Track("placed", "Blocks Placed", "blocks placed", new long[]{100, 1_000, 10_000, 100_000, 1_000_000}, FIVE,
                    new String[]{"Some Assembly Required", "Brick by Brick", "Not Just a Dirt Hut", "Zoning Violation", "Terraformer"}),
            new Track("battles", "Battles Won", "battles won", new long[]{1, 10, 50, 100, 250, 500}, SIX,
                    new String[]{"Beginner's Luck", "Getting the Hang of It", "Tactical Genius (Allegedly)", "Undefeated-ish", "Grid Lord", "Final Boss Energy"}),
            new Track("recruited", "Creatures Recruited", "creatures recruited", new long[]{5, 25, 100, 200, 300, 400}, SIX,
                    new String[]{"Making Friends", "Petting Zoo", "Animal Magnetism", "Creature Hoarder", "Noah Would Be Proud", "Zookeeper Supreme"}),
            new Track("seen", "Creatures Seen", "creatures seen", new long[]{5, 25, 50, 150, 300, 434}, SIX,
                    new String[]{"Wait, What Was That?", "Bird Watcher", "Taking Field Notes", "I've Seen Things", "Walking Encyclopedia", "Seen It All"}),
            new Track("smelted", "Items Smelted", "items smelted", new long[]{100, 1_000, 10_000, 100_000, 1_000_000}, FIVE,
                    new String[]{"Hot Stuff", "Cooking With Lava", "Total Smeltdown", "Industrial Revolution", "Forge of the Gods"}),
            new Track("armor", "Armor Crafted", "armor pieces crafted", new long[]{5, 10, 25, 100, 250}, new int[]{2, 4, 8, 16, 32},
                    new String[]{"Suit Up", "Fashion Forward", "Runway Ready", "Tin Can Collector", "Walking Armory"}),
            new Track("planted", "Seeds Planted", "seeds planted", new long[]{100, 1_000, 10_000, 100_000, 1_000_000}, FIVE,
                    new String[]{"Seeds of Doubt", "Green Thumb", "Down on the Farm", "Old MacDonald Had a Lot", "Feed the World"}),
            new Track("biomes", "Biomes Visited", "biomes visited", new long[]{3, 8, 15, 20, 30}, new int[]{1, 2, 3, 4, 6},
                    new String[]{"Day Tripper", "Sightseer", "Backpacker", "Globetrotter", "Professional Tourist"}),
            new Track("towers", "Towers Built", "towers rebuilt and claimed", new long[]{1, 2, 3, 4, 5}, new int[]{6, 10, 16, 24, 40},
                    new String[]{"Home Sweet Spire", "Double Decker", "Skyline Starter", "Real Estate Mogul", "Lord of the Spires"}),
            new Track("walked", "Blocks Walked", "blocks walked", new long[]{10_000, 50_000, 250_000, 1_000_000, 5_000_000}, FIVE,
                    new String[]{"Stretch Your Legs", "Out for a Stroll", "Are We There Yet?", "Who Needs Horses?", "Walked to the Moon"}),
            new Track("xp", "Experience Gained", "experience gained", new long[]{100, 1_000, 10_000, 100_000, 1_000_000}, FIVE,
                    new String[]{"Shiny Green Orbs", "Book Smart", "Big Brain Energy", "Seasoned Veteran", "Ascended"}),
            new Track("deaths", "Times Defeated", "deaths", new long[]{1, 5, 10, 50, 100}, new int[]{2, 4, 8, 16, 32},
                    new String[]{"Oops", "Respawn Regular", "Fall Damage Enthusiast", "Death's Frequent Flyer", "Immortal (Technically)"}));

    /** Set while XP is moved around rather than earned (spending XP gives back the rest). */
    public static final ThreadLocal<Boolean> NOT_GAINED = ThreadLocal.withInitial(() -> false);

    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI"};

    /** Added to players: Serantis's own counters and how many tiers of each track have been paid. */
    public interface Holder {
        long serantis$smelted();
        void serantis$addSmelted(long n);
        /** Every biome the player has stood in (ids like minecraft:plains). */
        java.util.Set<String> serantis$biomes();
        long serantis$planted();
        void serantis$addPlanted(long n);
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

    private static List<Item> armor;

    /** Helmets, chestplates, leggings and boots (anything worn in those slots, except elytra), from every mod. */
    private static List<Item> armorItems() {
        if (armor == null) {
            List<Item> out = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                var eq = item.components().get(net.minecraft.core.component.DataComponents.EQUIPPABLE);
                if (eq == null || item == Items.ELYTRA) continue;
                var slot = eq.slot();
                if (slot == net.minecraft.world.entity.EquipmentSlot.HEAD || slot == net.minecraft.world.entity.EquipmentSlot.CHEST
                        || slot == net.minecraft.world.entity.EquipmentSlot.LEGS || slot == net.minecraft.world.entity.EquipmentSlot.FEET) out.add(item);
            }
            armor = out;
        }
        return armor;
    }

    private static long armorCrafted(ServerStatsCounter stats) {
        long total = 0;
        for (Item item : armorItems()) total += stats.getValue(Stats.ITEM_CRAFTED, item);
        return total;
    }

    /** Finished towers (Ahrivaea Tower Challenges) that belong to the player. */
    private static long towersBuilt(ServerPlayer p) {
        try {
            var server = ((net.minecraft.server.level.ServerLevel) p.level()).getServer();
            String me = p.getUUID().toString();
            long n = 0;
            for (int i = 0; i < 128; i++) {
                var st = dev.ahrivaea.challenge.Challenges.state(server, dev.ahrivaea.challenge.StrongholdTowers.towerId(i)).orElse(null);
                if (st != null && st.complete && me.equals(st.owner)) n++;
            }
            return n;
        } catch (LinkageError | RuntimeException e) {
            return 0;                                   // the towers mod isn't installed
        }
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
            case "planted" -> h.serantis$planted();
            case "armor" -> armorCrafted(stats);
            case "towers" -> towersBuilt(p);
            case "biomes" -> h.serantis$biomes().size();
            case "walked" -> ((long) stats.getValue(Stats.CUSTOM, Stats.WALK_ONE_CM) + stats.getValue(Stats.CUSTOM, Stats.SPRINT_ONE_CM)
                    + stats.getValue(Stats.CUSTOM, Stats.CROUCH_ONE_CM)) / 100;
            case "xp" -> h.serantis$xpGained();
            case "deaths" -> stats.getValue(Stats.CUSTOM, Stats.DEATHS);
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
                p.sendSystemMessage(Component.literal("Achievement unlocked: " + t.titles()[claimed] + "! (" + t.name() + " " + ROMAN[claimed] + ": "
                        + String.format("%,d", t.tiers()[claimed]) + " " + t.verb() + ") +" + apples + " golden apples.").withStyle(ChatFormatting.GOLD));
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
            JsonArray titles = new JsonArray();
            for (String s : t.titles()) titles.add(s);
            o.add("titles", titles);
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
