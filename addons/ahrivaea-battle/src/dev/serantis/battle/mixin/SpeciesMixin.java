package dev.serantis.battle.mixin;

import dev.ahrivaea.creature.Species;
import dev.serantis.battle.BattleStats;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(Species.class)
public abstract class SpeciesMixin {
    @Shadow private static Map<String, Species> all;

    // After the creatures load, swap in the Serantis stats and Level 1-20 spawning.
    @Inject(method = "reload", at = @At("RETURN"))
    private static void serantis$applyBattleStats(ResourceManager resources, CallbackInfo ci) {
        all = BattleStats.apply(all);
    }

    // Wild spawns look for creatures within 5 levels of the player's own level, which goes up to 50.
    // Keep that window inside Level 1-20 so high-level players still find creatures.
    @ModifyVariable(method = "pickForLevels", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static int serantis$minLevel(int min) {
        return BattleStats.clampLevel(min);
    }

    @ModifyVariable(method = "pickForLevels", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private static int serantis$maxLevel(int max) {
        return BattleStats.clampLevel(max);
    }
}
