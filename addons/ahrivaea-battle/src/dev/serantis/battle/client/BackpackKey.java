package dev.serantis.battle.client;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

/** The B key: open your worn backpack, or take it off while sneaking. See Backpacks on the server. */
public final class BackpackKey {
    private static boolean down;

    private BackpackKey() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        boolean now = mc.player != null && mc.gui.screen() == null && InputConstants.isKeyDown(InputConstants.KEY_B);
        if (now && !down) {
            JsonObject o = new JsonObject();
            o.addProperty("kind", "serantis_backpack");
            o.addProperty("off", mc.player.isShiftKeyDown());
            dev.ahrivaea.client.battle.BattleClient.send(o);
        }
        down = now;
    }
}
