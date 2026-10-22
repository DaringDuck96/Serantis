package dev.serantis.battle.mixin.client;

import dev.ahrivaea.client.battle.BattleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** A clearer battle screen: the battle log under the turn banner shows only the latest line (not the last 3). */
@Mixin(BattleScreen.class)
public abstract class BattleLogMixin {
    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I"))
    private int serantis$oneLine(int a, int b) { return Math.min(Math.min(a, 1), b); }
}
