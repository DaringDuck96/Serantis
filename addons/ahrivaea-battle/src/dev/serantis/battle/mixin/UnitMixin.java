package dev.serantis.battle.mixin;

import dev.ahrivaea.battle.BattleMove;
import dev.ahrivaea.battle.DamageCalc;
import dev.ahrivaea.battle.Unit;
import dev.serantis.battle.moves.MoveEngine;
import dev.serantis.battle.moves.UnitAccess;
import dev.serantis.battle.moves.UnitState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(Unit.class)
public abstract class UnitMixin implements UnitAccess {
    @Shadow @Mutable @Final public List<BattleMove> moves;
    @Shadow @Final public boolean isPlayer;
    @Shadow @Final public int level;
    @Shadow @Final public dev.ahrivaea.creature.Stats stats;

    // Movement per turn from Speed and level (see Pace). The player keeps the creatures mod's own.
    @Inject(method = "squares", at = @At("HEAD"), cancellable = true)
    private void serantis$squares(CallbackInfoReturnable<Integer> cir) {
        if (!isPlayer && stats != null) cir.setReturnValue(dev.serantis.battle.Pace.squares(level, stats.speed()));
    }
    @Unique private UnitState serantis$state;

    @Override
    public UnitState serantis$state() {
        if (serantis$state == null) serantis$state = new UnitState();
        return serantis$state;
    }

    @Override
    public void serantis$setMoves(List<BattleMove> newMoves) {
        this.moves = List.copyOf(newMoves);
    }

    // Battle stats with stat changes applied.
    @Inject(method = "fighter", at = @At("RETURN"), cancellable = true)
    private void serantis$fighter(boolean inWater, CallbackInfoReturnable<DamageCalc.Fighter> cir) {
        cir.setReturnValue(MoveEngine.adjustFighter((Unit) (Object) this, cir.getReturnValue()));
    }
}
