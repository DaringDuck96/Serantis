package dev.serantis.battle.mixin;

import dev.ahrivaea.base.WorkerBrain;
import dev.ahrivaea.creature.CreatureEntity;
import dev.serantis.battle.Pace;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Base workers work at a speed set by their Charisma (see Pace), not just their level. */
@Mixin(WorkerBrain.class)
public abstract class WorkerBrainMixin {
    @Shadow @Final private CreatureEntity self;

    @Inject(method = "speed", at = @At("HEAD"), cancellable = true)
    private void serantis$speed(CallbackInfoReturnable<Double> cir) {
        if (self == null || self.getInfluence() == null) return;
        if (self.getSpecies().isPresent())         // getStats includes its temperament
            cir.setReturnValue(Pace.workSpeed(self.getCreatureLevel(), self.getStats().charisma()));
    }
}
