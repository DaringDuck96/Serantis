package dev.serantis.battle.mixin.client;

import dev.ahrivaea.map.client.MapScreen;
import dev.serantis.battle.client.map.WaypointScreen;
import dev.serantis.battle.client.map.Waypoints;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The full map (M): right-click a spot to mark and name it, or right-click a marker to change or remove it. */
@Mixin(MapScreen.class)
public abstract class MapScreenWaypointsMixin extends Screen {
    @Shadow public double cx;
    @Shadow public double cz;
    @Shadow public float scale;

    protected MapScreenWaypointsMixin(Component title) { super(title); }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        if (event.button() == 1 && my >= 24 && my < height - 14) {
            double midY = 24 + (height - 24 - 14) / 2.0;
            Waypoints.Mark near = null;
            for (Waypoints.Mark m : Waypoints.here()) {
                double sx = width / 2.0 + (m.x + 0.5 - cx) * scale, sy = midY + (m.z + 0.5 - cz) * scale;
                if (Math.abs(sx - mx) <= 7 && Math.abs(sy - my) <= 7) { near = m; break; }
            }
            int bx = (int) Math.floor(cx + (mx - width / 2.0) / scale), bz = (int) Math.floor(cz + (my - midY) / scale);
            Minecraft.getInstance().gui.setScreen(new WaypointScreen(this, bx, bz, near));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void serantis$hint(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial, CallbackInfo ci) {
        String hint = "Right-click to mark a place";
        int x = Math.max(6 + font.width("Map: The End") + 16, 0);
        if (x + font.width(hint) < width - 260) g.text(font, hint, x, 8, 0xFF9AA4B4);
    }
}
