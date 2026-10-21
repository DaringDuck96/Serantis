package dev.serantis.battle.client;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** "Are you sure?" before breaking a Base Core. See CoreRemoval on the server. */
public class CoreBreakScreen extends Screen {
    private static final int W = 300, GOLD = 0xFFF2C94C, TEXT = 0xFFE6E2D8, RED = 0xFFE07060;
    private static final String MESSAGE = "Creatures can't be recovered from a destroyed Base Core. To keep them safe, every creature "
            + "working at this base will be sent back to your party (or your box if the party is full) before it breaks. "
            + "The base, its area and its upgrades will be gone, and the core drops as an item.";
    private final BlockPos pos;
    private final List<String> lines = new ArrayList<>();

    public CoreBreakScreen(BlockPos pos) {
        super(Component.literal("Take down this base?"));
        this.pos = pos;
    }

    private int top() { return Math.max(6, height / 2 - 70); }

    @Override
    protected void init() {
        lines.clear();
        StringBuilder line = new StringBuilder();
        for (String word : MESSAGE.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (font.width(next) > W - 20 && !line.isEmpty()) { lines.add(line.toString()); line = new StringBuilder(word); }
            else line = new StringBuilder(next);
        }
        if (!line.isEmpty()) lines.add(line.toString());
        int y = top() + 40 + lines.size() * 10 + 10;
        addRenderableWidget(Button.builder(Component.literal("Yes, take it down"), b -> {
            JsonObject o = new JsonObject();
            o.addProperty("kind", "serantis_core_break");
            o.addProperty("x", pos.getX());
            o.addProperty("y", pos.getY());
            o.addProperty("z", pos.getZ());
            dev.ahrivaea.client.battle.BattleClient.send(o);
            onClose();
        }).bounds(width / 2 - 124, y, 120, 20).build());
        addRenderableWidget(Button.builder(Component.literal("No, keep it"), b -> onClose()).bounds(width / 2 + 4, y, 120, 20).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        g.fillGradient(0, 0, width, height, 0xC0100808, 0xC0201010);
        int l = (width - W) / 2, t = top(), h = 40 + lines.size() * 10 + 40;
        g.fill(l, t, l + W, t + h, 0xF0181010);
        g.outline(l, t, W, h, 0xFF804030);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        int l = (width - W) / 2 + 10, t = top();
        g.text(font, "Take down this base?", l, t + 10, GOLD);
        g.text(font, "Are you sure?", l, t + 24, RED);
        for (int i = 0; i < lines.size(); i++) g.text(font, lines.get(i), l, t + 40 + i * 10, TEXT);
    }

    @Override
    public void onClose() { Minecraft.getInstance().gui.setScreen(null); }

    @Override
    public boolean isPauseScreen() { return false; }
}
