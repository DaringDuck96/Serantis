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
    @Shadow @Final private net.minecraft.core.BlockPos core;

    @Inject(method = "speed", at = @At("HEAD"), cancellable = true)
    private void serantis$speed(CallbackInfoReturnable<Double> cir) {
        if (self == null || self.getInfluence() == null) return;
        if (self.getSpecies().isPresent())         // getStats includes its temperament
            cir.setReturnValue(Pace.workSpeed(self.getCreatureLevel(), self.getStats().charisma()) * serantis$bard() * serantis$performer());
    }

    // The base belongs to a Performer (online): everyone there works 7.5% faster.
    private double serantis$performer() {
        if (core == null || !(self.level() instanceof net.minecraft.server.level.ServerLevel level)) return 1.0;
        var base = dev.ahrivaea.base.Bases.coreAt(level, core);
        if (base == null || base.owner() == null) return 1.0;
        var owner = level.getServer().getPlayerList().getPlayer(base.owner());
        return dev.serantis.battle.PlayerClass.is(owner, "performer") ? dev.serantis.battle.PlayerClass.PERFORMER_WORK : 1.0;
    }

    // A Bard working at the same base: everyone there works 15% faster.
    private double serantis$bard() {
        if (core == null || !(self.level() instanceof net.minecraft.server.level.ServerLevel level)) return 1.0;
        var base = dev.ahrivaea.base.Bases.coreAt(level, core);
        return dev.serantis.battle.Villages.has(base, "bard") ? dev.serantis.battle.Villages.BARD_SPEED : 1.0;
    }
}
