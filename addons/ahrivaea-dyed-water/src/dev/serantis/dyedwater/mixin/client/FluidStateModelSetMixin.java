package dev.serantis.dyedwater.mixin.client;

import dev.serantis.dyedwater.DyedWater;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.MaterialBaker;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

/** Dyed water is drawn with water's textures, tinted its dye's colour. */
@Mixin(FluidStateModelSet.class)
public abstract class FluidStateModelSetMixin {
    @Inject(method = "bake", at = @At("RETURN"), cancellable = true)
    private static void serantis$dyedWater(MaterialBaker baker, CallbackInfoReturnable<Map<Fluid, FluidModel>> cir) {
        Map<Fluid, FluidModel> models = new HashMap<>(cir.getReturnValue());
        for (DyedWater.Kind k : DyedWater.KINDS) {
            FluidModel model = new FluidModel.Unbaked(
                    new Material(Identifier.withDefaultNamespace("block/water_still")),
                    new Material(Identifier.withDefaultNamespace("block/water_flow")),
                    new Material(Identifier.withDefaultNamespace("block/water_overlay")),
                    BlockTintSources.constant(k.color())).bake(baker, () -> "Dyed water");
            models.put(k.source, model);
            models.put(k.flowing, model);
        }
        cir.setReturnValue(models);
    }
}
