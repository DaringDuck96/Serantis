package dev.serantis.battle.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A village bounty board (any bell): today's three contracts. See Bounties on the server. */
public class BountyScreen extends Screen {
    private static final int W = 340, H = 168, GOLD = 0xFFF2C94C, TEXT = 0xFFE6E2D8, DIM = 0xFF9AA4B4, GREEN = 0xFF7CD67C;
    private String seen = "";

    public BountyScreen() { super(Component.literal("Bounty Board")); }

    public static void open() {
        send(-1);
        Minecraft.getInstance().gui.setScreen(new BountyScreen());
    }

    private static void send(int index) {
        JsonObject o = new JsonObject();
        o.addProperty("kind", "serantis_bounty");
        o.addProperty("index", index);
        dev.ahrivaea.client.battle.BattleClient.send(o);
    }

    private static JsonObject board() {
        JsonObject s = TradeClient.state();
        return s != null && s.has("serantis_bounties") ? s.getAsJsonObject("serantis_bounties") : null;
    }

    private int left() { return (width - W) / 2; }
    private int top() { return Math.max(4, (height - H) / 2); }

    @Override
    protected void init() {
        JsonObject b = board();
        seen = b == null ? "" : b.toString();
        if (b != null) {
            JsonArray cs = b.getAsJsonArray("contracts");
            for (int i = 0; i < cs.size(); i++) {
                JsonObject c = cs.get(i).getAsJsonObject();
                boolean claimed = c.get("claimed").getAsBoolean(), done = c.get("progress").getAsInt() >= c.get("goal").getAsInt();
                boolean deliver = c.get("deliver").getAsBoolean();
                String label = claimed ? "Claimed" : done ? "Claim" : deliver ? "Hand in" : "...";
                int idx = i;
                Button btn = Button.builder(Component.literal(label), x -> send(idx)).bounds(left() + W - 78, top() + 30 + i * 38, 70, 20).build();
                btn.active = !claimed && (done || deliver);
                addRenderableWidget(btn);
            }
        }
        addRenderableWidget(Button.builder(Component.literal("Done"), x -> onClose()).bounds(width / 2 - 40, top() + H - 26, 80, 20).build());
    }

    @Override
    public void tick() {
        super.tick();
        JsonObject b = board();
        if (!(b == null ? "" : b.toString()).equals(seen)) rebuildWidgets();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        g.fillGradient(0, 0, width, height, 0xD0100C08, 0xD0201810);
        int l = left(), t = top();
        g.fill(l, t, l + W, t + H, 0xF01C1610);
        g.outline(l, t, W, H, 0xFF7A5A2A);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        int l = left() + 8, t = top();
        g.text(font, "Bounty Board", l, t + 8, GOLD);
        JsonObject b = board();
        if (b == null) { g.text(font, "Reading the board...", l, t + 30, DIM); return; }
        String fresh = "New contracts in " + b.get("minutesLeft").getAsInt() + " min";
        g.text(font, fresh, left() + W - 8 - font.width(fresh), t + 8, DIM);
        JsonArray cs = b.getAsJsonArray("contracts");
        for (int i = 0; i < cs.size(); i++) {
            JsonObject c = cs.get(i).getAsJsonObject();
            int y = t + 26 + i * 38;
            boolean claimed = c.get("claimed").getAsBoolean();
            int p = c.get("progress").getAsInt(), goal = c.get("goal").getAsInt();
            g.text(font, font.plainSubstrByWidth(c.get("text").getAsString(), W - 100), l, y, claimed ? DIM : TEXT);
            String sub = (claimed ? "Done" : p + "/" + goal) + " · " + c.get("xp").getAsInt() + " XP + " + c.get("ingots").getAsInt() + " Orichalcum";
            g.text(font, sub, l, y + 12, claimed ? DIM : p >= goal ? GREEN : DIM);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
