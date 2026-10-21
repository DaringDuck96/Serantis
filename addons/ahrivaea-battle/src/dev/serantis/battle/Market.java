package dev.serantis.battle;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Species;
import dev.ahrivaea.creature.Variant;
import dev.ahrivaea.party.CaughtCreature;
import dev.ahrivaea.party.Party;
import dev.ahrivaea.party.PartySync;
import dev.ahrivaea.party.RecruitRules;
import dev.ahrivaea.villager.VillagerJob;
import dev.serantis.battle.moves.MovesHolder;
import dev.serantis.battle.skills.XpBank;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Merchants and the Black Market.
 *
 * Merchant: a village profession that, like a vanilla wandering trader, sells a random handful of
 * useful things (each merchant its own set, for XP).
 *
 * Black Market Merchant: never in villages. Each wild creature spawn in the Overworld has a 0.05% chance
 * to be one instead. It carries 3-5 Rare or rarer creatures and sells each for 350 XP. It can't be
 * fought or recruited, and it leaves after a day (or once it has sold out).
 */
public final class Market {
    public static final String MERCHANT = "merchant", BLACK_MARKET = "black_market";
    public static final int CREATURE_PRICE = 350;
    public static final double BLACK_MARKET_CHANCE = 0.0005;
    public static final long STAY_TICKS = 24_000;

    /** What a black market merchant carries (saved with it). */
    public interface Holder {
        List<CaughtCreature> serantis$stock();
        void serantis$setStock(List<CaughtCreature> stock);
        long serantis$leaveAt();
        void serantis$setLeaveAt(long tick);
        /** Swaps a Wandering Trader has left. */
        int serantis$swaps();
        void serantis$setSwaps(int swaps);
    }

    // ------------------------------------------------------------------ merchants

    public record Ware(Item item, int count, int cost) {}

    private static final List<Ware> POOL = List.of(
            new Ware(Items.ENDER_PEARL, 4, 12), new Ware(Items.NAME_TAG, 1, 10), new Ware(Items.SADDLE, 1, 12),
            new Ware(Items.GOLDEN_CARROT, 16, 10), new Ware(Items.DIAMOND, 1, 25), new Ware(Items.EMERALD, 4, 10),
            new Ware(Items.IRON_INGOT, 16, 10), new Ware(Items.GOLD_INGOT, 8, 10), new Ware(Items.BLAZE_ROD, 4, 12),
            new Ware(Items.SLIME_BALL, 8, 8), new Ware(Items.GUNPOWDER, 16, 8), new Ware(Items.STRING, 16, 5),
            new Ware(Items.LEATHER, 16, 6), new Ware(Items.GLOWSTONE_DUST, 16, 8), new Ware(Items.REDSTONE, 32, 6),
            new Ware(Items.LAPIS_LAZULI, 16, 8), new Ware(Items.QUARTZ, 32, 8), new Ware(Items.AMETHYST_SHARD, 16, 8),
            new Ware(Items.NAUTILUS_SHELL, 2, 15), new Ware(Items.BONE_MEAL, 32, 4), new Ware(Items.COOKED_BEEF, 16, 6),
            new Ware(Items.BREAD, 16, 4), new Ware(Items.ARROW, 32, 5), new Ware(Items.FIREWORK_ROCKET, 16, 8),
            new Ware(Items.LEAD, 2, 6), new Ware(Items.BOOK, 8, 6), new Ware(Items.OBSIDIAN, 8, 10),
            new Ware(Items.MAGMA_CREAM, 4, 8), new Ware(Items.GHAST_TEAR, 1, 15), new Ware(Items.HONEY_BOTTLE, 4, 5),
            new Ware(Items.BLUE_ICE, 8, 10), new Ware(Items.GLOW_BERRIES, 16, 5), new Ware(Items.SEA_LANTERN, 8, 8),
            new Ware(Items.SHULKER_SHELL, 2, 40), new Ware(Items.SPONGE, 2, 12), new Ware(Items.CLOCK, 1, 8),
            new Ware(Items.COMPASS, 1, 6), new Ware(Items.SPYGLASS, 1, 8), new Ware(Items.TURTLE_SCUTE, 2, 15),
            new Ware(Items.PRISMARINE_CRYSTALS, 8, 8), new Ware(Items.COCOA_BEANS, 16, 5), new Ware(Items.CACTUS, 16, 4),
            new Ware(Items.PACKED_ICE, 16, 6), new Ware(Items.MOSS_BLOCK, 16, 5), new Ware(Items.PODZOL, 16, 6),
            new Ware(Items.TROPICAL_FISH_BUCKET, 1, 8), new Ware(Items.POINTED_DRIPSTONE, 8, 6), new Ware(Items.DRIED_KELP_BLOCK, 8, 5));

    public static boolean isMerchant(CaughtCreature c) {
        return c != null && c.job() != null && MERCHANT.equals(c.job().profession());
    }

    /** This merchant's wares: the same each time for the same merchant, different between merchants. */
    public static List<Ware> wares(CaughtCreature c) {
        MovesHolder h = (MovesHolder) (Object) c;
        long seed = ((long) String.valueOf(h.serantis$moves()).hashCode() << 32) ^ String.valueOf(h.serantis$temperament()).hashCode() ^ c.species().hashCode();
        Random r = new Random(seed);
        List<Ware> all = new ArrayList<>(POOL);
        java.util.Collections.shuffle(all, r);
        return all.subList(0, 6 + r.nextInt(3));
    }

    /** Puts this merchant's wares into its trade screen. */
    public static void decorateView(ServerPlayer p, CaughtCreature c, JsonObject view) {
        if (view == null || !isMerchant(c)) return;
        var ops = p.level().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        JsonArray offers = new JsonArray();
        for (Ware w : wares(c)) {
            JsonObject o = new JsonObject();
            JsonElement stack = ItemStack.CODEC.encodeStart(ops, new ItemStack(w.item(), w.count())).getOrThrow();
            o.add("stack", stack);
            o.addProperty("cost", dev.serantis.battle.skills.Skills.price(p, w.cost()));
            o.addProperty("rank", 0);
            offers.add(o);
        }
        view.add("offers", offers);
        if (!view.has("rank") || view.get("rank").getAsInt() < 1) view.addProperty("rank", 1);
    }

    /** Buys from a merchant; returns a message for the player or null on success. */
    public static String buyFromMerchant(ServerPlayer p, CaughtCreature c, int index, int times) {
        List<Ware> wares = wares(c);
        if (index < 0 || index >= wares.size()) return "No such item.";
        Ware w = wares.get(index);
        int cost = dev.serantis.battle.skills.Skills.price(p, w.cost());
        int have = XpBank.points(p);
        int n = Math.min(Math.max(1, Math.min(64, times)), have / Math.max(1, cost));
        if (n <= 0) return "That costs " + cost + " XP; you have " + have + ".";
        XpBank.take(p, cost * n);
        for (int i = 0; i < n; i++) give(p, new ItemStack(w.item(), w.count()));
        p.level().playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.VILLAGER_YES, net.minecraft.sounds.SoundSource.NEUTRAL, 0.7f, 1f);
        return null;
    }

    private static void give(ServerPlayer p, ItemStack stack) {
        if (!p.getInventory().add(stack) && !stack.isEmpty())
            p.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(p.level(), p.getX(), p.getY(), p.getZ(), stack));
    }

    // ------------------------------------------------------------------ the black market

    public static boolean isBlackMarket(Entity e) {
        if (!(e instanceof CreatureEntity c)) return false;
        VillagerJob j = Villages.jobOf(c);
        return j != null && BLACK_MARKET.equals(j.profession());
    }

    /** Replaces a wild creature that just spawned in the Overworld with a black market merchant, now and then. */
    public static CreatureEntity maybeBlackMarket(ServerLevel level, CreatureEntity spawned, RandomSource random) {
        if (spawned == null || level.dimension() != Level.OVERWORLD || random.nextDouble() >= BLACK_MARKET_CHANCE) return spawned;
        CreatureEntity m = spawnBlackMarket(level, spawned.getX(), spawned.getY(), spawned.getZ(), random);
        if (m == null) return spawned;
        spawned.discard();
        return m;
    }

    public static CreatureEntity spawnBlackMarket(ServerLevel level, double x, double y, double z, RandomSource random) {
        Species villager = Species.get(dev.ahrivaea.villager.Villagers.SPECIES).orElse(null);
        if (villager == null) return null;
        CreatureEntity e = Ahrivaea.spawn(level, villager, Influence.NONE, 10, -1f, x, y, z);
        if (e == null) return null;
        Villages.setJob(e, new VillagerJob(BLACK_MARKET, 1, 0));
        e.setPersistenceRequired();
        e.setCustomName(Component.literal("Black Market Merchant").withStyle(ChatFormatting.DARK_PURPLE));
        e.setCustomNameVisible(true);
        Holder h = (Holder) e;
        h.serantis$setStock(stock(random));
        h.serantis$setLeaveAt(level.getGameTime() + STAY_TICKS);
        for (ServerPlayer p : level.players())
            if (p.distanceToSqr(e) < 64 * 64)
                p.sendSystemMessage(Component.literal("You hear whispers nearby... a Black Market Merchant has set up shop.").withStyle(ChatFormatting.DARK_PURPLE));
        return e;
    }

    /** 3-5 creatures, each Rare (70%), Super Rare (25%) or Legendary (5%), Level 10-20. */
    static List<CaughtCreature> stock(RandomSource random) {
        Map<String, List<Object[]>> byRarity = new java.util.HashMap<>();
        for (Species sp : Species.all()) {
            if (dev.ahrivaea.villager.Villagers.SPECIES.equals(sp.id())) continue;
            for (Influence inf : sp.forms().keySet())
                if (!Retired.is(sp, inf)) byRarity.computeIfAbsent(RecruitRules.rarity(sp, inf), k -> new ArrayList<>()).add(new Object[]{sp, inf});
        }
        List<CaughtCreature> out = new ArrayList<>();
        int n = 3 + random.nextInt(3);
        for (int i = 0; i < n; i++) {
            int roll = random.nextInt(100);
            String[] order = roll < 70 ? new String[]{"Rare", "Super Rare", "Legendary"}
                    : roll < 95 ? new String[]{"Super Rare", "Rare", "Legendary"} : new String[]{"Legendary", "Super Rare", "Rare"};
            List<Object[]> pool = null;
            for (String r : order) { pool = byRarity.get(r); if (pool != null && !pool.isEmpty()) break; }
            if (pool == null || pool.isEmpty()) break;
            Object[] pick = pool.get(random.nextInt(pool.size()));
            Species sp = (Species) pick[0];
            Influence inf = (Influence) pick[1];
            int level = 10 + random.nextInt(11);
            float health = sp.statsFor(inf).maxHealth(level);
            out.add(new CaughtCreature(sp.id(), inf, level, 0, health, 0, 0, false, Variant.NONE, VillagerJob.NONE));
        }
        return out;
    }

    // ------------------------------------------------------------------ shopping at the black market

    /** Which merchant (entity id) each player is looking at. */
    private static final Map<UUID, Integer> VIEWING = new ConcurrentHashMap<>();

    private static CreatureEntity merchant(ServerPlayer p, int entityId) {
        Entity e = p.level().getEntity(entityId);
        return e instanceof CreatureEntity c && isBlackMarket(c) && c.isAlive() && p.distanceToSqr(c) < 10 * 10 ? c : null;
    }

    public static void open(ServerPlayer p, int entityId) {
        if (merchant(p, entityId) == null) return;
        VIEWING.put(p.getUUID(), entityId);
        PartySync.sync(p);
    }

    public static void close(ServerPlayer p) {
        if (VIEWING.remove(p.getUUID()) != null) PartySync.sync(p);
    }

    public static void buy(ServerPlayer p, int entityId, int index) {
        CreatureEntity m = merchant(p, entityId);
        if (m == null) { tell(p, "The Black Market Merchant is gone."); return; }
        List<CaughtCreature> stock = new ArrayList<>(((Holder) m).serantis$stock());
        if (index < 0 || index >= stock.size()) return;
        int have = XpBank.points(p), cost = dev.serantis.battle.skills.Skills.price(p, CREATURE_PRICE);
        if (have < cost) { tell(p, "That costs " + cost + " XP; you have " + have + "."); return; }
        CaughtCreature c = stock.remove(index);
        XpBank.take(p, cost);
        boolean added = Party.addAnywhere(p, c);
        if (!added) { XpBank.give(p, cost); tell(p, "You have no room for another creature."); return; }
        ((Holder) m).serantis$setStock(stock);
        String name = Species.get(c.species()).map(sp -> sp.nameFor(c.influence())).orElse(c.species());
        p.sendSystemMessage(Component.literal("You bought a Level " + c.level() + " " + name + " from the Black Market for "
                + cost + " XP. No questions asked.").withStyle(ChatFormatting.DARK_PURPLE));
        if (stock.isEmpty()) {
            tell(p, "\"Pleasure doing business.\" The Black Market Merchant packs up and vanishes.");
            m.discard();
            VIEWING.remove(p.getUUID());
        }
        PartySync.sync(p);
    }

    /** "serantis_market" in the party state while a player is looking at a black market merchant. */
    public static void describe(ServerPlayer p, JsonObject state) {
        Integer id = VIEWING.get(p.getUUID());
        if (id == null) return;
        CreatureEntity m = merchant(p, id);
        if (m == null) { VIEWING.remove(p.getUUID()); return; }
        JsonObject o = new JsonObject();
        o.addProperty("entity", id);
        o.addProperty("price", dev.serantis.battle.skills.Skills.price(p, CREATURE_PRICE));
        o.addProperty("xp", XpBank.points(p));
        JsonArray offers = new JsonArray();
        for (CaughtCreature c : ((Holder) m).serantis$stock()) offers.add(PartySync.describe(c));
        o.add("offers", offers);
        state.add("serantis_market", o);
    }

    /** Called every tick for each creature: a black market merchant leaves when its time is up. */
    public static void tick(CreatureEntity e) {
        if (e.level().isClientSide()) return;
        long leave = ((Holder) e).serantis$leaveAt();
        if (leave > 0 && e.level().getGameTime() > leave && !e.isNoAi()) e.discard();     // not mid-battle
    }

    private static void tell(ServerPlayer p, String msg) {
        p.sendSystemMessage(Component.literal(msg));
    }
}
