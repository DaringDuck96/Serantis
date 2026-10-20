package dev.serantis.battle.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The trade itself: pick one creature from your party, see what the other player offers, and confirm.
 * Changing either pick needs both players to confirm again. Closing the screen cancels the trade.
 */
public class TradeScreen extends Screen {
    private static final String[] STATS = {"HP", "Atk", "Def", "Spd", "Mag", "Lck", "Cha"};
    private final int id;
    private String seen = "";
    private boolean leaving;

    public TradeScreen(int id) {
        super(Component.literal("Trade"));
        this.id = id;
    }

    private JsonObject trade() {
        JsonObject t = TradeClient.trade();
        return t != null && t.get("id").getAsInt() == id && "open".equals(t.get("stage").getAsString()) ? t : null;
    }

    private int panelW() { return Math.min(420, width - 20); }
    private int left() { return (width - panelW()) / 2; }
    private int top() { return Math.max(6, height / 2 - 110); }

    @Override
    protected void init() {
        JsonObject t = trade();
        seen = t == null ? "" : t.toString() + TradeClient.party().size();
        if (t == null) return;
        int mySlot = t.get("mySlot").getAsInt();
        JsonArray party = TradeClient.party();
        int l = left(), y = top() + 34, colW = panelW() / 2 - 10;
        for (int i = 0; i < party.size(); i++) {
            JsonObject c = party.get(i).getAsJsonObject();
            String label = (i == mySlot ? "▶ " : "") + c.get("name").getAsString() + "  Lv " + c.get("level").getAsInt();
            if (font.width(label) > colW - 8) label = font.plainSubstrByWidth(label, colW - 14) + "…";
            final int slot = i;
            Button b = Button.builder(Component.literal(label), btn -> TradeClient.send("serantis_trade_pick", "slot", slot))
                    .bounds(l + 4, y + i * 22, colW, 20).build();
            b.active = i != mySlot;
            addRenderableWidget(b);
        }
        boolean ready = mySlot >= 0 && t.has("theirs") && !t.get("myOk").getAsBoolean();
        int by = top() + 34 + 6 * 22 + 30;
        Button confirm = Button.builder(Component.literal(t.get("myOk").getAsBoolean() ? "Confirmed ✔" : "Confirm trade"),
                b -> TradeClient.send("serantis_trade_confirm", null, null)).bounds(width / 2 - 124, by, 120, 20).build();
        confirm.active = ready;
        addRenderableWidget(confirm);
        addRenderableWidget(Button.builder(Component.literal("Cancel trade"), b -> onClose()).bounds(width / 2 + 4, by, 120, 20).build());
    }

    @Override
    public void tick() {
        super.tick();
        JsonObject t = trade();
        if (t == null) { leaving = true; Minecraft.getInstance().gui.setScreen(null); return; }   // done or cancelled
        String now = t.toString() + TradeClient.party().size();
        if (!now.equals(seen)) rebuildWidgets();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        g.fillGradient(0, 0, width, height, 0xB00C0B14, 0xB0181226);
        int l = left(), t = top(), w = panelW(), h = 34 + 6 * 22 + 58;
        g.fill(l - 4, t - 4, l + w + 4, t + h, 0xF014161C);
        g.outline(l - 4, t - 4, w + 8, h + 4, 0xFF3C4560);
        g.fill(l + w / 2 - 1, t + 16, l + w / 2, t + 34 + 6 * 22, 0x40FFFFFF);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        JsonObject t = trade();
        if (t == null) return;
        String partner = t.get("partner").getAsString();
        int l = left(), top = top(), w = panelW(), rx = l + w / 2 + 8;
        g.centeredText(font, "Trading with " + partner, width / 2, top, 0xFFF2C94C);
        g.text(font, "Your party: pick one to offer", l + 4, top + 20, 0xFFB8C0D0);
        g.text(font, partner + " offers:", rx, top + 20, 0xFFB8C0D0);

        if (t.has("theirs")) card(g, t.getAsJsonObject("theirs"), rx, top + 34, w / 2 - 14);
        else g.text(font, "Waiting for " + partner + " to pick…", rx, top + 36, 0xFF8890A0);

        boolean mine = t.get("mySlot").getAsInt() >= 0, theirs = t.has("theirs");
        boolean myOk = t.get("myOk").getAsBoolean(), theirOk = t.get("theirOk").getAsBoolean();
        String status = !mine ? "Pick a creature to offer."
                : !theirs ? "Waiting for " + partner + " to pick a creature."
                : myOk && !theirOk ? "Waiting for " + partner + " to confirm…"
                : theirOk && !myOk ? partner + " has confirmed. Confirm to swap."
                : "Both picked. Confirm to swap.";
        g.centeredText(font, status, width / 2, top + 34 + 6 * 22 + 14, theirOk && !myOk ? 0xFF7FE08A : 0xFFDDDDDD);
    }

    /** What the other player is offering. */
    private void card(GuiGraphicsExtractor g, JsonObject c, int x, int y, int w) {
        List<String> lines = new ArrayList<>();
        g.text(font, font.plainSubstrByWidth(c.get("name").getAsString(), w), x, y, 0xFFFFFFFF);
        String influence = c.has("influence") && c.get("influence").isJsonPrimitive() ? c.get("influence").getAsString() : "";
        lines.add("Lv " + c.get("level").getAsInt() + (influence.isEmpty() ? "" : " · " + influence)
                + (c.has("serantis_rarity") ? " · " + c.get("serantis_rarity").getAsString() : ""));
        if (c.has("hp") && c.has("maxHp") && c.get("hp").isJsonPrimitive()) lines.add("HP " + c.get("hp").getAsInt() + "/" + c.get("maxHp").getAsInt()
                + (c.has("squares") ? " · moves " + c.get("squares").getAsInt() + " tiles" : ""));
        if (c.has("stats") && c.get("stats").isJsonArray()) {
            JsonArray s = c.getAsJsonArray("stats");
            StringBuilder a = new StringBuilder(), b = new StringBuilder();
            for (int i = 0; i < s.size() && i < STATS.length; i++) {
                int v = s.get(i).getAsJsonArray().get(0).getAsInt();
                (i < 4 ? a : b).append(STATS[i]).append(' ').append(v).append("  ");
            }
            lines.add(a.toString().trim());
            lines.add(b.toString().trim());
        }
        if (c.has("jobs") && c.get("jobs").isJsonPrimitive()) lines.add("Jobs: " + c.get("jobs").getAsString());
        int yy = y + 12;
        for (String line : lines) {
            g.text(font, font.plainSubstrByWidth(line, w), x, yy, 0xFFC8D0E0);
            yy += 11;
        }
    }

    @Override
    public void onClose() {
        if (!leaving && trade() != null) TradeClient.send("serantis_trade_cancel", null, null);
        leaving = true;
        Minecraft.getInstance().gui.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
