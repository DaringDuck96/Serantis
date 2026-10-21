package dev.serantis.battle.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Linking a spell to a Magic Staff at an enchanting table: three spells, pick one. See Staffs on the server. */
public class StaffScreen extends Screen {
    private static final int W = 320, GOLD = 0xFFF2C94C, TEXT = 0xFFE6E2D8, DIM = 0xFF9AA4B4, RED = 0xFFE07060;
    private final BlockPos table;
    private String seen = "";
    private boolean closed;

    public StaffScreen(BlockPos table) {
        super(Component.literal("Link a spell"));
        this.table = table;
    }

    public static void open(BlockPos table) {
        JsonObject o = new JsonObject();
        o.addProperty("kind", "serantis_staff_open");
        o.addProperty("x", table.getX());
        o.addProperty("y", table.getY());
        o.addProperty("z", table.getZ());
        dev.ahrivaea.client.battle.BattleClient.send(o);
        Minecraft.getInstance().gui.setScreen(new StaffScreen(table.immutable()));
    }

    private JsonObject data() {
        JsonObject s = TradeClient.state();
        return s != null && s.has("serantis_staff") ? s.getAsJsonObject("serantis_staff") : null;
    }

    private int top() { return Math.max(6, height / 2 - 80); }

    @Override
    protected void init() {
        JsonObject d = data();
        seen = d == null ? "" : d.toString();
        if (d == null) return;
        JsonArray spells = d.getAsJsonArray("spells");
        boolean afford = d.get("xp").getAsInt() >= d.get("cost").getAsInt();
        int l = (width - W) / 2, y = top() + 40;
        for (int i = 0; i < spells.size(); i++) {
            JsonObject m = spells.get(i).getAsJsonObject();
            final int index = i;
            Button b = Button.builder(Component.literal(m.get("name").getAsString() + "  (" + m.get("influence").getAsString() + ")"), btn -> {
                JsonObject o = new JsonObject();
                o.addProperty("kind", "serantis_staff_link");
                o.addProperty("index", index);
                dev.ahrivaea.client.battle.BattleClient.send(o);
                closed = true;
                Minecraft.getInstance().gui.setScreen(null);
            }).bounds(l + 10, y + i * 34, W - 20, 20)
                    .tooltip(Tooltip.create(Component.literal(m.get("name").getAsString() + "\n" + m.get("line").getAsString() + "\n\n" + m.get("explain").getAsString())))
                    .build();
            b.active = afford;
            addRenderableWidget(b);
        }
        addRenderableWidget(Button.builder(Component.literal("Not now"), b -> onClose()).bounds(width / 2 - 50, y + 3 * 34 + 6, 100, 20).build());
    }

    @Override
    public void tick() {
        super.tick();
        JsonObject d = data();
        if (!(d == null ? "" : d.toString()).equals(seen)) rebuildWidgets();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        g.fillGradient(0, 0, width, height, 0xD0100818, 0xD0201030);
        int l = (width - W) / 2, t = top();
        g.fill(l, t, l + W, t + 40 + 3 * 34 + 36, 0xF0140C20);
        g.outline(l, t, W, 40 + 3 * 34 + 36, 0xFF6A44A8);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        int l = (width - W) / 2 + 10, t = top();
        g.text(font, "Link a spell to your staff", l, t + 8, GOLD);
        JsonObject d = data();
        if (d == null) { g.text(font, "The table hums...", l, t + 22, DIM); return; }
        int cost = d.get("cost").getAsInt(), xp = d.get("xp").getAsInt();
        g.text(font, "Pick one, for good. Costs " + cost + " XP (you have " + xp + ").", l, t + 22, xp < cost ? RED : DIM);
        JsonArray spells = d.getAsJsonArray("spells");
        for (int i = 0; i < spells.size(); i++) {
            String line = spells.get(i).getAsJsonObject().get("line").getAsString();
            g.text(font, font.plainSubstrByWidth(line, W - 24), l + 2, t + 40 + i * 34 + 22, TEXT);
        }
    }

    @Override
    public void onClose() {
        if (!closed) { closed = true; TradeClient.send("serantis_staff_close", null, null); }
        Minecraft.getInstance().gui.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
