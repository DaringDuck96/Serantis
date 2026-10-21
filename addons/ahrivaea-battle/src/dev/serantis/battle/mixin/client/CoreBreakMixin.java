package dev.serantis.battle.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hitting a Base Core asks first instead of breaking it (see CoreBreakScreen). */
@Mixin(MultiPlayerGameMode.class)
public abstract class CoreBreakMixin {
    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void serantis$askFirst(BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getBlockState(pos).getBlock() instanceof dev.ahrivaea.base.BaseCoreBlock)) return;
        if (mc.gui.screen() == null) mc.gui.setScreen(new dev.serantis.battle.client.CoreBreakScreen(pos.immutable()));
        cir.setReturnValue(false);
    }
}
