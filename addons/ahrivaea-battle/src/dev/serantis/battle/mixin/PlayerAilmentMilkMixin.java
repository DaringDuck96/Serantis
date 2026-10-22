package dev.serantis.battle.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Milk (anything that clears all your effects) also cures lingering confusion and fright (see Ailments). */
@Mixin(LivingEntity.class)
public abstract class PlayerAilmentMilkMixin {
    @Inject(method = "removeAllEffects", at = @At("HEAD"))
    private void serantis$cure(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerPlayer p) dev.serantis.battle.Ailments.clearPlayer(p);
    }
}
