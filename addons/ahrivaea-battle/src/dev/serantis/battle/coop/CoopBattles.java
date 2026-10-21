package dev.serantis.battle.coop;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.battle.Arena;
import dev.ahrivaea.battle.ArenaReturn;
import dev.ahrivaea.battle.Battle;
import dev.ahrivaea.battle.BattleMove;
import dev.ahrivaea.battle.Battles;
import dev.ahrivaea.battle.Unit;
import dev.ahrivaea.character.PlayerCharacter;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Healing;
import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Species;
import dev.ahrivaea.creature.Variant;
import dev.ahrivaea.party.CaughtCreature;
import dev.ahrivaea.party.Party;
import dev.serantis.battle.moves.BattleAccess;
import dev.serantis.battle.moves.Body;
import dev.serantis.battle.moves.Field;
import dev.serantis.battle.moves.MoveEngine;
import dev.serantis.battle.mixin.BattleRaidAccess;
import dev.serantis.battle.mixin.BattlesAccess;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Playing battles together.
 *
 * <p><b>Joining a battle.</b> When a group member starts a battle, the rest of the group nearby are asked if they
 * want to join (or press Join on the Co-op page). An ally joins with up to 2 of their party creatures, on the same
 * side; each ally brings one more enemy along too. Everyone controls only their own units, and the turn passes to
 * the enemies once every player is done (or after {@value #TURN_SECONDS} seconds once someone is waiting). The
 * battle is lost only when every player is knocked out. Allies who win get the same XP, battle credit and bounty
 * progress; loot and recruits stay with the player whose battle it was.
 *
 * <p><b>Spars.</b> Two players can fight each other: right-click a player and challenge them. The challenger's
 * side moves first, then the other player's (the creatures mod's "enemy phase", held for them to play). Spars are
 * friendly: no XP, loot, recruiting, armour wear or real damage to anyone's creatures.
 */
public final class CoopBattles {
    public static final int MAX_ALLIES = 3, ALLY_CREATURES = 2, SPAR_CREATURES = 4, TURN_SECONDS = 90, JOIN_RANGE = 48;
    /** A route answer meaning "not co-op business: let the creatures mod handle it". */
    public static final String PASS = "\u0001pass";
    /** An act() answer meaning "handled here, nothing went wrong". */
    public static final String HANDLED = "\u0001handled";

    static final class Info {
        final List<UUID> allies = new ArrayList<>();
        UUID rival;                                           // a spar: the other player
        final Map<Integer, UUID> control = new HashMap<>();   // unit id -> who controls it (absent: the battle's owner)
        final Map<Integer, Integer> slots = new HashMap<>();  // an ally's creature -> its party slot
        boolean raid, noJoin, ended;
        ServerLevel home;
        Vec3 homePos = Vec3.ZERO;
        net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome;
        long phaseSince = System.currentTimeMillis();
        Battle.Phase lastPhase;
        int lastTurn;
        boolean warned;
    }

    private static final Map<Battle, Info> INFO = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<UUID, Battle> JOINED = new ConcurrentHashMap<>();   // ally or sparring rival -> their battle
    private static final Map<UUID, Battle> DONE = new ConcurrentHashMap<>();     // -> a finished battle, until they leave it

    public static final ThreadLocal<ServerPlayer> ACTOR = new ThreadLocal<>();
    public static final ThreadLocal<ServerPlayer> SPAR_STARTING = new ThreadLocal<>();
    private static final ThreadLocal<Object[]> START_HOME = new ThreadLocal<>();     // {ServerLevel, Vec3}

    private CoopBattles() {}

    // ------------------------------------------------------------------ queries

    static Info info(Battle b) { return b == null ? null : INFO.get(b); }

    public static boolean isSpar(Battle b) { Info i = info(b); return i != null && i.rival != null; }

    public static boolean isCoop(Battle b) { Info i = info(b); return i != null && (i.rival != null || !i.allies.isEmpty()); }

    /** The battle an ally or sparring rival is fighting in (null: none, or it's their own). */
    public static Battle joined(Player p) { return p == null ? null : JOINED.get(p.getUUID()); }

    private static ServerPlayer online(Battle b, UUID id) {
        return id == null ? null : b.level.getServer().getPlayerList().getPlayer(id);
    }

    /** Who gives this unit its orders. */
    public static UUID controllerId(Battle b, Unit u) {
        Info i = info(b);
        UUID c = i == null || u == null ? null : i.control.get(u.id);
        return c != null ? c : b.playerId;
    }

    private static boolean controls(Battle b, Unit u, ServerPlayer p) { return p != null && controllerId(b, u).equals(p.getUUID()); }

    /** The player behind a unit: the player themself for a player unit, otherwise whoever controls it. */
    public static ServerPlayer playerOf(Battle b, Unit u) {
        if (u == null) return b.player();
        if (u.isPlayer && u.entity != null) {
            ServerPlayer p = online(b, u.entity);
            if (p != null) return p;
        }
        ServerPlayer p = online(b, controllerId(b, u));
        return p != null ? p : b.player();
    }

    /** Whose potions an item action uses: the player acting. */
    public static ServerPlayer itemUser(Battle b) {
        ServerPlayer a = ACTOR.get();
        if (a != null && (a.getUUID().equals(b.playerId) || JOINED.get(a.getUUID()) == b)) return a;
        return b.player();
    }

    private static List<ServerPlayer> others(Battle b) {
        Info i = info(b);
        List<ServerPlayer> out = new ArrayList<>();
        if (i == null) return out;
        for (UUID id : i.allies) { ServerPlayer p = online(b, id); if (p != null) out.add(p); }
        if (i.rival != null) { ServerPlayer p = online(b, i.rival); if (p != null) out.add(p); }
        return out;
    }

    private static String name(Battle b, UUID id) {
        ServerPlayer p = online(b, id);
        if (p != null) return p.getPlainTextName();
        for (Unit u : b.units) if (u.isPlayer && id.equals(u.entity)) return u.name;
        return "?";
    }

    private static void say(Battle b, String line) { ((BattleAccess) (Object) b).serantis$say(line); }

    private static void send(ServerPlayer p, String json) {
        p.connection.send(new ClientboundCustomPayloadPacket(new Battles.StatePayload(json)));
    }

    /** Remembers where a player came from, so the creatures mod sends them back after the arena. */
    private static void setReturn(ServerPlayer p) {
        try {
            Object type = ArenaReturn.class.getField("ATTACHMENT").get(null);
            for (var m : p.getClass().getMethods()) {
                if (m.getName().equals("hasAttached") && m.getParameterCount() == 1 && (boolean) m.invoke(p, type)) return;
            }
            for (var m : p.getClass().getMethods())
                if (m.getName().equals("setAttached") && m.getParameterCount() == 2) { m.invoke(p, type, ArenaReturn.of(p)); return; }
        } catch (ReflectiveOperationException | RuntimeException e) {
            Ahrivaea.LOGGER.warn("Couldn't save {}'s return point", p.getPlainTextName(), e);
        }
    }

    private static void goBack(ServerPlayer p) {
        BattlesAccess.serantis$previewing().remove(p.getUUID());
        try {
            ArenaReturn.sendBack(p);
        } catch (RuntimeException e) {
            Ahrivaea.LOGGER.error("Couldn't send {} back from the arena", p.getPlainTextName(), e);
        }
    }

    // ------------------------------------------------------------------ battle start: offers to join

    /** Battles.start, at its head: where the battle is (for who's close enough to join). */
    public static void starting(ServerPlayer owner) {
        START_HOME.set(new Object[]{owner.level(), owner.position(), owner.level().getBiome(owner.blockPosition())});
    }

    /** Battles.start, on return: ask the owner's group nearby whether they'd like to join. */
    public static void started(ServerPlayer owner, Battle b) {
        Object[] home = START_HOME.get();
        START_HOME.remove();
        if (b == null || home == null || SPAR_STARTING.get() != null) return;
        Info i = new Info();
        i.home = (ServerLevel) home[0];
        i.homePos = (Vec3) home[1];
        @SuppressWarnings("unchecked") var biome = (net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>) home[2];
        i.biome = biome;
        i.raid = dev.serantis.battle.GoblinRaids.STARTING.get() != null;
        i.noJoin = dev.serantis.battle.FigureVisit.FIGHTING.get();       // an Acolyte's trial is yours alone
        INFO.put(b, i);
        if (i.noJoin) return;
        Groups.Group g = Groups.of(owner);
        if (g == null) return;
        for (UUID m : g.members()) {
            if (m.equals(owner.getUUID())) continue;
            ServerPlayer p = online(b, m);
            if (p != null && joinable(p, owner) == null) {
                Groups.ask(p, "join", owner, 30_000);
                p.sendSystemMessage(Component.literal(owner.getPlainTextName() + " is in a battle nearby. Join in from the pop-up or the Co-op page!").withStyle(ChatFormatting.AQUA));
            }
        }
    }

    /** How many of the owner's group are close enough to be asked to join a battle starting now. */
    public static int possibleAllies(ServerPlayer owner) {
        if (owner == null || SPAR_STARTING.get() != null || dev.serantis.battle.FigureVisit.FIGHTING.get()) return 0;
        Groups.Group g = Groups.of(owner);
        if (g == null) return 0;
        int n = 0;
        for (UUID m : g.members()) {
            if (m.equals(owner.getUUID())) continue;
            ServerPlayer p = owner.level().getServer().getPlayerList().getPlayer(m);
            if (p != null && p.level() == owner.level() && p.distanceTo(owner) <= JOIN_RANGE && Battles.of(p) == null) n++;
        }
        return Math.min(MAX_ALLIES, n);
    }

    /** Extra arena size for a co-op battle: room for the allies who might join (and their creatures), or a spar's rival team. */
    public static int extraArena(ServerPlayer owner) {
        if (SPAR_STARTING.get() != null) return 3;
        int allies = possibleAllies(owner);
        return allies == 0 ? 0 : allies == 1 ? 2 : 3;
    }

    /** Why this player can't join the owner's battle right now (null: they can). */
    public static String joinable(ServerPlayer p, ServerPlayer owner) {
        if (owner == null) return "They're not online.";
        Battle b = Battles.of(owner);
        if (b == null || !b.playerId.equals(owner.getUUID()) || b.phase == Battle.Phase.OVER) return owner.getPlainTextName() + " isn't in a battle.";
        Info i = info(b);
        if (i == null || i.noJoin || i.rival != null) return "That battle can't be joined.";
        if (!Groups.same(p, owner.getUUID())) return "Only " + owner.getPlainTextName() + "'s group can join.";
        if (Battles.of(p) != null) return "You're already in a battle.";
        if (i.allies.size() >= MAX_ALLIES) return "That battle is full.";
        if (p.level() != i.home || p.position().distanceTo(i.homePos) > JOIN_RANGE) return "You're too far away (within " + JOIN_RANGE + " blocks).";
        return null;
    }

    /** An ally steps into a group member's battle. */
    public static void join(ServerPlayer p, UUID ownerId) {
        ServerPlayer owner = p.level().getServer().getPlayerList().getPlayer(ownerId);
        String why = joinable(p, owner);
        if (why != null) { p.sendSystemMessage(Component.literal(why).withStyle(ChatFormatting.RED)); return; }
        Battle b = Battles.of(owner);
        Info i = info(b);
        var acc = (BattleRaidAccess) (Object) b;
        Unit anchor = b.playerUnit();
        if (anchor == null) for (Unit u : b.units) if (u.side == Unit.Side.PLAYER) { anchor = u; break; }
        if (anchor == null) return;
        UUID id = p.getUUID();
        int[] cell = acc.serantis$freeCellNear(anchor.x, anchor.z, Unit.Side.PLAYER);
        Unit me = acc.serantis$addPlayer(p, cell[0], cell[1]);
        if (me == null) return;
        i.control.put(me.id, id);
        i.allies.add(id);
        JOINED.put(id, b);
        Groups.forget(p);
        p.stopRiding();
        if (b.level.dimension().equals(Arena.DIMENSION)) {
            setReturn(p);
            BattlesAccess.serantis$previewing().add(id);
            BlockPos at = b.cellPos(me);
            p.teleportTo(b.level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Set.of(), 180f, 30f, true);
        }
        List<Unit> added = new ArrayList<>();
        int extra = 0;
        BattlesAccess.serantis$setSpawning(true);
        try {
            List<CaughtCreature> party = Party.get(p);
            for (int s = 0; s < party.size() && added.size() < ALLY_CREATURES; s++) {
                CaughtCreature c = party.get(s);
                Unit u = addCreature(b, Unit.Side.PLAYER, c, me.x, me.z, p);
                if (u == null) continue;
                i.control.put(u.id, id);
                i.slots.put(u.id, s);
                added.add(u);
            }
            for (int n = 0; n < 1 + added.size(); n++) if (addEnemy(b, i)) extra++;   // one more foe for the ally and one per creature they brought
        } finally {
            BattlesAccess.serantis$setSpawning(false);
        }
        for (Unit u : b.units) BattlesAccess.serantis$placeEntity(b, u);
        say(b, p.getPlainTextName() + " joins the battle" + (added.isEmpty() ? "!" : " with " + added.size() + " creature" + (added.size() == 1 ? "" : "s") + "!")
                + (extra == 0 ? "" : extra == 1 ? " Another enemy joins the fray!" : " " + extra + " more enemies join the fray!"));
        Battles.send(b);
    }

    /** A party creature into a battle: its unit (with its moves and temperament) and its entity, tamed to its owner. */
    private static Unit addCreature(Battle b, Unit.Side side, CaughtCreature c, int nearX, int nearZ, ServerPlayer owner) {
        Species sp = c == null ? null : Species.get(c.species()).orElse(null);
        if (sp == null) return null;
        var acc = (BattleRaidAccess) (Object) b;
        int[] cell = acc.serantis$freeCellNear(nearX, nearZ, side);
        Unit u = acc.serantis$addCreature(side, sp, c.influence(), c.variant(), c.level(), c.health(), -1, cell[0], cell[1]);
        if (u == null) return null;
        MoveEngine.fromCaught(u, c);
        BlockPos at = b.cellPos(u);
        CreatureEntity e = Ahrivaea.spawn(b.level, sp, c.influence(), c.level(), (float) u.hp, at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        if (e != null) {
            e.setVariant(c.variant());
            e.tame(owner);
            e.setNoAi(true);
            e.capSize(1.4f);
            u.entity = e.getUUID();
        }
        return u;
    }

    /**
     * One more enemy to keep a co-op battle hard: what this battle would bring as reinforcements (a Master's team,
     * the raid's goblins, or the creatures of the biome, Enraged and Mutated included), about as strong as the foes
     * already on the field. Recruitable like any other (except a Master's team).
     */
    private static boolean addEnemy(Battle b, Info i) {
        List<Unit> foes = new ArrayList<>();
        for (Unit u : b.units) if (u.alive() && u.side == Unit.Side.ENEMY && !u.isPlayer && u.speciesId != null && !u.speciesId.isEmpty()) foes.add(u);
        if (foes.isEmpty()) return false;
        Unit like = foes.get(b.random.nextInt(foes.size()));
        Species likeSpecies = Species.get(like.speciesId).orElse(null);
        if (likeSpecies == null) return false;
        Species sp = likeSpecies;
        Influence inf = like.influence;
        int lvl = Math.max(1, like.level - b.random.nextInt(2));
        Variant variant = Variant.NONE;
        String master = ((BattleAccess) (Object) b).serantis$master();
        Species.LevelledPick pick = null;
        if (master != null) pick = dev.serantis.battle.Masters.teamPick(master, like.level, b.random);
        else if (!i.raid && i.biome != null && i.home != null) {
            pick = dev.ahrivaea.party.RecruitRules.reinforcement(i.biome, i.home, b.random, likeSpecies, like.influence, like.level).orElse(null);
            variant = Variant.roll(i.home, b.random);
        }
        if (pick != null) { sp = pick.species(); inf = pick.influence(); lvl = pick.level(); }
        var acc = (BattleRaidAccess) (Object) b;
        int[] cell = acc.serantis$freeCellNear(like.x, like.z, Unit.Side.ENEMY);
        Unit u = acc.serantis$addCreature(Unit.Side.ENEMY, sp, inf, variant, lvl, -1f, -1, cell[0], cell[1]);
        if (u == null) return false;
        BlockPos at = b.cellPos(u);
        CreatureEntity e = Ahrivaea.spawn(b.level, sp, inf, lvl, -1f, at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        if (e != null) {
            e.setVariant(variant);
            e.setNoAi(true);
            e.capSize(1.4f);
            u.entity = e.getUUID();
        }
        return true;
    }

    // ------------------------------------------------------------------ spars

    /** Right-clicked a player and challenged them. */
    public static void challenge(ServerPlayer from, String targetId) {
        UUID id;
        try { id = UUID.fromString(targetId); } catch (IllegalArgumentException e) { return; }
        ServerPlayer to = from.level().getServer().getPlayerList().getPlayer(id);
        if (to == null || to == from) return;
        String why = sparProblem(from, to);
        if (why != null) { from.sendSystemMessage(Component.literal(why).withStyle(ChatFormatting.RED)); return; }
        Groups.ask(to, "spar", from, 45_000);
        from.sendSystemMessage(Component.literal("You challenged " + to.getPlainTextName() + " to a friendly spar.").withStyle(ChatFormatting.GREEN));
    }

    private static String sparProblem(ServerPlayer a, ServerPlayer b) {
        if (Battles.of(a) != null || Battles.of(b) != null) return "Not while either of you is in a battle.";
        if (a.level() != b.level() || a.distanceTo(b) > 32) return "You need to be within 32 blocks of each other.";
        if (a.level().dimension().equals(Arena.DIMENSION)) return "Not in the arenas.";
        if (Party.get(b).isEmpty()) return b.getPlainTextName() + " has no creatures in their party.";
        if (Party.get(a).isEmpty()) return "You have no creatures in your party.";
        return null;
    }

    /** The challenge was accepted: build the spar. The challenger is the battle's owner (they move first). */
    public static void startSpar(ServerPlayer a, ServerPlayer rival) {
        String why = sparProblem(a, rival);
        if (why != null) {
            a.sendSystemMessage(Component.literal(why).withStyle(ChatFormatting.RED));
            rival.sendSystemMessage(Component.literal(why).withStyle(ChatFormatting.RED));
            return;
        }
        List<CaughtCreature> party = Party.get(rival);
        CaughtCreature lead = party.get(0);
        Species sp = Species.get(lead.species()).orElse(null);
        if (sp == null) return;
        CreatureEntity stand = Ahrivaea.spawn(rival.level(), sp, lead.influence(), lead.level(), lead.health(), rival.getX(), rival.getY(), rival.getZ());
        if (stand == null) return;
        stand.setVariant(lead.variant());
        Battle b;
        SPAR_STARTING.set(rival);
        try {
            b = Battles.start(a, stand);
        } finally {
            SPAR_STARTING.remove();
        }
        if (b == null) { stand.discard(); return; }
        Info i = new Info();
        i.rival = rival.getUUID();
        i.noJoin = true;
        i.home = rival.level();
        i.homePos = rival.position();
        INFO.put(b, i);
        dev.serantis.battle.FigureVisit.clearWild(b);                // the stand-in creature isn't a wild one to send back
        Unit leadUnit = null;
        for (Unit u : b.units) if (u.side == Unit.Side.ENEMY) { leadUnit = u; break; }
        if (leadUnit == null) return;
        MoveEngine.fromCaught(leadUnit, lead);
        i.control.put(leadUnit.id, rival.getUUID());
        var acc = (BattleRaidAccess) (Object) b;
        int[] cell = acc.serantis$freeCellNear(leadUnit.x, leadUnit.z, Unit.Side.ENEMY);
        Unit ru = rivalUnit(b, rival, cell);
        i.control.put(ru.id, rival.getUUID());
        BattlesAccess.serantis$setSpawning(true);
        try {
            for (int s = 1; s < party.size() && s < SPAR_CREATURES; s++) {
                Unit u = addCreature(b, Unit.Side.ENEMY, party.get(s), leadUnit.x, leadUnit.z, rival);
                if (u != null) i.control.put(u.id, rival.getUUID());
            }
        } finally {
            BattlesAccess.serantis$setSpawning(false);
        }
        JOINED.put(rival.getUUID(), b);
        rival.stopRiding();
        if (b.level.dimension().equals(Arena.DIMENSION)) {
            setReturn(rival);
            BattlesAccess.serantis$previewing().add(rival.getUUID());
            BlockPos at = b.cellPos(ru);
            rival.teleportTo(b.level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Set.of(), 0f, 30f, true);
        }
        for (Unit u : b.units) BattlesAccess.serantis$placeEntity(b, u);
        b.log.clear();
        say(b, "A friendly spar: " + a.getPlainTextName() + " vs " + rival.getPlainTextName() + "! No XP, loot or recruiting, and nobody really gets hurt.");
        say(b, "Turn 1: " + a.getPlainTextName() + "'s turn");
        Battles.send(b);
    }

    /** The rival player themself, on the enemy side. */
    private static Unit rivalUnit(Battle b, ServerPlayer rival, int[] cell) {
        PlayerCharacter c = PlayerCharacter.get(rival);
        List<BattleMove> moves = new ArrayList<>(dev.serantis.battle.Equipment.playerMoves(rival));
        int hp = dev.serantis.battle.skills.Skills.battleHp(rival.getMaxHealth());
        Unit u = new Unit(((BattleRaidAccess) (Object) b).serantis$newId(), Unit.Side.ENEMY, true, rival.getPlainTextName(), c.battleStats(rival),
                c.level(), Influence.NONE, Species.Movement.WALK, moves, hp, hp, "", -1);
        u.x = cell[0];
        u.z = cell[1];
        u.entity = rival.getUUID();
        b.units.add(u);
        MoveEngine.assignNewUnit(b, u);
        MoveEngine.state(u).bonusTiles = dev.serantis.battle.skills.Skills.tiles(rival);
        if (rival.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).is(dev.serantis.battle.TeleportBoots.BOOTS)) MoveEngine.state(u).flight = true;
        return u;
    }

    private static Unit rivalPlayerUnit(Battle b, Info i) {
        for (Unit u : b.units) if (u.isPlayer && i.rival.equals(u.entity)) return u;
        return null;
    }

    /** The creatures mod plays the enemy phase itself; in a spar it waits for the rival instead. */
    public static boolean holdEnemyStep(Battle b) { return isSpar(b) && b.phase == Battle.Phase.ENEMY; }

    public static void enemyPhaseStarted(Battle b) {
        Info i = info(b);
        if (i != null && i.rival != null) say(b, name(b, i.rival) + "'s turn");
    }

    private static void endRivalTurn(Battle b) {
        if (b.phase != Battle.Phase.ENEMY) return;
        ((BattleAccess) (Object) b).serantis$enemyQueue().clear();
        b.phase = Battle.Phase.PLAYER;
        b.turn++;
        for (Unit u : b.units) u.acted = false;
        say(b, "Turn " + b.turn + ": " + name(b, b.playerId) + "'s turn");
    }

    /** The rival's move in a spar (their units are the "enemy" side). Mirrors Battle.act. */
    private static String rivalAct(Battle b, ServerPlayer rival, JsonObject j) {
        String kind = j.get("kind").getAsString();
        if (b.phase != Battle.Phase.ENEMY) return "It isn't your turn.";
        if ("end_turn".equals(kind)) { endRivalTurn(b); return null; }
        Unit u = b.unit(opt(j, "unit"));
        if (u == null || !u.alive() || u.side != Unit.Side.ENEMY || !controls(b, u, rival)) return "That's not one of your units.";
        if (u.acted) return u.name + " has already acted this turn.";
        int tx = opt(j, "x"), tz = opt(j, "z");
        if (!b.grid.inside(tx, tz)) return "That's off the battlefield.";
        Integer cost = b.reachable(u).get(tx + tz * b.grid.size);
        if (cost == null || !b.canStop(u, tx, tz)) return u.name + " can't move there.";
        Battle.Action action = new Battle.Action(kind, u.id, tx, tz, opt(j, "move"), opt(j, "target"));
        String before = MoveEngine.beforeAct(b, action);
        if (before != null) return before;
        int ox = u.x, oz = u.z;
        u.x = tx;
        u.z = tz;
        Unit t = b.unit(action.target());
        String problem = switch (kind) {
            case "attack" -> {
                if (action.move() < 0 || action.move() >= u.moves.size()) yield "No such move.";
                if (t == null || !t.alive()) yield "Pick a target.";
                boolean ok = t.side != u.side || MoveEngine.sideForTargetCheck(b, t, action) != t.side;
                if (!ok) yield "Pick an enemy to attack.";
                yield u.moves.get(action.move()).reaches(Body.distance(b, u.x, u.z, t.x, t.z)) ? null : "That target is out of range.";
            }
            case "rally" -> !u.isPlayer ? "Only you can rally."
                    : t != null && t != u && t.side == u.side && t.alive() && Body.distance(b, u.x, u.z, t.x, t.z) == 1 ? null : "Rally a creature next to you.";
            case "item" -> !u.isPlayer ? "Only you can use items."
                    : Healing.firstPotion(rival).isEmpty() ? "You have no healing potions."
                    : t != null && t.side == u.side && t.alive() && Body.distance(b, u.x, u.z, t.x, t.z) <= 1 ? null : "Use it on yourself or a unit next to you.";
            case "wait" -> null;
            case "equip" -> "You can't change gear during a spar.";
            default -> "Unknown action.";
        };
        if (problem != null) { u.x = ox; u.z = oz; return problem; }
        BattlesAccess.serantis$placeEntity(b, u);
        switch (kind) {
            case "attack" -> ((BattleAccess) (Object) b).serantis$attack(u, t, u.moves.get(action.move()), true);
            case "rally" -> {
                double ch = u.charisma() * (dev.serantis.battle.PlayerClass.is(rival, "performer") ? dev.serantis.battle.PlayerClass.PERFORMER_RALLY : 1);
                t.rally = 1.0 + ch / 200.0;
                say(b, u.name + " rallies " + t.name + " (+" + Math.round((t.rally - 1.0) * 100.0) + "% damage on its next attack)");
            }
            case "item" -> heal(b, rival, u, t);
            default -> { }
        }
        u.acted = true;
        var st = MoveEngine.state(u);
        if (u.alive() && st.startX != Integer.MIN_VALUE && (st.startX != tx || st.startZ != tz)) {
            Field field = ((BattleAccess) (Object) b).serantis$field();
            field.enter(b, u, field.walked(b, u, st.startX, st.startZ, tx, tz));
        }
        if (!((BattleAccess) (Object) b).serantis$checkEnd() && b.phase == Battle.Phase.ENEMY
                && b.units.stream().noneMatch(x -> x.alive() && x.side == Unit.Side.ENEMY && !x.acted)) endRivalTurn(b);
        return null;
    }

    private static void heal(Battle b, ServerPlayer p, Unit u, Unit target) {
        ItemStack s = Healing.firstPotion(p);
        var heal = Healing.potion(s);
        if (heal == null) return;
        boolean drinkable = s.is(Items.POTION);
        if (!p.getAbilities().instabuild) s.shrink(1);
        if (drinkable) Healing.giveBack(p, new ItemStack(Items.GLASS_BOTTLE));
        StringBuilder msg = new StringBuilder(u.name + " uses " + heal.name() + " on " + (target == u ? "themself" : target.name));
        for (Unit t : b.units) {
            boolean hit = t == target || heal.splash() && t.side == target.side && t.alive() && Body.distance(b, t.x, t.z, target.x, target.z) == 1;
            if (!hit) continue;
            int amount = Math.min(t.maxHp - t.hp, (int) Math.round(t.maxHp * heal.percent() / 100.0));
            t.hp += amount;
            msg.append(t == target ? ": " : ", ").append(t == target ? "" : t.name + " ").append("+").append(amount).append(" HP");
        }
        say(b, msg.toString());
    }

    private static int opt(JsonObject j, String k) { return j.has(k) ? j.get(k).getAsInt() : -1; }

    // ------------------------------------------------------------------ hooks in Battle

    /** Battle.act, first: only your own units, and in co-op "end turn" waits for everyone. */
    public static String checkAct(Battle b, Battle.Action a) {
        Info i = info(b);
        ServerPlayer actor = ACTOR.get();
        if (i == null || actor == null || "flee".equals(a.kind())) return null;
        if (i.rival != null) return actor.getUUID().equals(b.playerId) ? null : "It isn't your turn.";
        if (i.allies.isEmpty()) return null;
        if ("end_turn".equals(a.kind())) {
            if (b.phase != Battle.Phase.PLAYER) return null;
            for (Unit u : b.units) if (u.alive() && u.side == Unit.Side.PLAYER && controls(b, u, actor)) u.acted = true;
            List<String> waiting = waitingFor(b);
            if (waiting.isEmpty()) return null;                       // everyone's done: the enemy phase starts
            say(b, actor.getPlainTextName() + " is done. Waiting for " + String.join(" and ", waiting) + ".");
            return HANDLED;
        }
        Unit u = b.unit(a.unit());
        if (u != null && u.side == Unit.Side.PLAYER && !controls(b, u, actor)) return "That's " + name(b, controllerId(b, u)) + "'s to command.";
        return null;
    }

    private static List<String> waitingFor(Battle b) {
        List<String> out = new ArrayList<>();
        for (Unit u : b.units) {
            if (!u.alive() || u.side != Unit.Side.PLAYER || u.acted) continue;
            String n = name(b, controllerId(b, u));
            if (!out.contains(n)) out.add(n);
        }
        return out;
    }

    /** Battle.checkEnd, first: a co-op battle goes on while any player stands; a spar ends when a player falls. */
    public static Boolean checkEnd(Battle b) {
        Info i = info(b);
        if (i == null || b.phase == Battle.Phase.OVER) return null;
        boolean mine = false, theirs = false, playerUp = false;
        for (Unit u : b.units) {
            if (!u.alive()) continue;
            if (u.side == Unit.Side.PLAYER) { mine = true; if (u.isPlayer) playerUp = true; }
            else theirs = true;
        }
        if (i.rival != null) {
            Unit owner = b.playerUnit(), rival = rivalPlayerUnit(b, i);
            if (rival == null || !rival.alive() || !theirs) { ((BattleRaidAccess) (Object) b).serantis$finish(Battle.Outcome.WON); return true; }
            if (owner == null || !owner.alive() || !mine) { ((BattleRaidAccess) (Object) b).serantis$finish(Battle.Outcome.LOST); return true; }
            return false;
        }
        if (i.allies.isEmpty() || !theirs) return null;
        return playerUp ? false : null;
    }

    /** Battle.view, last: each viewer commands only their own units; a spar's rival sees the field from their side. */
    public static void view(Battle b, UUID viewer, JsonObject o) {
        Info i = info(b);
        if (i == null || o == null || viewer == null || (i.rival == null && i.allies.isEmpty())) return;
        boolean rival = viewer.equals(i.rival);
        if (o.has("units")) {
            for (var el : o.getAsJsonArray("units")) {
                JsonObject u = el.getAsJsonObject();
                int id = u.get("id").getAsInt();
                Unit unit = b.unit(id);
                boolean mineSide = rival ? "ENEMY".equals(u.get("side").getAsString()) : "PLAYER".equals(u.get("side").getAsString());
                if (rival) u.addProperty("side", "ENEMY".equals(u.get("side").getAsString()) ? "PLAYER" : "ENEMY");
                if (mineSide && unit != null && !controllerId(b, unit).equals(viewer)) u.addProperty("acted", true);   // an ally's: not yours to move
                if (mineSide && unit != null && !unit.isPlayer && !controllerId(b, unit).equals(viewer))
                    u.addProperty("name", u.get("name").getAsString() + " (" + name(b, controllerId(b, unit)) + ")");
            }
        }
        if (rival && o.has("phase")) {
            String ph = o.get("phase").getAsString();
            o.addProperty("phase", "PLAYER".equals(ph) ? "ENEMY" : "ENEMY".equals(ph) ? "PLAYER" : ph);
        }
        if (i.rival != null && o.has("log")) {
            JsonArray lg = new JsonArray();
            for (var l : o.getAsJsonArray("log")) {
                String s = l.getAsString();
                if (s.equals("Enemy phase")) s = name(b, i.rival) + "'s turn";
                else if (s.endsWith(": your phase")) s = s.replace(": your phase", ": " + name(b, b.playerId) + "'s turn");
                else if (s.startsWith("Victory!")) s = name(b, b.playerId) + " wins the spar!";
                else if (s.equals("You were defeated and retreat.")) s = name(b, i.rival) + " wins the spar!";
                lg.add(s);
            }
            o.add("log", lg);
        }
        if (!viewer.equals(b.playerId) && o.has("result")) {
            JsonObject r = o.getAsJsonObject("result");
            if (rival && r.has("outcome")) {
                String out = r.get("outcome").getAsString();
                r.addProperty("outcome", "WON".equals(out) ? "LOST" : "WON");
            }
            r.add("recruits", new JsonArray());
            r.add("loot", new JsonArray());
        }
    }

    // ------------------------------------------------------------------ hooks in Battles

    /** Battles.send, after: everyone else in the battle sees it too. */
    public static void sendOthers(Battle b) {
        for (ServerPlayer p : others(b)) {
            if (JOINED.get(p.getUUID()) != b && DONE.get(p.getUUID()) != b) continue;
            send(p, b.view(p.getUUID()).toString());
        }
    }

    /** Battles.handle, first. Allies and rivals aren't in the creatures mod's list of battles, so their actions come here. */
    public static String route(ServerPlayer p, String json) {
        JsonObject j;
        try { j = JsonParser.parseString(json).getAsJsonObject(); } catch (RuntimeException e) { return PASS; }
        String kind = j.has("kind") ? j.get("kind").getAsString() : "";
        if (kind.startsWith("serantis_")) return PASS;
        UUID id = p.getUUID();
        Battle b = JOINED.get(id);
        if (b != null) {
            Info i = info(b);
            if (i == null || b.phase == Battle.Phase.OVER) { JOINED.remove(id); return "The battle is over."; }
            String problem;
            if ("flee".equals(kind)) {
                withdraw(p, b, true);
                return null;
            } else if ("recruit".equals(kind) || "leave".equals(kind)) {
                problem = "The battle isn't over yet.";
            } else if (id.equals(i.rival)) {
                problem = rivalAct(b, p, j);
            } else {
                problem = b.act(new Battle.Action(kind, opt(j, "unit"), opt(j, "x"), opt(j, "z"), opt(j, "move"), opt(j, "target")));
            }
            if (problem != null) p.sendOverlayMessage(Component.literal(problem));
            Battles.send(b);
            return problem;
        }
        Battle done = DONE.get(id);
        if (done != null) {
            if ("leave".equals(kind)) { leave(p); return null; }
            String msg = "recruit".equals(kind) ? "Only " + name(done, done.playerId) + " can recruit from this battle." : "The battle is over.";
            p.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.RED));
            return msg;
        }
        return PASS;
    }

    /** An ally leaves a battle that's still going (fled, or logged out); a rival who does forfeits the spar. */
    private static void withdraw(ServerPlayer p, Battle b, boolean online) {
        UUID id = p.getUUID();
        Info i = info(b);
        JOINED.remove(id);
        if (i == null) return;
        boolean rival = id.equals(i.rival);
        if (!rival) savePartyHealth(b, i, p);
        for (Unit u : b.units) {
            if (!u.alive() || !id.equals(i.control.get(u.id))) continue;
            BattlesAccess.serantis$removeEntity(b, u);
            u.hp = 0;
        }
        i.allies.remove(id);
        say(b, p.getPlainTextName() + (rival ? " forfeits the spar." : " leaves the battle."));
        if (rival) ((BattleRaidAccess) (Object) b).serantis$finish(Battle.Outcome.WON);
        else if (b.phase == Battle.Phase.PLAYER && !((BattleAccess) (Object) b).serantis$checkEnd()
                && b.units.stream().noneMatch(x -> x.alive() && x.side == Unit.Side.PLAYER && !x.acted))
            ((BattleRaidAccess) (Object) b).serantis$startEnemyPhase();
        if (b.phase != Battle.Phase.OVER) Battles.send(b);
        DONE.remove(id);
        if (online) {
            send(p, "{}");
            goBack(p);
        }
    }

    /** Leaving a finished battle's result screen. */
    public static void leave(ServerPlayer p) {
        DONE.remove(p.getUUID());
        send(p, "{}");
        goBack(p);
    }

    /** Battles.goHome (the owner leaves the result screen): anyone still on theirs goes home too. */
    public static void ownerLeft(Battle b) {
        if (b == null) return;
        for (var e : List.copyOf(DONE.entrySet())) {
            if (e.getValue() != b) continue;
            ServerPlayer p = online(b, e.getKey());
            if (p != null) leave(p); else DONE.remove(e.getKey());
        }
    }

    /** Battles.cleanUp, after: the battle is over. Allies' creatures keep their wounds and allies who won are paid. */
    public static void ended(Battle b) {
        Info i = info(b);
        if (i == null || i.ended || (i.rival == null && i.allies.isEmpty())) return;
        i.ended = true;
        Battle.Outcome o = b.outcome;
        List<UUID> people = new ArrayList<>(i.allies);
        if (i.rival != null) people.add(i.rival);
        if (i.rival != null && !b.level.dimension().equals(Arena.DIMENSION))       // fought on the spot: the rival's creatures go away
            for (Unit u : b.units) if (u.side == Unit.Side.ENEMY && !u.isPlayer) BattlesAccess.serantis$removeEntity(b, u);
        for (UUID id : people) {
            JOINED.remove(id);
            ServerPlayer p = online(b, id);
            if (p == null) continue;
            if (i.rival != null) {
                boolean rivalWon = o != Battle.Outcome.WON;
                p.sendSystemMessage(Component.literal(rivalWon ? "You won the spar!" : name(b, b.playerId) + " won the spar.")
                        .withStyle(rivalWon ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
            } else {
                savePartyHealth(b, i, p);
                if (o == Battle.Outcome.WON) reward(b, p);
                if (o != Battle.Outcome.FLED) dev.serantis.battle.FigureVisit.battleOver(p);
            }
            DONE.put(id, b);
            send(p, b.view(id).toString());
        }
        ServerPlayer owner = b.player();
        if (i.rival != null && owner != null)
            owner.sendSystemMessage(Component.literal(o == Battle.Outcome.WON ? "You won the spar!" : name(b, i.rival) + " won the spar.")
                    .withStyle(o == Battle.Outcome.WON ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
    }

    private static void savePartyHealth(Battle b, Info i, ServerPlayer p) {
        if (i.rival != null) return;
        List<CaughtCreature> party = Party.get(p);
        for (var e : i.slots.entrySet()) {
            if (!p.getUUID().equals(i.control.get(e.getKey()))) continue;
            Unit u = b.unit(e.getKey());
            int slot = e.getValue();
            if (u == null || slot < 0 || slot >= party.size()) continue;
            Party.set(p, slot, party.get(slot).withHealth((float) Math.max(1, u.hp)));
        }
    }

    private static void reward(Battle b, ServerPlayer p) {
        int xp = b.poolXp;
        if (xp > 0) Party.addXp(p, xp);
        dev.ahrivaea.villager.Villagers.award(p, dev.ahrivaea.villager.Villagers.BATTLES_WON);
        dev.serantis.battle.Bounties.battleWon(p, b.defeated, ((BattleAccess) (Object) b).serantis$master() != null);
        p.sendSystemMessage(Component.literal("Victory alongside " + name(b, b.playerId) + "! +" + xp + " XP.").withStyle(ChatFormatting.GREEN));
    }

    /** Battle.saveParty: a spar leaves everyone's creatures as they were. */
    public static boolean skipSaveParty(Battle b) { return isSpar(b); }

    // ------------------------------------------------------------------ every tick, per player

    public static void tick(ServerPlayer p) {
        Battle b = JOINED.get(p.getUUID());
        if (b != null) {
            Info i = info(b);
            if (i == null || i.ended || b.phase == Battle.Phase.OVER) { if (i == null) JOINED.remove(p.getUUID()); }
            else {
                for (Unit u : b.units) {
                    if (!u.isPlayer || !p.getUUID().equals(u.entity) || !u.alive()) continue;
                    BlockPos c = b.cellPos(u);
                    if (p.position().distanceToSqr(c.getX() + 0.5, c.getY(), c.getZ() + 0.5) > 0.04)
                        p.connection.teleport(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, p.getYRot(), p.getXRot());
                    p.setDeltaMovement(0, 0, 0);
                }
            }
        }
        if (p.tickCount % 20 != 0) return;
        Battle own = Battles.of(p);
        if (own == null || !own.playerId.equals(p.getUUID())) return;
        Info i = info(own);
        if (i == null || i.ended || (i.rival == null && i.allies.isEmpty()) || own.phase == Battle.Phase.OVER) return;
        timeouts(own, i);
    }

    private static void timeouts(Battle b, Info i) {
        long now = System.currentTimeMillis();
        if (b.phase != i.lastPhase || b.turn != i.lastTurn) {
            i.lastPhase = b.phase;
            i.lastTurn = b.turn;
            i.phaseSince = now;
            i.warned = false;
        }
        boolean someoneWaiting;
        if (i.rival != null) someoneWaiting = true;
        else {
            if (b.phase != Battle.Phase.PLAYER) return;
            boolean done = false, notDone = false;
            Map<UUID, Boolean> ready = new HashMap<>();
            for (Unit u : b.units) {
                if (!u.alive() || u.side != Unit.Side.PLAYER) continue;
                UUID c = controllerId(b, u);
                ready.merge(c, u.acted, Boolean::logicalAnd);
            }
            for (boolean r : ready.values()) { if (r) done = true; else notDone = true; }
            someoneWaiting = done && notDone;
            if (!someoneWaiting) { i.phaseSince = now; i.warned = false; return; }
        }
        long left = TURN_SECONDS * 1000L - (now - i.phaseSince);
        if (left <= 15_000 && !i.warned) {
            i.warned = true;
            say(b, "15 seconds left in this turn.");
            Battles.send(b);
        }
        if (left > 0) return;
        say(b, "Time's up!");
        if (b.phase == Battle.Phase.ENEMY && i.rival != null) endRivalTurn(b);
        else if (b.phase == Battle.Phase.PLAYER) {
            for (Unit u : b.units) if (u.side == Unit.Side.PLAYER) u.acted = true;
            ((BattleRaidAccess) (Object) b).serantis$startEnemyPhase();
        }
        Battles.send(b);
    }

    /** A player logged out. */
    public static void disconnected(ServerPlayer p) {
        Battle b = JOINED.get(p.getUUID());
        if (b != null) withdraw(p, b, false);
        DONE.remove(p.getUUID());
        Groups.forget(p);
    }
}
