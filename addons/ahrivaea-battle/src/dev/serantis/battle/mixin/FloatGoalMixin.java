package dev.serantis.battle.mixin;

import dev.ahrivaea.creature.CreatureEntity;
import dev.serantis.battle.Locomotion;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Creatures that live below the surface don't bob back up to it (see Locomotion). */
@Mixin(FloatGoal.class)
public abstract class FloatGoalMixin {
    @Shadow @Final private Mob mob;

    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
    private void serantis$stayUnder(CallbackInfoReturnable<Boolean> cir) {
        if (mob instanceof CreatureEntity c && !c.isTame()) {
            byte m = ((Locomotion.Holder) c).serantis$moveMode();
            if (m == Locomotion.DIVE || m == Locomotion.CRAWL) cir.setReturnValue(false);
        }
    }
}
