package dev.serantis.battle.mixin.client;

import dev.ahrivaea.map.client.MapView;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

/** A small minimap: 50 pixels across (instead of 100), at the same zoom. Press M for the full map. */
@Mixin(MapView.class)
public abstract class MinimapSizeMixin {
    @ModifyConstant(method = "minimap", constant = @Constant(intValue = 100))
    private static int serantis$size(int size) { return 50; }

    // The coordinates under it are wider than the map now: line them up with its right edge so they stay on screen.
    @Redirect(method = "minimap", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;centeredText(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"))
    private static void serantis$coords(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color) {
        g.text(font, text, Math.min(x - font.width(text) / 2, g.guiWidth() - 4 - font.width(text)), y, color);
    }
}
