package dev.serantis.battle.mixin;

import dev.ahrivaea.creature.BattleSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Levelling a creature (party screen) costs 8,000 XP in all from Level 1 to Level 20, rising steadily: 150 for 1 to 2,
 * about 30 more each level, 692 for 19 to 20 (and on in the same way past 20). Each level's price is the difference
 * of a running total, so the 19 steps add up to exactly 8,000.
 */
@Mixin(BattleSettings.class)
public abstract class CreatureLevelCostMixin {
    @Unique private static final double TOTAL = 8000, WEIGHTS = 266;   // the 19 steps weigh 5, 6, ... 23 (sum 266)

    @Unique
    private static long serantis$total(int level) {                    // XP from Level 1 to this level
        long n = level - 1;
        return Math.round(TOTAL * (4 * n + n * level / 2.0) / WEIGHTS);
    }

    @Inject(method = "xpToNext", at = @At("HEAD"), cancellable = true)
    private static void serantis$cost(int level, CallbackInfoReturnable<Integer> cir) {
        int l = Math.max(1, level);
        cir.setReturnValue((int) (serantis$total(l + 1) - serantis$total(l)));
    }
}
