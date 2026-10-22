package dev.serantis.battle.mixin.client;

import dev.serantis.battle.client.BaseLooksClient;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Inside an Overworld base with a chosen look: grass, leaves and water take that biome's colours (see BaseLooksClient). */
@Mixin(BiomeColors.class)
public abstract class BiomeColorsMixin {
    @Inject(method = "getAverageGrassColor", at = @At("HEAD"), cancellable = true)
    private static void serantis$grass(BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        int c = BaseLooksClient.color(pos, 0);
        if (c != -1) cir.setReturnValue(c);
    }

    @Inject(method = "getAverageFoliageColor", at = @At("HEAD"), cancellable = true)
    private static void serantis$foliage(BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        int c = BaseLooksClient.color(pos, 1);
        if (c != -1) cir.setReturnValue(c);
    }

    @Inject(method = "getAverageDryFoliageColor", at = @At("HEAD"), cancellable = true)
    private static void serantis$dryFoliage(BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        int c = BaseLooksClient.color(pos, 2);
        if (c != -1) cir.setReturnValue(c);
    }

    @Inject(method = "getAverageWaterColor", at = @At("HEAD"), cancellable = true)
    private static void serantis$water(BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        int c = BaseLooksClient.color(pos, 3);
        if (c != -1) cir.setReturnValue(c);
    }
}
