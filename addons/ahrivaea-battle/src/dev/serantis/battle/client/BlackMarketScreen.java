package dev.serantis.battle.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The Black Market Merchant's wares: Rare or rarer creatures for XP. See Market on the server. */
public class BlackMarketScreen extends Screen {
    private static final int W = 380, ROW = 48, PURPLE = 0xFFB07CE8;
    private static final String[] STATS = {"HP", "Atk", "Def", "Spd", "Mag", "Lck", "Cha"};
    private final int entity;
    private String seen = "";
    private boolean closed;

    public BlackMarketScreen(int entity) {
        super(Component.literal("Black Market"));
        this.entity = entity;
    }

    public static void open(int entity) {
        TradeClient.send("serantis_market_open", "entity", entity);
        Minecraft.getInstance().gui.setScreen(new BlackMarketScreen(entity));
    }

    private JsonObject market() {
        JsonObject s = TradeClient.state();
        if (s == null || !s.has("serantis_market")) return null;
        JsonObject m = s.getAsJsonObject("serantis_market");
        return m.get("entity").getAsInt() == entity ? m : null;
    }

    private int left() { return (width - W) / 2; }
    private int top() { return Math.max(6, (height - (40 + 5 * ROW + 30)) / 2); }

    @Override
    protected void init() {
        JsonObject m = market();
        seen = m == null ? "" : m.toString();
        if (m != null) {
            JsonArray offers = m.getAsJsonArray("offers");
            int price = m.get("price").getAsInt(), xp = m.get("xp").getAsInt();
            for (int i = 0; i < offers.size(); i++) {
                final int index = i;
                Button b = Button.builder(Component.literal("Buy · " + price + " XP"), btn -> {
                    JsonObject o = new JsonObject();
                    o.addProperty("kind", "serantis_market_buy");
                    o.addProperty("entity", entity);
                    o.addProperty("index", index);
                    dev.ahrivaea.client.battle.BattleClient.send(o);
                }).bounds(left() + W - 98, top() + 40 + i * ROW + 12, 90, 20).build();
                b.active = xp >= price;
                addRenderableWidget(b);
            }
        }
        addRenderableWidget(Button.builder(Component.literal("Leave"), b -> onClose()).bounds(width / 2 - 40, height - 28, 80, 20).build());
    }

    @Override
    public void tick() {
        super.tick();
        JsonObject m = market();
        String now = m == null ? "" : m.toString();
        if (!now.equals(seen)) rebuildWidgets();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        g.fillGradient(0, 0, width, height, 0xE0080610, 0xE0180C24);
        int l = left(), t = top();
        JsonObject m = market();
        int rows = m == null ? 1 : Math.max(1, m.getAsJsonArray("offers").size());
        g.fill(l, t, l + W, t + 40 + rows * ROW + 6, 0xF0120E1A);
        g.outline(l, t, W, 40 + rows * ROW + 6, 0xFF5A3A80);
        for (int i = 0; i < rows; i++) g.fill(l + 4, t + 40 + i * ROW, l + W - 4, t + 40 + i * ROW + ROW - 4, i % 2 == 0 ? 0x24FFFFFF : 0x12FFFFFF);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        int l = left(), t = top();
        g.text(font, "Black Market", l + 8, t + 8, PURPLE);
        JsonObject m = market();
        if (m == null) { g.text(font, "\"Let's see what I've got...\"", l + 8, t + 22, 0xFF9A90B0); return; }
        g.text(font, "\"Rare finds. No questions asked.\"  Your XP: " + m.get("xp").getAsInt(), l + 8, t + 22, 0xFF9A90B0);
        JsonArray offers = m.getAsJsonArray("offers");
        if (offers.isEmpty()) g.text(font, "Sold out.", l + 8, t + 46, 0xFFB0B0C0);
        for (int i = 0; i < offers.size(); i++) {
            JsonObject c = offers.get(i).getAsJsonObject();
            int y = t + 40 + i * ROW + 3;
            String rarity = c.has("serantis_rarity") ? c.get("serantis_rarity").getAsString() : "";
            int color = switch (rarity) { case "Legendary" -> 0xFFFFC040; case "Super Rare" -> 0xFFE070F0; default -> 0xFF70B8FF; };
            g.text(font, font.plainSubstrByWidth(c.get("name").getAsString(), W - 120), l + 10, y, 0xFFFFFFFF);
            String influence = c.has("influence") && c.get("influence").isJsonPrimitive() ? c.get("influence").getAsString() : "";
            g.text(font, rarity, l + 10, y + 11, color);
            g.text(font, "Lv " + c.get("level").getAsInt() + (influence.isEmpty() ? "" : " · " + influence), l + 10 + font.width(rarity) + 8, y + 11, 0xFFC8D0E0);
            if (c.has("stats") && c.get("stats").isJsonArray()) {
                StringBuilder sb = new StringBuilder();
                JsonArray s = c.getAsJsonArray("stats");
                for (int k = 0; k < s.size() && k < STATS.length; k++) sb.append(STATS[k]).append(' ').append(s.get(k).getAsJsonArray().get(0).getAsInt()).append("  ");
                g.text(font, font.plainSubstrByWidth(sb.toString().trim(), W - 120), l + 10, y + 23, 0xFF9AA4B4);
            }
        }
    }

    @Override
    public void onClose() {
        if (!closed) {
            closed = true;
            TradeClient.send("serantis_market_close", null, null);
        }
        Minecraft.getInstance().gui.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
