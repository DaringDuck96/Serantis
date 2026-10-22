package dev.serantis.battle.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Breaking a wild chest instead of opening it doesn't dodge the Mimic. */
@Mixin(ServerPlayerGameMode.class)
public abstract class WildChestBreakMixin {
    @Shadow @Final protected ServerPlayer player;

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void serantis$mimic(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        try {
        if (dev.serantis.battle.WildChests.touched(player, pos)) cir.setReturnValue(false);
            } catch (Throwable serantisT) { dev.serantis.battle.Safe.caught("serantis.mimic", serantisT); }
    }
}
