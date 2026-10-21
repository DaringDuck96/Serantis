package dev.ahrivaea.client.battle;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ahrivaea.battle.BattleGrid;
import dev.ahrivaea.battle.Battles;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;
import net.minecraft.client.Minecraft;

public final class BattleClient {
   private static JsonObject state;
   private static BattleGrid grid;
   private static double camX;
   private static double camY;
   private static double camZ;
   private static boolean hudWasHidden;
   private static int maxHeight;
   private static double homeY;

   private BattleClient() {
   }

   public static void init() {
      ClientPlayNetworking.registerGlobalReceiver(Battles.StatePayload.TYPE, (payload, ctx) -> receive(ctx.client(), payload.json()));
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)mc -> {
         if (state != null && mc.level != null && mc.player != null && mc.gui.screen() == null) {
            mc.gui.setScreen(new BattleScreen());
         }
      });
      ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, mc) -> {
         state = null;
         grid = null;
      });
   }

   public static boolean active() {
      return state != null;
   }

   public static JsonObject state() {
      return state;
   }

   public static BattleGrid grid() {
      return grid;
   }

   public static double camX() {
      return camX;
   }

   public static double camY() {
      return camY;
   }

   static double minHeight() {
      return (double)(maxHeight + 5);
   }

   static double maxHeightAbove() {
      return (double)(maxHeight + 6) + (grid == null ? 23.0 : (double)grid.size * 1.2);
   }

   public static void zoomAt(double steps, double wx, double wz) {
      double before = camY;
      camY = Math.clamp(camY * Math.pow(0.88, steps) + (1.0 - Math.pow(0.88, steps)) * (double)maxHeight, minHeight(), maxHeightAbove());
      double k = (before - camY) / Math.max(1.0, before - (double)maxHeight);
      pan((wx - camX) * k, (wz - camZ) * k);
   }

   public static void zoom(double steps) {
      zoomAt(steps, camX, camZ);
   }

   public static void pan(double dx, double dz) {
      if (grid != null) {
         camX = Math.clamp(camX + dx, (double)(grid.originX - 2), (double)(grid.originX + grid.size + 2));
         camZ = Math.clamp(camZ + dz, (double)(grid.originZ - 2), (double)(grid.originZ + grid.size + 2));
      }
   }

   public static void centerOn(int x, int z) {
      if (grid != null) {
         camX = (double)(grid.originX + x) + 0.5;
         camZ = (double)(grid.originZ + z) + 0.5;
      }
   }

   public static void resetView() {
      if (grid != null) {
         camX = (double)grid.originX + (double)grid.size / 2.0;
         camZ = (double)grid.originZ + (double)grid.size / 2.0;
         camY = homeY;
      }
   }

   public static double camZ() {
      return camZ;
   }

   static void receive(Minecraft mc, String json) {
      JsonObject o = JsonParser.parseString(json).getAsJsonObject();
      if (!o.has("grid")) {
         end(mc);
      } else {
         boolean starting = state == null;
         state = o;
         if (starting) {
            JsonObject g = o.getAsJsonObject("grid");
            int size = g.get("size").getAsInt();
            int[] h = new int[size * size];
            BattleGrid.Terrain[] t = new BattleGrid.Terrain[size * size];
            String ts = g.get("t").getAsString();
            int max = Integer.MIN_VALUE;

            for (int i = 0; i < h.length; i++) {
               h[i] = g.getAsJsonArray("h").get(i).getAsInt();
               t[i] = BattleGrid.Terrain.of(ts.charAt(i));
               max = Math.max(max, h[i]);
            }

            grid = new BattleGrid(size, g.get("ox").getAsInt(), g.get("oz").getAsInt(), h, t);
            camX = (double)grid.originX + (double)size / 2.0;
            camZ = (double)grid.originZ + (double)size / 2.0;
            camY = (double)(max + Math.round((float)size * 0.7F));
            homeY = camY;
            maxHeight = max;
            hudWasHidden = mc.gui.hud.isHidden();
            if (!hudWasHidden) {
               mc.gui.hud.toggle();
            }
         }

         if (!(mc.gui.screen() instanceof BattleScreen)) {
            mc.gui.setScreen(new BattleScreen());
         } else {
            ((BattleScreen)mc.gui.screen()).refresh();
         }
      }
   }

   static void end(Minecraft mc) {
      if (state != null) {
         state = null;
         grid = null;
         if (mc.gui.hud.isHidden() != hudWasHidden) {
            mc.gui.hud.toggle();
         }

         if (mc.gui.screen() instanceof BattleScreen) {
            mc.gui.setScreen(null);
         }
      }
   }

   public static void send(JsonObject action) {
      ClientPlayNetworking.send(new Battles.ActionPayload(action.toString()));
   }
}
