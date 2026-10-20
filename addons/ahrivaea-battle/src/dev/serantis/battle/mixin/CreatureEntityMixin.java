package dev.serantis.battle.mixin;

import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.party.CaughtCreature;
import dev.serantis.battle.BattleStats;
import dev.serantis.battle.moves.MovesHolder;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CreatureEntity.class)
public abstract class CreatureEntityMixin implements MovesHolder {
    @Unique private String serantis$moves;

    @Override public String serantis$moves() { return serantis$moves; }
    @Override public void serantis$setMoves(String moves) { serantis$moves = moves; }

    // A creature's level is kept within 0-20 when it is set up (was 0-50).
    @ModifyConstant(method = "setup", constant = @Constant(intValue = 50))
    private int serantis$levelCap(int original) {
        return BattleStats.MAX_LEVEL;
    }

    // Its moves are saved with it in the world...
    @Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
    private void serantis$saveMoves(ValueOutput out, CallbackInfo ci) {
        if (serantis$moves != null) out.putString("SerantisMoves", serantis$moves);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
    private void serantis$loadMoves(ValueInput in, CallbackInfo ci) {
        String saved = in.getStringOr("SerantisMoves", "");
        if (!saved.isEmpty()) serantis$moves = saved;
    }

    // ...and carried over when it's caught or sent out from the party.
    @Inject(method = "toCaught", at = @At("RETURN"))
    private void serantis$toCaught(CallbackInfoReturnable<CaughtCreature> cir) {
        if (serantis$moves != null && cir.getReturnValue() != null) ((MovesHolder) (Object) cir.getReturnValue()).serantis$setMoves(serantis$moves);
    }

    @Inject(method = "applyCaught", at = @At("RETURN"))
    private void serantis$applyCaught(CaughtCreature caught, CallbackInfo ci) {
        String moves = ((MovesHolder) (Object) caught).serantis$moves();
        if (moves != null) serantis$moves = moves;
    }
}
