package dev.serantis.battle.mixin;

import dev.ahrivaea.party.Party;
import dev.serantis.battle.BattleStats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(Party.class)
public abstract class PartyMixin {
    // Feeding XP to a creature stops at Level 20 (was 50).
    @ModifyConstant(method = "feed", constant = @Constant(intValue = 50))
    private static int serantis$feedCap(int original) {
        return BattleStats.MAX_LEVEL;
    }
}
