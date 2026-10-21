package dev.ahrivaea.challenge.mixin;

import dev.ahrivaea.challenge.Challenges;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ServerLevel.class})
public abstract class ServerLevelMixin {
   @Inject(
      method = {"mayInteract"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void ahrivaea$towerProtection(Entity entity, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
      if (Challenges.protectedFrom((ServerLevel)this, entity, pos, true) != null) {
         cir.setReturnValue(false);
      }
   }
}
