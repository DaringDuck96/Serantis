package dev.ahrivaea.battle;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

public final class Pathing {
   private Pathing() {
   }

   public static Map<Integer, Integer> reachable(BattleGrid g, int sx, int sz, int squares, boolean flies, boolean lavaOk, Pathing.Occupancy occ) {
      Map<Integer, Integer> cost = new HashMap<>();
      ArrayDeque<int[]> q = new ArrayDeque<>();
      cost.put(sx + sz * g.size, 0);
      q.add(new int[]{sx, sz});
      int[][] dirs = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

      while (!q.isEmpty()) {
         int[] c = q.poll();
         int here = cost.get(c[0] + c[1] * g.size);
         if (here < squares) {
            for (int[] d : dirs) {
               int nx = c[0] + d[0];
               int nz = c[1] + d[1];
               if (g.inside(nx, nz) && !cost.containsKey(nx + nz * g.size) && canStep(g, c[0], c[1], nx, nz, flies, lavaOk) && occ.at(nx, nz) != 2) {
                  cost.put(nx + nz * g.size, here + 1);
                  q.add(new int[]{nx, nz});
               }
            }
         }
      }

      return cost;
   }

   public static boolean canStep(BattleGrid g, int x, int z, int nx, int nz, boolean flies, boolean lavaOk) {
      BattleGrid.Terrain t = g.at(nx, nz);
      if (flies) {
         return true;
      } else if (t == BattleGrid.Terrain.WALL || t == BattleGrid.Terrain.DEEP_WATER) {
         return false;
      } else if (t == BattleGrid.Terrain.LAVA && !lavaOk) {
         return false;
      } else {
         int dy = g.y(nx, nz) - g.y(x, z);
         return dy <= 1 && dy >= -3;
      }
   }

   public static boolean canStop(BattleGrid g, int x, int z, boolean flies, boolean lavaOk, Pathing.Occupancy occ, int selfX, int selfZ) {
      if ((x != selfX || z != selfZ) && occ.at(x, z) != 0) {
         return false;
      } else {
         BattleGrid.Terrain t = g.at(x, z);
         if (t == BattleGrid.Terrain.WALL) {
            return false;
         } else if (t == BattleGrid.Terrain.LAVA) {
            return lavaOk;
         } else {
            return t == BattleGrid.Terrain.DEEP_WATER ? flies : true;
         }
      }
   }

   public static int distance(int x1, int z1, int x2, int z2) {
      return Math.abs(x1 - x2) + Math.abs(z1 - z2);
   }

   public interface Occupancy {
      int at(int var1, int var2);
   }
}
