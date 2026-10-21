package dev.serantis.battle;

import com.google.gson.JsonObject;
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
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Wandering Trader: turns up in the wild the way vanilla wandering traders do (every vanilla
 * wandering trader becomes one). Give it a creature from your party and it gives you a random one back,
 * of the same level and any rarity (the usual wild odds). It does 3 swaps, then moves on; it also leaves
 * after a day. It can't be fought, hurt or recruited.
 */
public final class Wanderer {
    public static final String ROLE = "wandering_trader";
    public static final int SWAPS = 3;

    private Wanderer() {}

    public static boolean isWanderer(Entity e) {
        if (!(e instanceof CreatureEntity c)) return false;
        VillagerJob j = Villages.jobOf(c);
        return j != null && ROLE.equals(j.profession());
    }

    /** A vanilla wandering trader that was just replaced by a villager creature becomes the Wandering Trader. */
    public static void make(ServerLevel level, CreatureEntity e) {
        if (e == null) return;
        Villages.setJob(e, new VillagerJob(ROLE, 1, 0));
        e.setPersistenceRequired();
        e.setCustomName(Component.literal("Wandering Trader").withStyle(ChatFormatting.AQUA));
        e.setCustomNameVisible(true);
        Market.Holder h = (Market.Holder) e;
        h.serantis$setSwaps(SWAPS);
        h.serantis$setLeaveAt(level.getGameTime() + Market.STAY_TICKS);
    }

    /** A random creature of this level: rarity rolled with the usual wild odds (see SpawnOdds). */
    static CaughtCreature randomCreature(RandomSource random, int level) {
        Map<String, List<Object[]>> byRarity = new HashMap<>();
        for (Species sp : Species.all()) {
            if (dev.ahrivaea.villager.Villagers.SPECIES.equals(sp.id())) continue;
            for (Influence inf : sp.forms().keySet())
                if (!Retired.is(sp, inf)) byRarity.computeIfAbsent(RecruitRules.rarity(sp, inf), k -> new ArrayList<>()).add(new Object[]{sp, inf});
        }
        int roll = random.nextInt(100), tier = 0;
        for (int i = 0; i < SpawnOdds.PERCENT.length; i++) { if (roll < SpawnOdds.PERCENT[i]) { tier = i; break; } roll -= SpawnOdds.PERCENT[i]; }
        List<Object[]> pool = null;
        for (int d = 0; d < SpawnOdds.RARITY.length && (pool == null || pool.isEmpty()); d++)
            pool = byRarity.get(SpawnOdds.RARITY[Math.max(0, tier - d)]);
        if (pool == null || pool.isEmpty()) return null;
        Object[] pick = pool.get(random.nextInt(pool.size()));
        Species sp = (Species) pick[0];
        Influence inf = (Influence) pick[1];
        int lvl = Math.max(1, Math.min(BattleStats.MAX_LEVEL, level));
        return new CaughtCreature(sp.id(), inf, lvl, 0, sp.statsFor(inf).maxHealth(lvl), 0, 0, false, Variant.NONE, VillagerJob.NONE);
    }

    // ------------------------------------------------------------------ swapping

    private static final Map<UUID, Integer> VIEWING = new ConcurrentHashMap<>();

    private static CreatureEntity trader(ServerPlayer p, int entityId) {
        Entity e = p.level().getEntity(entityId);
        return e instanceof CreatureEntity c && isWanderer(c) && c.isAlive() && p.distanceToSqr(c) < 10 * 10 ? c : null;
    }

    public static void open(ServerPlayer p, int entityId) {
        if (trader(p, entityId) == null) return;
        VIEWING.put(p.getUUID(), entityId);
        PartySync.sync(p);
    }

    public static void close(ServerPlayer p) {
        if (VIEWING.remove(p.getUUID()) != null) PartySync.sync(p);
    }

    public static void swap(ServerPlayer p, int entityId, int slot) {
        CreatureEntity t = trader(p, entityId);
        if (t == null) { p.sendSystemMessage(Component.literal("The Wandering Trader has moved on.")); return; }
        Market.Holder h = (Market.Holder) t;
        if (h.serantis$swaps() <= 0) return;
        List<CaughtCreature> party = Party.get(p);
        if (slot < 0 || slot >= party.size()) return;
        CaughtCreature given = party.get(slot);
        CaughtCreature got = randomCreature(p.getRandom(), given.level());
        if (got == null) return;
        Party.set(p, slot, got);
        h.serantis$setSwaps(h.serantis$swaps() - 1);
        p.sendSystemMessage(Component.literal("You gave the Wandering Trader your " + label(given) + " and got a " + label(got) + " ("
                + RecruitRules.rarity(Species.get(got.species()).orElseThrow(), got.influence()) + ")!").withStyle(ChatFormatting.AQUA));
        if (h.serantis$swaps() <= 0) {
            p.sendSystemMessage(Component.literal("\"Happy travels!\" The Wandering Trader moves on."));
            t.discard();
            VIEWING.remove(p.getUUID());
        }
        PartySync.sync(p);
    }

    private static String label(CaughtCreature c) {
        String nick = ((MovesHolder) (Object) c).serantis$nickname();
        String species = Species.get(c.species()).map(sp -> sp.nameFor(c.influence())).orElse(c.species());
        return (nick != null && !nick.isEmpty() ? nick + " the " + species : species) + " (Lv " + c.level() + ")";
    }

    /** "serantis_wanderer" in the party state while a player is at a Wandering Trader. */
    public static void describe(ServerPlayer p, JsonObject state) {
        Integer id = VIEWING.get(p.getUUID());
        if (id == null) return;
        CreatureEntity t = trader(p, id);
        if (t == null) { VIEWING.remove(p.getUUID()); return; }
        JsonObject o = new JsonObject();
        o.addProperty("entity", id);
        o.addProperty("swaps", ((Market.Holder) t).serantis$swaps());
        state.add("serantis_wanderer", o);
    }
}
