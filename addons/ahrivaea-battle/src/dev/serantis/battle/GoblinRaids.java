package dev.serantis.battle;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.base.BaseCoreBlockEntity;
import dev.ahrivaea.base.Bases;
import dev.ahrivaea.base.Worker;
import dev.ahrivaea.battle.Battle;
import dev.ahrivaea.battle.Battles;
import dev.ahrivaea.battle.Unit;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Job;
import dev.ahrivaea.creature.Species;
import dev.ahrivaea.creature.Variant;
import dev.ahrivaea.party.CaughtCreature;
import dev.ahrivaea.party.PartySync;
import dev.serantis.battle.skills.XpBank;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Goblin raids, replacing the creatures mod's raids (the same nights and odds: 25% + 5% per base level,
 * at dusk, while the owner is near a base with workers).
 *
 * 3-10 goblins (Goblins, Treasure Goblins, Bugbears, Scrap Goblins) turn up at the base, levelled for it
 * (base 1: 1-5, 2: 5-9, 3: 9-13, 4: 13-17, 5: 16-20), and a 3-minute timer starts. Click one to bargain:
 * pay XP and they leave, or refuse and the raid battle starts. When the timer runs out the battle starts
 * anyway (after any battle you're in, or when you're next online).
 *
 * In the raid battle you fight with your party and every worker at the base with its Defense job on.
 * Win: the usual battle rewards. Lose (or flee): you lose 10/20/30/40% of your XP for an Easy (3-4 raiders),
 * Medium (5-6), Hard (7-8) or Very Hard (9-10) raid. Paying them off costs a quarter of that.
 */
public final class GoblinRaids {
    public static final String[] SPECIES = {"goblin", "treasure_goblin", "bugbear", "scrap_goblin"};
    private static final int[][] BANDS = {{1, 5}, {5, 9}, {9, 13}, {13, 17}, {16, 20}};
    public static final String[] TIERS = {"Easy", "Medium", "Hard", "Very Hard"};
    private static final int[] LOSS = {10, 20, 30, 40};
    public static final int WINDOW = 3 * 60 * 20;
    public static final String TAG = "serantis_raider";

    public record Pick(String species, int level) {}

    static final class Raid {
        UUID owner;
        ResourceKey<Level> dim;
        BlockPos core;
        int baseLevel;
        List<Pick> picks = new ArrayList<>();
        List<UUID> entities = new ArrayList<>();
        long ends;
        boolean talking, fighting;
        ServerBossEvent bar;
        int defenders, radius = 12;
        boolean resume;             // the raid battle was interrupted (logged out, crashed): it starts again
        int tier() { int n = picks.size(); return n <= 4 ? 0 : n <= 6 ? 1 : n <= 8 ? 2 : 3; }
    }

    private static final Map<UUID, Raid> BY_OWNER = new ConcurrentHashMap<>();
    private static final Map<Battle, Raid> IN_BATTLE = java.util.Collections.synchronizedMap(new IdentityHashMap<>());

    /** Set while a raid battle is being started (the battle code reads it). */
    public static final ThreadLocal<Raid> STARTING = new ThreadLocal<>();
    private static final ThreadLocal<Integer> NEXT = ThreadLocal.withInitial(() -> 1);

    private GoblinRaids() {}

    public static boolean isRaider(Entity e) {
        return e instanceof CreatureEntity && e.entityTags().contains(TAG);
    }

    private static Raid raidOf(Entity e) {
        for (Raid r : BY_OWNER.values()) if (r.entities.contains(e.getUUID())) return r;
        return null;
    }

    /** The creatures mod wants to raid this base: goblins come instead. Returns how many. */
    public static int begin(ServerLevel level, BaseCoreBlockEntity core) {
        UUID owner = core.owner();
        if (owner == null || BY_OWNER.containsKey(owner)) return 0;
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(owner);
        if (p == null) return 0;
        RandomSource random = level.getRandom();
        Raid r = new Raid();
        r.owner = owner;
        r.dim = level.dimension();
        r.core = core.getBlockPos();
        r.baseLevel = Math.max(1, Math.min(5, core.baseLevel()));
        int[] band = BANDS[r.baseLevel - 1];
        int count = 3 + random.nextInt(8);
        for (int i = 0; i < count; i++)
            r.picks.add(new Pick(SPECIES[random.nextInt(SPECIES.length)], band[0] + random.nextInt(band[1] - band[0] + 1)));
        r.radius = core.radius();
        gather(level, r, random);
        if (r.entities.isEmpty()) return 0;
        r.ends = level.getServer().overworld().getGameTime() + WINDOW;
        r.bar = new ServerBossEvent(UUID.randomUUID(), Component.literal("Goblin Raid"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
        r.bar.addPlayer(p);
        BY_OWNER.put(owner, r);
        save(p, r);
        level.playSound(null, r.core, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 2f, 1f);
        p.sendSystemMessage(Component.literal("Goblins are about to raid your base! " + r.picks.size() + " raiders (" + TIERS[r.tier()]
                + "). Click one within 3 minutes to bargain, or the raid begins.").withStyle(ChatFormatting.RED));
        PartySync.sync(p);
        return r.picks.size();
    }

    /** The raiders gather in a group at the edge of the base, facing the core. */
    private static void gather(ServerLevel level, Raid r, RandomSource random) {
        double angle = random.nextDouble() * Math.PI * 2;
        int dist = Math.min(Math.max(r.radius, 8), 12);
        double cx = r.core.getX() + 0.5 + Math.cos(angle) * dist, cz = r.core.getZ() + 0.5 + Math.sin(angle) * dist;
        r.entities.clear();
        for (Pick pick : r.picks) {
            Species sp = Species.get(pick.species()).orElse(null);
            if (sp == null) continue;
            double x = cx + (random.nextDouble() - 0.5) * 6, z = cz + (random.nextDouble() - 0.5) * 6;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
            CreatureEntity e = Ahrivaea.spawn(level, sp, influenceOf(sp), pick.level(), -1f, x, y, z);
            if (e == null) continue;
            e.setVariant(Variant.NONE);
            e.addTag(TAG);
            e.setNoAi(true);
            e.setGlowingTag(true);
            e.capSize(1.25f);
            float yaw = (float) Math.toDegrees(Math.atan2(r.core.getZ() + 0.5 - z, r.core.getX() + 0.5 - x)) - 90f;
            e.setYRot(yaw);
            e.setYHeadRot(yaw);
            r.entities.add(e.getUUID());
        }
    }

    // ------------------------------------------------------------------ saved on the player

    /** dim|x,y,z|baseLevel|radius|ticksLeft|resume|species:level;... */
    private static void save(ServerPlayer p, Raid r) {
        long left = Math.max(0, r.ends - p.level().getServer().overworld().getGameTime());
        StringBuilder sb = new StringBuilder();
        for (Pick k : r.picks) { if (sb.length() > 0) sb.append(';'); sb.append(k.species()).append(':').append(k.level()); }
        ((Welcome.Holder) p).serantis$setRaid(r.dim.identifier() + "|" + r.core.getX() + "," + r.core.getY() + "," + r.core.getZ() + "|" + r.baseLevel
                + "|" + r.radius + "|" + left + "|" + (r.resume || r.fighting ? 1 : 0) + "|" + sb);
    }

    private static void clear(ServerPlayer p) { ((Welcome.Holder) p).serantis$setRaid(""); }

    /** A raid saved on the player but not running (they logged back in, or its battle was interrupted): back it comes. */
    private static Raid restore(ServerPlayer p) {
        String s = ((Welcome.Holder) p).serantis$raid();
        if (s == null || s.isEmpty()) return null;
        try {
            String[] f = s.split("\\|", -1);
            Raid r = new Raid();
            r.owner = p.getUUID();
            r.dim = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, net.minecraft.resources.Identifier.parse(f[0]));
            String[] c = f[1].split(",");
            r.core = new BlockPos(Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2]));
            r.baseLevel = Integer.parseInt(f[2]);
            r.radius = Integer.parseInt(f[3]);
            long left = Long.parseLong(f[4]);
            r.resume = "1".equals(f[5]);
            for (String k : f[6].split(";")) { String[] kv = k.split(":"); r.picks.add(new Pick(kv[0], Integer.parseInt(kv[1]))); }
            if (r.picks.isEmpty()) { clear(p); return null; }
            r.ends = p.level().getServer().overworld().getGameTime() + (r.resume ? 0 : Math.max(20 * 30, left));
            r.bar = new ServerBossEvent(UUID.randomUUID(), Component.literal("Goblin Raid"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
            BY_OWNER.put(r.owner, r);
            p.sendSystemMessage(Component.literal(r.resume ? "The goblins are back to finish the raid on your base!"
                    : "The goblins are still waiting at your base.").withStyle(ChatFormatting.RED));
            return r;
        } catch (RuntimeException e) {
            clear(p);
            return null;
        }
    }

    private static Influence influenceOf(Species sp) {
        return sp.forms().containsKey(Influence.NONE) ? Influence.NONE : sp.forms().keySet().iterator().next();
    }

    public static int lossPercent(Raid r) { return LOSS[r.tier()]; }

    public static int lossXp(ServerPlayer p, Raid r) { return (int) Math.ceil(XpBank.points(p) * LOSS[r.tier()] / 100.0); }

    /** A quarter of what losing would take. */
    public static int bribe(ServerPlayer p, Raid r) { return (int) Math.ceil(XpBank.points(p) * LOSS[r.tier()] / 400.0); }

    private static String mix(Raid r) {
        Map<String, Integer> n = new LinkedHashMap<>();
        for (Pick pick : r.picks) n.merge(pick.species(), 1, Integer::sum);
        List<String> parts = new ArrayList<>();
        n.forEach((sp, c) -> parts.add(c + " " + Species.get(sp).map(Species::name).orElse(sp) + (c > 1 ? "s" : "")));
        return String.join(", ", parts);
    }

    private static String band(Raid r) { int[] b = BANDS[r.baseLevel - 1]; return b[0] + "-" + b[1]; }

    // ------------------------------------------------------------------ talking to them

    /** A player clicked a raider (right-click, or hit it). */
    public static void open(ServerPlayer p, Entity raider) {
        Raid r = raider == null ? null : raidOf(raider);
        if (r == null) return;
        if (!r.owner.equals(p.getUUID())) {
            p.sendOverlayMessage(Component.literal("These goblins are raiding someone else's base."));
            return;
        }
        if (r.fighting) return;
        r.talking = true;
        PartySync.sync(p);
    }

    public static void open(ServerPlayer p, int entityId) { open(p, p.level().getEntity(entityId)); }

    public static void close(ServerPlayer p) {
        Raid r = BY_OWNER.get(p.getUUID());
        if (r != null && r.talking) { r.talking = false; PartySync.sync(p); }
    }

    public static void pay(ServerPlayer p) {
        Raid r = BY_OWNER.get(p.getUUID());
        if (r == null || r.fighting) return;
        int cost = bribe(p, r);
        if (XpBank.points(p) < cost) return;
        if (cost > 0) XpBank.take(p, cost);
        clear(p);
        p.sendSystemMessage(Component.literal("The goblins pocket " + cost + " XP and slink off into the night.").withStyle(ChatFormatting.YELLOW));
        end(p.level().getServer(), r);
        PartySync.sync(p);
    }

    public static void refuse(ServerPlayer p) {
        Raid r = BY_OWNER.get(p.getUUID());
        if (r == null || r.fighting) return;
        if (Battles.of(p) != null) { p.sendSystemMessage(Component.literal("Finish your battle first.").withStyle(ChatFormatting.YELLOW)); return; }
        fight(p, r);
    }

    // ------------------------------------------------------------------ the timer

    /** Every second, per player. */
    public static void tick(ServerPlayer p) {
        // a raid battle that ended without a result (logged out, crashed): forget it, the saved raid comes back
        synchronized (IN_BATTLE) {
            IN_BATTLE.entrySet().removeIf(en -> en.getValue().owner.equals(p.getUUID()) && Battles.of(p) != en.getKey());
        }
        Raid r = BY_OWNER.get(p.getUUID());
        if (r == null && !inRaidBattle(p)) r = restore(p);
        if (r == null || r.fighting) return;
        ensureRaiders(p, r);
        long now = p.level().getServer().overworld().getGameTime();
        long left = r.ends - now;
        if (r.bar != null) {
            if (!r.bar.getPlayers().contains(p)) r.bar.addPlayer(p);
            r.bar.setProgress(Math.max(0f, Math.min(1f, left / (float) WINDOW)));
            long s = Math.max(0, left / 20);
            r.bar.setName(Component.literal("Goblin Raid (" + TIERS[r.tier()] + ") - " + (s / 60) + ":" + String.format("%02d", s % 60) + " to bargain"));
        }
        if (p.tickCount % 100 == 7) save(p, r);           // the timer survives a restart
        if (left <= 0 && Battles.of(p) == null) fight(p, r);
    }

    private static boolean inRaidBattle(ServerPlayer p) {
        synchronized (IN_BATTLE) {
            for (Raid x : IN_BATTLE.values()) if (x.owner.equals(p.getUUID())) return true;
        }
        return false;
    }

    /** The raiders are back at the base whenever it's loaded and you're near (after a restart, say). */
    private static void ensureRaiders(ServerPlayer p, Raid r) {
        if (r.resume || p.level().dimension() != r.dim || p.blockPosition().distSqr(r.core) > 96 * 96) return;
        ServerLevel level = (ServerLevel) p.level();
        if (!level.isLoaded(r.core)) return;
        for (UUID id : r.entities) if (level.getEntity(id) != null) return;
        gather(level, r, level.getRandom());
        PartySync.sync(p);
    }

    private static void removeRaiders(net.minecraft.server.MinecraftServer server, Raid r) {
        ServerLevel level = server.getLevel(r.dim);
        if (level == null) return;
        for (UUID id : r.entities) {
            Entity e = level.getEntity(id);
            if (e != null) e.discard();
        }
    }

    private static void end(net.minecraft.server.MinecraftServer server, Raid r) {
        BY_OWNER.remove(r.owner);
        if (r.bar != null) r.bar.removeAllPlayers();
        removeRaiders(server, r);
    }

    /** A raider entity no raid knows about any more (the server restarted, or the raid is over): gone. */
    public static void check(CreatureEntity e) {
        if (e.level().isClientSide() || !isRaider(e)) return;
        if (raidOf(e) == null) e.discard();
    }

    // ------------------------------------------------------------------ the battle

    private static List<Worker> defenders(ServerPlayer p, Raid r) {
        List<Worker> out = new ArrayList<>();
        ServerLevel level = p.level().getServer().getLevel(r.dim);
        BaseCoreBlockEntity core = level == null ? null : Bases.coreAt(level, r.core);
        if (core == null) return out;
        for (Worker w : core.workers()) {
            CaughtCreature c = w.creature();
            if (c == null || c.influence() == null) continue;
            Species sp = Species.get(c.species()).orElse(null);
            if (sp != null && w.active(sp.jobsFor(c.influence())).contains(Job.DEFENSE)) out.add(w);
        }
        return out;
    }

    private static void fight(ServerPlayer p, Raid r) {
        r.fighting = true;
        r.talking = false;
        if (r.bar != null) r.bar.removeAllPlayers();
        removeRaiders(p.level().getServer(), r);
        List<Worker> guards = defenders(p, r);
        r.defenders = guards.size();
        ServerLevel level = (ServerLevel) p.level();
        Pick lead = r.picks.getFirst();
        Species sp = Species.get(lead.species()).orElse(null);
        var look = p.getLookAngle();
        double x = p.getX() + look.x * 3, z = p.getZ() + look.z * 3;
        double y = p.getY();
        CreatureEntity leader = sp == null ? null : Ahrivaea.spawn(level, sp, influenceOf(sp), lead.level(), -1f, x, y, z);
        Battle b = null;
        if (leader != null) {
            leader.setVariant(Variant.NONE);
            STARTING.set(r);
            NEXT.set(1);
            try { b = Battles.start(p, leader); } finally { STARTING.remove(); }
        }
        if (b == null) {
            if (leader != null && leader.isAlive()) leader.discard();
            p.sendSystemMessage(Component.literal("The goblins lost their nerve and ran off.").withStyle(ChatFormatting.YELLOW));
            end(p.level().getServer(), r);
            clear(p);
            PartySync.sync(p);
            return;
        }
        BY_OWNER.remove(r.owner);
        r.resume = true;
        save(p, r);                                        // interrupted, it starts again; won or lost, it's cleared
        IN_BATTLE.put(b, r);
        addDefenders(p, b, guards);
        ((dev.serantis.battle.moves.BattleAccess) (Object) b).serantis$say("Goblin raid! " + r.picks.size() + " raiders (" + TIERS[r.tier()] + ")"
                + (guards.isEmpty() ? "." : "; " + guards.size() + " base defender" + (guards.size() == 1 ? "" : "s") + " join you!"));
        Battles.send(b);
        PartySync.sync(p);
    }

    private static void addDefenders(ServerPlayer p, Battle b, List<Worker> guards) {
        Unit me = b.playerUnit();
        if (me == null) return;
        var acc = (dev.serantis.battle.mixin.BattleRaidAccess) (Object) b;
        for (Worker w : guards) {
            CaughtCreature c = w.creature();
            Species sp = Species.get(c.species()).orElse(null);
            if (sp == null) continue;
            int[] cell = acc.serantis$freeCellNear(me.x, me.z, Unit.Side.PLAYER);
            if (cell == null) break;
            Unit u = acc.serantis$addCreature(Unit.Side.PLAYER, sp, c.influence(), c.variant(), c.level(), -1f, -1, cell[0], cell[1]);
            if (u == null) continue;
            dev.serantis.battle.moves.MoveEngine.fromCaught(u, c);
            BlockPos at = b.cellPos(u);
            CreatureEntity e = Ahrivaea.spawn(b.level, sp, c.influence(), c.level(), -1f, at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            if (e == null) continue;
            e.setVariant(c.variant());
            e.tame(p);
            e.setNoAi(true);
            e.capSize(1.4f);
            u.entity = e.getUUID();
        }
    }

    /** Raid battles: the reinforcements are the rest of the raid, in order. */
    public static Species.LevelledPick nextPick() {
        Raid r = STARTING.get();
        if (r == null) return null;
        int i = NEXT.get();
        NEXT.set(i + 1);
        if (i >= r.picks.size()) return null;
        Pick pick = r.picks.get(i);
        Species sp = Species.get(pick.species()).orElse(null);
        return sp == null ? null : new Species.LevelledPick(sp, influenceOf(sp), pick.level());
    }

    public static int raidSize() {
        Raid r = STARTING.get();
        return r == null ? -1 : r.picks.size();
    }

    /** Every 5 seconds, per player: leftover raiders nearby with no raid any more go away. */
    public static void sweep(ServerPlayer p) {
        for (CreatureEntity e : p.level().getEntitiesOfClass(CreatureEntity.class, p.getBoundingBox().inflate(96), GoblinRaids::isRaider)) check(e);
    }

    public static int extraArena() {
        Raid r = STARTING.get();
        return r == null ? 0 : r.defenders > 3 ? 4 : 2;
    }

    /** A battle ended: if it was a raid, the result. */
    public static void finished(Battle b, Battle.Outcome outcome) {
        Raid r = IN_BATTLE.remove(b);
        if (r == null) return;
        FigureVisit.clearWild(b);                         // the raid leader doesn't stay behind
        ServerPlayer p = b.player();
        if (p == null) return;
        clear(p);
        if (outcome == Battle.Outcome.WON) {
            p.sendSystemMessage(Component.literal("You fought off the goblin raid! Your base is safe.").withStyle(ChatFormatting.GREEN));
            Bounties.raidRepelled(p);
            return;
        }
        int lose = lossXp(p, r);
        if (lose > 0) {
            Achievements.NOT_GAINED.set(true);
            try { XpBank.take(p, lose); } finally { Achievements.NOT_GAINED.set(false); }
        }
        p.sendSystemMessage(Component.literal("The goblins overran your base and made off with " + lose + " XP (" + LOSS[r.tier()] + "%).").withStyle(ChatFormatting.RED));
    }

    /** "serantis_raid" in the party state while goblins wait at the player's base. */
    public static void describe(ServerPlayer p, JsonObject state) {
        Raid r = BY_OWNER.get(p.getUUID());
        if (r == null || r.fighting) return;
        JsonObject o = new JsonObject();
        ServerLevel level = p.level().getServer().getLevel(r.dim);
        JsonArray ids = new JsonArray();
        if (level != null) for (UUID id : r.entities) { Entity e = level.getEntity(id); if (e != null) ids.add(e.getId()); }
        o.add("entities", ids);
        o.addProperty("count", r.picks.size());
        o.addProperty("tier", TIERS[r.tier()]);
        o.addProperty("levels", band(r));
        o.addProperty("mix", mix(r));
        o.addProperty("bribe", bribe(p, r));
        o.addProperty("loss", lossXp(p, r));
        o.addProperty("lossPct", LOSS[r.tier()]);
        o.addProperty("xp", XpBank.points(p));
        o.addProperty("defenders", defenders(p, r).size());
        o.addProperty("left", Math.max(0, (r.ends - p.level().getServer().overworld().getGameTime()) / 20));
        o.addProperty("talking", r.talking);
        state.add("serantis_raid", o);
    }
}
