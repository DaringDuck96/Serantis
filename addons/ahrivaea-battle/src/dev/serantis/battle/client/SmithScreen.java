package dev.serantis.battle.client;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A Smith improving the weapon in your main hand (+1 attack, up to +5). See Honing on the server. */
public class SmithScreen extends Screen {
    private static final int W = 300, H = 128, GOLD = 0xFFF2C94C, DIM = 0xFF9AA4B4, TEXT = 0xFFE6E2D8;
    private final int entity;
    private String seen = "";
    private boolean closed;

    public SmithScreen(int entity) {
        super(Component.literal("Smith"));
        this.entity = entity;
    }

    public static void open(int entity) {
        TradeClient.send("serantis_smith_open", "entity", entity);
        Minecraft.getInstance().gui.setScreen(new SmithScreen(entity));
    }

    private JsonObject smith() {
        JsonObject s = TradeClient.state();
        if (s == null || !s.has("serantis_smith")) return null;
        JsonObject t = s.getAsJsonObject("serantis_smith");
        return t.get("entity").getAsInt() == entity ? t : null;
    }

    private int left() { return (width - W) / 2; }
    private int top() { return Math.max(4, (height - H) / 2); }

    @Override
    protected void init() {
        JsonObject t = smith();
        seen = t == null ? "" : t.toString();
        int fy = top() + H - 28;
        boolean has = t != null && t.has("name");
        int lvl = has ? t.get("level").getAsInt() : 0, max = t == null ? 5 : t.get("max").getAsInt();
        int cost = has ? t.get("cost").getAsInt() : 0;
        String label = !has ? "Improve" : lvl >= max ? "Fully improved" : "Improve to +" + (lvl + 1) + " (" + cost + " XP)";
        Button go = Button.builder(Component.literal(label), b -> TradeClient.send("serantis_smith_improve", null, null))
                .bounds(width / 2 - 124, fy, 160, 20).build();
        go.active = has && lvl < max && t.get("xp").getAsInt() >= cost;
        addRenderableWidget(go);
        addRenderableWidget(Button.builder(Component.literal("Leave"), b -> onClose()).bounds(width / 2 + 44, fy, 80, 20).build());
    }

    @Override
    public void tick() {
        super.tick();
        JsonObject t = smith();
        if (!(t == null ? "" : t.toString()).equals(seen)) rebuildWidgets();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        g.fillGradient(0, 0, width, height, 0xD0100C08, 0xD0201810);
        int l = left(), t = top();
        g.fill(l, t, l + W, t + H, 0xF0181410);
        g.outline(l, t, W, H, 0xFF6B5420);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        int l = left() + 8, top = top();
        g.text(font, "Smith", l, top + 8, GOLD);
        JsonObject t = smith();
        if (t == null) { g.text(font, "\"Let's see that blade...\"", l, top + 22, DIM); return; }
        g.text(font, "\"I can sharpen any weapon, +1 at a time, up to +" + t.get("max").getAsInt() + ".\"", l, top + 22, DIM);
        if (!t.has("name")) { g.text(font, "Hold the weapon you want improved.", l, top + 46, TEXT); return; }
        int lvl = t.get("level").getAsInt(), max = t.get("max").getAsInt();
        g.text(font, t.get("name").getAsString() + ": +" + lvl + " of +" + max + " (+" + lvl + " attack damage)", l, top + 46, TEXT);
        StringBuilder pips = new StringBuilder();
        for (int i = 0; i < max; i++) pips.append(i < lvl ? "■ " : "□ ");
        g.text(font, pips.toString(), l, top + 60, GOLD);
        int xp = t.get("xp").getAsInt(), cost = t.get("cost").getAsInt();
        if (lvl < max)
            g.text(font, xp < cost ? "You need " + cost + " XP (you have " + xp + ")." : "You have " + xp + " XP.", l, top + 76, xp < cost ? 0xFFE07060 : DIM);
    }

    @Override
    public void onClose() {
        if (!closed) {
            closed = true;
            TradeClient.send("serantis_smith_close", null, null);
        }
        Minecraft.getInstance().gui.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
