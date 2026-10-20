package dev.serantis.battle.moves;

import dev.ahrivaea.battle.Battle;
import dev.ahrivaea.battle.BattleGrid;
import dev.ahrivaea.battle.BattleMove;
import dev.ahrivaea.battle.DamageCalc;
import dev.ahrivaea.battle.Pathing;
import dev.ahrivaea.battle.Unit;
import dev.serantis.battle.mixin.BattlesInvoker;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * How enemy creatures fight. Every enemy in a battle gets one of six mentalities at random:
 *
 *   Aggression        full speed at the nearest opponent
 *   Smart Aggression  full speed at the player
 *   Support           heals, boosts and protects its allies; weakens foes; attacks only when there's nothing to support
 *   Mixed             Aggression, Smart Aggression and Support in rotation, one per turn
 *   Archer            ranged attacks from a distance, keeping away from opponents
 *   Hunter            the most-hurt opponent (or the nearest, if nobody is hurt yet)
 *
 * All of them weigh terrain: cells behind cover from opponents and cells on higher ground score better.
 */
public final class EnemyBrain {
    public static final String[] TRAITS = {"Aggression", "Smart Aggression", "Support", "Mixed", "Archer", "Hunter"};

    private static final double COVER = 6.0;       // per opponent this cell is covered from
    private static final double HIGH_GROUND = 4.0; // per block above the opponents' average height (up to 3)

    private EnemyBrain() {}

    public static String roll(RandomSource random) {
        return TRAITS[random.nextInt(TRAITS.length)];
    }

    /** The mentality in charge this turn (Mixed rotates). */
    public static String active(Battle b, Unit u) {
        String t = MoveEngine.state(u).trait;
        if (t == null) return "Aggression";
        if ("Mixed".equals(t)) return new String[]{"Aggression", "Smart Aggression", "Support"}[Math.floorMod(b.turn, 3)];
        return t;
    }

    private record Plan(int cell, Unit target, BattleMove move, double score) {}

    /** One enemy's turn: move (maybe) and act, following its mentality. */
    public static void act(Battle b, Unit u) {
        BattleAccess acc = (BattleAccess) (Object) b;
        String mind = active(b, u);
        int size = b.grid.size;
        Map<Integer, Integer> reach = b.reachable(u);
        List<Unit> foes = new ArrayList<>(), friends = new ArrayList<>();
        for (Unit o : b.units) if (o.alive()) (o.side == u.side ? friends : foes).add(o);
        if (foes.isEmpty()) return;

        Unit focus = switch (mind) {
            case "Aggression" -> nearest(u, foes);
            case "Smart Aggression" -> player(foes, u);
            case "Hunter" -> mostHurt(u, foes);
            default -> null;
        };

        boolean focusReachable = focus != null && reachableFocus(b, u, focus, reach);
        Field field = Field.of(b);
        int ox = u.x, oz = u.z, n = Body.size(u);
        Plan best = null;
        for (Map.Entry<Integer, Integer> cell : reach.entrySet()) {
            int cx = cell.getKey() % size, cz = cell.getKey() / size;
            if (!b.canStop(u, cx, cz)) continue;
            u.x = cx; u.z = cz;
            double terrain = terrain(b, cx, cz, foes, u.flies()) - field.danger(b, u, cell.getKey()) - cell.getValue() * 0.01;
            Unit closest = nearest(u, foes);
            double nearestFoe = Body.gap(cx, cz, n, closest.x, closest.z, Body.size(closest));

            if ("Support".equals(mind)) {
                for (BattleMove m : u.moves) {
                    MoveSpec s = MoveBook.of(m);
                    if (s == null || MoveEngine.blockedRepeat(b, u, s)) continue;
                    for (Unit t : s.support() || "any".equals(s.target) ? friends : List.<Unit>of()) {
                        int d = Body.gap(cx, cz, n, t.x, t.z, Body.size(t));
                        if (!("self".equals(s.target) ? t == u : m.reaches(d) || (t == u && "self_or_ally".equals(s.target)))) continue;
                        if ("ally".equals(s.target) && t == u) continue;
                        double v = supportValue(b, u, t, s, foes);
                        if (v <= 0) continue;
                        // supporters keep their distance from opponents
                        double score = v + terrain + Math.min(nearestFoe, 4) * 2;
                        best = better(best, new Plan(cell.getKey(), t, m, score));
                    }
                }
            }

            for (Unit t : foes) {
                if (focusReachable && t != focus) continue;      // locked on while the focus is in reach
                int d = Body.gap(cx, cz, n, t.x, t.z, Body.size(t));
                for (BattleMove m : u.moves) {
                    if (!m.reaches(d)) continue;
                    MoveSpec s = MoveBook.of(m);
                    if (s != null && (!(s.damaging() || ("enemy".equals(s.target) && s.power == 0)) || MoveEngine.blockedRepeat(b, u, s))) continue;
                    if (s == null && m.move().power() <= 0) continue;
                    if (s != null && s.has("charge") && (cx != ox || cz != oz)) continue;   // charged standing still
                    boolean ranged = m.maxRange() > 1;
                    if ("Archer".equals(mind) && !ranged && hasRangedOption(u)) continue;
                    DamageCalc.Result r = acc.serantis$preview(u, t, m);
                    double score = r.expected() * areaCount(b, u, t, s) + (r.damage() >= t.hp && r.damage() > 0 ? 1000 : 0);
                    if (s != null && (s.has("charge") || s.has("delayed"))) score *= 0.6;     // it lands a turn later
                    if (s != null && s.has("interrupt") && field.pendingOf(t) != null) score += 30;
                    if (s != null && (s.has("zone") || s.has("delayed"))) score -= 20 * alliesCaught(b, u, t, s, cx, cz);
                    if (t.isPlayer) score += "Smart Aggression".equals(mind) ? 40 : 15;
                    if (t == focus) score += 25;
                    if ("Hunter".equals(mind)) score += (t.maxHp - t.hp) * 0.2;
                    if ("Archer".equals(mind)) score += Math.min(nearestFoe, 5) * 4;
                    if ("Support".equals(mind)) score *= 0.5;            // supporters attack only as a fallback
                    score += terrain;
                    best = better(best, new Plan(cell.getKey(), t, m, score));
                }
            }
        }
        u.x = ox; u.z = oz;

        if (best != null) {
            u.x = best.cell % size; u.z = best.cell / size;
            BattlesInvoker.serantis$placeEntity(b, u);
            field.enter(b, u, field.walked(b, u, ox, oz, u.x, u.z));
            if (!u.alive()) return;
            MoveSpec s = MoveBook.of(best.move);
            if (s != null && (s.support() || ("any".equals(s.target) && best.target.side == u.side))) {
                MoveEngine.use(b, u, best.target, best.move, false);
            } else {
                acc.serantis$attack(u, best.target, best.move, true);
            }
            return;
        }

        // Nothing to do from here: reposition.
        Unit goal = focus != null ? focus : nearest(u, foes);
        int ideal = "Archer".equals(mind) ? idealRange(u) : "Support".equals(mind) ? 3 : 1;
        int bestCell = -1;
        double bestScore = -1e9;
        for (Map.Entry<Integer, Integer> cell : reach.entrySet()) {
            int cx = cell.getKey() % size, cz = cell.getKey() / size;
            if (!b.canStop(u, cx, cz)) continue;
            int d = Body.gap(cx, cz, n, goal.x, goal.z, Body.size(goal));
            double score = -Math.abs(d - ideal) * 10 + terrain(b, cx, cz, foes, u.flies()) - field.danger(b, u, cell.getKey()) - cell.getValue() * 0.01;
            if ("Support".equals(mind) && !friends.isEmpty()) {
                Unit ally = nearestOther(u, friends);
                if (ally != null) score -= Body.gap(cx, cz, n, ally.x, ally.z, Body.size(ally)) * 3;
            }
            if (score > bestScore) { bestScore = score; bestCell = cell.getKey(); }
        }
        if (bestCell >= 0 && bestCell != ox + oz * size) {
            u.x = bestCell % size; u.z = bestCell / size;
            BattlesInvoker.serantis$placeEntity(b, u);
            field.enter(b, u, field.walked(b, u, ox, oz, u.x, u.z));
            acc.serantis$say(u.name + (switch (mind) {
                case "Archer" -> " moves into firing position.";
                case "Support" -> " stays near its allies.";
                default -> " moves closer.";
            }));
        }
    }

    private static Plan better(Plan a, Plan b) { return a == null || b.score > a.score ? b : a; }

    // ---------------------------------------------------------------- choices

    private static boolean reachableFocus(Battle b, Unit u, Unit focus, Map<Integer, Integer> reach) {
        int size = b.grid.size;
        for (int cell : reach.keySet()) {
            int d = Body.gap(cell % size, cell / size, Body.size(u), focus.x, focus.z, Body.size(focus));
            for (BattleMove m : u.moves) {
                MoveSpec s = MoveBook.of(m);
                if ((s == null || s.damaging()) && m.reaches(d)) return true;
            }
        }
        return false;
    }

    private static boolean hasRangedOption(Unit u) {
        for (BattleMove m : u.moves) {
            MoveSpec s = MoveBook.of(m);
            if (m.maxRange() > 1 && (s == null || s.damaging())) return true;
        }
        return false;
    }

    private static int idealRange(Unit u) {
        int best = 1;
        for (BattleMove m : u.moves) {
            MoveSpec s = MoveBook.of(m);
            if (s == null || s.damaging()) best = Math.max(best, Math.min(m.maxRange(), Math.max(m.minRange(), 4)));
        }
        return best;
    }

    private static double supportValue(Battle b, Unit u, Unit t, MoveSpec s, List<Unit> foes) {
        UnitState ts = MoveEngine.state(t);
        double v = 0;
        Unit nearFoe = nearest(t, foes);
        boolean threatened = nearFoe != null && Body.gap(t, nearFoe) <= 6;
        for (var e : s.effects) {
            String type = MoveSpec.str(e, "type");
            if ("heal".equals(type)) {
                int missing = t.maxHp - t.hp;
                if (missing > 0) v += Math.min(missing, amount(e) * 4) * 1.5 + (t.hp * 3 < t.maxHp ? 30 : 0);
            } else if ("stat".equals(type)) {
                int amt = amount(e);
                if (amt > 0 && !ts.has("nobuff")) v += (threatened ? 14 : 6) * amt / 2.0;
            } else if ("cleanse".equals(type)) {
                for (UnitState.Effect ef : ts.effects) if (ef.negative()) { v += 25; break; }
            } else if ("recoil_stance".equals(type)) {
                v += threatened ? 12 : 0;
            }
        }
        return v;
    }

    private static int amount(com.google.gson.JsonObject e) {
        return e.has("amount") ? e.get("amount").getAsInt() : 1;
    }

    /** Allies (and itself) inside the zone or meteor area this move would leave around the target. */
    private static int alliesCaught(Battle b, Unit u, Unit t, MoveSpec s, int cx, int cz) {
        String shape = "square";
        for (com.google.gson.JsonObject e : s.effects)
            if ("zone".equals(MoveSpec.str(e, "type")) && MoveSpec.str(e, "cells") != null) shape = MoveSpec.str(e, "cells");
        java.util.Set<Integer> cells = Field.cells(b, t.x, t.z, shape, Math.max(1, s.radius));
        int n = 0;
        for (int c : Body.cells(b.grid, cx, cz, Body.size(u))) if (cells.contains(c)) { n = 1; break; }
        for (Unit o : b.units) if (o != u && o.alive() && o.side == u.side && Field.inside(b, o, cells)) n++;
        return n;
    }

    /** How many opponents an area move would catch (1 for single-target moves). */
    private static int areaCount(Battle b, Unit u, Unit t, MoveSpec s) {
        if (s == null || "single".equals(s.shape)) return 1;
        int n = 0;
        for (Unit o : b.units) {
            if (!o.alive() || o.side == u.side) continue;
            int d = switch (s.shape) {
                case "self_radius" -> Body.gap(u, o);
                case "square" -> Body.squareGap(t.x, t.z, Body.size(t), o.x, o.z, Body.size(o));
                default -> Body.gap(t, o);
            };
            if (d <= Math.max(1, s.radius)) n++;
        }
        return Math.max(1, Math.min(n, 4));
    }

    private static Unit nearest(Unit from, List<Unit> list) {
        Unit best = null;
        for (Unit o : list) if (best == null || Body.gap(from, o) < Body.gap(from, best)) best = o;
        return best;
    }

    private static Unit nearestOther(Unit from, List<Unit> list) {
        Unit best = null;
        for (Unit o : list) if (o != from && (best == null || Body.gap(from, o) < Body.gap(from, best))) best = o;
        return best;
    }

    private static Unit player(List<Unit> foes, Unit u) {
        for (Unit o : foes) if (o.isPlayer) return o;
        return nearest(u, foes);
    }

    private static Unit mostHurt(Unit u, List<Unit> foes) {
        Unit best = null;
        for (Unit o : foes) if (o.maxHp - o.hp > 0 && (best == null || o.maxHp - o.hp > best.maxHp - best.hp)) best = o;
        return best != null ? best : nearest(u, foes);
    }

    // ---------------------------------------------------------------- terrain

    /** Bonus for standing here: cover from opponents, and height above them. */
    public static double terrain(Battle b, int x, int z, List<Unit> foes, boolean flies) {
        BattleGrid g = b.grid;
        int h = g.y(x, z);
        double score = 0, avg = 0;
        for (Unit f : foes) {
            avg += g.y(f.x, f.z);
            if (covered(g, x, z, f.x, f.z)) score += COVER;
        }
        if (!foes.isEmpty() && !flies) score += Math.max(0, Math.min(3, h - avg / foes.size())) * HIGH_GROUND;   // flyers never have high ground
        return score;
    }

    /** True if a wall, or ground 2+ blocks above this cell, lies between it and the opponent. */
    public static boolean covered(BattleGrid g, int x, int z, int fx, int fz) {
        int dx = fx - x, dz = fz - z, n = Math.max(Math.abs(dx), Math.abs(dz));
        int h = g.y(x, z);
        for (int k = 1; k < n; k++) {
            int cx = x + (int) Math.round((double) dx * k / n), cz = z + (int) Math.round((double) dz * k / n);
            if (!g.inside(cx, cz)) continue;
            if (g.at(cx, cz) == BattleGrid.Terrain.WALL || g.y(cx, cz) >= h + 2) return true;
        }
        return false;
    }
}
