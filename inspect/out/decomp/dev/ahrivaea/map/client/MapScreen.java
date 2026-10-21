package dev.ahrivaea.map.client;

import dev.ahrivaea.map.Markers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class MapScreen extends Screen {
   public static final float MIN_SCALE = 0.125F;
   public static final float MAX_SCALE = 8.0F;
   private static final int TOP = 24;
   private static final int BOTTOM = 14;
   public double cx;
   public double cz;
   public float scale = 2.0F;
   public boolean follow = true;

   public MapScreen() {
      super(Component.literal("Map"));
   }

   protected void init() {
      this.addRenderableWidget(Button.builder(minimapLabel(), b -> {
         MapClient.setMinimap(!MapClient.minimap);
         b.setMessage(minimapLabel());
      }).bounds(this.width - 254, 2, 90, 20).build());
      this.addRenderableWidget(Button.builder(Component.literal("Centre on me"), b -> this.follow = true).bounds(this.width - 160, 2, 90, 20).build());
      this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose()).bounds(this.width - 66, 2, 60, 20).build());
   }

   private static Component minimapLabel() {
      return Component.literal("Minimap: " + (MapClient.minimap ? "On" : "Off"));
   }

   public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
      g.fill(0, 0, this.width, this.height, -15987696);
   }

   public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
      if (this.minecraft.player != null && this.minecraft.level != null) {
         if (this.follow) {
            this.cx = this.minecraft.player.getX();
            this.cz = this.minecraft.player.getZ();
         }

         MapView.Hover hover = new MapView.Hover();
         MapView.draw(g, 0, 24, this.width, this.height - 24 - 14, this.cx, this.cz, this.scale, true, mouseX, mouseY, hover);
         super.extractRenderState(g, mouseX, mouseY, partialTick);
         g.text(this.font, "Map: " + dimensionName(this.minecraft.level.dimension().identifier().toString()), 6, 8, -12224);
         String info;
         if (mouseY >= 24 && mouseY < this.height - 14) {
            int bx = (int)Math.floor(this.cx + ((double)mouseX - (double)this.width / 2.0) / (double)this.scale);
            int bz = (int)Math.floor(this.cz + ((double)mouseY - (24.0 + (double)(this.height - 24 - 14) / 2.0)) / (double)this.scale);
            info = "x " + bx + ", z " + bz;
         } else {
            info = "";
         }

         info = info
            + (info.isEmpty() ? "" : "   ")
            + (this.scale >= 1.0F ? "1 block = " + (int)this.scale + " px" : (int)(1.0F / this.scale) + " blocks = 1 px")
            + "   Scroll to zoom, drag to move";
         g.text(this.font, info, 6, this.height - 14 + 3, -5592406);
         List<String> elsewhere = new ArrayList<>();
         String here = this.minecraft.level.dimension().identifier().toString();
         if (MapClient.markersFresh()) {
            for (Markers.PlayerMark p : MapClient.markers.players()) {
               if (!p.dimension().equals(here)) {
                  elsewhere.add(p.name() + " (" + dimensionName(p.dimension()) + ")");
               }
            }
         }

         if (!elsewhere.isEmpty()) {
            String s = "Elsewhere: " + String.join(", ", elsewhere);
            g.text(this.font, s, this.width - 6 - this.font.width(s), this.height - 14 + 3, -6305537);
         }

         if (!hover.lines.isEmpty()) {
            g.setComponentTooltipForNextFrame(this.font, hover.lines, mouseX, mouseY);
         }
      }
   }

   static String dimensionName(String id) {
      return switch (id) {
         case "minecraft:overworld" -> "Overworld";
         case "minecraft:the_nether" -> "Nether";
         case "minecraft:the_end" -> "The End";
         default -> {
            String path = id.substring(id.indexOf(58) + 1).replace('_', ' ');
            yield path.isEmpty() ? id : Character.toUpperCase(path.charAt(0)) + path.substring(1);
         }
      };
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
      if (dy == 0.0) {
         return false;
      } else {
         float next = Math.clamp(dy > 0.0 ? this.scale * 2.0F : this.scale / 2.0F, 0.125F, 8.0F);
         if (next == this.scale) {
            return true;
         } else {
            double midY = 24.0 + (double)(this.height - 24 - 14) / 2.0;
            double wx = this.cx + (mouseX - (double)this.width / 2.0) / (double)this.scale;
            double wz = this.cz + (mouseY - midY) / (double)this.scale;
            this.cx = wx - (mouseX - (double)this.width / 2.0) / (double)next;
            this.cz = wz - (mouseY - midY) / (double)next;
            this.scale = next;
            this.follow = false;
            return true;
         }
      }
   }

   public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
      if (super.mouseDragged(event, dx, dy)) {
         return true;
      } else if (event.button() == 0 && !(event.y() < 24.0)) {
         this.cx = this.cx - dx / (double)this.scale;
         this.cz = this.cz - dy / (double)this.scale;
         this.follow = false;
         return true;
      } else {
         return false;
      }
   }

   public boolean keyPressed(KeyEvent event) {
      if (MapClient.OPEN.matches(event)) {
         this.onClose();
         return true;
      } else {
         return super.keyPressed(event);
      }
   }

   public boolean isPauseScreen() {
      return false;
   }
}
