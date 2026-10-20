package dev.serantis.battle.mixin.client;

import dev.ahrivaea.client.PartyScreen;
import dev.serantis.battle.BattleStats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(PartyScreen.class)
public abstract class PartyScreenMixin {
    // The party screen's feed button turns off at the max level. In init() the number 50 appears three
    // times: two button positions, then the "level >= 50" check (the third one) changed here.
    @ModifyConstant(method = "init", constant = @Constant(intValue = 50, ordinal = 2))
    private int serantis$feedButtonCap(int original) {
        return BattleStats.MAX_LEVEL;
    }
}
