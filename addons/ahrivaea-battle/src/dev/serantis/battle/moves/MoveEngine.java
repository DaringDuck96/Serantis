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

    private MoveEngine() {}

    // ------------------------------------------------------------------ small helpers

    public static UnitState state(Unit u) { return ((UnitAccess) (Object) u).serantis$state(); }
    private static BattleAccess acc(Battle b) { return (BattleAccess) (Object) b; }
    private static int dist(Unit a, Unit b) { return Pathing.distance(a.x, a.z, b.x, b.z); }
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
        if (u.isPlayer) return;
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

    /** Once the battle is set up: wild creatures keep the moves saved on them (or save the ones just rolled). */
    public static void assignFromWorld(Battle b) {
        if (b == null || !MoveBook.available()) return;
        for (Unit u : b.units) {
            if (u.isPlayer || u.entity == null || u.side != Unit.Side.ENEMY) continue;
            Entity e = b.level.getEntity(u.entity);
            if (!(e instanceof CreatureEntity)) continue;
            MovesHolder h = (MovesHolder) (Object) e;
            List<MoveSpec> saved = MoveBook.decode(h.serantis$moves());
            if (saved != null) ((UnitAccess) (Object) u).serantis$setMoves(MoveBook.toBattleMoves(saved));
            else h.serantis$setMoves(encodeUnit(u));
        }
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

        int bonus = bonusPower(a, t, spec);
        BattleMove bm = bonus == 0 ? m : withPower(m, m.move().power() + bonus);
        List<Unit> hit = new ArrayList<>();
        for (Unit u : area(b, a, t, spec)) {
            if (!a.alive()) break;
            if (!u.alive()) continue;
            if (hitUnit(b, a, u, spec, bm)) { hit.add(u); onHit(b, a, u, spec); }
        }
        if ("chain".equals(spec.shape) && a.alive()) chain(b, a, t, spec, m, hit);
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
                for (Unit u : b.units) if (u.alive() && u.side == foe && Math.abs(u.x - t.x) <= r && Math.abs(u.z - t.z) <= r) out.add(u);
            }
            case "arc" -> {
                out.add(t);
                for (Unit u : b.units) if (out.size() < 3 && u != t && u.alive() && u.side == foe && dist(a, u) == 1) out.add(u);
            }
            case "line", "line_first" -> {
                for (int[] c : line(a, t, spec.max))
                    for (Unit u : b.units)
                        if (u.alive() && u.side == foe && u.x == c[0] && u.z == c[1] && !out.contains(u)) out.add(u);
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
            else if ("recoil_stance".equals(type)) { state(a).recoil = true; acc(b).serantis$say(a.name + " takes a Counter Stance."); }
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
                case "recoil_stance" -> { state(a).recoil = true; acc.serantis$say(a.name + " takes a Counter Stance."); }
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
        String label = switch (stat) { case "attack" -> "Attack"; case "defense" -> "Defense"; case "magic" -> "Magic"; case "speed" -> "Speed"; default -> "Accuracy"; };
        acc(b).serantis$say(u.name + "'s " + label + (amount > 0 ? " +" : " ") + amount + ("accuracy".equals(stat) ? "%" : "") + " for " + turns + (turns == 1 ? " turn." : " turns."));
    }

    private static void addTiles(Battle b, Unit u, int tiles) {
        if (tiles == 0) return;
        state(u).effects.add(new UnitState.Effect("move", "tiles", tiles, 1, ownTurn(b, u), 0));
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
    public static int squares(Unit u, int base) {
        UnitState st = state(u);
        if (st.has("immobilized")) return 0;
        int sq = (int) Math.round(base * (1 + 0.1 * st.stage("speed"))) + st.tiles();
        if (st.has("slowed")) sq -= 2;
        return Math.max(0, sq);
    }

    /** Hit and crit chances shown and rolled for an attack, with accuracy changes and high ground. */
    public static DamageCalc.Result adjustPreview(Battle b, Unit a, Unit t, BattleMove m, DamageCalc.Result r) {
        if (r == null) return null;
        double hit = Math.max(0, Math.min(1, r.hitChance() + state(a).stage("accuracy") / 100.0));
        double crit = r.critChance();
        if (b.grid.y(a.x, a.z) - b.grid.y(t.x, t.z) >= 1) crit = Math.min(1, crit * 2);
        MoveSpec spec = MoveBook.of(m);
        int hits = spec == null ? 1 : spec.hits;
        int damage = r.damage();
        double expected;
        if (spec != null && (spec.support() || (t.side == a.side))) { damage = 0; expected = 0; }
        else if (spec != null && m.move().power() <= 0) { damage = 0; expected = 0.5 * hit; }
        else expected = damage * hit * (1 + crit) * hits;
        return new DamageCalc.Result(damage, r.multiplier(), hit, crit, expected);
    }

    /** Best damaging move this unit can use on the target from this distance (for the AI and counterattacks). */
    public static BattleMove bestMove(Battle b, Unit u, Unit t, int distance) {
        BattleMove best = null;
        double bestScore = -1;
        for (BattleMove m : u.moves) {
            if (!m.reaches(distance)) continue;
            MoveSpec spec = MoveBook.of(m);
            if (spec != null && (!spec.damaging() || blockedRepeat(b, u, spec))) continue;
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
    }

    private static void startTurn(Battle b, Unit.Side side) {
        BattleAccess acc = acc(b);
        for (Unit u : b.units) {
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
        for (JsonElement el : view.getAsJsonArray("units")) {
            JsonObject uj = el.getAsJsonObject();
            Unit u = b.unit(uj.get("id").getAsInt());
            if (u == null) continue;
            String tags = state(u).tags();
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
            }
        }
    }
}
