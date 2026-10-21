package dev.serantis.battle.mixin.client;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntityRenderer.class)
public interface LivingRendererInvoker {
    @SuppressWarnings("rawtypes")
    @Invoker("addLayer")
    boolean serantis$addLayer(RenderLayer layer);
}
