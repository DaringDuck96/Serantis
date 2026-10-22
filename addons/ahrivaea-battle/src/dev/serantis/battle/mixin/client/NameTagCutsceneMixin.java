package dev.serantis.battle.mixin.client;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No name tags floating over the scene during conversations, cutscenes and battles (the battle cards name everyone). */
@Mixin(EntityRenderer.class)
public abstract class NameTagCutsceneMixin {
    @Inject(method = "extractNameTags(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V", at = @At("TAIL"))
    private void serantis$cutscene(Entity entity, EntityRenderState state, float partial, CallbackInfo ci) {
        if (dev.serantis.battle.client.DialogueCamera.cutscene() || dev.ahrivaea.client.battle.BattleClient.active()) { state.nameTag = null; state.scoreText = null; }
    }
}
