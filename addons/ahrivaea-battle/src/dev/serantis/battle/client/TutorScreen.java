package dev.serantis.battle.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The Move Tutor: pick a party creature, one of the 3 moves offered, and the move it forgets. See Tutor on the server. */
public class TutorScreen extends Screen {
    private static final int W = 340, H = 236, GOLD = 0xFFF2C94C, DIM = 0xFF9AA4B4;
    private final int entity;
    private String seen = "";
    private int offer = -1, replace = -1, lastSlot = -2;
    private boolean closed;

    public TutorScreen(int entity) {
        super(Component.literal("Move Tutor"));
        this.entity = entity;
    }

    public static void open(int entity) {
        TradeClient.send("serantis_tutor_open", "entity", entity);
        Minecraft.getInstance().gui.setScreen(new TutorScreen(entity));
    }

    private JsonObject tutor() {
        JsonObject s = TradeClient.state();
        if (s == null || !s.has("serantis_tutor")) return null;
        JsonObject t = s.getAsJsonObject("serantis_tutor");
        return t.get("entity").getAsInt() == entity ? t : null;
    }

    private int left() { return (width - W) / 2; }
    private int top() { return Math.max(4, (height - H) / 2); }

    private static String fit(net.minecraft.client.gui.Font font, String s, int w) {
        return font.width(s) <= w ? s : font.plainSubstrByWidth(s, w - 6) + "…";
    }

    private Tooltip tip(JsonObject m) {
        return Tooltip.create(Component.literal(m.get("name").getAsString() + "\n" + m.get("line").getAsString() + "\n\n" + m.get("explain").getAsString()));
    }

    private void send(String kind, String... kv) {
        JsonObject o = new JsonObject();
        o.addProperty("kind", kind);
        for (int i = 0; i + 1 < kv.length; i += 2) o.addProperty(kv[i], Integer.parseInt(kv[i + 1]));
        dev.ahrivaea.client.battle.BattleClient.send(o);
    }

    @Override
    protected void init() {
        JsonObject t = tutor();
        JsonArray party = TradeClient.party();
        seen = (t == null ? "" : t.toString()) + party;
        int slot = t == null ? -1 : t.get("slot").getAsInt();
        if (slot != lastSlot) { offer = -1; replace = -1; lastSlot = slot; }
        int l = left(), y = top() + 34, half = (W - 24) / 2;

        // the party: two columns of three
        for (int i = 0; i < party.size() && i < 6; i++) {
            JsonObject c = party.get(i).getAsJsonObject();
            final int s = i;
            String label = (i == slot ? "▶ " : "") + c.get("name").getAsString() + "  Lv " + c.get("level").getAsInt();
            Button b = Button.builder(Component.literal(fit(font, label, half - 8)), btn -> send("serantis_tutor_pick", "slot", String.valueOf(s)))
                    .bounds(l + 8 + (i % 2) * (half + 8), y + (i / 2) * 22, half, 20).build();
            b.active = t != null && i != slot;
            addRenderableWidget(b);
        }

        if (t == null || slot < 0 || !t.has("moves")) { addFooter(t, false); return; }
        int my = top() + 34 + 3 * 22 + 18;
        JsonArray moves = t.getAsJsonArray("moves"), offered = t.getAsJsonArray("offer");
        for (int i = 0; i < moves.size(); i++) {
            JsonObject m = moves.get(i).getAsJsonObject();
            final int k = i;
            Button b = Button.builder(Component.literal(fit(font, (i == replace ? "✖ " : "") + m.get("name").getAsString(), half - 8)),
                    btn -> { replace = k; rebuildWidgets(); }).bounds(l + 8, my + i * 22, half, 20).tooltip(tip(m)).build();
            b.active = offered.size() > 0 && i != replace;
            addRenderableWidget(b);
        }
        for (int i = 0; i < offered.size(); i++) {
            JsonObject m = offered.get(i).getAsJsonObject();
            final int k = i;
            Button b = Button.builder(Component.literal(fit(font, (i == offer ? "✚ " : "") + m.get("name").getAsString(), half - 8)),
                    btn -> { offer = k; rebuildWidgets(); }).bounds(l + 16 + half, my + i * 22, half, 20).tooltip(tip(m)).build();
            b.active = i != offer;
            addRenderableWidget(b);
        }
        boolean ready = offer >= 0 && replace >= 0 && offer < offered.size() && replace < moves.size()
                && t.get("xp").getAsInt() >= t.get("cost").getAsInt();
        addFooter(t, ready);
    }

    private void addFooter(JsonObject t, boolean ready) {
        int fy = top() + H - 28;
        Button teach = Button.builder(Component.literal("Teach (" + (t == null ? 25 : t.get("cost").getAsInt()) + " XP)"), b -> {
            send("serantis_tutor_teach", "slot", String.valueOf(t.get("slot").getAsInt()), "offer", String.valueOf(offer), "replace", String.valueOf(replace));
            offer = -1;
            replace = -1;
        }).bounds(width / 2 - 104, fy, 100, 20).build();
        teach.active = ready;
        addRenderableWidget(teach);
        addRenderableWidget(Button.builder(Component.literal("Leave"), b -> onClose()).bounds(width / 2 + 4, fy, 100, 20).build());
    }

    @Override
    public void tick() {
        super.tick();
        JsonObject t = tutor();
        String now = (t == null ? "" : t.toString()) + TradeClient.party();
        if (!now.equals(seen)) rebuildWidgets();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        g.fillGradient(0, 0, width, height, 0xD0100C08, 0xD0201810);
        int l = left(), t = top();
        g.fill(l, t, l + W, t + H, 0xF0181410);
        g.outline(l, t, W, H, 0xFF6B5420);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        int l = left(), top = top(), half = (W - 24) / 2;
        g.text(font, "Move Tutor", l + 8, top + 8, GOLD);
        JsonObject t = tutor();
        if (t == null) { g.text(font, "\"Ah, a student...\"", l + 8, top + 20, DIM); return; }
        g.text(font, fit(font, "\"Pick a creature and I'll show it 3 new moves. " + t.get("cost").getAsInt() + " XP a lesson.\"", W - 16), l + 8, top + 20, DIM);
        int slot = t.get("slot").getAsInt();
        int my = top + 34 + 3 * 22 + 18;
        if (slot < 0 || !t.has("moves")) {
            g.text(font, "Choose one of your creatures above.", l + 8, my - 12, 0xFFC8C0B0);
            return;
        }
        g.text(font, fit(font, t.get("name").getAsString() + " knows (pick one to forget):", half), l + 8, my - 12, 0xFFC8C0B0);
        if (t.getAsJsonArray("offer").size() > 0)
            g.text(font, "Can learn (pick one):", l + 16 + half, my - 12, 0xFFC8C0B0);
        else if (t.has("taught") && t.get("taught").getAsBoolean()) {
            g.text(font, "Lesson learned!", l + 16 + half, my, GOLD);
            g.text(font, fit(font, "Talk to me again for", half), l + 16 + half, my + 14, DIM);
            g.text(font, fit(font, "3 new moves.", half), l + 16 + half, my + 26, DIM);
        } else g.text(font, "Nothing new to teach.", l + 16 + half, my, DIM);
        int xp = t.get("xp").getAsInt(), cost = t.get("cost").getAsInt();
        String status = xp < cost ? "You need " + cost + " XP (you have " + xp + ")." : "You have " + xp + " XP. Hover a move for details.";
        g.text(font, fit(font, status, W - 16), l + 8, top + H - 42, xp < cost ? 0xFFE07060 : DIM);
    }

    @Override
    public void onClose() {
        if (!closed) {
            closed = true;
            TradeClient.send("serantis_tutor_close", null, null);
        }
        Minecraft.getInstance().gui.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
