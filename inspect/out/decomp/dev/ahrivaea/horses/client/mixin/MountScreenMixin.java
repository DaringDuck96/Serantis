package dev.ahrivaea.horses.client.mixin;

import dev.ahrivaea.horses.HorseSpeed;
import dev.ahrivaea.horses.client.HorseStats;
import dev.ahrivaea.horses.client.HorsesClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractMountInventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({AbstractMountInventoryScreen.class})
public class MountScreenMixin {
   @Shadow
   @Final
   protected LivingEntity mount;

   @Inject(
      method = {"extractRenderState"},
      at = {@At("TAIL")}
   )
   private void ahrivaea$horseStats(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      if (HorsesClient.serverHasMod()) {
         if (this.mount instanceof AbstractHorse h && HorseSpeed.covers(h)) {
            Screen screen = (Screen)this;
            HorseStats.draw(g, h, false, (screen.width + 176) / 2 + 8, (screen.height - 166) / 2 + 4);
            return;
         }
      }
   }
}
