package dev.serantis.battle.client;

import com.google.gson.JsonObject;
import dev.serantis.battle.Masters;
import dev.serantis.battle.PlayerClass;
import dev.serantis.battle.Temperament;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Choosing a class, once, when a player first joins (after the welcome guide, before the starter).
 * Wizards also choose their staff's influence; Performers their starter's temperament. See PlayerClass.
 */
public class ClassScreen extends Screen {
    private static final int GOLD = 0xFFF2C94C, TEXT = 0xFFE6E2D8, DIM = 0xFF9AA4B4;
    private static boolean shown;

    public static void resetForNewWorld() { shown = false; }

    private final Screen parent;
    private PlayerClass.Kind picked;
    private String extra;
    private boolean sent;

    public ClassScreen(Screen parent) {
        super(Component.literal("Choose your class"));
        this.parent = parent;
    }

    /** Every client tick: once the welcome guide is out of the way, a player with no class picks one. */
    public static void autoOpen() {
        Minecraft mc = Minecraft.getInstance();
        if (shown || mc.player == null || IntroScreen.running()) return;
        JsonObject s = TradeClient.state();
        if (s == null || !s.has("serantis_class") || s.has("serantis_welcome")) return;
        if (s.getAsJsonObject("serantis_class").get("chosen").getAsBoolean()) { shown = true; return; }
        Screen open = mc.gui.screen();
        if (open != null && !(open instanceof dev.ahrivaea.client.StarterScreen)) return;
        shown = true;
        mc.gui.setScreen(new ClassScreen(open));
    }

    private int panelW() { return Math.min(400, width - 20); }

    @Override
    protected void init() {
        int w = panelW(), l = (width - w) / 2, y = 44;
        int n = PlayerClass.ALL.size(), bw = (w - 16 - 4 * (n - 1)) / n;
        for (int i = 0; i < PlayerClass.ALL.size(); i++) {
            PlayerClass.Kind k = PlayerClass.ALL.get(i);
            Button b = Button.builder(Component.literal(k.name()), btn -> { picked = k; extra = null; rebuildWidgets(); })
                    .bounds(l + 8 + i * (bw + 4), y, bw, 20)
                    .tooltip(Tooltip.create(Component.literal((k.boosts() ? Temperament.label(k.stat()) + " boost. " : "No stat boost. ") + k.perk()))).build();
            b.active = picked != k;
            addRenderableWidget(b);
        }
        int oy = y + 80;
        if (picked != null && "wizard".equals(picked.id())) {               // the staff's influence
            int cw = (w - 16 - 5 * 3) / 6;
            String[] staffs = java.util.Arrays.stream(Masters.INFLUENCES).filter(dev.serantis.battle.Staffs::hasStaff).toArray(String[]::new);
            for (int i = 0; i < staffs.length; i++) {
                String inf = staffs[i];
                Button b = Button.builder(Component.literal(Masters.influenceName(inf)), btn -> { extra = inf; rebuildWidgets(); })
                        .bounds(l + 8 + (i % 6) * (cw + 3), oy + (i / 6) * 22, cw, 20).build();
                b.active = !inf.equals(extra);
                addRenderableWidget(b);
            }
        } else if (picked != null && "performer".equals(picked.id())) {     // the starter's temperament
            String[] names = Temperament.ALL.keySet().toArray(new String[0]);
            int cols = 5, cw = (w - 16 - (cols - 1) * 3) / cols;
            for (int i = 0; i < names.length; i++) {
                String t = names[i];
                Temperament.Kind k = Temperament.of(t);
                Button b = Button.builder(Component.literal(t), btn -> { extra = t; rebuildWidgets(); })
                        .bounds(l + 8 + (i % cols) * (cw + 3), oy + (i / cols) * 22, cw, 20)
                        .tooltip(Tooltip.create(Component.literal("+" + Temperament.label(k.up()) + ", -" + Temperament.label(k.down())))).build();
                b.active = !t.equals(extra);
                addRenderableWidget(b);
            }
        }
        boolean needsExtra = picked != null && ("wizard".equals(picked.id()) || "performer".equals(picked.id()));
        Button go = Button.builder(Component.literal(picked == null ? "Choose a class" : "Become a " + picked.name()), b -> {
            JsonObject o = new JsonObject();
            o.addProperty("kind", "serantis_class_pick");
            o.addProperty("class", picked.id());
            if (extra != null) o.addProperty("extra", extra);
            dev.ahrivaea.client.battle.BattleClient.send(o);
            sent = true;
            Minecraft.getInstance().gui.setScreen(parent);
        }).bounds(width / 2 - 70, height - 28, 140, 20).build();
        go.active = picked != null && (!needsExtra || extra != null);
        addRenderableWidget(go);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        g.fillGradient(0, 0, width, height, 0xF00C0B14, 0xF0181226);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        int w = panelW(), l = (width - w) / 2 + 8;
        g.pose().pushMatrix();
        g.pose().translate(width / 2f, 8);
        g.pose().scale(1.5f, 1.5f);
        g.centeredText(font, "Choose your class", 0, 0, GOLD);
        g.pose().popMatrix();
        g.centeredText(font, "How do you want to take on Serantia? You choose once.", width / 2, 26, DIM);
        if (picked == null) { g.text(font, "Hover a class to see what it brings.", l, 72, TEXT); return; }
        g.text(font, picked.name() + ": " + picked.boostText() + ".", l, 70, TEXT);
        int py = 82;
        for (String line : wrap(picked.perk(), panelW() - 16)) { g.text(font, line, l, py, TEXT); py += 10; }
        if ("wizard".equals(picked.id())) g.text(font, "Your staff's influence:", l, 112, GOLD);
        if ("performer".equals(picked.id())) g.text(font, "Your starter's temperament (hover for what it changes):", l, 112, GOLD);
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
    public void onClose() {
        if (!sent) shown = false;            // not picked yet: it comes back
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
