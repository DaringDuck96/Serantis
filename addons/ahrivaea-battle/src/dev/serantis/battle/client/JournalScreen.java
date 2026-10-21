package dev.serantis.battle.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** The Journal: next steps, progress, the story so far and lore found. See Journal on the server. */
public class JournalScreen extends Screen {
    private static final int GOLD = 0xFFF2C94C, TEXT = 0xFFE6E2D8, DIM = 0xFF9AA4B4, PURPLE = 0xFFB89BE8, RED = 0xFFE07060;
    private static final String[] TABS = {"Next Steps", "Progress", "Story", "Lore"};
    private static int tab;
    private static JsonArray scenes;

    private final Screen parent;
    private String reading;            // a story scene being read again (its id), or null
    private int scroll;

    public JournalScreen(Screen parent) {
        super(Component.literal("Journal"));
        this.parent = parent;
    }

    private static JsonObject journal() {
        JsonObject s = TradeClient.state();
        return s != null && s.has("serantis_journal") ? s.getAsJsonObject("serantis_journal") : null;
    }

    private static JsonArray scenes() {
        if (scenes == null) {
            try (var in = JournalScreen.class.getResourceAsStream("/serantis_battle/figure_scenes.json")) {
                scenes = in == null ? new JsonArray() : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("scenes");
            } catch (Exception e) {
                scenes = new JsonArray();
            }
        }
        return scenes;
    }

    private int panelW() { return Math.min(420, width - 20); }
    private int left() { return (width - panelW()) / 2; }

    @Override
    protected void init() {
        int l = left(), w = panelW(), bw = (w - 3 * 4) / 4;
        for (int i = 0; i < TABS.length; i++) {
            int t = i;
            Button b = Button.builder(Component.literal(TABS[i]), x -> { tab = t; reading = null; scroll = 0; rebuildWidgets(); })
                    .bounds(l + i * (bw + 4), 28, bw, 20).build();
            b.active = tab != i;
            addRenderableWidget(b);
        }
        JsonObject j = journal();
        if (tab == 2 && reading == null && j != null) {               // the story: one button per scene seen
            int met = j.get("met").getAsInt(), y = 58, n = 0;
            for (var el : scenes()) {
                JsonObject s = el.getAsJsonObject();
                if ((met & s.get("bit").getAsInt()) == 0) continue;
                String id = s.get("id").getAsString();
                String who = "last".equals(s.has("npc") ? s.get("npc").getAsString() : "first") ? j.get("lastName").getAsString() : j.get("firstName").getAsString();
                String first = s.getAsJsonArray("lines").isEmpty() ? id : s.getAsJsonArray("lines").get(0).getAsJsonArray().get(1).getAsString();
                String label = who + ": " + first.replace("{name}", who).replace("Mysterious Figure", who);
                addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(label, w - 12)), x -> { reading = id; rebuildWidgets(); })
                        .bounds(l, y + n * 22, w, 20).build());
                n++;
            }
        }
        if (tab == 2 && reading != null)
            addRenderableWidget(Button.builder(Component.literal("Back"), x -> { reading = null; rebuildWidgets(); }).bounds(l, height - 28, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), x -> onClose()).bounds(width / 2 - 40, height - 28, 80, 20).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        g.fillGradient(0, 0, width, height, 0xF0141008, 0xF0201810);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        g.centeredText(font, "Journal", width / 2, 10, GOLD);
        JsonObject j = journal();
        if (j == null) { g.centeredText(font, "...", width / 2, 70, DIM); return; }
        int l = left(), w = panelW(), y = 58;
        switch (tab) {
            case 0 -> {
                g.text(font, "What to do next", l, y, GOLD); y += 14;
                for (var el : j.getAsJsonArray("next")) {
                    for (String row : wrap("• " + el.getAsString(), w)) { g.text(font, row, l, y, TEXT); y += 11; }
                    y += 5;
                }
                y += 6;
                g.text(font, "Bounties today: " + j.get("bounties").getAsString() + " claimed. Right-click a village bell to see them.", l, y, DIM);
            }
            case 1 -> {
                y = bar(g, l, y, w, "Creature Dex", j.get("dex").getAsInt(), j.get("dexTotal").getAsInt());
                y = bar(g, l, y, w, "Gym Master Stones" + (j.get("eternita").getAsBoolean() ? "  (Eternita Stone!)" : ""), j.get("stones").getAsInt(), 60);
                y = bar(g, l, y, w, "Towers rebuilt", j.get("towers").getAsInt(), 5);
                y = bar(g, l, y, w, "Best base level", j.get("base").getAsInt(), 5);
                y = bar(g, l, y, w, "Lore books found", j.get("lore").getAsInt(), Math.max(1, j.get("loreTotal").getAsInt()));
                g.text(font, "Battles fought: " + j.get("battles").getAsInt(), l, y, DIM);
            }
            case 2 -> {
                if (reading == null) {
                    g.text(font, j.get("intro").getAsBoolean() ? "" : "Your story hasn't begun yet.", l, y - 2, DIM);
                    if (j.get("met").getAsInt() == 0 && j.get("intro").getAsBoolean())
                        g.text(font, "You woke at spawn beside a Mysterious Figure. \"Find the towers. Save Serantia.\"", l, y, DIM);
                    return;
                }
                for (var el : scenes()) {
                    JsonObject s = el.getAsJsonObject();
                    if (!reading.equals(s.get("id").getAsString())) continue;
                    String who = "last".equals(s.has("npc") ? s.get("npc").getAsString() : "first") ? j.get("lastName").getAsString() : j.get("firstName").getAsString();
                    for (var arr : List.of(s.getAsJsonArray("lines"), s.getAsJsonArray("end")))
                        for (var r : arr) {
                            String kind = r.getAsJsonArray().get(0).getAsString(), text = r.getAsJsonArray().get(1).getAsString().replace("{name}", who).replace("Mysterious Figure", who);
                            String line = "action".equals(kind) ? text : who + ": " + text;
                            for (String row : wrap(line, w)) { g.text(font, row, l, y, "action".equals(kind) ? DIM : PURPLE); y += 11; }
                            y += 3;
                        }
                }
            }
            default -> {
                JsonArray t = j.getAsJsonArray("loreTitles");
                g.text(font, "Lore books found: " + t.size() + " of " + j.get("loreTotal").getAsInt() + " (they drop from beaten creatures)", l, y, GOLD); y += 14;
                for (var el : t) { g.text(font, "• " + el.getAsString(), l, y, TEXT); y += 11; if (y > height - 40) break; }
            }
        }
    }

    private int bar(GuiGraphicsExtractor g, int l, int y, int w, String label, int have, int total) {
        g.text(font, label + ": " + have + "/" + total, l, y, TEXT);
        int bw = w, filled = (int) Math.round(bw * Math.min(1.0, have / (double) Math.max(1, total)));
        g.fill(l, y + 11, l + bw, y + 15, 0xFF2A2420);
        g.fill(l, y + 11, l + filled, y + 15, have >= total ? 0xFF7CD67C : GOLD);
        return y + 24;
    }

    private List<String> wrap(String text, int w) {
        List<String> out = new ArrayList<>();
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
