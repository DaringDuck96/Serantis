package dev.serantis.battle.moves;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.ahrivaea.battle.Battle;
import dev.ahrivaea.battle.BattleMove;
import dev.ahrivaea.battle.DamageCalc;
import dev.ahrivaea.battle.Pathing;
import dev.ahrivaea.battle.Unit;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Stats;
import dev.ahrivaea.party.CaughtCreature;
import dev.ahrivaea.party.Party;
import dev.serantis.battle.fx.Fx;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Serantis moves in the creatures mod's grid battles (Stage 1).
 *
 * Damage itself still goes through the creatures mod's own strike: Attack vs Defense (Magic vs Magic
 * for magic moves), influence matchups, same-influence bonus, Luck-based hit chance and its high
 * ground and ally bonuses. This class adds what the Move Dex describes on top: areas, multiple hits,
 * stat changes, statuses, healing, Counter Stance recoil, and the turn timing for all of them.
 */
public final class MoveEngine {
    private static final int K = 4;                      // the same power scale the catalog uses
    private static final ThreadLocal<Object[]> IGNORE = new ThreadLocal<>();   // {Unit, stat, points}
    private static boolean firing;                       // a Charged Shot going off (not starting to charge)

    private MoveEngine() {}

    // ------------------------------------------------------------------ small helpers

    public static UnitState state(Unit u) { return ((UnitAccess) (Object) u).serantis$state(); }
    private static BattleAccess acc(Battle b) { return (BattleAccess) (Object) b; }
    private static int dist(Unit a, Unit b) { return Body.gap(a, b); }
    private static int scaled(int sheetAmount, Unit source) { return Math.max(1, (int) Math.round(sheetAmount * K * Stats.levelScale(source.level))); }
    private static Unit.Side other(Unit.Side s) { return s == Unit.Side.PLAYER ? Unit.Side.ENEMY : Unit.Side.PLAYER; }
    private static String s(JsonObject o, String k) { return MoveSpec.str(o, k); }
    private static int i(JsonObject o, String k, int def) { return o.has(k) ? o.get(k).getAsInt() : def; }
    private static double d(JsonObject o, String k, double def) { return o.has(k) ? o.get(k).getAsDouble() : def; }

    private static boolean ownTurn(Battle b, Unit u) {
        return (b.phase == Battle.Phase.PLAYER && u.side == Unit.Side.PLAYER) || (b.phase == Battle.Phase.ENEMY && u.side == Unit.Side.ENEMY);
    }

    private static BattleMove withPower(BattleMove m, int power) {
        DamageCalc.Move mv = m.move();
        return new BattleMove(m.name(), new DamageCalc.Move(power, mv.category(), mv.influence(), mv.accuracy()), m.minRange(), m.maxRange());
    }

    // ------------------------------------------------------------------ move sets

    /** A new unit gets the creature's saved moves (party creatures) or three random ones. */
    public static void assignNewUnit(Battle b, Unit u) {
        boolean guard = dev.serantis.battle.Villages.NEXT_GUARD.get();
        if (guard && u.side == Unit.Side.ENEMY) {
            dev.serantis.battle.Villages.NEXT_GUARD.set(false);
            state(u).guard = true;
            ((UnitAccess) (Object) u).serantis$setName("Town Guard");
        }
        assignNewUnitInner(b, u);
        if (state(u).guard)
            ((UnitAccess) (Object) u).serantis$setStats(dev.serantis.battle.Temperament.apply(dev.serantis.battle.Villages.GUARD_STATS, state(u).temperament, u.level));
    }

    private static void assignNewUnitInner(Battle b, Unit u) {
        state(u).size = u.isPlayer || u.influence == null ? 1 : Footprints.tiles(u.speciesId, u.influence.getSerializedName());
        if (state(u).size > 1) settle(b, u);
        if (u.isPlayer) return;
        String temperament = null, apples = null;
        if (u.side == Unit.Side.PLAYER && u.partySlot >= 0 && b.player() != null) {
            List<CaughtCreature> party = Party.get(b.player());
            if (u.partySlot < party.size()) {
                MovesHolder h = (MovesHolder) (Object) party.get(u.partySlot);
                temperament = h.serantis$temperament();
                apples = h.serantis$apples();
                if (h.serantis$nickname() != null && !h.serantis$nickname().isEmpty()) ((UnitAccess) (Object) u).serantis$setName(h.serantis$nickname());
            }
        }
        setTemperament(u, temperament != null ? temperament : dev.serantis.battle.Temperament.roll(b.random), apples);
        if (u.side == Unit.Side.ENEMY) state(u).trait = EnemyBrain.roll(b.random);
        if (!MoveBook.available()) return;
        List<MoveSpec> specs = null;
        if (u.side == Unit.Side.PLAYER && u.partySlot >= 0) {
            ServerPlayer player = b.player();
            if (player != null) {
                List<CaughtCreature> party = Party.get(player);
                if (u.partySlot < party.size()) {
                    MovesHolder h = (MovesHolder) (Object) party.get(u.partySlot);
                    specs = MoveBook.decode(h.serantis$moves());
                    if (specs == null) {
                        specs = MoveBook.roll(u.influence, b.random);
                        h.serantis$setMoves(MoveBook.encode(specs));
                    }
                }
            }
        }
        if (specs == null) specs = MoveBook.roll(u.influence, b.random);
        ((UnitAccess) (Object) u).serantis$setMoves(MoveBook.toBattleMoves(specs));
    }

    /** A creature that isn't in the party (a raid's base defender): its own temperament, apples, nickname and moves. */
    public static void fromCaught(Unit u, CaughtCreature c) {
        if (u == null || c == null) return;
        MovesHolder h = (MovesHolder) (Object) c;
        if (h.serantis$nickname() != null && !h.serantis$nickname().isEmpty()) ((UnitAccess) (Object) u).serantis$setName(h.serantis$nickname());
        if (h.serantis$temperament() != null) setTemperament(u, h.serantis$temperament(), h.serantis$apples());
        List<MoveSpec> saved = MoveBook.available() ? MoveBook.decode(h.serantis$moves()) : null;
        if (saved != null) ((UnitAccess) (Object) u).serantis$setMoves(MoveBook.toBattleMoves(saved));
    }

    /** Moves a big creature to the nearest spot its whole body fits (or makes it smaller if none does). */
    static void settle(Battle b, Unit u) {
        UnitState st = state(u);
        for (int n = st.size; n >= 1; n--) {
            st.size = n;
            if (Body.canStop(b, u, u.x, u.z)) break;
            int[] fit = Body.nearestFit(Body.ctx(b, u), u.x, u.z, n, u.flies(), Body.lava(u));
            if (fit != null) { u.x = fit[0]; u.z = fit[1]; break; }
        }
        dev.serantis.battle.mixin.BattlesInvoker.serantis$placeEntity(b, u);
    }

    /** Gives a unit its temperament and the stats that go with it. */
    /** Its temperament and golden apples, applied to its battle stats. */
    static void setTemperament(Unit u, String temperament, String apples) {
        if (temperament == null || u.speciesId == null || u.influence == null) return;
        UnitState st = state(u);
        String key = temperament + "|" + (apples == null ? "" : apples);
        if (key.equals(st.statsKey)) return;
        st.temperament = temperament;
        st.statsKey = key;
        dev.ahrivaea.creature.Species.get(u.speciesId).ifPresent(sp ->
                ((UnitAccess) (Object) u).serantis$setStats(dev.serantis.battle.Apples.full(sp.statsFor(u.influence), temperament, apples, u.speciesId, u.influence, u.level)));
    }

    /** Once the battle is set up: wild creatures keep the moves saved on them (or save the ones just rolled). */
    public static void assignFromWorld(Battle b) {
        if (b == null || !MoveBook.available()) return;
        for (Unit u : b.units) {
            if (u.isPlayer || u.entity == null || u.side != Unit.Side.ENEMY) continue;
            Entity e = b.level.getEntity(u.entity);
            if (!(e instanceof CreatureEntity)) continue;
            MovesHolder h = (MovesHolder) (Object) e;
            setTemperament(u, h.serantis$temperament(), h.serantis$apples());
            var job = dev.serantis.battle.Villages.jobOf((CreatureEntity) e);           // villagers show their profession
            if (job != null) ((UnitAccess) (Object) u).serantis$setName(u.name + " (" + dev.serantis.battle.Villages.professionName(job.profession()) + ")");
            List<MoveSpec> saved = MoveBook.decode(h.serantis$moves());
            if (saved != null) ((UnitAccess) (Object) u).serantis$setMoves(MoveBook.toBattleMoves(saved));
            else h.serantis$setMoves(encodeUnit(u));
        }
    }

    /**
     * A battle against a Creature Master: the Master (its world entity) is 40% stronger, its team 25%,
     * and every one of them fights with the strongest of several rolled move sets.
     */
    public static void masterBattle(Battle b, Entity master, String influence) {
        acc(b).serantis$setMaster(influence);
        String name = dev.serantis.battle.Masters.influenceName(influence) + " Creature Master";
        for (Unit u : b.units) {
            if (u.isPlayer || u.side != Unit.Side.ENEMY || u.stats == null) continue;
            boolean lead = master != null && master.getUUID().equals(u.entity);
            if (lead) ((UnitAccess) (Object) u).serantis$setName(name);
            double f = lead ? dev.serantis.battle.Masters.STAT_BOOST_MASTER : dev.serantis.battle.Masters.STAT_BOOST_TEAM;
            var s = u.stats;
            ((UnitAccess) (Object) u).serantis$setStats(new dev.ahrivaea.creature.Stats((int) Math.round(s.hp() * f), (int) Math.round(s.attack() * f),
                    (int) Math.round(s.defense() * f), (int) Math.round(s.speed() * f), (int) Math.round(s.magic() * f),
                    (int) Math.round(s.luck() * f), (int) Math.round(s.charisma() * f)));
            if (!lead && MoveBook.available() && u.influence != null) {
                List<MoveSpec> best = null;
                int bestPower = -1;
                for (int i = 0; i < 6; i++) {
                    List<MoveSpec> roll = MoveBook.roll(u.influence, b.random);
                    int power = 0;
                    for (MoveSpec m : roll) power += Math.max(0, m.sheetPower);
                    if (power > bestPower) { bestPower = power; best = roll; }
                }
                if (best != null) ((UnitAccess) (Object) u).serantis$setMoves(MoveBook.toBattleMoves(best));
            }
        }
        acc(b).serantis$say("The " + name + " sends out its team! Its creatures can't be recruited, but winning pays double XP and a Gym Master Stone.");
    }

    public static String encodeUnit(Unit u) {
        List<MoveSpec> specs = new ArrayList<>();
        for (BattleMove m : u.moves) { MoveSpec s = MoveBook.of(m); if (s != null) specs.add(s); }
        return specs.isEmpty() ? null : MoveBook.encode(specs);
    }

    // ------------------------------------------------------------------ using a move

    public static void use(Battle b, Unit a, Unit t, BattleMove m, boolean counter) {
        BattleAccess acc = acc(b);
        MoveSpec spec = MoveBook.of(m);
        if (spec == null || !a.alive()) return;
        UnitState as = state(a);

        // The AI can pick a move that doesn't suit the target it chose; use its best attack instead.
        boolean sameSide = t.side == a.side;
        if (("enemy".equals(spec.target) && sameSide) || (spec.support() && !sameSide)) {
            BattleMove alt = sameSide ? null : bestMove(b, a, t, dist(a, t));
            if (alt != null && alt != m) { use(b, a, t, alt, counter); return; }
            acc.serantis$say(a.name + " hesitates.");
            return;
        }
        as.lastMoveId = spec.id;
        as.lastMoveTurn = b.turn;

        if (as.has("confused") && !spec.support() && b.random.nextBoolean()) {
            List<Unit> near = new ArrayList<>();
            for (Unit u : b.units) {
                int dd = dist(a, u);
                if (u != a && u.alive() && dd >= Math.max(1, spec.min) && dd <= spec.max) near.add(u);
            }
            if (!near.isEmpty()) {
                t = near.get(b.random.nextInt(near.size()));
                acc.serantis$say(a.name + " is confused and attacks " + t.name + "!");
                counter = false;
                sameSide = t.side == a.side;
            }
        }

        if (spec.support() || ("any".equals(spec.target) && sameSide)) {
            support(b, a, t, spec);
            return;
        }

        Fx.wiggle(b, a);
        Field field = Field.of(b);
        if (spec.has("charge") && !firing) {
            field.addPending("charge", a, t, m, spec, Set.of());
            acc.serantis$say(a.name + " starts charging " + spec.name + " at " + t.name + "! It fires next turn unless interrupted.");
            return;
        }
        if (spec.has("delayed")) {
            field.addPending("meteor", a, t, m, spec, Field.cells(b, t.x, t.z, "square", Math.max(1, spec.radius)));
            acc.serantis$say(a.name + " calls down " + spec.name + "! It lands at the start of " + a.name + "'s next turn.");
            return;
        }
        if (spec.has("dash")) { field.dash(b, a, t); if (!a.alive()) return; }
        int cx = t.x, cz = t.z;                     // where the move was aimed, before anything moves

        int bonus = bonusPower(a, t, spec);
        BattleMove bm = bonus == 0 ? m : withPower(m, m.move().power() + bonus);
        List<Unit> targets = area(b, a, t, spec);
        for (JsonObject e : spec.effects)
            if ("pull".equals(s(e, "type")) && e.has("first") && e.get("first").getAsBoolean())
                forced(b, a, targets, e, cx, cz);
        List<Unit> hit = new ArrayList<>();
        for (Unit u : targets) {
            if (!a.alive()) break;
            if (!u.alive()) continue;
            if (hitUnit(b, a, u, spec, bm)) { hit.add(u); onHit(b, a, u, spec); }
        }
        if ("chain".equals(spec.shape) && a.alive()) chain(b, a, t, spec, m, hit);
        for (JsonObject e : spec.effects)
            if (!(e.has("first") && e.get("first").getAsBoolean())) forced(b, a, hit, e, cx, cz);
        for (JsonObject e : spec.effects)
            if ("zone".equals(s(e, "type")) && a.alive())
                field.addZone(b, a, s(e, "zone"), Field.cells(b, cx, cz, s(e, "cells"), Math.max(1, spec.radius)),
                        i(e, "amount", 0) > 0 ? scaled(i(e, "amount", 0), a) : 0, i(e, "turns", 2));
        selfEffects(b, a, spec);

        if (counter && "single".equals(spec.shape) && a.alive() && t.alive() && t.side != a.side) {
            BattleMove cm = bestMove(b, t, a, dist(t, a));
            if (cm != null) {
                acc.serantis$say(t.name + " strikes back!");
                if (MoveBook.of(cm) != null) use(b, t, a, cm, false);
                else acc.serantis$strike(t, a, cm);
            }
        }
    }

    /** Damage (or, for a no-damage move, just the hit roll) against one unit. True if it landed. */
    private static boolean hitUnit(Battle b, Unit a, Unit u, MoveSpec spec, BattleMove bm) {
        BattleAccess acc = acc(b);
        if (bm.move().power() <= 0) {
            DamageCalc.Result r = acc.serantis$preview(a, u, bm);
            if (b.random.nextDouble() < r.hitChance()) {
                acc.serantis$say(a.name + " used " + spec.name + " on " + u.name + ".");
                return true;
            }
            acc.serantis$say(a.name + " used " + spec.name + " but missed " + u.name + ".");
            Fx.flash(b, u, Fx.MISS);
            return false;
        }
        boolean landed = false;
        for (int h = 0; h < spec.hits && a.alive() && u.alive(); h++) {
            int before = u.hp;
            IGNORE.set(ignoreFor(u, spec));
            try { acc.serantis$strike(a, u, bm); } finally { IGNORE.remove(); }
            int dealt = before - u.hp;
            if (dealt > 0) { landed = true; afterDamage(b, a, u, dealt); }
        }
        return landed;
    }

    /** Pushes, pulls, teleports and Geyser's shove, on the units the move caught. */
    private static void forced(Battle b, Unit a, List<Unit> units, JsonObject e, int cx, int cz) {
        Field f = Field.of(b);
        String type = s(e, "type");
        int n = i(e, "tiles", 1);
        switch (type) {
            case "push" -> {
                boolean fromUser = !"center".equals(s(e, "origin"));
                int ox = fromUser ? a.x : cx, oz = fromUser ? a.z : cz;
                List<Unit> order = new ArrayList<>(units);
                order.sort((p, q) -> Pathing.distance(ox, oz, q.x, q.z) - Pathing.distance(ox, oz, p.x, p.z));   // farthest first
                for (Unit u : order) if (u != a) f.push(b, a, u, ox, oz, n);
            }
            case "pull" -> {
                boolean toUser = "user".equals(s(e, "toward"));
                int tx = toUser ? a.x : cx, tz = toUser ? a.z : cz;
                List<Unit> order = new ArrayList<>(units);
                order.sort((p, q) -> Pathing.distance(tx, tz, p.x, p.z) - Pathing.distance(tx, tz, q.x, q.z));   // nearest first
                for (Unit u : order) if (u != a) f.pull(b, u, tx, tz, n, toUser);
            }
            case "teleport" -> { for (Unit u : units) f.teleport(b, u, n); }
            case "shift" -> { for (Unit u : units) f.shift(b, u); }
            default -> { }
        }
    }

    /** A Charged Shot going off at the start of its user's turn. */
    static void fire(Battle b, Unit a, Unit t, BattleMove m) {
        firing = true;
        try { use(b, a, t, m, false); } finally { firing = false; }
    }

    /** A Blazing Meteor landing on the tiles it marked. */
    static void land(Battle b, Field.Pending p) {
        Unit a = p.user;
        for (Unit u : new ArrayList<>(b.units)) {
            if (!a.alive()) break;
            if (u == a || !u.alive() || !Field.inside(b, u, p.cells)) continue;   // anyone under it, friend or foe
            if (hitUnit(b, a, u, p.spec, p.move)) onHit(b, a, u, p.spec);
        }
        boolean any = false;
        for (Unit u : b.units) if (u.alive() && u != a && Field.inside(b, u, p.cells)) any = true;
        if (!any) acc(b).serantis$say("It hits nothing but ground.");
    }

    static void slow(Battle b, Unit u) { addStatus(b, u, "slowed", 1, 0); }
    static void weaken(Battle b, Unit u, String stat, int amount) { addStat(b, u, stat, amount, 1); }

    private static Object[] ignoreFor(Unit u, MoveSpec spec) {
        for (JsonObject e : spec.effects)
            if ("ignore".equals(s(e, "type"))) return new Object[]{u, s(e, "stat"), i(e, "amount", 0)};
        return null;
    }

    private static void afterDamage(Battle b, Unit a, Unit u, int dealt) {
        BattleAccess acc = acc(b);
        UnitState us = state(u);
        if (us.remove("sleep")) acc.serantis$say(u.name + " wakes up!");
        if (us.recoil && a != u && a.alive()) {
            int r = Math.max(1, (int) Math.round(dealt * 0.25));
            a.hp = Math.max(0, a.hp - r);
            acc.serantis$say(a.name + " takes " + r + " recoil from " + u.name + "'s Counter Stance.");
            Fx.flash(b, a, Fx.DAMAGE);
            if (!a.alive()) acc.serantis$knockOut(a);
        }
    }

    private static int bonusPower(Unit a, Unit t, MoveSpec spec) {
        int bonus = 0;
        UnitState as = state(a);
        for (JsonObject e : spec.effects) {
            if (!"bonus".equals(s(e, "type"))) continue;
            String when = s(e, "when");
            boolean ok = "moved2".equals(when)
                    ? as.startX != Integer.MIN_VALUE && Pathing.distance(as.startX, as.startZ, a.x, a.z) >= 2
                    : "target_buffed".equals(when) && state(t).hasBuff();
            if (ok) bonus += (int) Math.round(i(e, "amount", 0) * K * ("melee".equals(spec.range) ? 1.15 : 1.0));
        }
        return bonus;
    }

    // ------------------------------------------------------------------ areas

    private static List<Unit> area(Battle b, Unit a, Unit t, MoveSpec spec) {
        List<Unit> out = new ArrayList<>();
        Unit.Side foe = other(a.side);
        switch (spec.shape) {
            case "self_radius" -> { for (Unit u : b.units) if (u.alive() && u.side == foe && dist(a, u) <= spec.radius) out.add(u); }
            case "target_radius", "cross" -> { for (Unit u : b.units) if (u.alive() && u.side == foe && dist(t, u) <= Math.max(1, spec.radius)) out.add(u); }
            case "square" -> {
                int r = Math.max(1, spec.radius);
                for (Unit u : b.units)
                    if (u.alive() && u.side == foe && Body.squareGap(t.x, t.z, Body.size(t), u.x, u.z, Body.size(u)) <= r) out.add(u);
            }
            case "arc" -> {
                out.add(t);
                for (Unit u : b.units) if (out.size() < 3 && u != t && u.alive() && u.side == foe && dist(a, u) == 1) out.add(u);
            }
            case "line", "line_first" -> {
                for (int[] c : line(a, t, spec.max))
                    for (Unit u : b.units)
                        if (u.alive() && u.side == foe && Body.covers(u.x, u.z, Body.size(u), c[0], c[1]) && !out.contains(u)) out.add(u);
                if ("line_first".equals(spec.shape) && out.size() > 1) out = new ArrayList<>(out.subList(0, 1));
                if (out.isEmpty()) out.add(t);
            }
            default -> out.add(t);
        }
        if (out.remove(t)) out.add(0, t);        // the chosen target is hit first
        return out;
    }

    /** Cells from the attacker through the target, up to the move's reach. */
    private static List<int[]> line(Unit a, Unit t, int reach) {
        List<int[]> cells = new ArrayList<>();
        int dx = t.x - a.x, dz = t.z - a.z;
        int n = Math.max(Math.abs(dx), Math.abs(dz));
        if (n == 0) return cells;
        for (int k = 1; k <= Math.max(n, reach); k++) {
            int cx = a.x + (int) Math.round((double) dx * k / n);
            int cz = a.z + (int) Math.round((double) dz * k / n);
            if (Pathing.distance(a.x, a.z, cx, cz) > reach) break;
            cells.add(new int[]{cx, cz});
        }
        return cells;
    }

    private static void chain(Battle b, Unit a, Unit t, MoveSpec spec, BattleMove m, List<Unit> already) {
        int jumps = 2, power = 6;
        for (JsonObject e : spec.effects) if ("chain".equals(s(e, "type"))) { jumps = i(e, "jumps", 2); power = i(e, "power", 6); }
        BattleMove jm = withPower(m, Math.round(power * K));
        Set<Unit> done = new HashSet<>(already);
        done.add(t);
        Unit prev = t;
        for (int j = 0; j < jumps && a.alive(); j++) {
            Unit next = null;
            for (Unit u : b.units)
                if (u.alive() && u.side == other(a.side) && !done.contains(u) && dist(prev, u) <= 2 && (next == null || dist(prev, u) < dist(prev, next))) next = u;
            if (next == null) break;
            acc(b).serantis$say("The lightning jumps to " + next.name + "!");
            hitUnit(b, a, next, spec, jm);
            done.add(next);
            prev = next;
        }
    }

    // ------------------------------------------------------------------ effects

    private static void onHit(Battle b, Unit a, Unit u, MoveSpec spec) {
        for (JsonObject e : spec.effects) {
            String type = s(e, "type"), who = s(e, "who");
            boolean onTarget = who == null || "target".equals(who) || "hit".equals(who);
            switch (type) {
                case "stat" -> { if (onTarget) addStat(b, u, s(e, "stat"), i(e, "amount", 0), i(e, "turns", 2)); }
                case "movement" -> { if (onTarget) addTiles(b, u, i(e, "amount", 0)); }
                case "status" -> {
                    if (b.random.nextDouble() < d(e, "chance", 1.0)) {
                        int p = e.has("power") ? scaled(i(e, "power", 0), a) : 0;
                        addStatus(b, u, s(e, "status"), i(e, "turns", 2), p);
                    }
                }
                case "interrupt" -> {
                    if (b.random.nextDouble() < d(e, "chance", 1.0)) Field.of(b).cancel(b, u);
                }
                case "dispel" -> {
                    if (state(u).removeFirst(true)) acc(b).serantis$say(u.name + " loses a boost.");
                    addStatus(b, u, "nobuff", i(e, "block_turns", 1), 0);
                }
                default -> { }
            }
        }
    }

    /** Effects on the user or its allies that happen once per use (after the hits). */
    private static void selfEffects(Battle b, Unit a, MoveSpec spec) {
        if (!a.alive()) return;
        for (JsonObject e : spec.effects) {
            String type = s(e, "type"), who = s(e, "who");
            if ("stat".equals(type) && "self".equals(who)) addStat(b, a, s(e, "stat"), i(e, "amount", 0), i(e, "turns", 2));
            else if ("stat".equals(type) && "allies_near_self".equals(who))
                for (Unit u : allies(b, a, i(e, "radius", 1))) addStat(b, u, s(e, "stat"), i(e, "amount", 0), i(e, "turns", 2));
            else if ("movement".equals(type) && "self".equals(who)) addTiles(b, a, i(e, "amount", 0));
            else if ("cleanse".equals(type) && "allies_near_self".equals(who))
                for (Unit u : allies(b, a, i(e, "radius", 1))) cleanse(b, u, i(e, "count", 1));
            else if ("recoil_stance".equals(type)) { state(a).recoil = true; acc(b).serantis$say(a.name + " takes a Counter Stance."); Fx.flash(b, a, Fx.BUFF); }
        }
    }

    private static void support(Battle b, Unit a, Unit t, MoveSpec spec) {
        BattleAccess acc = acc(b);
        acc.serantis$say(a.name + " used " + spec.name + (t != a ? " on " + t.name : "") + ".");
        for (JsonObject e : spec.effects) {
            String type = s(e, "type"), who = s(e, "who");
            switch (type) {
                case "heal" -> heal(b, t, scaled(i(e, "amount", 0), a));
                case "stat" -> {
                    int amt = i(e, "amount", 0), turns = i(e, "turns", 2);
                    String st = s(e, "stat");
                    if ("self".equals(who)) addStat(b, a, st, amt, turns);
                    else if ("allies_near_self".equals(who)) for (Unit u : allies(b, a, i(e, "radius", 1))) addStat(b, u, st, amt, turns);
                    else if ("allies_near_target".equals(who)) for (Unit u : allies(b, t, i(e, "radius", 1))) addStat(b, u, st, amt, turns);
                    else addStat(b, t, st, amt, turns);
                }
                case "cleanse" -> cleanse(b, t, i(e, "count", 1));
                case "movement" -> addTiles(b, "self".equals(who) ? a : t, i(e, "amount", 0));
                case "recoil_stance" -> { state(a).recoil = true; acc.serantis$say(a.name + " takes a Counter Stance."); Fx.flash(b, a, Fx.BUFF); }
                default -> { }
            }
        }
    }

    private static List<Unit> allies(Battle b, Unit center, int radius) {
        List<Unit> out = new ArrayList<>();
        for (Unit u : b.units) if (u.alive() && u.side == center.side && dist(center, u) <= radius) out.add(u);
        return out;
    }

    private static void heal(Battle b, Unit u, int amount) {
        int before = u.hp;
        u.hp = Math.min(u.maxHp, u.hp + amount);
        acc(b).serantis$say(u.name + " recovers " + (u.hp - before) + " HP.");
        Fx.flash(b, u, Fx.HEAL);
    }

    private static void cleanse(Battle b, Unit u, int count) {
        for (int n = 0; n < count; n++)
            if (state(u).removeFirst(false)) acc(b).serantis$say(u.name + " shakes off an effect.");
    }

    private static void addStat(Battle b, Unit u, String stat, int amount, int turns) {
        if (amount == 0 || stat == null) return;
        UnitState st = state(u);
        if (amount > 0 && st.has("nobuff")) { acc(b).serantis$say(u.name + " can't be boosted right now."); return; }
        st.effects.add(new UnitState.Effect("stat", stat, amount, turns, ownTurn(b, u), 0));
        if (amount > 0) Fx.flash(b, u, Fx.BUFF);
        String label = switch (stat) { case "attack" -> "Attack"; case "defense" -> "Defense"; case "magic" -> "Magic"; case "speed" -> "Speed"; default -> "Accuracy"; };
        acc(b).serantis$say(u.name + "'s " + label + (amount > 0 ? " +" : " ") + amount + ("accuracy".equals(stat) ? "%" : "") + " for " + turns + (turns == 1 ? " turn." : " turns."));
    }

    private static void addTiles(Battle b, Unit u, int tiles) {
        if (tiles == 0) return;
        state(u).effects.add(new UnitState.Effect("move", "tiles", tiles, 1, ownTurn(b, u), 0));
        if (tiles > 0) Fx.flash(b, u, Fx.BUFF);
        acc(b).serantis$say(u.name + "'s next move " + (tiles > 0 ? "+" : "") + tiles + " tiles.");
    }

    private static void addStatus(Battle b, Unit u, String status, int turns, int power) {
        if (status == null || !u.alive()) return;
        UnitState st = state(u);
        st.remove(status);
        st.effects.add(new UnitState.Effect("status", status, 0, turns, ownTurn(b, u), power));
        if (!"nobuff".equals(status)) acc(b).serantis$say(u.name + " is " + statusWord(status) + "!");
    }

    private static String statusWord(String s) {
        return switch (s) {
            case "burning" -> "burning"; case "bleeding" -> "bleeding"; case "poison" -> "poisoned"; case "freeze" -> "frozen";
            case "stun" -> "stunned"; case "sleep" -> "asleep"; case "confused" -> "confused"; case "frightened" -> "frightened";
            case "immobilized" -> "immobilized"; case "slowed" -> "slowed"; case "doom" -> "held in a deadly grasp"; default -> s;
        };
    }

    // ------------------------------------------------------------------ stats, movement, previews

    /** The unit's battle stats with its current stat changes (and any "ignores X" on the hit in progress). */
    public static DamageCalc.Fighter adjustFighter(Unit u, DamageCalc.Fighter f) {
        if (f == null || u.isPlayer && state(u).effects.isEmpty()) return f;
        UnitState st = state(u);
        int atk = st.stage("attack"), def = st.stage("defense"), mag = st.stage("magic"), spd = st.stage("speed");
        if (st.has("frightened")) { atk -= 3; mag -= 3; }
        Object[] ig = IGNORE.get();
        if (ig != null && ig[0] == u) {
            if ("defense".equals(ig[1])) def -= (Integer) ig[2];
            if ("magic".equals(ig[1])) mag -= (Integer) ig[2];
        }
        if (atk == 0 && def == 0 && mag == 0 && spd == 0) return f;
        Stats s = f.stats();
        Stats changed = new Stats(s.hp(), pct(s.attack(), atk), pct(s.defense(), def), pct(s.speed(), spd), pct(s.magic(), mag), s.luck(), s.charisma());
        return new DamageCalc.Fighter(changed, f.level(), f.influence(), f.inWater(), f.raw());
    }

    private static int pct(int stat, int stage) {
        return Math.max(1, (int) Math.round(stat * (1 + 0.1 * Math.max(-9, stage))));
    }

    /** Movement this turn, after Speed changes, Slowed, Immobilized and one-turn movement changes. */
    public static int squares(Battle b, Unit u, int base) {
        UnitState st = state(u);
        if (st.has("immobilized")) return 0;
        int speed = st.stage("speed"), tiles = st.tiles();
        if (b != null && Field.of(b).inSink(b, u)) { speed = Math.min(0, speed); tiles = Math.min(0, tiles); }   // no bonuses in a Sinkhole
        int sq = (int) Math.round(base * (1 + 0.1 * speed)) + tiles + st.bonusTiles;
        if (st.has("slowed")) sq -= 2;
        return Math.max(0, sq);
    }

    /** Hit and crit chances shown and rolled for an attack, with accuracy changes and high ground. */
    public static DamageCalc.Result adjustPreview(Battle b, Unit a, Unit t, BattleMove m, DamageCalc.Result r) {
        if (r == null) return null;
        double hit = Math.max(0, Math.min(1, r.hitChance() + state(a).stage("accuracy") / 100.0));
        double crit = r.critChance();
        if (!a.flies() && Body.height(b, a) - Body.height(b, t) >= 1) crit = Math.min(1, crit * 2);   // flyers never have high ground
        MoveSpec spec = MoveBook.of(m);
        int hits = spec == null ? 1 : spec.hits;
        int damage = r.damage();
        double expected;
        if (spec != null && (spec.support() || (t.side == a.side))) { damage = 0; expected = 0; }
        else if (spec != null && m.move().power() <= 0) { damage = 0; expected = 0.5 * hit; }
        else {
            damage = flyerDamage(t, m, damage);
            expected = damage * hit * (1 + crit) * hits;
        }
        return new DamageCalc.Result(damage, r.multiplier(), hit, crit, expected);
    }

    /** Ranged attacks (moves that reach more than 1 tile) hit flying creatures 1.25x as hard. */
    public static final double FLYER_BONUS = 1.25;

    static int flyerDamage(Unit t, BattleMove m, int damage) {
        return t != null && t.flies() && m.maxRange() > 1 && damage > 0 ? (int) Math.round(damage * FLYER_BONUS) : damage;
    }

    public static DamageCalc.Result flyerBonus(Unit t, BattleMove m, DamageCalc.Result r) {
        if (r == null) return null;
        int damage = flyerDamage(t, m, r.damage());
        return damage == r.damage() ? r : new DamageCalc.Result(damage, r.multiplier(), r.hitChance(), r.critChance(), r.expected() * damage / Math.max(1, r.damage()));
    }

    /** Best damaging move this unit can use on the target from this distance (for the AI and counterattacks). */
    public static BattleMove bestMove(Battle b, Unit u, Unit t, int distance) {
        BattleMove best = null;
        double bestScore = -1;
        for (BattleMove m : u.moves) {
            if (!m.reaches(distance)) continue;
            MoveSpec spec = MoveBook.of(m);
            if (spec != null && (!spec.damaging() || blockedRepeat(b, u, spec) || spec.has("charge") || spec.has("delayed"))) continue;
            if (spec == null && m.move().power() <= 0) continue;
            double score = acc(b).serantis$preview(u, t, m).expected();
            if (score > bestScore) { bestScore = score; best = m; }
        }
        return best;
    }

    static boolean blockedRepeat(Battle b, Unit u, MoveSpec spec) {
        UnitState st = state(u);
        return spec.has("no_consecutive") && spec.id.equals(st.lastMoveId) && st.lastMoveTurn == b.turn - 1;
    }

    // ------------------------------------------------------------------ the player's actions

    /** Before a player action: catch up on turn changes, remember where the unit started, block repeats. */
    public static String beforeAct(Battle b, Battle.Action action) {
        sync(b);
        Unit u = b.unit(action.unit());
        if (u == null) return null;
        UnitState st = state(u);
        st.startX = u.x;
        st.startZ = u.z;
        if ("attack".equals(action.kind()) && action.move() >= 0 && action.move() < u.moves.size()) {
            MoveSpec spec = MoveBook.of(u.moves.get(action.move()));
            if (spec != null && blockedRepeat(b, u, spec)) return spec.name + " can't be used two turns in a row.";
            if (spec != null && spec.has("charge") && (action.toX() != u.x || action.toZ() != u.z))
                return spec.name + " has to be charged standing still. Use it without moving.";
        }
        return null;
    }

    /** Lets self and ally moves pass the creatures mod's "pick an enemy" check. */
    public static Unit.Side sideForTargetCheck(Battle b, Unit target, Battle.Action action) {
        Unit u = b.unit(action.unit());
        if (u == null || target.side != u.side || action.move() < 0 || action.move() >= u.moves.size()) return target.side;
        MoveSpec spec = MoveBook.of(u.moves.get(action.move()));
        if (spec == null) return target.side;
        boolean ok = switch (spec.target) {
            case "self" -> target == u;
            case "ally" -> target != u;
            case "self_or_ally", "any" -> true;
            default -> false;
        };
        return ok ? other(u.side) : target.side;
    }

    // ------------------------------------------------------------------ turns

    /** Runs end-of-turn and start-of-turn effects whenever the battle has switched sides. */
    public static void sync(Battle b) {
        BattleAccess acc = acc(b);
        Battle.Phase last = acc.serantis$lastPhase();
        if (last == null) { acc.serantis$setLastPhase(b.phase); return; }
        if (last == b.phase) return;
        acc.serantis$setLastPhase(b.phase);
        if (b.phase == Battle.Phase.ENEMY) { endTurn(b, Unit.Side.PLAYER); startTurn(b, Unit.Side.ENEMY); }
        else if (b.phase == Battle.Phase.PLAYER) { endTurn(b, Unit.Side.ENEMY); startTurn(b, Unit.Side.PLAYER); }
        else return;
        acc.serantis$checkEnd();
    }

    private static void endTurn(Battle b, Unit.Side side) {
        BattleAccess acc = acc(b);
        for (Unit u : new ArrayList<>(b.units)) {
            if (u.side != side || !u.alive()) continue;
            UnitState st = state(u);
            for (UnitState.Effect e : new ArrayList<>(st.effects)) {
                if (!"status".equals(e.kind) || e.fresh || !u.alive()) continue;
                int dmg = switch (e.key) {
                    case "bleeding", "poison" -> Math.max(1, (int) Math.round(u.maxHp * 0.05));
                    case "burning" -> Math.max(1, e.power);
                    case "doom" -> Math.max(1, e.power);
                    default -> 0;
                };
                if (dmg > 0) {
                    u.hp = Math.max(0, u.hp - dmg);
                    Fx.flash(b, u, Fx.DAMAGE);
                    acc.serantis$say(u.name + " takes " + dmg + " damage from " + (switch (e.key) {
                        case "bleeding" -> "bleeding"; case "poison" -> "poison"; case "burning" -> "burns"; default -> "the grasp"; }) + ".");
                    if (!u.alive()) acc.serantis$knockOut(u);
                }
            }
            st.effects.removeIf(e -> {
                if (e.fresh) { e.fresh = false; return false; }
                return --e.turns <= 0;
            });
        }
        Field.of(b).endTurn(b, side);
    }

    private static void startTurn(Battle b, Unit.Side side) {
        BattleAccess acc = acc(b);
        Field.of(b).startTurn(b, side);
        for (Unit u : new ArrayList<>(b.units)) {
            if (u.side != side || !u.alive()) continue;
            UnitState st = state(u);
            st.recoil = false;
            st.startX = Integer.MIN_VALUE;
            String why = st.has("freeze") ? "frozen" : st.has("stun") ? "stunned" : st.has("sleep") ? "asleep" : null;
            if (why == null) continue;
            acc.serantis$say(u.name + " is " + why + " and can't act.");
            if (side == Unit.Side.PLAYER) u.acted = true;
            else acc.serantis$enemyQueue().remove(u);
        }
    }

    // ------------------------------------------------------------------ battle screen

    /** Adds statuses to unit names and move details for the battle screen. */
    public static void decorate(Battle b, JsonObject view) {
        if (view == null || !view.has("units")) return;
        Field field = Field.of(b);
        field.describe(view);
        for (JsonElement el : view.getAsJsonArray("units")) {
            JsonObject uj = el.getAsJsonObject();
            Unit u = b.unit(uj.get("id").getAsInt());
            if (u == null) continue;
            uj.addProperty("serantis_size", Body.size(u));
            uj.addProperty("serantis_swims", Body.swims(u));
            String tags = state(u).tags();
            Field.Pending p = field.pendingOf(u);
            if (p != null) tags = ("charge".equals(p.kind) ? "Charging" : "Meteor falling") + (tags.isEmpty() ? "" : ", " + tags);
            if (!tags.isEmpty()) uj.addProperty("name", uj.get("name").getAsString() + " [" + tags + "]");
            if (!uj.has("moves")) continue;
            JsonArray moves = uj.getAsJsonArray("moves");
            for (int n = 0; n < moves.size() && n < u.moves.size(); n++) {
                MoveSpec spec = MoveBook.of(u.moves.get(n));
                if (spec == null) continue;
                JsonObject mj = moves.get(n).getAsJsonObject();
                mj.addProperty("serantis_target", spec.target);
                mj.addProperty("power", spec.sheetPower);
                if (spec.hits > 1) mj.addProperty("name", spec.name + " x" + spec.hits);
                if (spec.text != null) mj.addProperty("info", spec.text);
                mj.addProperty("serantis_line", MoveExplain.line(spec));
                mj.addProperty("serantis_influence", spec.influence == null ? "none" : spec.influence);
                mj.addProperty("serantis_explain", MoveExplain.explain(spec));
            }
        }
    }
}
