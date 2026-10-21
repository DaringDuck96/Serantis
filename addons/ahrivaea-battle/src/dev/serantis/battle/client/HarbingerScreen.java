package dev.serantis.battle.client;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * The Harbinger of Calamity's entrance (see Harbinger on the server): the camera watches it claw its way out
 * of the ground, then closes in on its face as it roars its line.
 */
public class HarbingerScreen extends Screen {
    private static final int RED = 0xFFE05050, TEXT = 0xFFF0ECE4, DIM = 0xFF9AA4B4;
    private static final String NAME = "Harbinger of Calamity";
    private static final String LINE = "Gwarrrrrgh!!! It's time for the Harbinger of Calamity to rule!";
    private static String lastSeen = "";

    private final Entity harbinger;
    private boolean risen, done;
    private long roarAt;

    private HarbingerScreen(Entity e) {
        super(Component.literal(NAME));
        this.harbinger = e;
    }

    static JsonObject state() {
        JsonObject s = TradeClient.state();
        return s != null && s.has("serantis_harbinger") ? s.getAsJsonObject("serantis_harbinger") : null;
    }

    public static void autoOpen() {
        Minecraft mc = Minecraft.getInstance();
        JsonObject h = state();
        if (h == null) { lastSeen = ""; return; }
        if (mc.player == null || mc.level == null || mc.gui.screen() != null) return;
        String key = String.valueOf(h.get("entity").getAsInt());
        if (key.equals(lastSeen)) return;
        Entity e = mc.level.getEntity(h.get("entity").getAsInt());
        if (e == null) return;
        lastSeen = key;
        float face = e.getBbHeight() * 0.85f;
        try {
            float g = dev.ahrivaea.client.GeoModels.height("harbinger_of_calamity", "darkness");
            if (g > face) face = g * 0.85f;
        } catch (RuntimeException ignored) { }
        DialogueCamera.start(e, face);
        DialogueCamera.watchShot();
        mc.gui.setScreen(new HarbingerScreen(e));
    }

    @Override
    public void tick() {
        super.tick();
        JsonObject h = state();
        if (!risen && (h == null || h.get("risen").getAsBoolean())) {     // it's up: the close-up, and the roar
            risen = true;
            roarAt = System.currentTimeMillis();
            DialogueCamera.focusPlayer(false);
        }
        if (risen && System.currentTimeMillis() - roarAt > 9000) end();
    }

    private int visibleChars() { return (int) Math.min(Integer.MAX_VALUE, (System.currentTimeMillis() - roarAt - 500) * 40 / 1000); }

    private void end() {
        if (done) return;
        done = true;
        TradeClient.send("serantis_harbinger_watched", null, null);
        DialogueCamera.end(true);
        Minecraft.getInstance().gui.setScreen(null);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!risen) return true;
        if (visibleChars() < LINE.length()) { roarAt = System.currentTimeMillis() - 100_000; return true; }
        end();
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    public void onClose() { }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, 18, 0xC0000000);                             // letterbox
        if (!risen) { g.fill(0, height - 18, width, height, 0xC0000000); return; }
        int top = height - 78;
        g.fillGradient(0, top - 30, width, top, 0x00000000, 0xA0000000);
        g.fill(0, top, width, height, 0xC8080608);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        if (!risen) return;
        int top = height - 78, l = Math.max(12, width / 2 - 200), w = Math.min(400, width - 24);
        g.text(font, NAME, l, top + 8, RED);
        int n = Math.max(0, Math.min(LINE.length(), visibleChars())), y = top + 22;
        for (String row : wrap(LINE.substring(0, n), w)) { g.text(font, row, l, y, TEXT); y += 11; }
        if (n >= LINE.length() && (System.currentTimeMillis() / 500) % 2 == 0)
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
