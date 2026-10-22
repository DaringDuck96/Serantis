package dev.serantis.battle.moves;

import dev.ahrivaea.battle.Battle;
import dev.ahrivaea.battle.BattleGrid;
import dev.ahrivaea.battle.Pathing;
import dev.ahrivaea.battle.Unit;
import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Species;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.IntToDoubleFunction;

/**
 * Creatures' bodies on the battle grid, and moving them.
 *
 * A creature covers an n x n square of tiles (1x1 small and medium, 2x2 large, 3x3 extra large,
 * 4x4 XX large). Its position (x, z) is the square's corner with the lowest x and z.
 *
 * Moving costs 1 movement per tile, plus 2 per block climbed (going down is free), plus 2 for
 * shallow water and 3 for deep water (nothing extra for swimmers), plus 1 on Frozen Ground or in a
 * Sinkhole. Flying creatures are always in the air: every tile costs them 1.
 *
 * Used by the server and the battle screen alike, so what the screen shows matches what happens.
 */
public final class Body {
    private Body() {}

    /** What pathing needs to know about the field around one unit. */
    public interface Ctx {
        BattleGrid grid();
        /** 0 empty (or the moving unit itself), 1 an ally (can pass, can't stop), 2 an opponent (blocks). */
        int occ(int x, int z);
        /** Frozen Ground or Sinkhole. */
        boolean slow(int cell);
    }

    // ------------------------------------------------------------------ squares and distances

    public static boolean covers(int ux, int uz, int n, int x, int z) {
        return x >= ux && x < ux + n && z >= uz && z < uz + n;
    }

    /** Tiles between two squares (0 if they overlap, 1 if they touch side to side). */
    public static int gap(int ax, int az, int an, int bx, int bz, int bn) {
        int dx = Math.max(0, Math.max(bx - (ax + an - 1), ax - (bx + bn - 1)));
        int dz = Math.max(0, Math.max(bz - (az + an - 1), az - (bz + bn - 1)));
        return dx + dz;
    }

    /** Chebyshev version of gap: the largest of the x and z gaps (for 3x3 "square" areas). */
    public static int squareGap(int ax, int az, int an, int bx, int bz, int bn) {
        int dx = Math.max(0, Math.max(bx - (ax + an - 1), ax - (bx + bn - 1)));
        int dz = Math.max(0, Math.max(bz - (az + an - 1), az - (bz + bn - 1)));
        return Math.max(dx, dz);
    }

    public static List<Integer> cells(BattleGrid g, int x, int z, int n) {
        List<Integer> out = new ArrayList<>(n * n);
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                if (g.inside(x + i, z + j)) out.add((x + i) + (z + j) * g.size);
        return out;
    }

    // ------------------------------------------------------------------ units on the server

    public static int size(Unit u) { return u == null ? 1 : Math.max(1, MoveEngine.state(u).size); }
    public static boolean swims(Unit u) { return u.movement == Species.Movement.SWIM; }
    public static boolean lava(Unit u) { return u.influence == Influence.FIRE; }

    public static int gap(Unit a, Unit b) { return gap(a.x, a.z, size(a), b.x, b.z, size(b)); }
    public static List<Integer> cells(Battle b, Unit u) { return cells(b.grid, u.x, u.z, size(u)); }

    public static boolean covers(Battle b, Unit u, int cell) {
        return covers(u.x, u.z, size(u), cell % b.grid.size, cell / b.grid.size);
    }

    /** The living unit whose body covers a tile. */
    public static Unit unitAt(Battle b, int x, int z) {
        for (Unit u : b.units) if (u.alive() && covers(u.x, u.z, size(u), x, z)) return u;
        return null;
    }

    /**
     * Distance between two points the creatures mod asks about. When they are the corners of living
     * units, it's the distance between their bodies; otherwise between the tiles.
     */
    /**
     * Attack reach between two bodies. Straight lines count tile by tile; diagonals count as the straight-line distance,
     * rounded (3 across and 3 down is 4, not 6), so ranged attacks reach about as far diagonally as straight. A tile
     * touching only at a corner counts as 2, so melee (reach 1) still needs a side-by-side neighbour.
     */
    public static int range(int ax, int az, int an, int bx, int bz, int bn) {
        int dx = Math.max(0, Math.max(bx - (ax + an - 1), ax - (bx + bn - 1)));
        int dz = Math.max(0, Math.max(bz - (az + an - 1), az - (bz + bn - 1)));
        if (dx == 0 || dz == 0) return dx + dz;
        if (dx == 1 && dz == 1) return 2;
        return (int) Math.round(Math.sqrt(dx * dx + dz * dz));
    }

    public static int range(Unit a, Unit b) { return range(a.x, a.z, size(a), b.x, b.z, size(b)); }

    public static int distance(Battle b, int x1, int z1, int x2, int z2) {
        Unit a = null, c = null;
        for (Unit u : b.units) {
            if (!u.alive()) continue;
            if (a == null && u.x == x1 && u.z == z1) a = u;
            if (c == null && u.x == x2 && u.z == z2) c = u;
        }
        return range(x1, z1, size(a), x2, z2, size(c));
    }

    /** Height of the highest tile under a unit. */
    public static int height(Battle b, Unit u) {
        int h = Integer.MIN_VALUE, n = size(u);
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                if (b.grid.inside(u.x + i, u.z + j)) h = Math.max(h, b.grid.y(u.x + i, u.z + j));
        return h == Integer.MIN_VALUE ? b.grid.y(u.x, u.z) : h;
    }

    public static Ctx ctx(Battle b, Unit u) {
        Pathing.Occupancy occ = b.occupancy(u);
        Field field = Field.of(b);
        return new Ctx() {
            public BattleGrid grid() { return b.grid; }
            public int occ(int x, int z) { return occ.at(x, z); }
            public boolean slow(int cell) { return field.slows(u.side, cell); }
        };
    }

    /** A pathing context from plain parts (the battle screen builds one from what the server sent). */
    public static Ctx ctx(BattleGrid grid, Pathing.Occupancy occ, java.util.function.IntPredicate slow) {
        return new Ctx() {
            public BattleGrid grid() { return grid; }
            public int occ(int x, int z) { return occ.at(x, z); }
            public boolean slow(int cell) { return slow.test(cell); }
        };
    }

    public static boolean canStop(Battle b, Unit u, int x, int z) {
        return canStop(ctx(b, u), x, z, size(u), u.flies(), lava(u));
    }

    public static Map<Integer, Integer> reach(Battle b, Unit u, int budget) {
        return reach(ctx(b, u), u.x, u.z, size(u), budget, u.flies(), lava(u), swims(u));
    }

    // ------------------------------------------------------------------ the rules

    /** Every tile of the body is on the field, can be stood on, and has no one else on it. */
    public static boolean canStop(Ctx c, int x, int z, int n, boolean flies, boolean lava) {
        BattleGrid g = c.grid();
        Pathing.Occupancy occ = c::occ;
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++) {
                int cx = x + i, cz = z + j;
                if (!g.inside(cx, cz) || !Pathing.canStop(g, cx, cz, flies, lava, occ, Integer.MIN_VALUE, Integer.MIN_VALUE)) return false;
            }
        return true;
    }

    /** Movement it costs to step onto a tile from the one next to it. */
    public static int tileCost(Ctx c, int fx, int fz, int tx, int tz, boolean flies, boolean swims) {
        if (flies) return 1;
        BattleGrid g = c.grid();
        int cost = 1 + 2 * Math.max(0, g.y(tx, tz) - g.y(fx, fz));
        BattleGrid.Terrain t = g.at(tx, tz);
        if (!swims && t == BattleGrid.Terrain.WATER) cost += 2;
        if (!swims && t == BattleGrid.Terrain.DEEP_WATER) cost += 3;
        if (c.slow(tx + tz * g.size)) cost += 1;
        return cost;
    }

    /**
     * Cost of moving a body one tile in direction (dx, dz), or -1 if it can't. Only the tiles the body
     * moves onto matter: each must be reachable from the tile behind it and not hold an opponent.
     */
    public static int stepCost(Ctx c, int x, int z, int n, int dx, int dz, boolean flies, boolean lava, boolean swims) {
        BattleGrid g = c.grid();
        int worst = 0;
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++) {
                int nx = x + dx + i, nz = z + dz + j;
                if (covers(x, z, n, nx, nz)) continue;              // already under the body
                if (!g.inside(nx, nz)) return -1;
                int fx = nx - dx, fz = nz - dz;
                if (!Pathing.canStep(g, fx, fz, nx, nz, flies, lava) || c.occ(nx, nz) == 2) return -1;
                worst = Math.max(worst, tileCost(c, fx, fz, nx, nz, flies, swims));
            }
        return worst;
    }

    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** Every corner position the body can move to within its movement, with what it costs. */
    public static Map<Integer, Integer> reach(Ctx c, int sx, int sz, int n, int budget, boolean flies, boolean lava, boolean swims) {
        BattleGrid g = c.grid();
        Map<Integer, Integer> best = new HashMap<>();
        PriorityQueue<int[]> q = new PriorityQueue<>(Comparator.comparingInt(a -> a[2]));
        best.put(sx + sz * g.size, 0);
        q.add(new int[]{sx, sz, 0});
        while (!q.isEmpty()) {
            int[] p = q.poll();
            if (p[2] > best.get(p[0] + p[1] * g.size) || p[2] >= budget) continue;
            for (int[] d : DIRS) {
                int step = stepCost(c, p[0], p[1], n, d[0], d[1], flies, lava, swims);
                if (step < 0) continue;
                int cost = p[2] + step;
                if (cost > budget) continue;
                int key = (p[0] + d[0]) + (p[1] + d[1]) * g.size;
                Integer old = best.get(key);
                if (old != null && old <= cost) continue;
                best.put(key, cost);
                q.add(new int[]{p[0] + d[0], p[1] + d[1], cost});
            }
        }
        return best;
    }

    /**
     * The corner positions a body most likely passed through from one place to another (cheapest
     * route, then the one with the least "avoid" along it). Excludes the start.
     */
    public static List<Integer> route(Ctx c, int sx, int sz, int tx, int tz, int n, boolean flies, boolean lava, boolean swims,
                                      IntToDoubleFunction avoid) {
        BattleGrid g = c.grid();
        int start = sx + sz * g.size, goal = tx + tz * g.size;
        if (start == goal) return List.of();
        Map<Integer, Double> dist = new HashMap<>();
        Map<Integer, Integer> from = new HashMap<>();
        PriorityQueue<double[]> q = new PriorityQueue<>(Comparator.comparingDouble(a -> a[1]));
        dist.put(start, 0.0);
        q.add(new double[]{start, 0});
        while (!q.isEmpty()) {
            double[] p = q.poll();
            int at = (int) p[0];
            if (p[1] > dist.get(at)) continue;
            if (at == goal) break;
            int x = at % g.size, z = at / g.size;
            for (int[] d : DIRS) {
                int step = stepCost(c, x, z, n, d[0], d[1], flies, lava, swims);
                if (step < 0) continue;
                int next = (x + d[0]) + (z + d[1]) * g.size;
                double cost = p[1] + step * 1000 + avoid.applyAsDouble(next);
                Double old = dist.get(next);
                if (old != null && old <= cost) continue;
                dist.put(next, cost);
                from.put(next, at);
                q.add(new double[]{next, cost});
            }
        }
        if (!from.containsKey(goal)) return List.of(goal);
        List<Integer> path = new ArrayList<>();
        for (int at = goal; at != start; at = from.get(at)) path.add(0, at);
        return path;
    }

    /** The nearest corner position (to x, z) where a body of size n fits, or null. */
    public static int[] nearestFit(Ctx c, int x, int z, int n, boolean flies, boolean lava) {
        BattleGrid g = c.grid();
        for (int r = 0; r < 2 * g.size; r++)
            for (int dx = -r; dx <= r; dx++) {
                int rest = r - Math.abs(dx);
                for (int dz : rest == 0 ? new int[]{0} : new int[]{-rest, rest})
                    if (canStop(c, x + dx, z + dz, n, flies, lava)) return new int[]{x + dx, z + dz};
            }
        return null;
    }
}
