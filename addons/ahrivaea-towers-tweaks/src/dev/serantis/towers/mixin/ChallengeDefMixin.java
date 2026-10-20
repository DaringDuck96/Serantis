package dev.serantis.towers.mixin;

import dev.ahrivaea.challenge.ChallengeDef;
import dev.serantis.towers.TowerCosts;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lowers the goals of the first towers (see TowerCosts) as each tower's file is loaded. */
@Mixin(ChallengeDef.class)
public abstract class ChallengeDefMixin {
    @Shadow @Final @Mutable private int goal;
    @Shadow @Final private String name;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void serantis$cheaper(CallbackInfo ci) {
        this.goal = Math.max(1, TowerCosts.goal(name, goal));
    }
}
