package dev.serantis.battle.mixin;

import dev.ahrivaea.creature.CreatureEntity;
import dev.serantis.battle.BattleStats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(CreatureEntity.class)
public abstract class CreatureEntityMixin {
    // A creature's level is kept within 0-20 when it is set up (was 0-50).
    @ModifyConstant(method = "setup", constant = @Constant(intValue = 50))
    private int serantis$levelCap(int original) {
        return BattleStats.MAX_LEVEL;
    }
}
