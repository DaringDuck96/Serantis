package dev.serantis.battle.mixin.client;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Conversation close-ups (see DialogueCamera). Runs after the creatures mod's own battle camera. */
@Mixin(value = Camera.class, priority = 1100)
public abstract class DialogueCameraMixin {
    @Shadow private boolean detached;
    @Shadow protected abstract void setPosition(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void serantis$dialogueShot(float partialTicks, CallbackInfo ci) {
        double[] f = dev.serantis.battle.client.DialogueCamera.frame(partialTicks);
        if (f == null) return;
        detached = true;
        setPosition(f[0], f[1], f[2]);
        setRotation((float) f[3], (float) f[4]);
    }
}
