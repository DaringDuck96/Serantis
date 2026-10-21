package dev.serantis.battle.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The Wandering Trader: give it a creature from your party, get a random one back. See Wanderer on the server. */
public class WandererScreen extends Screen {
    private static final int W = 300, AQUA = 0xFF6FD8E8;
    private final int entity;
    private String seen = "";
    private int picked = -1;
    private boolean closed;

    public WandererScreen(int entity) {
        super(Component.literal("Wandering Trader"));
        this.entity = entity;
    }

    public static void open(int entity) {
        TradeClient.send("serantis_wander_open", "entity", entity);
        Minecraft.getInstance().gui.setScreen(new WandererScreen(entity));
    }

    private JsonObject trader() {
        JsonObject s = TradeClient.state();
        if (s == null || !s.has("serantis_wanderer")) return null;
        JsonObject w = s.getAsJsonObject("serantis_wanderer");
        return w.get("entity").getAsInt() == entity ? w : null;
    }

    private int left() { return (width - W) / 2; }
    private int top() { return Math.max(6, height / 2 - 100); }

    @Override
    protected void init() {
        JsonObject w = trader();
        JsonArray party = TradeClient.party();
        seen = (w == null ? "" : w.toString()) + party;
        int l = left(), y = top() + 46;
        for (int i = 0; i < party.size(); i++) {
            JsonObject c = party.get(i).getAsJsonObject();
            final int slot = i;
            String label = (i == picked ? "▶ " : "") + c.get("name").getAsString() + "  Lv " + c.get("level").getAsInt();
            if (font.width(label) > W - 16) label = font.plainSubstrByWidth(label, W - 22) + "…";
            Button b = Button.builder(Component.literal(label), btn -> { picked = slot; rebuildWidgets(); })
                    .bounds(l + 8, y + i * 22, W - 16, 20).build();
            b.active = w != null && i != picked;
            addRenderableWidget(b);
        }
        Button swap = Button.builder(Component.literal("Swap it"), b -> {
            JsonObject o = new JsonObject();
            o.addProperty("kind", "serantis_wander_swap");
            o.addProperty("entity", entity);
            o.addProperty("slot", picked);
            dev.ahrivaea.client.battle.BattleClient.send(o);
            picked = -1;
        }).bounds(width / 2 - 104, top() + 46 + 6 * 22 + 8, 100, 20).build();
        swap.active = w != null && picked >= 0 && picked < party.size() && w.get("swaps").getAsInt() > 0;
        addRenderableWidget(swap);
        addRenderableWidget(Button.builder(Component.literal("Leave"), b -> onClose()).bounds(width / 2 + 4, top() + 46 + 6 * 22 + 8, 100, 20).build());
    }

    @Override
    public void tick() {
        super.tick();
        JsonObject w = trader();
        String now = (w == null ? "" : w.toString()) + TradeClient.party();
        if (!now.equals(seen)) rebuildWidgets();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        g.fillGradient(0, 0, width, height, 0xD0081014, 0xD0102028);
        int l = left(), t = top();
        g.fill(l, t, l + W, t + 46 + 6 * 22 + 36, 0xF0101820);
        g.outline(l, t, W, 46 + 6 * 22 + 36, 0xFF2E6070);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        int l = left(), t = top();
        g.text(font, "Wandering Trader", l + 8, t + 8, AQUA);
        JsonObject w = trader();
        if (w == null) { g.text(font, "\"Hello, traveller...\"", l + 8, t + 22, 0xFF9AB0B8); return; }
        int swaps = w.get("swaps").getAsInt();
        g.text(font, "\"Give me a creature, I'll give you another. Same level, any kind.\"", l + 8, t + 20, 0xFF9AB0B8);
        g.text(font, swaps + (swaps == 1 ? " swap" : " swaps") + " left. Pick one of yours:", l + 8, t + 32, 0xFFC8D8E0);
    }

    @Override
    public void onClose() {
        if (!closed) {
            closed = true;
            TradeClient.send("serantis_wander_close", null, null);
        }
        Minecraft.getInstance().gui.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
