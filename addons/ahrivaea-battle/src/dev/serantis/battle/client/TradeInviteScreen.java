package dev.serantis.battle.client;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Someone wants to trade creatures: accept or decline. */
public class TradeInviteScreen extends Screen {
    private final int id;
    private boolean answered;
    private final long opened = System.currentTimeMillis();
    private int startLeft = -1;

    public TradeInviteScreen(int id) {
        super(Component.literal("Trade request"));
        this.id = id;
    }

    private JsonObject trade() {
        JsonObject t = TradeClient.trade();
        return t != null && t.get("id").getAsInt() == id ? t : null;
    }

    private void answer(boolean yes) {
        if (!answered) {
            answered = true;
            TradeClient.send("serantis_trade_answer", "accept", yes);
        }
        if (!yes) Minecraft.getInstance().gui.setScreen(null);
    }

    @Override
    protected void init() {
        int cx = width / 2, cy = height / 2;
        addRenderableWidget(Button.builder(Component.literal("Accept"), b -> answer(true)).bounds(cx - 102, cy + 8, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Decline"), b -> answer(false)).bounds(cx + 2, cy + 8, 100, 20).build());
    }

    @Override
    public void tick() {
        super.tick();
        JsonObject t = trade();
        if (t == null || !"invite".equals(t.get("stage").getAsString()) && !answered || !answered && secondsLeft() <= 0)
            Minecraft.getInstance().gui.setScreen(null);              // expired or cancelled
    }

    private int secondsLeft() {
        JsonObject t = trade();
        if (startLeft < 0 && t != null && t.has("secondsLeft")) startLeft = t.get("secondsLeft").getAsInt();
        return startLeft < 0 ? 60 : startLeft - (int) ((System.currentTimeMillis() - opened) / 1000);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        int cx = width / 2, cy = height / 2;
        g.fill(cx - 132, cy - 36, cx + 132, cy + 36, 0xE014161C);
        g.outline(cx - 132, cy - 36, 264, 72, 0xFF3C6040);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        JsonObject t = trade();
        String who = t != null ? t.get("partner").getAsString() : "Someone";
        g.centeredText(font, who + " wants to trade creatures.", width / 2, height / 2 - 24, 0xFFFFFFFF);
        String sub = answered ? "Opening the trade…"
                : "Each of you picks one party creature to swap." + " (" + Math.max(0, secondsLeft()) + "s)";
        g.centeredText(font, sub, width / 2, height / 2 - 11, 0xFFA0A8B8);
    }

    @Override
    public void onClose() { answer(false); }

    @Override
    public boolean isPauseScreen() { return false; }
}
