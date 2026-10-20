package dev.serantis.battle.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.ahrivaea.client.battle.BattleClient;
import dev.serantis.battle.mixin.client.PartyClientAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.player.Player;

/** The client side of creature trades (see Trades on the server). */
public final class TradeClient {
    private static int shownInvite = -1, shownTrade = -1;

    private TradeClient() {}

    public static JsonObject state() {
        try { return PartyClientAccessor.serantis$state(); } catch (RuntimeException e) { return null; }
    }

    /** The trade this player is in, or null. */
    public static JsonObject trade() {
        JsonObject s = state();
        return s != null && s.has("serantis_trade") && s.get("serantis_trade").isJsonObject() ? s.getAsJsonObject("serantis_trade") : null;
    }

    public static JsonArray party() {
        JsonObject s = state();
        return s != null && s.has("party") && s.get("party").isJsonArray() ? s.getAsJsonArray("party") : new JsonArray();
    }

    public static void send(String kind, String key, Object value) {
        JsonObject o = new JsonObject();
        o.addProperty("kind", kind);
        if (key != null) {
            if (value instanceof Number n) o.addProperty(key, n);
            else if (value instanceof Boolean b) o.addProperty(key, b);
            else o.addProperty(key, String.valueOf(value));
        }
        BattleClient.send(o);
    }

    /** Right-clicked another player: ask whether to send them a trade request. */
    public static boolean askToTrade(Player other) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != null) return false;
        mc.gui.setScreen(new TradeAskScreen(other.getPlainTextName(), other.getUUID().toString()));
        return true;
    }

    /** Every client tick: open the invite or the trade screen when the server says so. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        JsonObject t = trade();
        if (t == null) return;
        int id = t.get("id").getAsInt();
        String stage = t.get("stage").getAsString();
        Screen open = mc.gui.screen();
        boolean free = open == null || open instanceof TradeAskScreen || open instanceof TradeInviteScreen;
        if ("invite".equals(stage) && id != shownInvite && open == null) {
            shownInvite = id;
            mc.gui.setScreen(new TradeInviteScreen(id));
        } else if ("open".equals(stage) && id != shownTrade && free) {
            shownTrade = id;
            mc.gui.setScreen(new TradeScreen(id));
        }
    }
}
