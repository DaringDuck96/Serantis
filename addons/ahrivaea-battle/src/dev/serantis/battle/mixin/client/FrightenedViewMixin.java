package dev.serantis.battle.mixin.client;

import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lingering fright (see client/PlayerAilments): you see half as far (at least 2 chunks). */
@Mixin(Options.class)
public abstract class FrightenedViewMixin {
    @Inject(method = "getEffectiveRenderDistance", at = @At("RETURN"), cancellable = true)
    private void serantis$halve(CallbackInfoReturnable<Integer> cir) {
        if (dev.serantis.battle.client.PlayerAilments.frightened()) cir.setReturnValue(Math.max(2, cir.getReturnValue() / 2));
    }
}
