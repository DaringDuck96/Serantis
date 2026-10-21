package dev.ahrivaea.map.client;

import dev.ahrivaea.map.Markers;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.PlayerSkin;

public final class MapView {
   public static final int MINIMAP_SIZE = 100;
   public static final int BACKGROUND = -15395300;

   private MapView() {
   }

   public static void draw(
      GuiGraphicsExtractor g, int x0, int y0, int w, int h, double cx, double cz, float scale, boolean big, int mouseX, int mouseY, MapView.Hover hover
   ) {
      Minecraft mc = Minecraft.getInstance();
      g.enableScissor(x0, y0, x0 + w, y0 + h);
      g.fill(x0, y0, x0 + w, y0 + h, -15395300);
      float midX = (float)x0 + (float)w / 2.0F;
      float midY = (float)y0 + (float)h / 2.0F;
      float originX = (float)Math.floor((double)midX - cx * (double)scale);
      float originY = (float)Math.floor((double)midY - cz * (double)scale);
      double bx0 = cx - (double)w / 2.0 / (double)scale;
      double bx1 = cx + (double)w / 2.0 / (double)scale;
      double bz0 = cz - (double)h / 2.0 / (double)scale;
      double bz1 = cz + (double)h / 2.0 / (double)scale;
      int s = 256;

      for (int tx = Math.floorDiv((int)Math.floor(bx0), s); tx <= Math.floorDiv((int)Math.floor(bx1), s); tx++) {
         for (int tz = Math.floorDiv((int)Math.floor(bz0), s); tz <= Math.floorDiv((int)Math.floor(bz1), s); tz++) {
            MapTiles.Tile t = MapTiles.tile(tx, tz, false);
            if (t != null) {
               MapTiles.ready(t);
               g.pose().pushMatrix();
               g.pose().translate(originX + (float)(tx * s) * scale, originY + (float)(tz * s) * scale);
               g.pose().scale(scale, scale);
               g.blit(RenderPipelines.GUI_TEXTURED, t.id, 0, 0, 0.0F, 0.0F, s, s, s, s);
               g.pose().popMatrix();
            }
         }
      }

      String dim = mc.level.dimension().identifier().toString();
      Markers.Payload m = MapClient.markersFresh() ? MapClient.markers : new Markers.Payload(List.of(), List.of());
      int dot = big ? 5 : 3;

      for (Markers.CreatureMark c : m.creatures()) {
         int sx = Math.round(midX + (float)(((double)c.x() + 0.5 - cx) * (double)scale));
         int sy = Math.round(midY + (float)(((double)c.z() + 0.5 - cz) * (double)scale));
         if (sx >= x0 && sx < x0 + w && sy >= y0 && sy < y0 + h) {
            int rim = c.kind() == 1 ? -1 : (c.kind() == 2 ? -11702 : -16777216);
            int lo = dot / 2;
            g.fill(sx - lo - 1, sy - lo - 1, sx - lo + dot + 1, sy - lo + dot + 1, rim);
            g.fill(sx - lo, sy - lo, sx - lo + dot, sy - lo + dot, 0xFF000000 | c.color());
            if (hover != null && Math.abs(mouseX - sx) <= lo + 1 && Math.abs(mouseY - sy) <= lo + 1) {
               hover.lines.add(Component.literal(c.name()).withColor(c.color()));
               hover.lines.add(Component.literal(c.kind() == 1 ? "Yours" : (c.kind() == 2 ? "Someone else's" : "Wild")).withColor(11184810));
            }
         }
      }

      int face = big ? 10 : 8;
      UUID me = mc.player.getUUID();

      for (Markers.PlayerMark p : m.players()) {
         if (!p.id().equals(me) && p.dimension().equals(dim)) {
            float fx = midX + (float)(((double)p.x() + 0.5 - cx) * (double)scale);
            float fy = midY + (float)(((double)p.z() + 0.5 - cz) * (double)scale);
            boolean off = fx < (float)x0 || fx >= (float)(x0 + w) || fy < (float)y0 || fy >= (float)(y0 + h);
            if (!off || !big) {
               int sx = Math.round(Math.clamp(fx, (float)x0 + (float)face / 2.0F + 1.0F, (float)(x0 + w) - (float)face / 2.0F - 1.0F));
               int sy = Math.round(Math.clamp(fy, (float)y0 + (float)face / 2.0F + 1.0F, (float)(y0 + h) - (float)face / 2.0F - 1.0F));
               g.fill(sx - face / 2 - 1, sy - face / 2 - 1, sx - face / 2 + face + 1, sy - face / 2 + face + 1, off ? -7829368 : -1);
               PlayerFaceExtractor.extractRenderState(g, skin(mc, p.id()), sx - face / 2, sy - face / 2, face);
               if (big) {
                  g.centeredText(mc.font, p.name(), sx, sy + face / 2 + 2, -1);
               }

               if (hover != null && Math.abs(mouseX - sx) <= face / 2 + 1 && Math.abs(mouseY - sy) <= face / 2 + 1) {
                  hover.lines.add(Component.literal(p.name()));
                  hover.lines.add(Component.literal(p.x() + ", " + p.y() + ", " + p.z()).withColor(11184810));
               }
            }
         }
      }

      float px = midX + (float)((mc.player.getX() - cx) * (double)scale);
      float py = midY + (float)((mc.player.getZ() - cz) * (double)scale);
      if (px >= (float)x0 && px < (float)(x0 + w) && py >= (float)y0 && py < (float)(y0 + h)) {
         arrow(g, px, py, mc.player.getYRot(), big ? 1.4F : 1.0F);
      }

      g.disableScissor();
   }

   private static PlayerSkin skin(Minecraft mc, UUID id) {
      PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(id);
      return info != null ? info.getSkin() : DefaultPlayerSkin.get(id);
   }

   static void arrow(GuiGraphicsExtractor g, float x, float y, float yaw, float size) {
      g.pose().pushMatrix();
      g.pose().translate(x, y);
      g.pose().rotate((float)Math.toRadians((double)(yaw - 180.0F)));
      g.pose().scale(size, size);

      for (int i = 0; i < 8; i++) {
         int half = i / 2 + 1;
         g.fill(-half, -5 + i, half + 1, -3 + i, -16777216);
      }

      for (int i = 0; i < 6; i++) {
         int half = i / 2;
         g.fill(-half, -4 + i, half + 1, -3 + i, i < 2 ? -1 : -49088);
      }

      g.pose().popMatrix();
   }

   public static void minimap(GuiGraphicsExtractor g, Minecraft mc) {
      boolean good = false;
      boolean bad = false;

      for (MobEffectInstance e : mc.player.getActiveEffects()) {
         if (e.showIcon()) {
            if (((MobEffect)e.getEffect().value()).isBeneficial()) {
               good = true;
            } else {
               bad = true;
            }
         }
      }

      int size = 100;
      int x = g.guiWidth() - size - 4;
      int y = 4 + (bad ? 52 : (good ? 26 : 0));
      g.fill(x - 1, y - 1, x + size + 1, y + size + 1, -10855834);
      draw(g, x, y, size, size, mc.player.getX(), mc.player.getZ(), 1.0F, false, 0, 0, null);
      g.centeredText(mc.font, "N", x + size / 2, y + 2, -1);
      String where = mc.player.getBlockX() + ", " + mc.player.getBlockY() + ", " + mc.player.getBlockZ();
      g.centeredText(mc.font, where, x + size / 2, y + size + 3, -1);
   }

   public static final class Hover {
      public final List<Component> lines = new ArrayList<>();
   }
}
