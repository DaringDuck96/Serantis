package dev.serantis.battle.mixin;

import dev.ahrivaea.creature.Variant;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every spawned creature has a 1% chance of being Mutated (with its mutated skin). The creatures mod's own
 * roll still decides Enraged, at its usual rate; its own Mutated result is turned off so Mutated stays 1%.
 */
@Mixin(Variant.class)
public abstract class VariantMixin {
    @Inject(method = "roll", at = @At("HEAD"), cancellable = true)
    private static void serantis$mutatedOnePercent(ServerLevel level, RandomSource random, CallbackInfoReturnable<Variant> cir) {
        if (random.nextInt(100) == 0) cir.setReturnValue(Variant.MUTATED);
    }

    @Inject(method = "roll", at = @At("RETURN"), cancellable = true)
    private static void serantis$noOtherMutated(ServerLevel level, RandomSource random, CallbackInfoReturnable<Variant> cir) {
        if (cir.getReturnValue() == Variant.MUTATED) cir.setReturnValue(Variant.NONE);
    }
}
