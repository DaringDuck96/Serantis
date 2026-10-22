package dev.serantis.battle.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Right-click a hay bale in a base with an empty hand: switch its farm animals on or off (see HayBales). */
@Mixin(BlockBehaviour.class)
public abstract class HayToggleMixin {
    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true)
    private void serantis$hay(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (!state.is(Blocks.HAY_BLOCK) || !player.getMainHandItem().isEmpty()) return;
        if (level.isClientSide()) { cir.setReturnValue(InteractionResult.SUCCESS); return; }   // the server decides; don't use the off-hand item
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp && dev.serantis.battle.HayBales.toggle(sl, pos, sp))
            cir.setReturnValue(InteractionResult.SUCCESS);
    }
}
