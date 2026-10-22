package dev.serantis.battle.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** With chat open, the minimap can be dragged (see client/MinimapDrag). */
@Mixin(ChatScreen.class)
public abstract class ChatMinimapDragMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void serantis$dragMinimap(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial, CallbackInfo ci) {
        dev.serantis.battle.client.MinimapDrag.chatFrame(mouseX, mouseY, g.guiWidth(), g.guiHeight());
    }
}
