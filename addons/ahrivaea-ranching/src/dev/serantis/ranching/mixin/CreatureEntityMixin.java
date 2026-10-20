package dev.serantis.ranching.mixin;

import dev.ahrivaea.creature.CreatureEntity;
import dev.serantis.ranching.Ranching;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreatureEntity.class)
public abstract class CreatureEntityMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void serantis$ranch(CallbackInfo ci) {
        Ranching.tick((CreatureEntity) (Object) this);
    }
}
