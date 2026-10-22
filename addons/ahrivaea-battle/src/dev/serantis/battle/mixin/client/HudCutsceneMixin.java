package dev.serantis.battle.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * During conversations and cutscenes only the scene and the dialogue are shown: no hotbar, chat, minimap, party
 * summary or other HUD text behind them. The same in battles, where the battle screen has everything you need
 * (chat still shows while you're typing).
 */
@Mixin(Hud.class)
public abstract class HudCutsceneMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void serantis$cutscene(CallbackInfo ci) {
        if (dev.serantis.battle.client.DialogueCamera.cutscene()) { ci.cancel(); return; }
        if (dev.ahrivaea.client.battle.BattleClient.active() && !(Minecraft.getInstance().gui.screen() instanceof ChatScreen)) ci.cancel();
    }
}
