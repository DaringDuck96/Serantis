package dev.serantis.battle.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DynamicGpuData;
import net.minecraft.client.renderer.SkyRenderer;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** On a Blood Moon (see BloodMoon) the moon is drawn red. */
@Mixin(SkyRenderer.class)
public abstract class SkyMoonMixin {
    @Redirect(method = "renderMoon", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/DynamicGpuData;writeTransform(Lorg/joml/Matrix4f;Lorg/joml/Vector4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"))
    private com.mojang.renderpearl.api.buffers.GpuBufferSlice serantis$red(DynamicGpuData data, Matrix4f transform, Vector4f color) {
        if (dev.serantis.battle.BloodMoon.tonight(Minecraft.getInstance().level)) color.set(1f, 0.16f, 0.1f, color.w());
        return data.writeTransform(transform, color);
    }
}
