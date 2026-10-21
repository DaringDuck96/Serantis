package dev.ahrivaea.client;

import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Mounts;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.world.phys.Vec3;

public final class MountHud {
   public static double currentSpeed;
   private static Vec3 lastPos;

   private MountHud() {
   }

   static int hudTop(Minecraft mc, int guiHeight) {
      return mc.getDebugOverlay().showDebugScreen() ? guiHeight / 4 : 6;
   }

   public static void register() {
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)mc -> {
         if (mc.player != null && mc.player.getVehicle() instanceof CreatureEntity c) {
            Vec3 now = c.position();
            double step = lastPos == null ? 0.0 : now.distanceTo(lastPos) * 20.0;
            currentSpeed = currentSpeed + (Math.min(step, 150.0) - currentSpeed) * 0.2;
            lastPos = now;
         } else {
            lastPos = null;
            currentSpeed = 0.0;
         }
      });
      HudElementRegistry.attachElementAfter(
         VanillaHudElements.CROSSHAIR,
         Ahrivaea.id("mount_stats"),
         (g, delta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && !mc.gui.hud.isHidden() && mc.player.getVehicle() instanceof CreatureEntity c) {
               List<MountHud.Line> lines = new ArrayList<>();
               lines.add(new MountHud.Line(c.getCustomName() != null ? c.getCustomName().getString() : "Creature", -171));
               lines.add(new MountHud.Line("Speed: " + String.format("%.1f", c.getTopSpeed()) + " blocks/sec", -1));
               lines.add(new MountHud.Line("  now " + String.format("%.1f", currentSpeed), -5592406));
               int tier = c.getRideTier();
               lines.add(
                  new MountHud.Line(
                     "Tier "
                        + tier
                        + " (x"
                        + Mounts.MULTIPLIER[Math.clamp((long)tier, 0, Mounts.MAX_TIER)]
                        + ")"
                        + (c.ridesFlying() ? ", flies" : (c.ridesSwimming() ? ", swims" : "")),
                     -11649
                  )
               );
               if (c.ridesFlying()) {
                  lines.add(new MountHud.Line("Jump: take off / rise. Look where to fly.", -5592406));
               } else if (c.ridesSwimming()) {
                  lines.add(new MountHud.Line("In water: look where to swim, jump to rise.", -5592406));
               }

               Mounts.Step step = Mounts.stepFrom(c.getMountTier());
               if (step != null) {
                  lines.add(
                     new MountHud.Line(
                        "Next tier: " + c.getMountFed() + "/" + step.amount() + " " + step.treat().getDefaultInstance().getHoverName().getString(), -6303489
                     )
                  );
               } else if (tier < Mounts.MAX_TIER) {
                  lines.add(new MountHud.Line("Tier " + (tier + 1) + ": Speed " + (tier < 4 ? 60 : 80) + " (levels raise it)", -6303489));
               }

               Font font = mc.font;
               int w = 0;

               for (MountHud.Line l : lines) {
                  w = Math.max(w, font.width(l.text()));
               }

               int x = 6;
               int y = hudTop(mc, g.guiHeight());
               g.fill(x - 3, y - 3, x + w + 3, y + lines.size() * 10 + 1, -1342177280);

               for (int i = 0; i < lines.size(); i++) {
                  g.text(font, lines.get(i).text(), x, y + i * 10, lines.get(i).color());
               }
            }
         }
      );
   }

   private static record Line(String text, int color) {
   }
}
