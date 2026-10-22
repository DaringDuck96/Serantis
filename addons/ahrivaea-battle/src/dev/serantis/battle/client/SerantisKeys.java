package dev.serantis.battle.client;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;

/** Serantis keys, all rebindable under Controls > Serantis: the backpack (B) and the Helm of Linx (Z, R, H, V). */
public final class SerantisKeys {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.parse("serantis_battle:serantis"));
    public static final KeyMapping BACKPACK = new KeyMapping("key.serantis_battle.backpack", InputConstants.KEY_B, CATEGORY);
    public static final KeyMapping PHASE = new KeyMapping("key.serantis_battle.phase_jump", InputConstants.KEY_Z, CATEGORY);
    public static final KeyMapping LIFT = new KeyMapping("key.serantis_battle.shape_lift", InputConstants.KEY_R, CATEGORY);   // X and C are vanilla's toolbar keys
    public static final KeyMapping WAYMARK = new KeyMapping("key.serantis_battle.waymark_home", InputConstants.KEY_H, CATEGORY);   // G is the guide
    public static final KeyMapping HIGH_JUMP = new KeyMapping("key.serantis_battle.high_jump", InputConstants.KEY_V, CATEGORY);
    public static final KeyMapping CRAFT = new KeyMapping("key.serantis_battle.craft_u", InputConstants.KEY_U, CATEGORY);   // Tradesman (K is the party screen)
    public static final KeyMapping[] ALL = {BACKPACK, PHASE, LIFT, WAYMARK, HIGH_JUMP, CRAFT};

    /** Whether a shape has been picked from the Shape Lift menu (plain X then lifts/sets down rather than opening it). */
    static boolean shapePicked;

    private SerantisKeys() {}

    private static Object helloFor;                                     // the connection we last said hello on
    private static int helloTicks;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.getConnection() != null && helloFor != mc.getConnection()) { helloFor = mc.getConnection(); helloTicks = 0; }
        var vst = TradeClient.state();
        boolean confirmed = vst != null && vst.has("serantis_version_ok");
        if (mc.player != null && mc.getConnection() != null && (!confirmed || helloTicks == 0) && helloTicks >= 0 && helloTicks % 60 == 0) {   // tell the server our version until it confirms (see VersionCheck)
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            o.addProperty("kind", "serantis_hello");
            o.addProperty("version", dev.serantis.battle.VersionCheck.version());
            dev.ahrivaea.client.battle.BattleClient.send(o);
        }
        if (mc.player != null && mc.getConnection() != null && helloTicks >= 0 && ++helloTicks > 20 * 20) {   // 20 s in: does this server have Serantis?
            var st = TradeClient.state();
            if (st == null || !st.has("serantis_server_version")) {
                helloTicks = -1;
                mc.getConnection().getConnection().disconnect(net.minecraft.network.chat.Component.literal(
                        "This server doesn't have the Serantis mods, so they can't be used here.\nAsk its owner to install Serantis "
                                + dev.serantis.battle.VersionCheck.version() + " (" + dev.serantis.battle.VersionCheck.DOWNLOAD + ")."));
            } else helloTicks = -1;                                 // it does: nothing more to check on this connection
        }
        BaseLooksClient.tick();                                          // base looks (see BaseLooks)
        if (mc.player == null) return;
        boolean sneak = mc.player.isShiftKeyDown();
        while (BACKPACK.consumeClick()) {
            JsonObject o = new JsonObject();
            o.addProperty("kind", "serantis_backpack");
            o.addProperty("off", sneak);
            dev.ahrivaea.client.battle.BattleClient.send(o);
        }
        while (CRAFT.consumeClick()) {                                 // Tradesman: crafting without a table
            JsonObject o = new JsonObject();
            o.addProperty("kind", "serantis_craft");
            dev.ahrivaea.client.battle.BattleClient.send(o);
        }
        boolean helm = mc.player.getItemBySlot(EquipmentSlot.HEAD).is(dev.serantis.battle.Linx.HELM);
        if (!helm) shapePicked = false;
        while (PHASE.consumeClick()) if (helm) send(1, sneak, null, 0);
        while (LIFT.consumeClick()) {
            if (!helm) continue;
            var st = TradeClient.state();
            boolean picked = st != null && st.has("serantis_linx_lift");   // a shape is being aimed or carried
            if (sneak || !picked) mc.gui.setScreen(new LinxShapeScreen());   // after each set-down: pick the shape and size again
            else send(2, false, null, 0);
        }
        while (WAYMARK.consumeClick()) if (helm) send(3, sneak, null, 0);
        while (HIGH_JUMP.consumeClick()) if (helm) send(4, sneak, null, 0);
    }

    static void send(int ability, boolean sneak, String shape, int size) {
        JsonObject o = new JsonObject();
        o.addProperty("kind", "serantis_linx");
        o.addProperty("ability", ability);
        o.addProperty("sneak", sneak);
        if (shape != null) { o.addProperty("shape", shape); o.addProperty("size", size); o.addProperty("any", LinxShapeScreen.any); }
        dev.ahrivaea.client.battle.BattleClient.send(o);
    }
}
