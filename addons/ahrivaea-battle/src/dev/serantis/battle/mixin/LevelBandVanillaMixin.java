package dev.serantis.battle.mixin;

import dev.ahrivaea.creature.VanillaMobs;
import dev.serantis.battle.LevelBand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Creatures standing in for vanilla mobs use the same window (see LevelBand): replace's locals lo = me - 5 (slot 10), hi = me + 5 (slot 11). */
@Mixin(VanillaMobs.class)
public abstract class LevelBandVanillaMixin {
    @ModifyVariable(method = "replace", at = @At("STORE"), index = 10)
    private static int serantis$lo(int lo) { return LevelBand.lo(lo + 5); }

    @ModifyVariable(method = "replace", at = @At("STORE"), index = 11)
    private static int serantis$hi(int hi) { return LevelBand.hi(hi - 5); }
}
