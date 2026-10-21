package dev.ahrivaea.base.mixin;

import dev.ahrivaea.base.PlacedBlocks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({BlockItem.class})
public class BlockItemPlacedMixin {
   @Inject(
      method = {"place"},
      at = {@At("RETURN")}
   )
   private void ahrivaea$rememberPlacedBlock(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
      if (context.getLevel() instanceof ServerLevel level && context.getPlayer() != null && ((InteractionResult)cir.getReturnValue()).consumesAction()) {
         PlacedBlocks.placed(level, context.getClickedPos());
         return;
      }
   }
}
