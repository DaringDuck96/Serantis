package dev.ahrivaea.minecarts.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartBehavior;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({NewMinecartBehavior.class})
public abstract class NewMinecartBehaviorMixin extends MinecartBehavior {
   protected NewMinecartBehaviorMixin(AbstractMinecart minecart) {
      super(minecart);
   }

   @Inject(
      method = {"getMaxSpeed"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void ahrivaea$fastMinecarts(ServerLevel level, CallbackInfoReturnable<Double> cir) {
      cir.setReturnValue(30.0 * (this.minecart.isInWater() ? 0.5 : 1.0) / 20.0);
   }
}
