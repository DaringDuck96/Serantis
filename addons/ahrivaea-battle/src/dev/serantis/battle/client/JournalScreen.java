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
                if (y + n * 22 + 20 > height - 34) break;                  // no room for more (a very small window)
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
        List<Object[]> lines = new ArrayList<>();                       // {text, colour, gap before}: drawn scrolled below
        switch (tab) {
            case 0 -> {
                lines.add(new Object[]{"What to do next", GOLD, 0});
                int gap = 3;
                for (var el : j.getAsJsonArray("next")) {
                    for (String row : wrap("• " + el.getAsString(), w - 8)) { lines.add(new Object[]{row, TEXT, gap}); gap = 0; }
                    gap = 5;
                }
                gap = 11;
                for (String row : wrap("Bounties today: " + j.get("bounties").getAsString() + " claimed. Right-click a village bell to see them.", w - 8)) {
                    lines.add(new Object[]{row, DIM, gap}); gap = 0;
                }
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
                    String say = !j.get("intro").getAsBoolean() ? "Your story hasn't begun yet."
                            : j.get("met").getAsInt() == 0 ? "You woke at spawn beside a Mysterious Figure. \"Find the towers. Save Serantia.\"" : null;
                    if (say != null) for (String row : wrap(say, w)) { g.text(font, row, l, y, DIM); y += 11; }
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
                            int gap = lines.isEmpty() ? 0 : 3;
                            for (String row : wrap(line, w - 8)) { lines.add(new Object[]{row, "action".equals(kind) ? DIM : PURPLE, gap}); gap = 0; }
                        }
                }
            }
            default -> {
                JsonArray t = j.getAsJsonArray("loreTitles");
                int gap = 0;
                for (String row : wrap("Lore books found: " + t.size() + " of " + j.get("loreTotal").getAsInt() + " (they drop from beaten creatures)", w - 8)) {
                    lines.add(new Object[]{row, GOLD, gap}); gap = 0;
                }
                gap = 4;
                for (var el : t) {
                    for (String row : wrap("• " + el.getAsString(), w - 8)) { lines.add(new Object[]{row, TEXT, gap}); gap = 0; }
                    gap = 1;
                }
            }
        }
        drawScrolled(g, lines, l, w);
    }

    // A page longer than the screen scrolls with the mouse wheel (with a bar on the right); rows are only drawn whole.
    private int contentH;

    private int viewTop() { return 58; }
    private int viewBottom() { return height - 34; }

    private void drawScrolled(GuiGraphicsExtractor g, List<Object[]> lines, int l, int w) {
        int h = 0;
        for (Object[] line : lines) h += (int) line[2] + 11;
        contentH = h;
        int max = Math.max(0, contentH - (viewBottom() - viewTop()));
        scroll = Math.max(0, Math.min(scroll, max));
        int y = viewTop() - scroll;
        for (Object[] line : lines) {
            y += (int) line[2];
            if (y >= viewTop() - 1 && y + 9 <= viewBottom()) g.text(font, (String) line[0], l, y, (int) line[1]);
            y += 11;
        }
        if (max > 0) {
            int track = viewBottom() - viewTop(), bar = Math.max(16, track * track / (contentH + 1));
            int bx = l + w - 3, by = viewTop() + (int) ((track - bar) * (scroll / (double) max));
            g.fill(bx, viewTop(), bx + 3, viewBottom(), 0x40FFFFFF);
            g.fill(bx, by, bx + 3, by + bar, 0xC0F2C94C);
        }
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        int max = Math.max(0, contentH - (viewBottom() - viewTop()));
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.round(dy * 22)));
        return true;
    }

    private int bar(GuiGraphicsExtractor g, int l, int y, int w, String label, int have, int total) {
        g.text(font, font.plainSubstrByWidth(label + ": " + have + "/" + total, w), l, y, TEXT);
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
