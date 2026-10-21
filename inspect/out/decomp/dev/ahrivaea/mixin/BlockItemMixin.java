package dev.ahrivaea.mixin;

import dev.ahrivaea.stellar.PlacedLogs;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({BlockItem.class})
public class BlockItemMixin {
   @Inject(
      method = {"place"},
      at = {@At("RETURN")}
   )
   private void ahrivaea$rememberPlacedLog(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
      Level level = context.getLevel();
      if (!level.isClientSide() && context.getPlayer() != null && ((InteractionResult)cir.getReturnValue()).consumesAction()) {
         BlockPos pos = context.getClickedPos();
         if (level.getBlockState(pos).is(BlockTags.LOGS)) {
            PlacedLogs.mark(level, pos);
         }
      }
   }
}
