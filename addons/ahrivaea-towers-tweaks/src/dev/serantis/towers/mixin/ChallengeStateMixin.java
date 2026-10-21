package dev.serantis.towers.mixin;

import dev.ahrivaea.challenge.ChallengeState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A claimed tower is still protected for everyone until it's rebuilt: claiming it (the first offering) only
 * decides who gets it then. Without this, the claimer and their guild could build and break in the ruin
 * and the land around it straight away.
 */
@Mixin(ChallengeState.class)
public abstract class ChallengeStateMixin {
    @Inject(method = "controller", at = @At("HEAD"), cancellable = true)
    private void serantis$notYet(CallbackInfoReturnable<String> cir) {
        if (!((ChallengeState) (Object) this).complete) cir.setReturnValue("");
    }
}
