package dev.serantis.battle.mixin.client;

import dev.serantis.battle.client.BaseLooksClient;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Inside an Overworld base with a chosen look: grass, leaves and water take that biome's colours (see BaseLooksClient). */
@Mixin(BiomeColors.class)
public abstract class BiomeColorsMixin {
    @Inject(method = "getAverageGrassColor", at = @At("HEAD"), cancellable = true)
    private static void serantis$grass(BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        Biome b = BaseLooksClient.at(pos);
        if (b != null) cir.setReturnValue(b.getGrassColor(pos.getX(), pos.getZ()));
    }

    @Inject(method = "getAverageFoliageColor", at = @At("HEAD"), cancellable = true)
    private static void serantis$foliage(BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        Biome b = BaseLooksClient.at(pos);
        if (b != null) cir.setReturnValue(b.getFoliageColor());
    }

    @Inject(method = "getAverageDryFoliageColor", at = @At("HEAD"), cancellable = true)
    private static void serantis$dryFoliage(BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        Biome b = BaseLooksClient.at(pos);
        if (b != null) cir.setReturnValue(b.getDryFoliageColor());
    }

    @Inject(method = "getAverageWaterColor", at = @At("HEAD"), cancellable = true)
    private static void serantis$water(BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        Biome b = BaseLooksClient.at(pos);
        if (b != null) cir.setReturnValue(b.getWaterColor());
    }
}
