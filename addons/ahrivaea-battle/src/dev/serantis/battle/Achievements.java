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

    public static final List<Track> TRACKS;
    private static final List<Track> MAIN = List.of(
            new Track("placed", "Blocks Placed", "blocks placed", new long[]{100, 1_000, 10_000, 50_000, 150_000}, FIVE,
                    new String[]{"Some Assembly Required", "Brick by Brick", "Not Just a Dirt Hut", "Zoning Violation", "Terraformer"}),
            new Track("battles", "Battles Won", "battles won", new long[]{1, 10, 50, 100, 250, 500}, SIX,
                    new String[]{"Beginner's Luck", "Getting the Hang of It", "Tactical Genius (Allegedly)", "Undefeated-ish", "Grid Lord", "Final Boss Energy"}),
            new Track("recruited", "Creatures Recruited", "creatures recruited", new long[]{5, 25, 100, 200, 300, 400}, SIX,
                    new String[]{"Making Friends", "Petting Zoo", "Animal Magnetism", "Creature Hoarder", "Noah Would Be Proud", "Zookeeper Supreme"}),
            new Track("seen", "Creatures Seen", "creatures seen", new long[]{5, 25, 50, 150, 300, Retired.FORMS}, SIX,
                    new String[]{"Wait, What Was That?", "Bird Watcher", "Taking Field Notes", "I've Seen Things", "Walking Encyclopedia", "Seen It All"}),
            new Track("smelted", "Items Smelted", "items smelted", new long[]{100, 1_000, 10_000, 50_000, 150_000}, FIVE,
                    new String[]{"Hot Stuff", "Cooking With Lava", "Total Smeltdown", "Industrial Revolution", "Forge of the Gods"}),
            new Track("armor", "Armor Crafted", "armor pieces crafted", new long[]{5, 10, 25, 100, 250}, new int[]{2, 4, 8, 16, 32},
                    new String[]{"Suit Up", "Fashion Forward", "Runway Ready", "Tin Can Collector", "Walking Armory"}),
            new Track("planted", "Seeds Planted", "seeds planted", new long[]{100, 1_000, 10_000, 50_000, 150_000}, FIVE,
                    new String[]{"Seeds of Doubt", "Green Thumb", "Down on the Farm", "Old MacDonald Had a Lot", "Feed the World"}),
            new Track("biomes", "Biomes Visited", "biomes visited", new long[]{3, 8, 15, 20, 30}, new int[]{1, 2, 3, 4, 6},
                    new String[]{"Day Tripper", "Sightseer", "Backpacker", "Globetrotter", "Professional Tourist"}),
            new Track("towers", "Towers Built", "towers rebuilt and claimed", new long[]{1, 2, 3, 4, 5}, new int[]{6, 10, 16, 24, 40},
                    new String[]{"Home Sweet Spire", "Double Decker", "Skyline Starter", "Real Estate Mogul", "Lord of the Spires"}),
            new Track("walked", "Distance Traveled", "blocks traveled", new long[]{10_000, 50_000, 150_000, 400_000, 1_000_000}, FIVE,
                    new String[]{"Stretch Your Legs", "Out for a Stroll", "Are We There Yet?", "Frequent Traveler", "Around the World"}),
            new Track("xp", "Experience Gained", "experience gained", new long[]{100, 1_000, 10_000, 75_000, 250_000}, FIVE,
                    new String[]{"Shiny Green Orbs", "Book Smart", "Big Brain Energy", "Seasoned Veteran", "Ascended"}),
            new Track("deaths", "Times Defeated", "deaths", new long[]{1, 5, 10, 50, 100}, new int[]{2, 4, 8, 16, 32},
                    new String[]{"Oops", "Respawn Regular", "Fall Damage Enthusiast", "Death's Frequent Flyer", "Immortal (Technically)"}),
            new Track("lore", "Lore Books Collected", "lore books collected", new long[]{1, 5, 10, 20, 30}, new int[]{2, 4, 8, 16, 32},
                    new String[]{"Light Reading", "Bookworm", "Well Read", "Lorekeeper", "Krea's Apprentice"}),
            new Track("eternita", "The Eternita Stone", "Eternita Stone earned", new long[]{1}, new int[]{0},
                    new String[]{"I Won! I Won the Game!"}),
            new Track("chaos", "Chaos Seeker", "Acolyte of the Last faced", new long[]{1}, new int[]{0},
                    new String[]{"Chaos Seeker"}),
            new Track("bragging", "Bragging Rights", "spar won against another player", new long[]{1}, new int[]{0},
                    new String[]{"Bragging Rights"}));

    /** Tracks that pay XP instead of golden apples (per tier). */
    private static final java.util.Map<String, int[]> XP_REWARD = java.util.Map.of("eternita", new int[]{10_000});

    /** Icons for the achievement log (item ids). */
    private static final java.util.Map<String, String> ICONS = new java.util.HashMap<>(java.util.Map.ofEntries(
            java.util.Map.entry("placed", "minecraft:bricks"), java.util.Map.entry("battles", "minecraft:iron_sword"),
            java.util.Map.entry("recruited", "minecraft:lead"), java.util.Map.entry("seen", "minecraft:spyglass"),
            java.util.Map.entry("smelted", "minecraft:furnace"), java.util.Map.entry("armor", "minecraft:iron_chestplate"),
            java.util.Map.entry("planted", "minecraft:wheat_seeds"), java.util.Map.entry("biomes", "minecraft:map"),
            java.util.Map.entry("towers", "minecraft:chiseled_stone_bricks"), java.util.Map.entry("walked", "minecraft:leather_boots"),
            java.util.Map.entry("xp", "minecraft:experience_bottle"), java.util.Map.entry("deaths", "minecraft:skeleton_skull"),
            java.util.Map.entry("lore", "minecraft:written_book"),
            java.util.Map.entry("eternita", "serantis_battle:eternita_stone"), java.util.Map.entry("chaos", "minecraft:wither_rose"),
            java.util.Map.entry("bragging", "minecraft:goat_horn")));

    /** One "recruit creatures of this influence" line per influence: {influence, name, icon, five titles}. */
    private static final String[][] INFLUENCES = {
            {"none", "Normal", "minecraft:white_wool", "Plain and Simple", "Nothing Special", "Vanilla Flavored", "Normcore", "Extraordinarily Ordinary"},
            {"fire", "Fire", "minecraft:blaze_powder", "Playing With Fire", "Hot Hands", "Fire Hazard", "Smoke Alarm Fatigue", "Too Hot to Handle"},
            {"frost", "Frost", "minecraft:snowball", "Chill Vibes", "Cold Shoulder", "Brain Freeze", "Snow Problem", "Absolute Zero"},
            {"storm", "Storm", "minecraft:lightning_rod", "Feeling Charged", "Under the Weather", "Brainstorming", "Shocking Behavior", "Eye of the Storm"},
            {"earth", "Earth", "minecraft:cobblestone", "Down to Earth", "Rock Solid", "Between a Rock", "Stone Cold", "Mountain Mover"},
            {"water", "Water", "minecraft:water_bucket", "Making a Splash", "Testing the Waters", "In Too Deep", "Water You Doing?", "Tidal Wave"},
            {"nature", "Nature", "minecraft:oak_sapling", "Tree Hugger", "Back to Nature", "Wild at Heart", "Photosynthesizer", "Mother Nature's Favorite"},
            {"void", "Void", "minecraft:ender_pearl", "Null and Void", "Lost in Space", "Empty Inside", "Staring Into the Abyss", "The Abyss Stares Back"},
            {"fae", "Fae", "minecraft:pink_petals", "Pixie Dust", "Away With the Fairies", "Glamour Shot", "Changeling", "Fae-mous"},
            {"darkness", "Darkness", "minecraft:ink_sac", "Lights Out", "Afraid of the Dark", "Shady Business", "Embrace the Dark", "Eternal Night Owl"},
            {"arcane", "Arcane", "minecraft:amethyst_shard", "Abracadabra", "Spell Check", "Mana Addict", "Wizard Hat Required", "Archmage"},
            {"radiant", "Radiant", "minecraft:glowstone_dust", "Bright Idea", "Glow Up", "Shine On", "Sun Worshipper", "Supernova"}};

    static {
        List<Track> all = new ArrayList<>(MAIN);
        for (String[] f : INFLUENCES) {
            all.add(new Track("rec_" + f[0], f[1] + " Recruits", f[1].toLowerCase() + " creatures recruited",
                    new long[]{1, 10, 15, 25, 30}, new int[]{1, 2, 3, 4, 6}, new String[]{f[3], f[4], f[5], f[6], f[7]}));
            ICONS.put("rec_" + f[0], f[2]);
        }
        TRACKS = List.copyOf(all);
    }

    /** Set while XP is moved around rather than earned (spending XP gives back the rest). */
    public static final ThreadLocal<Boolean> NOT_GAINED = ThreadLocal.withInitial(() -> false);

    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI"};

    /** Added to players: Serantis's own counters and how many tiers of each track have been paid. */
    public interface Holder {
        long serantis$smelted();
        void serantis$addSmelted(long n);
        /** Every biome the player has stood in (ids like minecraft:plains). */
        java.util.Set<String> serantis$biomes();
        /** Creatures recruited, by influence (none, fire, ...). */
        long serantis$recruitedOf(String influence);
        void serantis$addRecruited(String influence, long n);
        /** False until the per-influence counts were started from the creatures the player already had. */
        boolean serantis$recruitsSeeded();
        void serantis$setRecruitsSeeded();
        long serantis$planted();
        /** Centimetres ridden on creatures (Minecraft only counts horses, pigs and the like). */
        long serantis$rodeCm();
        void serantis$addRodeCm(long cm);
        void serantis$addPlanted(long n);
        /** Lore books found (every drop counts, repeats too). */
        long serantis$loreBooks();
        void serantis$addLoreBooks(long n);
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

    /** Distance Traveled: on foot (walking, sprinting, sneaking, swimming), by elytra, and riding (horses, boats, pigs, striders,
     *  minecarts; creatures you ride are counted separately, see serantis$rodeCm). In blocks. */
    private static long traveled(ServerStatsCounter stats) {
        long cm = 0;
        for (var s : new net.minecraft.resources.Identifier[]{Stats.WALK_ONE_CM, Stats.SPRINT_ONE_CM, Stats.CROUCH_ONE_CM, Stats.SWIM_ONE_CM,
                Stats.WALK_ON_WATER_ONE_CM, Stats.WALK_UNDER_WATER_ONE_CM, Stats.AVIATE_ONE_CM, Stats.HORSE_ONE_CM, Stats.BOAT_ONE_CM,
                Stats.PIG_ONE_CM, Stats.STRIDER_ONE_CM, Stats.MINECART_ONE_CM})
            cm += stats.getValue(Stats.CUSTOM, s);
        return cm / 100;
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
            case String s when s.startsWith("rec_") -> h.serantis$recruitedOf(s.substring(4));
            case "biomes" -> h.serantis$biomes().size();
            case "walked" -> traveled(stats) + h.serantis$rodeCm() / 100;
            case "xp" -> h.serantis$xpGained();
            case "lore" -> h.serantis$loreBooks();
            case "deaths" -> stats.getValue(Stats.CUSTOM, Stats.DEATHS);
            case "eternita" -> p instanceof Masters.Holder m && m.serantis$eternita() ? 1 : 0;
            case "chaos" -> (((Welcome.Holder) p).serantis$figureMet() & FigureVisit.CHAOS_FOUGHT) != 0 ? 1 : 0;
            case "bragging" -> (((Welcome.Holder) p).serantis$figureMet() & dev.serantis.battle.coop.CoopBattles.SPAR_WON) != 0 ? 1 : 0;
            default -> 0;
        };
    }

    /** Pays out every tier the player has reached and not been paid for. */
    public static void check(ServerPlayer p) {
        Holder h = (Holder) p;
        if (!h.serantis$recruitsSeeded()) {
            // Recruits by influence weren't counted before: start from the creatures the player owns now.
            h.serantis$setRecruitsSeeded();
            for (var c : dev.ahrivaea.party.Party.get(p)) recruited(p, c.influence());
            for (var c : dev.ahrivaea.party.Party.box(p)) recruited(p, c.influence());
        }
        boolean any = false;
        for (Track t : TRACKS) {
            long v = value(p, t.id());
            int claimed = h.serantis$claimed(t.id());
            while (claimed < t.tiers().length && v >= t.tiers()[claimed]) {
                int[] xp = XP_REWARD.get(t.id());
                if ("bragging".equals(t.id())) {
                    p.sendSystemMessage(Component.literal("Achievement unlocked: Bragging Rights! You beat another player in a spar. That's it. That's the prize.").withStyle(ChatFormatting.GOLD));
                } else if ("chaos".equals(t.id())) {
                    p.sendSystemMessage(Component.literal("Achievement unlocked: Chaos Seeker! Enraged creatures now find you more often (+"
                            + FigureVisit.enragedBonus(p) + "% of wild creatures near you).").withStyle(ChatFormatting.GOLD));
                } else if (xp != null) {
                    p.giveExperiencePoints(xp[claimed]);
                    p.sendSystemMessage(Component.literal("Achievement unlocked: " + t.titles()[claimed] + "! (" + t.verb() + ") +"
                            + String.format("%,d", xp[claimed]) + " XP.").withStyle(ChatFormatting.GOLD));
                    if ("eternita".equals(t.id()))
                        p.sendSystemMessage(Component.literal("You beat the game! (Nothing happens. Keep playing.)").withStyle(ChatFormatting.LIGHT_PURPLE));
                } else {
                    int apples = t.apples()[claimed];
                    give(p, apples);
                    p.sendSystemMessage(Component.literal("Achievement unlocked: " + t.titles()[claimed] + "! (" + t.name() + " " + ROMAN[claimed] + ": "
                            + String.format("%,d", t.tiers()[claimed]) + " " + t.verb() + ") +" + apples + " golden apples.").withStyle(ChatFormatting.GOLD));
                }
                claimed++;
                any = true;
            }
            h.serantis$setClaimed(t.id(), claimed);
        }
        if (TypeMastery.check(p)) any = true;
        if (any) {
            p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.2f);
            PartySync.sync(p);
        }
    }

    /** A creature of this influence was recruited. */
    public static void recruited(ServerPlayer p, dev.ahrivaea.creature.Influence influence) {
        if (p == null) return;
        ((Holder) p).serantis$addRecruited(influence == null ? "none" : influence.getSerializedName(), 1);
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
            o.addProperty("icon", ICONS.getOrDefault(t.id(), "minecraft:golden_apple"));
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
            int[] xp = XP_REWARD.get(t.id());
            if ("bragging".equals(t.id())) {
                o.addProperty("badge", "!");
                o.addProperty("reward", "Nothing but the bragging rights");
            } else if ("chaos".equals(t.id())) {
                o.addProperty("badge", "!");
                o.addProperty("reward", "More Enraged creatures (2% if you beat him, 1% if not)");
            } else if (xp != null) {
                o.addProperty("badge", "XP");
                o.addProperty("reward", String.format("%,d XP", xp[0]));
            }
            out.add(o);
        }
        TypeMastery.describe(p, out);
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
