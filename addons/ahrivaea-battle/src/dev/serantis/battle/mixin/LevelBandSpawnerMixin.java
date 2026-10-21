package dev.serantis.battle.mixin;

import dev.ahrivaea.creature.WildSpawner;
import dev.serantis.battle.LevelBand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Wild spawns near new players come from a gentler level window (see LevelBand): pickForLevels(..., me - 5, me + 5, ...). */
@Mixin(WildSpawner.class)
public abstract class LevelBandSpawnerMixin {
    private static final String PICK = "Ldev/ahrivaea/creature/Species;pickForLevels(Lnet/minecraft/core/Holder;Lnet/minecraft/world/level/Level;Lnet/minecraft/util/RandomSource;IILjava/util/function/BiPredicate;)Ljava/util/Optional;";

    @ModifyArg(method = "trySpawnNear", at = @At(value = "INVOKE", target = PICK), index = 3)
    private static int serantis$lo(int lo) { return LevelBand.lo(lo + 5); }

    @ModifyArg(method = "trySpawnNear", at = @At(value = "INVOKE", target = PICK), index = 4)
    private static int serantis$hi(int hi) { return LevelBand.hi(hi - 5); }
}
