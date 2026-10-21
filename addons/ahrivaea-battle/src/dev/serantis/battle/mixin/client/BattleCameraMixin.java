package dev.serantis.battle.mixin.client;

import dev.ahrivaea.client.battle.BattleClient;
import dev.serantis.battle.client.BattleFeel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The battle camera nudges on crits and knockouts (see BattleFeel). */
@Mixin(BattleClient.class)
public abstract class BattleCameraMixin {
    @Inject(method = "camX", at = @At("RETURN"), cancellable = true)
    private static void serantis$shakeX(CallbackInfoReturnable<Double> cir) { cir.setReturnValue(cir.getReturnValue() + BattleFeel.dx()); }

    @Inject(method = "camY", at = @At("RETURN"), cancellable = true)
    private static void serantis$shakeY(CallbackInfoReturnable<Double> cir) { cir.setReturnValue(cir.getReturnValue() + BattleFeel.dy()); }

    @Inject(method = "camZ", at = @At("RETURN"), cancellable = true)
    private static void serantis$shakeZ(CallbackInfoReturnable<Double> cir) { cir.setReturnValue(cir.getReturnValue() + BattleFeel.dz()); }
}
