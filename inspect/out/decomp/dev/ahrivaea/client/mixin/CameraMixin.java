package dev.ahrivaea.client.mixin;

import dev.ahrivaea.client.battle.BattleClient;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Camera.class})
public abstract class CameraMixin {
   @Shadow
   private boolean detached;

   @Shadow
   protected abstract void setPosition(double var1, double var3, double var5);

   @Shadow
   protected abstract void setRotation(float var1, float var2);

   @Inject(
      method = {"alignWithEntity"},
      at = {@At("TAIL")}
   )
   private void ahrivaea$battleView(float partialTicks, CallbackInfo ci) {
      if (BattleClient.active()) {
         this.detached = true;
         this.setPosition(BattleClient.camX(), BattleClient.camY(), BattleClient.camZ());
         this.setRotation(180.0F, 90.0F);
      }
   }
}
