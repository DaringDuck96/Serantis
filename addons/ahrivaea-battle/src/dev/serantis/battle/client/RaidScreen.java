package dev.serantis.battle.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/** Goblins at your base: pay them off or fight. See GoblinRaids on the server. */
public class RaidScreen extends Screen {
    private static final int W = 320, H = 170, GOLD = 0xFFF2C94C, DIM = 0xFF9AA4B4, TEXT = 0xFFE6E2D8, RED = 0xFFE07060;
    private String seen = "";
    private static String dismissed = "";
    private boolean closed;
    private long openedAt;
    private int leftAtOpen;

    public RaidScreen() { super(Component.literal("Goblin Raid")); }

    static JsonObject raid() {
        JsonObject s = TradeClient.state();
        return s != null && s.has("serantis_raid") ? s.getAsJsonObject("serantis_raid") : null;
    }

    /** One of the goblins raiding this player's base. */
    public static boolean isRaider(Entity e) {
        JsonObject r = raid();
        if (r == null || e == null) return false;
        for (JsonElement id : r.getAsJsonArray("entities")) if (id.getAsInt() == e.getId()) return true;
        return false;
    }

    /** Every client tick: the server says we're talking to the goblins. */
    public static void autoOpen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.screen() != null) return;
        JsonObject r = raid();
        if (r != null && r.get("talking").getAsBoolean() && !r.toString().equals(dismissed)) mc.gui.setScreen(new RaidScreen());
    }

    private int left() { return (width - W) / 2; }
    private int top() { return Math.max(4, (height - H) / 2); }

    @Override
    protected void init() {
        JsonObject r = raid();
        seen = r == null ? "" : r.toString();
        if (r != null) { openedAt = System.currentTimeMillis(); leftAtOpen = r.get("left").getAsInt(); }
        int fy = top() + H - 28;
        int bribe = r == null ? 0 : r.get("bribe").getAsInt();
        Button pay = Button.builder(Component.literal("Pay " + bribe + " XP"), b -> TradeClient.send("serantis_raid_pay", null, null))
                .bounds(width / 2 - 150, fy, 96, 20).build();
        pay.active = r != null && r.get("xp").getAsInt() >= bribe;
        addRenderableWidget(pay);
        Button fight = Button.builder(Component.literal("Fight!"), b -> { closed = true; dismiss(); TradeClient.send("serantis_raid_fight", null, null); Minecraft.getInstance().gui.setScreen(null); })
                .bounds(width / 2 - 48, fy, 96, 20).build();
        fight.active = r != null;
        addRenderableWidget(fight);
        addRenderableWidget(Button.builder(Component.literal("Not yet"), b -> onClose()).bounds(width / 2 + 54, fy, 96, 20).build());
    }

    @Override
    public void tick() {
        super.tick();
        JsonObject r = raid();
        if (r == null) { closed = true; Minecraft.getInstance().gui.setScreen(null); return; }
        if (!r.toString().equals(seen)) rebuildWidgets();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        g.fillGradient(0, 0, width, height, 0xD0100808, 0xD0201010);
        int l = left(), t = top();
        g.fill(l, t, l + W, t + H, 0xF0181010);
        g.outline(l, t, W, H, 0xFF8A2A20);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        JsonObject r = raid();
        if (r == null) return;
        int l = left() + 8, y = top() + 8;
        g.text(font, "Goblin Raid! (" + r.get("tier").getAsString() + ")", l, y, GOLD);
        long s = Math.max(0, leftAtOpen - (System.currentTimeMillis() - openedAt) / 1000);
        String clock = (s / 60) + ":" + String.format("%02d", s % 60);
        g.text(font, clock, left() + W - 8 - font.width(clock), y, s <= 30 ? RED : DIM);
        y += 16;
        g.text(font, "\"Shiny base you got. Be a shame if something happened to it...\"", l, y, DIM);
        y += 16;
        g.text(font, r.get("count").getAsInt() + " raiders, Level " + r.get("levels").getAsString() + ":", l, y, TEXT);
        y += 11;
        g.text(font, font.plainSubstrByWidth(r.get("mix").getAsString(), W - 16), l, y, TEXT);
        y += 16;
        int d = r.get("defenders").getAsInt();
        g.text(font, "You'd fight with your party" + (d > 0 ? " and " + d + " base defender" + (d == 1 ? "" : "s") : " (no base defenders)") + ".", l, y, DIM);
        y += 14;
        g.text(font, "Lose (or flee) and they take " + r.get("lossPct").getAsInt() + "% of your XP (" + r.get("loss").getAsInt() + " XP).", l, y, RED);
        y += 11;
        g.text(font, "Pay " + r.get("bribe").getAsInt() + " XP and they leave. Win and your base is safe.", l, y, DIM);
    }

    private static void dismiss() {
        JsonObject r = raid();
        dismissed = r == null ? "" : r.toString();
    }

    @Override
    public void onClose() {
        dismiss();
        if (!closed) {
            closed = true;
            TradeClient.send("serantis_raid_close", null, null);
        }
        Minecraft.getInstance().gui.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
