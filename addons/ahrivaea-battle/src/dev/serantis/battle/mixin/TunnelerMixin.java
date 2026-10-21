package dev.serantis.battle.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tunneler: breaking a block with the enchanted pickaxe breaks the pane around it (see Tunneler). */
@Mixin(ServerPlayerGameMode.class)
public abstract class TunnelerMixin {
    @Shadow @Final protected ServerPlayer player;
    @Unique private float[] serantis$tunnel;

    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void serantis$tunnelBefore(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        serantis$tunnel = dev.serantis.battle.Tunneler.before(player, pos);
    }

    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void serantis$tunnelAfter(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        float[] info = serantis$tunnel;
        serantis$tunnel = null;
        if (info != null && Boolean.TRUE.equals(cir.getReturnValue())) dev.serantis.battle.Tunneler.after(player, pos, info);
    }
}
