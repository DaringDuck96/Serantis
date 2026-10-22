package dev.serantis.battle.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A Base Core can't be placed where its base would overlap another player's base (see BaseShape.clash). */
@Mixin(Block.class)
public abstract class BaseCorePlaceMixin {
    @Inject(method = "getStateForPlacement", at = @At("HEAD"), cancellable = true)
    private void serantis$noOverlap(BlockPlaceContext ctx, CallbackInfoReturnable<BlockState> cir) {
        if (!((Object) this instanceof dev.ahrivaea.base.BaseCoreBlock) || !(ctx.getLevel() instanceof ServerLevel level) || ctx.getPlayer() == null) return;
        String other = dev.serantis.battle.BaseShape.clash(level, ctx.getClickedPos(), dev.serantis.battle.BaseShape.radiusAt(1),
                ctx.getPlayer().getUUID(), ctx.getClickedPos());
        if (other == null) return;
        if (ctx.getPlayer() instanceof net.minecraft.server.level.ServerPlayer sp) sp.sendOverlayMessage(Component.literal("Too close to " + other + "'s base: bases can only overlap their owner's other bases."));
        cir.setReturnValue(null);
    }
}
