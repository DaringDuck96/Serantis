package dev.serantis.battle.client;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * The Mysterious Figure's visits (see FigureVisit on the server; the words are in serantis_battle/figure_scenes.json):
 * a cutscene while it walks up to you, then a short talk, then it vanishes.
 */
public class FigureScreen extends Screen {
    private static final int GOLD = 0xFFB89BE8, TEXT = 0xFFF0ECE4, YOU = 0xFF8FD1E8, DIM = 0xFF9AA4B4;
    private String name = "Mysterious Figure";
    private boolean named;
    private static com.google.gson.JsonArray all;

    private final Entity figure;
    private final List<String[]> lines = new ArrayList<>();     // {"figure"/"player"/"action", text}
    private int index = -1;
    private long shownAt, leftAt;
    private boolean choosing, chose, leaving;
    private final JsonObject scene;

    private FigureScreen(Entity figure, JsonObject scene) {
        super(Component.literal("Mysterious Figure"));
        this.figure = figure;
        this.scene = scene;
    }

    private static JsonObject scene(String id) {
        if (all == null) {
            try (var in = FigureScreen.class.getResourceAsStream("/serantis_battle/figure_scenes.json")) {
                all = in == null ? new com.google.gson.JsonArray()
                        : com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("scenes");
            } catch (Exception e) {
                all = new com.google.gson.JsonArray();
            }
        }
        for (var e : all) if (e.getAsJsonObject().get("id").getAsString().equals(id)) return e.getAsJsonObject();
        return null;
    }

    private void add(com.google.gson.JsonArray rows) {
        if (rows != null) for (var r : rows)
            lines.add(new String[]{r.getAsJsonArray().get(0).getAsString(), r.getAsJsonArray().get(1).getAsString().replace("Mysterious Figure", name)});
    }

    private com.google.gson.JsonArray choices() { return scene.getAsJsonArray("choices"); }

    static JsonObject visit() {
        JsonObject s = TradeClient.state();
        return s != null && s.has("serantis_visit") ? s.getAsJsonObject("serantis_visit") : null;
    }

    /** Every client tick: the figure is coming; the scene takes over the screen. */
    public static void autoOpen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gui.screen() != null) return;
        JsonObject v = visit();
        if (v == null) return;
        Entity e = mc.level.getEntity(v.get("entity").getAsInt());
        JsonObject sc = v.has("scene") ? scene(v.get("scene").getAsString()) : null;
        if (e == null || sc == null) return;
        float face = e.getBbHeight() * 0.85f;
        try {
            float h = dev.ahrivaea.client.GeoModels.height("villager", "mysterious_figure");
            if (h > face) face = h * 0.88f;
        } catch (RuntimeException ignored) { }
        DialogueCamera.start(e, face);
        DialogueCamera.wideShot();
        FigureScreen s = new FigureScreen(e, sc);
        if (v.has("name")) s.name = v.get("name").getAsString();
        mc.gui.setScreen(s);
    }

    private boolean arrived() {
        JsonObject v = visit();
        return v == null || v.get("arrived").getAsBoolean();
    }

    private void show(int i) {
        index = i;
        shownAt = System.currentTimeMillis();
        if (index < lines.size()) DialogueCamera.focusPlayer("player".equals(lines.get(index)[0]));
        rebuildWidgets();
    }

    @Override
    protected void init() {
        if (!choosing) return;
        int n = choices().size(), bw = Math.min(260, width - 40), y = height - 30 - n * 22;
        for (int i = 0; i < n; i++) {
            int pick = i;
            String text = choices().get(i).getAsJsonObject().get("text").getAsString();
            addRenderableWidget(Button.builder(Component.literal(text), b -> choose(pick)).bounds(width / 2 - bw / 2, y + i * 22, bw, 20).build());
        }
    }

    private void choose(int pick) {
        choosing = false;
        chose = true;
        JsonObject c = choices().get(pick).getAsJsonObject();
        named = c.has("reveal") && c.get("reveal").getAsBoolean();
        lines.add(new String[]{"player", c.get("text").getAsString()});
        add(c.getAsJsonArray("reply"));
        add(scene.getAsJsonArray("end"));
        show(index + 1);
    }

    @Override
    public void tick() {
        super.tick();
        if (index < 0 && arrived()) {                                   // it's here: it speaks
            add(scene.getAsJsonArray("lines"));
            if (lines.isEmpty()) lines.add(new String[]{"action", "..."});
            show(0);
        }
        if (!leaving && visit() == null && index >= 0) { leaving = true; leftAt = System.currentTimeMillis(); }   // it left on its own
        if (leaving && System.currentTimeMillis() - leftAt > 1400) {
            DialogueCamera.end(true);
            Minecraft.getInstance().gui.setScreen(null);
        }
    }

    private int visibleChars() { return (int) Math.min(Integer.MAX_VALUE, (System.currentTimeMillis() - shownAt) * 40 / 1000); }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (index < 0 || choosing || leaving) return true;
        String text = lines.get(index)[1];
        if (visibleChars() < text.length()) { shownAt = System.currentTimeMillis() - 100_000; return true; }
        if (index + 1 < lines.size()) { show(index + 1); return true; }
        if (!chose && !choices().isEmpty()) { choosing = true; DialogueCamera.focusPlayer(true); rebuildWidgets(); return true; }
        boolean fight = scene.has("battle") && scene.get("battle").getAsBoolean();
        JsonObject o = new JsonObject();
        o.addProperty("kind", "serantis_visit_done");
        o.addProperty("fight", fight);
        o.addProperty("named", named);
        if (fight) {                                                     // the battle camera takes over
            dev.ahrivaea.client.battle.BattleClient.send(o);
            DialogueCamera.end(false);
            Minecraft.getInstance().gui.setScreen(null);
            return true;
        }
        leaving = true;                                                  // it vanishes
        leftAt = System.currentTimeMillis();
        DialogueCamera.wideShot();
        dev.ahrivaea.client.battle.BattleClient.send(o);
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    public void onClose() { }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, 18, 0xC0000000);                             // letterbox
        if (index < 0 || leaving) { g.fill(0, height - 18, width, height, 0xC0000000); return; }
        int top = height - 78;
        g.fillGradient(0, top - 30, width, top, 0x00000000, 0xA0000000);
        g.fill(0, top, width, height, 0xC8080608);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        if (index < 0 || leaving) return;
        int top = height - 78, l = Math.max(12, width / 2 - 200), w = Math.min(400, width - 24);
        if (choosing) { g.text(font, "What do you say?", l, top + 8, DIM); return; }
        String[] line = lines.get(index);
        var player = Minecraft.getInstance().player;
        boolean you = "player".equals(line[0]), action = "action".equals(line[0]);
        if (!action) g.text(font, you ? (player == null ? "You" : player.getPlainTextName()) : name, l, top + 8, you ? YOU : GOLD);
        String text = line[1];
        int n = Math.min(text.length(), visibleChars()), y = top + 22;
        for (String row : wrap(text.substring(0, n), w)) { g.text(font, row, l, y, action ? DIM : TEXT); y += 11; }
        if (n >= text.length() && (System.currentTimeMillis() / 500) % 2 == 0)
            g.text(font, "▼ click to continue", l + w - font.width("▼ click to continue"), height - 14, DIM);
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
    public boolean isPauseScreen() { return false; }
}
