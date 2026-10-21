package dev.ahrivaea.horses.client;

import dev.ahrivaea.horses.HorseSpeed;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;

public final class HorseStats {
   private HorseStats() {
   }

   public static List<HorseStats.Line> lines(AbstractHorse h, boolean riding) {
      HorseSpeed s = HorseSpeed.of(h);
      List<HorseStats.Line> out = new ArrayList<>();
      out.add(new HorseStats.Line(h.getName().getString() + (h.isTamed() ? "" : " (wild)"), -171));
      out.add(new HorseStats.Line("Speed: " + String.format("%.1f", HorseSpeed.blocksPerSecond(h)) + " blocks/sec", -1));
      if (riding) {
         double now = HorsesClient.currentSpeed;
         out.add(new HorseStats.Line("  now " + String.format("%.1f", now), -5592406));
      }

      out.add(
         new HorseStats.Line(
            "Tier "
               + s.tier()
               + " (x"
               + HorseSpeed.MULTIPLIER[Math.clamp((long)s.tier(), 0, HorseSpeed.MAX_TIER)]
               + ")"
               + (h instanceof SkeletonHorse ? ", always top speed" : ""),
            -11649
         )
      );
      out.add(new HorseStats.Line("Jump: " + String.format("%.1f", HorseSpeed.jumpHeight(h)) + " blocks", -1));
      out.add(new HorseStats.Line("Health: " + String.format("%.1f", h.getMaxHealth() / 2.0F) + " hearts", -32640));
      if (!(h instanceof SkeletonHorse) && s.tier() < HorseSpeed.MAX_TIER) {
         HorseSpeed.Step step = HorseSpeed.stepFrom(s.tier());
         if (step == null) {
            out.add(new HorseStats.Line("Next tier: breed two tier " + s.tier() + " horses", -6303489));
         } else {
            out.add(
               new HorseStats.Line("Next tier: " + s.fed() + "/" + step.amount() + " " + step.feed().getDefaultInstance().getHoverName().getString(), -6303489)
            );
         }

         return out;
      } else {
         return out;
      }
   }

   public static void draw(GuiGraphicsExtractor g, AbstractHorse h, boolean riding, int x, int y) {
      Font font = Minecraft.getInstance().font;
      List<HorseStats.Line> lines = lines(h, riding);
      int w = 0;

      for (HorseStats.Line l : lines) {
         w = Math.max(w, font.width(l.text()));
      }

      g.fill(x - 3, y - 3, x + w + 3, y + lines.size() * 10 + 1, -1342177280);

      for (int i = 0; i < lines.size(); i++) {
         g.text(font, lines.get(i).text(), x, y + i * 10, lines.get(i).color());
      }
   }

   public static record Line(String text, int color) {
   }
}
