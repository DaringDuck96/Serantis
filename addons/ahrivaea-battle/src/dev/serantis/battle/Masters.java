package dev.serantis.battle;

import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Species;
import dev.ahrivaea.party.RecruitRules;
import dev.ahrivaea.villager.VillagerJob;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Creature Masters, the "gym leaders".
 *
 * A Creature Master turns up twice as often as a Black Market Merchant (each wild creature spawn has a 0.1%
 * chance to be one instead), with an influence that fits the biome. It's Level 12-20 and leads 3-5
 * creatures of its influence, each 3-5 levels away from its own. Its team is 25% stronger than wild
 * creatures (it's 40% stronger) and always fights with the best of several move sets. You don't have to
 * fight it, but beating it gives double XP and a Gym Master Stone of its influence. Its creatures can't
 * be recruited; it leaves after a day, or once it's been beaten.
 *
 * Each stone you earn raises your mastery of that influence (up to 5): creatures of that influence spawn
 * a little more often around you in their usual biomes (+3% per stone; rarity odds unchanged). With all
 * 5 stones of all 12 influences you get the Eternita Stone: 5 in 100 wild creatures near you are
 * Mutated instead of 1 in 100.
 */
public final class Masters {
    public static final String ROLE = "creature_master";
    public static final double CHANCE = 0.001;
    public static final int MAX_STONES = 5;
    public static final double STAT_BOOST_TEAM = 1.25, STAT_BOOST_MASTER = 1.4;
    public static final double MASTERY_PER_STONE = 0.03;
    public static final int ETERNITA_MUTATED = 5, NORMAL_MUTATED = 1;

    public static final String[] INFLUENCES = {"none", "fire", "frost", "storm", "earth", "water", "nature", "void", "fae", "darkness", "arcane", "radiant"};
    public static final Map<String, Item> STONES = new LinkedHashMap<>();
    public static Item ETERNITA;

    /** Added to players: Gym Master Stones earned per influence, and the Eternita Stone. */
    public interface Holder {
        int serantis$stones(String influence);
        void serantis$setStones(String influence, int count);
        boolean serantis$eternita();
        void serantis$setEternita(boolean has);
    }

    private Masters() {}

    // ------------------------------------------------------------------ items

    private static Identifier id(String path) { return Identifier.parse("serantis_battle:" + path); }

    public static void registerItems() {
        for (String inf : INFLUENCES) {
            ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(inf + "_master_stone"));
            STONES.put(inf, Registry.register(BuiltInRegistries.ITEM, key,
                    new Item(new Item.Properties().stacksTo(16).rarity(Rarity.RARE).setId(key))));
        }
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id("eternita_stone"));
        ETERNITA = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).setId(key)));
    }

    public static String influenceName(String inf) {
        return "none".equals(inf) ? "Normal" : Character.toUpperCase(inf.charAt(0)) + inf.substring(1);
    }

    // ------------------------------------------------------------------ the master in the world

    public static String influenceOf(Entity e) {
        if (!(e instanceof CreatureEntity c)) return null;
        VillagerJob j = Villages.jobOf(c);
        if (j == null || !j.profession().startsWith(ROLE + ":")) return null;
        return j.profession().substring(ROLE.length() + 1);
    }

    public static boolean isMaster(Entity e) { return influenceOf(e) != null; }

    /** Now and then a wild creature that just spawned is a Creature Master instead. */
    public static CreatureEntity maybeMaster(ServerLevel level, CreatureEntity spawned, RandomSource random) {
        if (spawned == null || random.nextDouble() >= CHANCE) return spawned;
        // its influence fits the place: the influence of a creature that could live here
        String inf = INFLUENCES[random.nextInt(INFLUENCES.length)];
        var pick = Species.pickFor(level.getBiome(spawned.blockPosition()), level, random);
        if (pick.isPresent()) inf = pick.get().influence().getSerializedName();
        CreatureEntity m = spawn(level, inf, spawned.getX(), spawned.getY(), spawned.getZ(), random);
        if (m == null) return spawned;
        spawned.discard();
        return m;
    }

    public static CreatureEntity spawn(ServerLevel level, String inf, double x, double y, double z, RandomSource random) {
        Species villager = Species.get(dev.ahrivaea.villager.Villagers.SPECIES).orElse(null);
        if (villager == null) return null;
        int lvl = 12 + random.nextInt(9);
        CreatureEntity e = Ahrivaea.spawn(level, villager, Influence.NONE, lvl, -1f, x, y, z);
        if (e == null) return null;
        Villages.setJob(e, new VillagerJob(ROLE + ":" + inf, 1, 0));
        e.setPersistenceRequired();
        e.setCustomName(Component.literal(influenceName(inf) + " Creature Master").withStyle(ChatFormatting.GOLD));
        e.setCustomNameVisible(true);
        ((Market.Holder) e).serantis$setLeaveAt(level.getGameTime() + Market.STAY_TICKS);
        for (ServerPlayer p : level.players())
            if (p.distanceToSqr(e) < 64 * 64)
                p.sendSystemMessage(Component.literal("A " + influenceName(inf) + " Creature Master is nearby, looking for a challenge.").withStyle(ChatFormatting.GOLD));
        return e;
    }

    // ------------------------------------------------------------------ the master's team

    /** One of a master's creatures: its influence, 3-5 levels away from the master's level, leaning tougher. */
    public static Species.LevelledPick teamPick(String inf, int masterLevel, RandomSource random) {
        Influence influence = null;
        for (Influence i : Influence.values()) if (i.getSerializedName().equals(inf)) influence = i;
        if (influence == null) return null;
        Map<String, List<Species>> byRarity = new LinkedHashMap<>();
        for (Species sp : Species.all()) {
            if (dev.ahrivaea.villager.Villagers.SPECIES.equals(sp.id()) || !sp.forms().containsKey(influence)) continue;
            byRarity.computeIfAbsent(RecruitRules.rarity(sp, influence), k -> new ArrayList<>()).add(sp);
        }
        int roll = random.nextInt(100);
        String[] order = roll < 30 ? new String[]{"Common", "Uncommon", "Rare"} : roll < 60 ? new String[]{"Uncommon", "Rare", "Common"}
                : roll < 85 ? new String[]{"Rare", "Uncommon", "Super Rare"} : roll < 97 ? new String[]{"Super Rare", "Rare", "Legendary"}
                : new String[]{"Legendary", "Super Rare", "Rare"};
        List<Species> pool = null;
        for (String r : order) { pool = byRarity.get(r); if (pool != null && !pool.isEmpty()) break; }
        if (pool == null || pool.isEmpty()) {
            pool = new ArrayList<>();
            for (List<Species> l : byRarity.values()) pool.addAll(l);
        }
        if (pool.isEmpty()) return null;
        int diff = 3 + random.nextInt(3);
        int lvl = masterLevel + (random.nextBoolean() ? diff : -diff);
        if (lvl > BattleStats.MAX_LEVEL) lvl = masterLevel - diff;
        return new Species.LevelledPick(pool.get(random.nextInt(pool.size())), influence, Math.max(1, Math.min(BattleStats.MAX_LEVEL, lvl)));
    }

    // ------------------------------------------------------------------ rewards

    public static void reward(ServerPlayer p, String inf) {
        Holder h = (Holder) p;
        String name = influenceName(inf);
        p.sendSystemMessage(Component.literal("You beat the " + name + " Creature Master! Double XP for this battle.").withStyle(ChatFormatting.GOLD));
        int have = h.serantis$stones(inf);
        if (have >= MAX_STONES) {
            p.sendSystemMessage(Component.literal("You already have all " + MAX_STONES + " " + name + " Gym Master Stones.").withStyle(ChatFormatting.GRAY));
            return;
        }
        h.serantis$setStones(inf, have + 1);
        give(p, new ItemStack(STONES.get(inf)));
        p.sendSystemMessage(Component.literal("You earned a " + name + " Gym Master Stone! " + name + " mastery " + (have + 1) + "/" + MAX_STONES
                + ": " + name + " creatures turn up a little more often around you.").withStyle(ChatFormatting.AQUA));
        if (!h.serantis$eternita() && allStones(p)) {
            h.serantis$setEternita(true);
            give(p, new ItemStack(ETERNITA));
            p.sendSystemMessage(Component.literal("All 60 Gym Master Stones! They fuse into the Eternita Stone: Mutated creatures are now 5 times as common around you.")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            var server = ((ServerLevel) p.level()).getServer();
            server.getPlayerList().broadcastSystemMessage(Component.literal(p.getPlainTextName() + " has earned the Eternita Stone!")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        }
    }

    public static boolean allStones(Player p) {
        Holder h = (Holder) p;
        for (String inf : INFLUENCES) if (h.serantis$stones(inf) < MAX_STONES) return false;
        return true;
    }

    private static void give(ServerPlayer p, ItemStack stack) {
        if (!p.getInventory().add(stack) && !stack.isEmpty())
            p.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(p.level(), p.getX(), p.getY(), p.getZ(), stack));
    }

    // ------------------------------------------------------------------ mastery and the Eternita Stone at spawn time

    /** The player a creature is spawning near (set where spawns happen; see Villages.markSpawn). */
    public static final ThreadLocal<Player> SPAWN_PLAYER = new ThreadLocal<>();

    public static void markSpawn(Level level, net.minecraft.core.BlockPos pos) {
        Player p = level == null || pos == null ? null : level.getNearestPlayer(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 64, false);
        SPAWN_PLAYER.set(p);
    }

    /** % of spawns near this player that are Mutated. */
    public static int mutatedPercent() {
        Player p = SPAWN_PLAYER.get();
        return p instanceof Holder h && h.serantis$eternita() ? ETERNITA_MUTATED : NORMAL_MUTATED;
    }

    /**
     * Mastery: each Gym Master Stone the nearby player has of an influence gives creatures of that influence
     * +3% spawn weight. After a normal pick (same biome, same rarity), a second pick is drawn; if it's of a
     * mastered influence it replaces the first with a chance equal to that bonus.
     */
    public static java.util.Optional<Species.Pick> mastery(java.util.Optional<Species.Pick> got, net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome,
                                                          Level level, RandomSource random, java.util.function.BiPredicate<Species, Influence> filter) {
        Player p = SPAWN_PLAYER.get();
        if (!(p instanceof Holder h) || got.isEmpty()) return got;
        if (h.serantis$stones(got.get().influence().getSerializedName()) > 0) return got;
        boolean any = false;
        for (String inf : INFLUENCES) if (h.serantis$stones(inf) > 0) { any = true; break; }
        if (!any) return got;
        var other = Species.pickFor(biome, level, random, filter);
        if (other.isEmpty()) return got;
        int n = h.serantis$stones(other.get().influence().getSerializedName());
        return n > 0 && random.nextDouble() < n * MASTERY_PER_STONE ? other : got;
    }
}
