package dev.serantis.battle.client;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * The opening scene (see Intro on the server): you and the Mysterious Figure at spawn. It talks, you answer,
 * you choose your class and your starter when it asks, a roar sounds in the distance, it gives you the Atlas of
 * the Fallen Towers (see TowerAtlas) and tells you to rebuild
 * the towers, and vanishes as the screen goes dark. Then the welcome guide.
 */
public class IntroScreen extends Screen {
    private static final int GOLD = 0xFFB89BE8, TEXT = 0xFFF0ECE4, YOU = 0xFF8FD1E8, DIM = 0xFF9AA4B4;
    private static final String NAME = "Mysterious Figure";

    /** A step: the figure speaks ("F"), you choose ("C"), or class / starter / roar / vanish. */
    private record Step(String kind, String[] text) {}
    private static final List<Step> SCRIPT = List.of(
            new Step("F", new String[]{"Do you remember anything?"}),
            new Step("C", new String[]{"No? What do you mean?", "There was this strange portal, right? I think?…"}),
            new Step("F", new String[]{"It doesn't matter. Your memories will come back, in time. We have bigger issues."}),
            new Step("C", new String[]{"What do you mean?", "What are you talking about?", "Who are you?"}),
            new Step("F", new String[]{"I'm sure you have a lot of questions. More questions than I have time to answer."}),
            new Step("C", new String[]{"Where am I?"}),
            new Step("F", new String[]{"This is Serantia. You'll remember in time. I'm glad you're okay. Do you remember anything about yourself?"}),
            new Step("CLASS", new String[0]),
            new Step("F", new String[]{"Good. I'm glad it's coming back. And I see you're not alone…. Who is this?"}),
            new Step("STARTER", new String[0]),
            new Step("F", new String[]{"It's nice to have company. You'll need all the help you can get. Serantia needs you to help rebuild."}),
            new Step("C", new String[]{"Rebuild what?"}),
            new Step("F", new String[]{"Well… everything."}),
            new Step("ROAR", new String[0]),
            new Step("F", new String[]{"I have to go. But first… take this."}),
            new Step("GIFT", new String[0]),
            new Step("F", new String[]{"It marks every tower within ten thousand blocks of this place. Long ago they kept watch over Serantia. Now they lie in ruins."}),
            new Step("C", new String[]{"What am I supposed to do with them?", "Why me?"}),
            new Step("F", new String[]{"Rebuild them. Every tower raised brings Serantia closer to what it was. Start with the nearest one."}),
            new Step("F", new String[]{"Find the towers. Save Serantia."}),
            new Step("VANISH", new String[0]));

    // where the scene is; kept while the class and starter screens are up
    private static int step = -1;
    private static String answer;                  // the line you just chose, shown before the figure's reply
    private static JsonObject starterOffer;
    private static boolean started, finished;

    public static void resetForNewWorld() { step = -1; answer = null; started = false; finished = false; }

    private Entity figure;
    private long shownAt, eventAt;

    public IntroScreen() { super(Component.literal("Serantia")); }

    static JsonObject intro() {
        JsonObject s = TradeClient.state();
        return s != null && s.has("serantis_intro") ? s.getAsJsonObject("serantis_intro") : null;
    }

    /** True while the scene is on: nothing else (welcome guide, class, starter) opens by itself. */
    public static boolean running() { return !finished && intro() != null; }

    /** Every client tick: the server says a scene is on; open it (taking over a starter screen that beat it). */
    public static void autoOpen() {
        Minecraft mc = Minecraft.getInstance();
        if (finished || mc.player == null || mc.level == null) return;
        JsonObject in = intro();
        if (in == null) return;
        Screen open = mc.gui.screen();
        if (open instanceof IntroScreen || open instanceof ClassScreen) return;
        if (open instanceof dev.ahrivaea.client.StarterScreen st) {
            if (step == indexOf("STARTER")) return;                      // that's the one we opened
            starterOffer = ((dev.serantis.battle.mixin.client.StarterAccess) st).serantis$offer();
        } else if (open != null) return;
        int id = in.get("entity").getAsInt();
        Entity e = id < 0 ? null : mc.level.getEntity(id);
        if (e == null) return;                                          // wait for the figure to arrive
        IntroScreen s = new IntroScreen();
        s.figure = e;
        mc.gui.setScreen(s);
    }

    private static int indexOf(String kind) {
        for (int i = 0; i < SCRIPT.size(); i++) if (SCRIPT.get(i).kind().equals(kind)) return i;
        return -1;
    }

    private Step current() { return step >= 0 && step < SCRIPT.size() ? SCRIPT.get(step) : null; }

    @Override
    protected void init() {
        if (!started) {
            started = true;
            float face = figure.getBbHeight() * 0.85f;
            try {
                float h = dev.ahrivaea.client.GeoModels.height("villager", "mysterious_figure");
                if (h > face) face = h * 0.88f;
            } catch (RuntimeException ignored) { }
            DialogueCamera.start(figure, face);
            DialogueCamera.wideShot();
            eventAt = System.currentTimeMillis();
            step = -1;                                                  // a moment of the two of you before it speaks
        } else if (!DialogueCamera.active()) {
            DialogueCamera.start(figure, figure.getBbHeight() * 0.85f);
        }
        if (shownAt == 0) shownAt = System.currentTimeMillis();
        Step s = current();
        if (s == null || answer != null) return;
        if ("C".equals(s.kind())) {
            int n = s.text().length, bw = Math.min(260, width - 40), y = height - 30 - n * 22;
            for (int i = 0; i < n; i++) {
                String line = s.text()[i];
                addRenderableWidget(Button.builder(Component.literal(line), b -> choose(line)).bounds(width / 2 - bw / 2, y + i * 22, bw, 20).build());
            }
        } else if ("CLASS".equals(s.kind()) || "STARTER".equals(s.kind())) {
            int bw = 160;
            addRenderableWidget(Button.builder(Component.literal("CLASS".equals(s.kind()) ? "Remember who you are..." : "Introduce your companion..."),
                    b -> openPicker()).bounds(width / 2 - bw / 2, height - 46, bw, 20).build());
        }
    }

    private void go(int to) {
        step = to;
        answer = null;
        shownAt = System.currentTimeMillis();
        eventAt = shownAt;
        Step s = current();
        if (s != null && "F".equals(s.kind())) DialogueCamera.focusPlayer(false);
        if (s != null && "C".equals(s.kind())) DialogueCamera.focusPlayer(true);
        if (s != null && "ROAR".equals(s.kind())) {
            DialogueCamera.wideShot();
            var mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.playSound(SoundEvents.ENDER_DRAGON_GROWL, 1.2f, 0.6f);
        }
        if (s != null && "VANISH".equals(s.kind())) DialogueCamera.wideShot();
        if (s != null && "GIFT".equals(s.kind())) {                    // it hands you the Atlas of the Fallen Towers
            DialogueCamera.wideShot();
            TradeClient.send("serantis_tower_atlas", null, null);
            var mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.playSound(SoundEvents.BOOK_PAGE_TURN, 1f, 0.9f);
        }
        rebuildWidgets();
    }

    private void choose(String line) {
        answer = line;
        shownAt = System.currentTimeMillis();
        DialogueCamera.focusPlayer(true);
        rebuildWidgets();
    }

    private boolean classChosen() {
        JsonObject s = TradeClient.state();
        return s != null && s.has("serantis_class") && s.getAsJsonObject("serantis_class").get("chosen").getAsBoolean();
    }

    private boolean hasStarter() { return !TradeClient.party().isEmpty(); }

    private void openPicker() {
        Minecraft mc = Minecraft.getInstance();
        Step s = current();
        if (s == null) return;
        if ("CLASS".equals(s.kind())) {
            if (classChosen()) { go(step + 1); return; }
            mc.gui.setScreen(new ClassScreen(this));
        } else {
            if (hasStarter()) { go(step + 1); return; }
            JsonObject offer = dev.serantis.battle.mixin.client.PartyClientAccessor.serantis$pendingStarter();
            if (offer != null) { starterOffer = offer; dev.serantis.battle.mixin.client.PartyClientAccessor.serantis$setPendingStarter(null); }
            if (starterOffer == null) return;                           // not here yet: click again in a moment
            mc.gui.setScreen(new dev.ahrivaea.client.StarterScreen(starterOffer));
        }
    }

    private int visibleChars() { return (int) Math.min(Integer.MAX_VALUE, (System.currentTimeMillis() - shownAt) * 40 / 1000); }

    @Override
    public void tick() {
        super.tick();
        long now = System.currentTimeMillis();
        if (step == -1 && now - eventAt > 2500) go(0);                  // the opening two-shot, then it speaks
        Step s = current();
        if (s == null) return;
        if ("CLASS".equals(s.kind()) && classChosen()) go(step + 1);
        else if ("STARTER".equals(s.kind()) && hasStarter()) go(step + 1);
        else if ("ROAR".equals(s.kind()) && now - eventAt > 2600) go(step + 1);
        else if ("GIFT".equals(s.kind()) && now - eventAt > 2400) go(step + 1);
        else if ("VANISH".equals(s.kind())) {
            long t = now - eventAt;
            if (t > 1000 && !finished) {                                // fully dark: it's gone
                finished = true;
                TradeClient.send("serantis_intro_done", null, null);
            }
            if (t > 4200) {
                DialogueCamera.end(true);
                Minecraft.getInstance().gui.setScreen(null);
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        Step s = current();
        if (s == null) return true;
        String text = answer != null ? answer : "F".equals(s.kind()) ? s.text()[0] : null;
        if (text == null) return true;
        if (visibleChars() < text.length()) { shownAt = System.currentTimeMillis() - 100_000; return true; }
        go(step + 1);
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    public void onClose() { }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        int top = height - 78;
        g.fill(0, 0, width, 18, 0xC0000000);                             // letterbox
        Step s = current();
        if (s != null && "VANISH".equals(s.kind())) return;
        g.fillGradient(0, top - 30, width, top, 0x00000000, 0xA0000000);
        g.fill(0, top, width, height, 0xC8080608);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        Step s = current();
        if (s != null && "VANISH".equals(s.kind())) {                   // fade to black, hold, fade back
            long t = System.currentTimeMillis() - eventAt;
            float a = t < 1000 ? t / 1000f : t < 2600 ? 1f : Math.max(0f, 1f - (t - 2600) / 1400f);
            g.fill(0, 0, width, height, ((int) (a * 255) << 24));
            return;
        }
        super.extractRenderState(g, mouseX, mouseY, partial);
        if (s == null) return;
        int top = height - 78, l = Math.max(12, width / 2 - 200), w = Math.min(400, width - 24);
        String speaker, text;
        if (answer != null) { speaker = Minecraft.getInstance().player == null ? "You" : Minecraft.getInstance().player.getPlainTextName(); text = answer; }
        else if ("F".equals(s.kind())) { speaker = NAME; text = s.text()[0]; }
        else if ("C".equals(s.kind())) { g.text(font, "What do you say?", l, top + 8, DIM); return; }
        else if ("ROAR".equals(s.kind())) { g.text(font, "*A deafening roar echoes in the distance*", l, top + 22, DIM); return; }
        else if ("GIFT".equals(s.kind())) { g.text(font, "*The figure presses a worn, leather-bound book into your hands*", l, top + 22, DIM); return; }
        else return;
        g.text(font, speaker, l, top + 8, answer != null ? YOU : GOLD);
        int n = Math.min(text.length(), visibleChars()), y = top + 22;
        for (String row : wrap(text.substring(0, n), w)) { g.text(font, row, l, y, TEXT); y += 11; }
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
