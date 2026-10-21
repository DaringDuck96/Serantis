package dev.serantis.battle.mixin;

import dev.serantis.battle.Achievements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Counts crops a player plants by hand, for the Seeds Planted achievement: wheat, beetroot, carrots,
 * potatoes, torchflowers, pitcher pods, melon and pumpkin seeds, nether wart, cocoa and sweet berries.
 */
@Mixin(BlockItem.class)
public abstract class BlockItemPlantedMixin {
    @Shadow public abstract Block getBlock();

    @Inject(method = "place", at = @At("RETURN"))
    private void serantis$planted(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (!(context.getPlayer() instanceof ServerPlayer player) || !cir.getReturnValue().consumesAction()) return;
        Block b = getBlock();
        if (b instanceof CropBlock || b instanceof StemBlock || b instanceof NetherWartBlock || b instanceof CocoaBlock
                || b instanceof PitcherCropBlock || b instanceof SweetBerryBushBlock)
            if (dev.serantis.battle.BaseCredit.freshPlanting(player, context.getClickedPos())) ((Achievements.Holder) player).serantis$addPlanted(1);
    }
}
