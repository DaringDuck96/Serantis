package dev.serantis.battle.mixin.client;

import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Nether base looks (see client/BaseLooksClient): grass blocks are drawn as netherrack, crimson or warped nylium, soul
 * sand or basalt. Only the copy of the world the game draws from is changed, so the blocks are still grass blocks.
 */
@Mixin(RenderSectionRegion.class)
public abstract class BaseGroundMixin {
    @Inject(method = "getBlockState", at = @At("RETURN"), cancellable = true)
    private void serantis$ground(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        BlockState swap = dev.serantis.battle.client.BaseLooksClient.ground(pos, cir.getReturnValue());
        if (swap != null) cir.setReturnValue(swap);
    }
}
