package dev.serantis.battle.client;

import com.google.gson.JsonArray;
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

/** An End dragon's riddle, as a cutscene: it speaks, you pick one of three answers. See Riddles on the server. */
public class RiddleScreen extends Screen {
    private static final int GOLD = 0xFFE7B3FF, TEXT = 0xFFF0ECE4, DIM = 0xFF9AA4B4;
    private final Entity dragon;
    private final String name;
    private String line;            // what the dragon is saying now
    private long shownAt;
    private int seenWrong;
    private boolean choosing, won, closed;
    private int step;               // 0 greeting, 1 riddle, then answers

    private RiddleScreen(Entity dragon) {
        super(Component.literal("Riddle"));
        this.dragon = dragon;
        this.name = dragon.getDisplayName().getString();
    }

    private static JsonObject riddle() {
        JsonObject s = TradeClient.state();
        return s != null && s.has("serantis_riddle") ? s.getAsJsonObject("serantis_riddle") : null;
    }

    /** Every client tick: a dragon asking us its riddle opens the cutscene. */
    public static void autoOpen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gui.screen() != null) return;
        JsonObject r = riddle();
        if (r == null) return;
        Entity e = mc.level.getEntity(r.get("entity").getAsInt());
        if (e == null) return;
        float face = e.getBbHeight() * 0.85f;
        if (e instanceof dev.ahrivaea.creature.CreatureEntity c) try {
            float h = dev.ahrivaea.client.GeoModels.height(c.getSpeciesId(), c.getInfluence().getSerializedName());
            if (h > face) face = h * 0.8f;
        } catch (RuntimeException ignored) { }
        DialogueCamera.start(e, face);
        RiddleScreen s = new RiddleScreen(e);
        s.line = r.get("line").getAsString();
        s.seenWrong = r.get("wrong").getAsInt();
        mc.gui.setScreen(s);
    }

    private void say(String text) {
        line = text;
        shownAt = System.currentTimeMillis();
        choosing = false;
        rebuildWidgets();
    }

    @Override
    protected void init() {
        if (shownAt == 0) shownAt = System.currentTimeMillis();
        if (!choosing) return;
        JsonObject r = riddle();
        if (r == null) return;
        JsonArray opts = r.getAsJsonArray("options");
        int bw = Math.min(150, (width - 40) / 3), total = opts.size() * bw + (opts.size() - 1) * 8, x = (width - total) / 2;
        for (int i = 0; i < opts.size(); i++) {
            final int index = i;
            addRenderableWidget(Button.builder(Component.literal(opts.get(i).getAsString()), b -> {
                JsonObject o = new JsonObject();
                o.addProperty("kind", "serantis_riddle_answer");
                o.addProperty("index", index);
                dev.ahrivaea.client.battle.BattleClient.send(o);
                choosing = false;
                rebuildWidgets();
            }).bounds(x + i * (bw + 8), height - 46, bw, 20).build());
        }
    }

    @Override
    public void tick() {
        super.tick();
        JsonObject r = riddle();
        if (r == null) { if (!closed) close(false); return; }
        int wrong = r.get("wrong").getAsInt();
        if (wrong != seenWrong) {                          // the dragon's answer to our pick
            seenWrong = wrong;
            won = wrong < 0;
            say(r.get("line").getAsString());
            step = 2;
        }
    }

    private int visibleChars() { return (int) ((System.currentTimeMillis() - shownAt) * 45 / 1000); }

    private void advance() {
        if (choosing) return;
        if (visibleChars() < line.length()) { shownAt = System.currentTimeMillis() - 100_000; return; }
        JsonObject r = riddle();
        if (won) { close(true); return; }
        if (step == 0 && r != null) { step = 1; say(r.get("riddle").getAsString()); return; }
        choosing = true;                                   // after the riddle, or after "try again"
        rebuildWidgets();
    }

    private void close(boolean fight) {
        closed = true;
        TradeClient.send(fight ? "serantis_riddle_fight" : "serantis_riddle_leave", null, null);
        DialogueCamera.end(!fight);
        Minecraft.getInstance().gui.setScreen(null);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        advance();
        return true;
    }

    @Override
    public void onClose() { if (!closed) close(false); }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        int top = height - 78;
        g.fillGradient(0, top - 30, width, top, 0x00000000, 0xA0000000);
        g.fill(0, top, width, height, 0xC8100818);
        g.fill(0, 0, width, 18, 0x90000000);
    }

    private List<String> wrap(String text, int w) {
        List<String> out = new ArrayList<>();
        StringBuilder row = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = row.isEmpty() ? word : row + " " + word;
            if (font.width(next) > w && !row.isEmpty()) { out.add(row.toString()); row = new StringBuilder(word); }
            else row = new StringBuilder(next);
        }
        if (!row.isEmpty()) out.add(row.toString());
        return out;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        int top = height - 78, l = Math.max(12, width / 2 - 200), w = Math.min(400, width - 24);
        g.text(font, name, l, top + 8, GOLD);
        if (choosing) {
            JsonObject r = riddle();
            int y = top + 22;
            if (r != null) for (String row : wrap(r.get("riddle").getAsString(), w)) { g.text(font, row, l, y, DIM); y += 10; }
            return;
        }
        String text = line == null ? "" : line;
        int n = Math.min(text.length(), visibleChars()), y = top + 22;
        for (String row : wrap(text.substring(0, n), w)) { g.text(font, row, l, y, TEXT); y += 11; }
        if (n >= text.length() && (System.currentTimeMillis() / 500) % 2 == 0)
            g.text(font, "▼ click to continue", l + w - font.width("▼ click to continue"), height - 14, DIM);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
