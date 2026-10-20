package dev.serantis.battle.mixin;

import com.mojang.serialization.Codec;
import dev.ahrivaea.party.CaughtCreature;
import dev.ahrivaea.villager.VillagerJob;
import dev.serantis.battle.moves.MovesCodec;
import dev.serantis.battle.moves.MovesHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A caught creature remembers its three moves, saved with it and kept through every update. */
@Mixin(CaughtCreature.class)
public abstract class CaughtCreatureMixin implements MovesHolder {
    @Shadow @Final @Mutable public static Codec<CaughtCreature> CODEC;
    @Unique private String serantis$moves;

    @Override public String serantis$moves() { return serantis$moves; }
    @Override public void serantis$setMoves(String moves) { serantis$moves = moves; }

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void serantis$wrapCodec(CallbackInfo ci) { CODEC = MovesCodec.wrap(CODEC); }

    @Inject(method = "withProgress", at = @At("RETURN"))
    private void serantis$keepOnProgress(int level, int xp, CallbackInfoReturnable<CaughtCreature> cir) { serantis$copy(cir.getReturnValue()); }

    @Inject(method = "withHealth", at = @At("RETURN"))
    private void serantis$keepOnHealth(float health, CallbackInfoReturnable<CaughtCreature> cir) { serantis$copy(cir.getReturnValue()); }

    @Inject(method = "withJob", at = @At("RETURN"))
    private void serantis$keepOnJob(VillagerJob job, CallbackInfoReturnable<CaughtCreature> cir) { serantis$copy(cir.getReturnValue()); }

    @Unique
    private void serantis$copy(CaughtCreature next) {
        if (next != null && serantis$moves != null) ((MovesHolder) (Object) next).serantis$setMoves(serantis$moves);
    }
}
