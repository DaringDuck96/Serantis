package dev.serantis.battle.mixin.client;

import dev.ahrivaea.map.client.MapView;
import dev.serantis.battle.client.map.Waypoints;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marked places (Waypoints: yours, and public ones with a white rim) on the full map (with their names) and the minimap. */
@Mixin(MapView.class)
public abstract class MapViewWaypointsMixin {
    @Inject(method = "draw", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;disableScissor()V"))
    private static void serantis$marks(GuiGraphicsExtractor g, int x0, int y0, int w, int h, double cx, double cz, float scale, boolean big,
                                       int mouseX, int mouseY, MapView.Hover hover, CallbackInfo ci) {
        var font = Minecraft.getInstance().font;
        float midX = x0 + w / 2f, midY = y0 + h / 2f;
        int r = big ? 4 : 2;
        for (Waypoints.Mark m : Waypoints.here()) {
            int sx = Math.round(midX + (float) ((m.x + 0.5 - cx) * scale)), sy = Math.round(midY + (float) ((m.z + 0.5 - cz) * scale));
            if (sx < x0 - r || sx >= x0 + w + r || sy < y0 - r || sy >= y0 + h + r) continue;
            int rim = m.pub ? 0xFFFFFFFF : 0xFF000000;                       // a small diamond: a dark rim, or white if public
            for (int i = 0; i <= r; i++) {
                g.fill(sx - i - 1, sy - (r - i) - 1, sx + i + 2, sy + (r - i) + 2, rim);
            }
            for (int i = 0; i <= r; i++) g.fill(sx - i, sy - (r - i), sx + i + 1, sy + (r - i) + 1, m.color);
            if (big) {
                int tw = font.width(m.name);
                g.fill(sx - tw / 2 - 2, sy + r + 2, sx + tw / 2 + 2, sy + r + 12, 0x90000000);
                g.centeredText(font, m.name, sx, sy + r + 3, m.color);
            }
            if (hover != null && Math.abs(mouseX - sx) <= r + 2 && Math.abs(mouseY - sy) <= r + 2) {
                hover.lines.add(Component.literal(m.name).withColor(m.color & 0xFFFFFF));
                if (m.pub) hover.lines.add(Component.literal(m.mine ? "Public (yours)" : "Public, by " + m.by).withColor(0xDDDDDD));
                hover.lines.add(Component.literal("x " + m.x + ", z " + m.z + (m.mine ? "   (right-click to edit)" : "")).withColor(0xAAAAAA));
            }
        }
    }
}
