package dev.serantis.battle.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Opening a wild chest: 60% of the time it's a Mimic. */
@Mixin(ChestBlock.class)
public abstract class WildChestMixin {
    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true)
    private void serantis$mimic(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (player instanceof ServerPlayer sp && dev.serantis.battle.WildChests.touched(sp, pos)) cir.setReturnValue(InteractionResult.SUCCESS);
    }
}
