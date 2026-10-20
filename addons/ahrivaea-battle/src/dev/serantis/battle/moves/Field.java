package dev.serantis.battle.moves;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.ahrivaea.battle.Battle;
import dev.ahrivaea.battle.BattleGrid;
import dev.ahrivaea.battle.BattleMove;
import dev.ahrivaea.battle.Pathing;
import dev.ahrivaea.battle.Unit;
import dev.ahrivaea.creature.Influence;
import dev.serantis.battle.mixin.BattlesInvoker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * The battlefield's Stage 2 state: terrain zones left by moves, attacks waiting to happen (Charged Shot,
 * Blazing Meteor), and forced movement (pushes, pulls, teleports).
 *
 * Zones affect every creature in them, friend or foe. A zone's turns count down at the start of the
 * turns of the side that made it. Flying creatures pass over ground zones (burning ground, Inferno
 * Ring, Frozen Ground, Sinkhole) but not the others.
 */
public final class Field {
    public static final class Zone {
        public final String kind;           // burning | ring | ice | sink | storm | horizon | night
        public final Unit.Side owner;
        public final Set<Integer> cells;
        public final int damage;            // already scaled to the caster's level
        public int turns;

        Zone(String kind, Unit.Side owner, Set<Integer> cells, int damage, int turns) {
            this.kind = kind; this.owner = owner; this.cells = cells; this.damage = damage; this.turns = turns;
        }

        boolean ground() { return "burning".equals(kind) || "ring".equals(kind) || "ice".equals(kind) || "sink".equals(kind); }
        boolean slows() { return "ice".equals(kind) || "sink".equals(kind); }
    }

    public static final class Pending {
        public final String kind;           // charge | meteor
        public final Unit user, target;
        public final BattleMove move;
        public final MoveSpec spec;
        public final Set<Integer> cells;

        Pending(String kind, Unit user, Unit target, BattleMove move, MoveSpec spec, Set<Integer> cells) {
            this.kind = kind; this.user = user; this.target = target; this.move = move; this.spec = spec; this.cells = cells;
        }
    }

    public final List<Zone> zones = new ArrayList<>();
    public final List<Pending> pending = new ArrayList<>();

    /** Recent tile effects for the battle screen (see Fx): {id, cell, kind, game tick, unit id}. */
    private final List<long[]> fx = new ArrayList<>();
    private final int fxBattle = new java.util.Random().nextInt(Integer.MAX_VALUE);
    private int fxNext = 1;
    private static final int FX_KEEP_TICKS = 60;

    public void fx(Battle b, Unit u, int kind) {
        long now = b.level.getGameTime();
        fx.removeIf(e -> now - e[3] > FX_KEEP_TICKS);
        if (kind != dev.serantis.battle.fx.Fx.ATTACK) {
            for (long[] e : fx) {
                if (e[3] != now || e[4] != u.id || e[2] == dev.serantis.battle.fx.Fx.ATTACK) continue;
                if (e[2] >= kind) return;           // something more important already showed this moment
                e[0] = fxNext++; e[1] = idx(b, u.x, u.z); e[2] = kind; e[5] = Body.size(u);
                return;
            }
        }
        fx.add(new long[]{fxNext++, idx(b, u.x, u.z), kind, now, u.id, Body.size(u)});
    }

    public static Field of(Battle b) { return ((BattleAccess) (Object) b).serantis$field(); }
    private static BattleAccess acc(Battle b) { return (BattleAccess) (Object) b; }
    static boolean lava(Unit u) { return u.influence == Influence.FIRE; }
    private static int idx(Battle b, int x, int z) { return x + z * b.grid.size; }

    // ------------------------------------------------------------------ zones

    public static String zoneName(String kind) {
        return switch (kind) {
            case "burning" -> "the burning ground"; case "ring" -> "the Inferno Ring"; case "ice" -> "the Frozen Ground";
            case "sink" -> "the Sinkhole"; case "storm" -> "the Storm Cloud"; case "horizon" -> "the Event Horizon";
            case "night" -> "the Eternal Night"; default -> kind;
        };
    }

    /** Cells of a zone centred on (cx, cz): "square" (radius r, 3x3 for r = 1), "radius" (diamond) or "ring" (just outside radius r). */
    public static Set<Integer> cells(Battle b, int cx, int cz, String shape, int r) {
        Set<Integer> out = new HashSet<>();
        BattleGrid g = b.grid;
        int reach = "ring".equals(shape) ? r + 1 : r;
        for (int x = cx - reach; x <= cx + reach; x++)
            for (int z = cz - reach; z <= cz + reach; z++) {
                if (!g.inside(x, z) || g.at(x, z) == BattleGrid.Terrain.WALL) continue;
                int d = Pathing.distance(cx, cz, x, z);
                boolean in = switch (shape) {
                    case "ring" -> d == r + 1;
                    case "radius" -> d <= r;
                    default -> true;
                };
                if (in) out.add(idx(b, x, z));
            }
        return out;
    }

    public void addZone(Battle b, Unit caster, String kind, Set<Integer> cells, int damage, int turns) {
        if (cells.isEmpty()) return;
        zones.add(new Zone(kind, caster.side, cells, damage, turns));
        String name = zoneName(kind);
        acc(b).serantis$say(Character.toUpperCase(name.charAt(0)) + name.substring(1) + " covers " + cells.size()
                + " tiles for " + turns + (turns == 1 ? " turn." : " turns."));
    }

    /** True if a cell costs 2 movement for units of this side (Frozen Ground, Sinkhole). */
    public boolean slows(Unit.Side side, int cell) {
        for (Zone z : zones) if (z.slows() && z.cells.contains(cell)) return true;
        return false;
    }

    public boolean slowsAny(Unit.Side side) {
        for (Zone z : zones) if (z.slows()) return true;
        return false;
    }

    /** True if any tile of the unit's body is among these cells. */
    public static boolean inside(Battle b, Unit u, Set<Integer> cells) {
        for (int c : Body.cells(b, u)) if (cells.contains(c)) return true;
        return false;
    }

    /** Standing in a Sinkhole: no movement bonuses. */
    public boolean inSink(Battle b, Unit u) {
        if (u.flies()) return false;
        for (Zone z : zones) if ("sink".equals(z.kind) && inside(b, u, z.cells)) return true;
        return false;
    }

    /** How bad it is for this unit to stand with its corner on this cell (for the enemy AI). */
    public double danger(Battle b, Unit u, int cell) {
        double worst = 0;
        for (int c : Body.cells(b.grid, cell % b.grid.size, cell / b.grid.size, Body.size(u))) worst = Math.max(worst, tileDanger(b, u, c));
        return worst;
    }

    private double tileDanger(Battle b, Unit u, int cell) {
        double d = 0;
        for (Zone z : zones) {
            if (!z.cells.contains(cell) || (z.ground() && u.flies())) continue;
            d += switch (z.kind) {
                case "burning" -> 12; case "ring" -> 6; case "storm" -> 6; case "horizon", "night" -> 9;
                case "sink" -> 3; default -> 2;
            };
        }
        for (Pending p : pending) if ("meteor".equals(p.kind) && p.cells.contains(cell)) d += 40;
        return d;
    }

    /** Burning ground and the Inferno Ring hurt units that step onto them (once per zone per move). */
    public void enter(Battle b, Unit u, List<Integer> path) {
        if (path.isEmpty() || u.flies()) return;
        for (Zone z : new ArrayList<>(zones)) {
            if (!u.alive()) return;
            if (!("burning".equals(z.kind) || "ring".equals(z.kind))) continue;
            for (int c : path) if (z.cells.contains(c)) { hurt(b, u, z.damage, zoneName(z.kind)); break; }
        }
    }

    static void hurt(Battle b, Unit u, int damage, String source) {
        if (!u.alive() || damage <= 0) return;
        u.hp = Math.max(0, u.hp - damage);
        acc(b).serantis$say(u.name + " takes " + damage + " damage from " + source + ".");
        b2fx(b, u);
        if (!u.alive()) acc(b).serantis$knockOut(u);
    }

    private static void b2fx(Battle b, Unit u) { dev.serantis.battle.fx.Fx.flash(b, u, dev.serantis.battle.fx.Fx.DAMAGE); }

    /** Start of a side's turn: its zones tick down, opponents standing on burning ground burn, its pending attacks resolve. */
    public void startTurn(Battle b, Unit.Side side) {
        BattleAccess acc = acc(b);
        for (Zone z : new ArrayList<>(zones)) {
            if (z.owner != side) continue;
            if (--z.turns <= 0) {
                zones.remove(z);
                String name = zoneName(z.kind);
                acc.serantis$say(Character.toUpperCase(name.charAt(0)) + name.substring(1) + " fades.");
            }
        }
        for (Unit u : new ArrayList<>(b.units)) {
            if (u.side != side || !u.alive() || u.flies()) continue;
            for (Zone z : new ArrayList<>(zones))
                if ("burning".equals(z.kind) && inside(b, u, z.cells)) hurt(b, u, z.damage, zoneName(z.kind));
        }
        for (Pending p : new ArrayList<>(pending)) {
            if (p.user.side != side) continue;
            pending.remove(p);
            if (!p.user.alive()) continue;
            UnitState st = MoveEngine.state(p.user);
            if (st.has("stun") || st.has("freeze") || st.has("sleep")) {
                acc.serantis$say(p.user.name + " loses focus. " + p.spec.name + " is lost.");
                continue;
            }
            if ("charge".equals(p.kind)) {
                if (!p.target.alive() || !p.move.reaches(Body.gap(p.user, p.target))) {
                    acc.serantis$say(p.user.name + "'s " + p.spec.name + " has nothing to hit.");
                    continue;
                }
                acc.serantis$say(p.user.name + " releases " + p.spec.name + "!");
                MoveEngine.fire(b, p.user, p.target, p.move);
                if (side == Unit.Side.PLAYER) p.user.acted = true;     // firing takes this turn
                else acc.serantis$enemyQueue().remove(p.user);
            } else {
                acc.serantis$say("The " + p.spec.name + " crashes down!");
                MoveEngine.land(b, p);
            }
        }
    }

    /** End of a side's turn: Storm Cloud, Event Horizon and Eternal Night hit that side's units still inside. */
    public void endTurn(Battle b, Unit.Side side) {
        for (Zone z : new ArrayList<>(zones)) {
            if (z.ground()) continue;
            List<Unit> inside = new ArrayList<>();
            for (Unit u : b.units) if (u.alive() && u.side == side && inside(b, u, z.cells)) inside.add(u);
            if (inside.isEmpty()) continue;
            switch (z.kind) {
                case "storm" -> hurt(b, inside.get(b.random.nextInt(inside.size())), z.damage, "lightning from " + zoneName(z.kind));
                case "horizon" -> { for (Unit u : inside) { hurt(b, u, z.damage, zoneName(z.kind)); if (u.alive()) MoveEngine.slow(b, u); } }
                case "night" -> { for (Unit u : inside) { hurt(b, u, z.damage, zoneName(z.kind)); if (u.alive()) MoveEngine.weaken(b, u, "defense", -2); } }
                default -> { }
            }
        }
    }

    // ------------------------------------------------------------------ pending attacks

    public Pending pendingOf(Unit u) {
        for (Pending p : pending) if (p.user == u) return p;
        return null;
    }

    void addPending(String kind, Unit user, Unit target, BattleMove move, MoveSpec spec, Set<Integer> cells) {
        pending.removeIf(p -> p.user == user);
        pending.add(new Pending(kind, user, target, move, spec, cells));
    }

    /** Interrupts a unit's charged or prepared attack. */
    public boolean cancel(Battle b, Unit u) {
        Pending p = pendingOf(u);
        if (p == null) return false;
        pending.remove(p);
        acc(b).serantis$say(u.name + "'s " + p.spec.name + " is interrupted!");
        return true;
    }

    // ------------------------------------------------------------------ forced movement

    private static void place(Battle b, Unit u) { BattlesInvoker.serantis$placeEntity(b, u); }

    /** The whole body can be shoved one tile this way and stop there. */
    private static boolean open(Battle b, Unit u, int dx, int dz) {
        return Body.stepCost(Body.ctx(b, u), u.x, u.z, Body.size(u), dx, dz, u.flies(), lava(u), Body.swims(u)) >= 0
                && b.canStop(u, u.x + dx, u.z + dz);
    }

    /** Moves a unit up to n tiles in one direction, stopping at anything in the way. Returns the tiles its body moved onto. */
    static List<Integer> slide(Battle b, Unit u, int dx, int dz, int n) {
        List<Integer> path = new ArrayList<>();
        for (int k = 0; k < n; k++) {
            if (!open(b, u, dx, dz)) break;
            u.x += dx; u.z += dz;
            path.addAll(Body.cells(b, u));
        }
        if (!path.isEmpty()) place(b, u);
        return path;
    }

    private static int[] axis(Battle b, int dx, int dz) {
        if (dx == 0 && dz == 0) return null;
        if (Math.abs(dx) > Math.abs(dz) || (Math.abs(dx) == Math.abs(dz) && b.random.nextBoolean())) return new int[]{Integer.signum(dx), 0};
        return new int[]{0, Integer.signum(dz)};
    }

    /** Twice the centre of a unit's body (so it stays a whole number). */
    private static int cx2(Unit u) { return 2 * u.x + Body.size(u) - 1; }
    private static int cz2(Unit u) { return 2 * u.z + Body.size(u) - 1; }

    /** Pushes a unit away from (ox, oz); a unit standing on that cell is pushed away from the attacker instead. */
    public void push(Battle b, Unit a, Unit u, int ox, int oz, int n) {
        if (!u.alive()) return;
        int[] dir = axis(b, cx2(u) - (2 * ox), cz2(u) - (2 * oz));
        if (dir == null) dir = axis(b, cx2(u) - cx2(a), cz2(u) - cz2(a));
        if (dir == null) return;
        List<Integer> path = slide(b, u, dir[0], dir[1], n);
        if (path.isEmpty()) return;
        acc(b).serantis$say(u.name + " is pushed back " + path.size() + (path.size() == 1 ? " tile." : " tiles."));
        enter(b, u, path);
    }

    /** Pulls a unit up to n tiles toward (tx, tz), stopping next to it if stopNext. */
    public void pull(Battle b, Unit u, int tx, int tz, int n, boolean stopNext) {
        if (!u.alive()) return;
        List<Integer> path = new ArrayList<>();
        for (int k = 0; k < n; k++) {
            if (Body.gap(u.x, u.z, Body.size(u), tx, tz, 1) <= (stopNext ? 1 : 0)) break;
            int dx = 2 * tx - cx2(u), dz = 2 * tz - cz2(u);
            int[][] tries = Math.abs(dx) >= Math.abs(dz)
                    ? new int[][]{{Integer.signum(dx), 0}, {0, Integer.signum(dz)}}
                    : new int[][]{{0, Integer.signum(dz)}, {Integer.signum(dx), 0}};
            boolean moved = false;
            for (int[] t : tries) {
                if (t[0] == 0 && t[1] == 0) continue;
                if (open(b, u, t[0], t[1])) {
                    u.x += t[0]; u.z += t[1];
                    path.addAll(Body.cells(b, u));
                    moved = true;
                    break;
                }
            }
            if (!moved) break;
        }
        if (path.isEmpty()) return;
        place(b, u);
        acc(b).serantis$say(u.name + " is pulled " + path.size() + (path.size() == 1 ? " tile." : " tiles."));
        enter(b, u, path);
    }

    /** Teleports a unit to a random open tile n tiles away (or closer, if none is open). */
    public void teleport(Battle b, Unit u, int n) {
        if (!u.alive()) return;
        for (int d = n; d >= 1; d--) {
            List<int[]> spots = new ArrayList<>();
            for (int x = u.x - d; x <= u.x + d; x++)
                for (int z = u.z - d; z <= u.z + d; z++)
                    if (Pathing.distance(u.x, u.z, x, z) == d && b.grid.inside(x, z) && b.canStop(u, x, z)) spots.add(new int[]{x, z});
            if (spots.isEmpty()) continue;
            int[] s = spots.get(b.random.nextInt(spots.size()));
            u.x = s[0]; u.z = s[1];
            place(b, u);
            acc(b).serantis$say(u.name + " is flung through the rift!");
            enter(b, u, Body.cells(b, u));
            return;
        }
    }

    /** Moves a unit 1 tile in a random open direction (Geyser). */
    public void shift(Battle b, Unit u) {
        if (!u.alive()) return;
        List<int[]> dirs = new ArrayList<>(List.of(new int[]{1, 0}, new int[]{-1, 0}, new int[]{0, 1}, new int[]{0, -1}));
        java.util.Collections.shuffle(dirs, new java.util.Random(b.random.nextLong()));
        for (int[] d : dirs) {
            List<Integer> path = slide(b, u, d[0], d[1], 1);
            if (path.isEmpty()) continue;
            acc(b).serantis$say(u.name + " is thrown aside.");
            enter(b, u, path);
            return;
        }
    }

    /** The attacker rushes to an open spot right next to its target (Phantom Charge). */
    public void dash(Battle b, Unit a, Unit t) {
        if (Body.gap(a, t) <= 1) return;
        int an = Body.size(a), tn = Body.size(t);
        int[] best = null;
        for (int x = t.x - an; x <= t.x + tn; x++)
            for (int z = t.z - an; z <= t.z + tn; z++) {
                if (Body.gap(x, z, an, t.x, t.z, tn) != 1 || !b.canStop(a, x, z)) continue;
                if (best == null || Pathing.distance(a.x, a.z, x, z) < Pathing.distance(a.x, a.z, best[0], best[1])) best = new int[]{x, z};
            }
        if (best == null) return;
        a.x = best[0]; a.z = best[1];
        place(b, a);
        acc(b).serantis$say(a.name + " rushes at " + t.name + "!");
        enter(b, a, Body.cells(b, a));
    }

    // ------------------------------------------------------------------ hazards on the way

    /** The tiles a unit's body most likely passed over going from (fx, fz) to (gx, gz), avoiding hazards when it can. */
    public List<Integer> walked(Battle b, Unit u, int fx, int fz, int gx, int gz) {
        int n = Body.size(u);
        List<Integer> corners = Body.route(Body.ctx(b, u), fx, fz, gx, gz, n, u.flies(), lava(u), Body.swims(u),
                cell -> danger(b, u, cell));
        List<Integer> tiles = new ArrayList<>();
        for (int c : corners)
            for (int t : Body.cells(b.grid, c % b.grid.size, c / b.grid.size, n)) if (!tiles.contains(t)) tiles.add(t);
        return tiles;
    }

    // ------------------------------------------------------------------ battle screen

    public void describe(JsonObject view) {
        JsonArray arr = new JsonArray();
        for (Zone z : zones) arr.add(zoneJson(z.kind, z.owner, z.cells, z.turns));
        for (Pending p : pending) if ("meteor".equals(p.kind)) arr.add(zoneJson("meteor", p.user.side, p.cells, 1));
        view.add("serantis_zones", arr);
        JsonArray effects = new JsonArray();
        for (long[] e : fx) {
            JsonObject o = new JsonObject();
            o.addProperty("id", e[0]);
            o.addProperty("cell", e[1]);
            o.addProperty("kind", e[2]);
            o.addProperty("size", e[5]);
            effects.add(o);
        }
        view.add("serantis_fx", effects);
        view.addProperty("serantis_fx_battle", fxBattle);
    }

    private static JsonObject zoneJson(String kind, Unit.Side owner, Set<Integer> cells, int turns) {
        JsonObject o = new JsonObject();
        o.addProperty("kind", kind);
        o.addProperty("owner", owner.name());
        o.addProperty("turns", turns);
        JsonArray c = new JsonArray();
        for (int i : cells) c.add(i);
        o.add("cells", c);
        return o;
    }
}
