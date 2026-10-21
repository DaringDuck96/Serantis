package dev.serantis.battle.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The Co-op page: your group, who's in a battle you can join, and leaving the group. */
public class CoopScreen extends Screen {
    private static final int GOLD = 0xFFF2C94C, TEXT = 0xFFE6E2D8, DIM = 0xFF9AA4B4, GREEN = 0xFF7CD67C, BLUE = 0xFF8FD3FF;
    private final Screen parent;
    private String seen = "";

    public CoopScreen(Screen parent) {
        super(Component.literal("Co-op"));
        this.parent = parent;
    }

    private static JsonObject coop() {
        JsonObject s = TradeClient.state();
        return s != null && s.has("serantis_coop") ? s.getAsJsonObject("serantis_coop") : null;
    }

    private int panelW() { return Math.min(380, width - 20); }
    private int left() { return (width - panelW()) / 2; }

    @Override
    protected void init() {
        JsonObject c = coop();
        seen = c == null ? "" : c.getAsJsonArray("members").toString();
        int l = left(), w = panelW(), y = 50;
        if (c != null) {
            JsonArray ms = c.getAsJsonArray("members");
            for (var el : ms) {
                JsonObject m = el.getAsJsonObject();
                if (m.get("joinable").getAsBoolean()) {
                    String id = m.get("uuid").getAsString();
                    addRenderableWidget(Button.builder(Component.literal("Join battle"), b -> {
                        TradeClient.send("serantis_coop_join", "target", id);
                        Minecraft.getInstance().gui.setScreen(null);
                    }).bounds(l + w - 84, y - 4, 80, 18).build());
                }
                y += 22;
            }
            if (!ms.isEmpty())
                addRenderableWidget(Button.builder(Component.literal("Leave group"), b -> {
                    TradeClient.send("serantis_coop_leave", null, null);
                    rebuildWidgets();
                }).bounds(l, height - 28, 90, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose()).bounds(width / 2 - 40, height - 28, 80, 20).build());
    }

    @Override
    public void tick() {
        super.tick();
        JsonObject c = coop();
        String now = c == null ? "" : c.getAsJsonArray("members").toString();
        if (!now.equals(seen)) rebuildWidgets();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        g.fillGradient(0, 0, width, height, 0xF0101418, 0xF0182028);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        g.centeredText(font, "Co-op", width / 2, 12, GOLD);
        JsonObject c = coop();
        int l = left(), y = 50;
        JsonArray ms = c == null ? new JsonArray() : c.getAsJsonArray("members");
        if (ms.isEmpty()) {
            g.text(font, "You're not in a group yet.", l, 30, TEXT);
            y = 50;
        } else {
            g.text(font, "Your group (" + ms.size() + "/" + (c == null ? 4 : c.get("max").getAsInt()) + ")", l, 30, TEXT);
            for (var el : ms) {
                JsonObject m = el.getAsJsonObject();
                String n = m.get("name").getAsString() + (m.get("me").getAsBoolean() ? " (you)" : "") + (m.get("leader").getAsBoolean() ? "  ★" : "");
                String status = !m.get("online").getAsBoolean() ? "offline"
                        : m.get("battle").getAsBoolean() ? "in a battle"
                        : m.get("distance").getAsInt() >= 0 && !m.get("me").getAsBoolean() ? m.get("distance").getAsInt() + " blocks away" : "";
                g.text(font, n, l, y, m.get("online").getAsBoolean() ? TEXT : DIM);
                g.text(font, status, l + 150, y, m.get("battle").getAsBoolean() ? BLUE : DIM);
                y += 22;
            }
        }
        y += 8;
        String[] help = {
                "Right-click a player to invite them to your group (up to 4), trade, or challenge them to a spar.",
                "Group members nearby are asked to join each other's battles, with up to 2 creatures each.",
                "Everyone commands their own units; the enemies move once all of you are done.",
                "Group members can use each other's bases: jobs, upgrades, training and the Move Tutor.",
                "Each day the bounty board has a group contract: everyone's progress counts, and everyone gets paid.",
                "Spars are friendly: no XP, loot or recruiting, and nobody's creatures really get hurt."
        };
        for (String h : help) {
            for (String row : wrap(h, panelW())) { g.text(font, row, l, y, DIM); y += 11; }
            y += 3;
        }
    }

    private java.util.List<String> wrap(String text, int w) {
        java.util.List<String> out = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (font.width(next) > w && !line.isEmpty()) { out.add(line.toString()); line = new StringBuilder(word); }
            else line = new StringBuilder(next);
        }
        if (!line.isEmpty()) out.add(line.toString());
        return out;
    }

    @Override
    public void onClose() { Minecraft.getInstance().gui.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
