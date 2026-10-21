package dev.serantis.battle.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** The achievement log (opened from the party screen). See Achievements on the server. */
public class AchievementScreen extends Screen {
    private static final int GOLD = 0xFFF2C94C, W = 360, ROW = 31, HEAD = 42;
    private static final java.util.Map<String, ItemStack> ICONS = new java.util.HashMap<>();
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI"};

    private final Screen parent;
    private int ticks;
    private int hoverX, hoverY;                       // mouse position inside the (scaled) panel
    private List<Component> tip;
    private int scroll, visible = 1, rowCount;

    private static ItemStack icon(JsonObject a) {
        String id = a.has("icon") ? a.get("icon").getAsString() : "minecraft:golden_apple";
        return ICONS.computeIfAbsent(id, k -> {
            try {
                Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(k));
                return new ItemStack(item == null || item == Items.AIR ? Items.GOLDEN_APPLE : item);
            } catch (RuntimeException e) {
                return new ItemStack(Items.GOLDEN_APPLE);
            }
        });
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        scroll = Math.max(0, Math.min(Math.max(0, rowCount - visible), scroll - (int) Math.signum(dy)));
        return true;
    }

    public AchievementScreen(Screen parent) {
        super(Component.literal("Achievements"));
        this.parent = parent;
    }

    private static void refresh() {
        JsonObject o = new JsonObject();
        o.addProperty("kind", "serantis_achievements");
        dev.ahrivaea.client.battle.BattleClient.send(o);
    }

    @Override
    protected void init() {
        refresh();
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose()).bounds(width - 66, height - 26, 60, 20).build());
    }

    @Override
    public void tick() {
        super.tick();
        if (++ticks % 40 == 0) refresh();             // keep the numbers fresh while it's open
    }

    private float scale() { return Math.min(1f, (width - 12) / (float) W); }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        g.fillGradient(0, 0, width, height, 0xF00C0B14, 0xF0181226);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        JsonObject st = TradeClient.state();
        JsonArray list = st != null && st.has("serantis_achievements") ? st.getAsJsonArray("serantis_achievements") : new JsonArray();
        float s = scale();
        rowCount = list.size();
        visible = Math.max(1, Math.min(rowCount, (int) (((height - 34) / s - HEAD - 18) / ROW)));
        scroll = Math.max(0, Math.min(scroll, rowCount - visible));
        int h = HEAD + Math.max(1, visible) * ROW + 18;
        float x0 = (width - W * s) / 2f, y0 = Math.max(4, (height - 30 - h * s) / 2f);

        hoverX = (int) ((mouseX - x0) / s);
        hoverY = (int) ((mouseY - y0) / s);
        tip = null;
        g.pose().pushMatrix();
        g.pose().translate(x0, y0);
        g.pose().scale(s, s);

        g.fill(0, 0, W, h, 0xE0141620);
        g.outline(0, 0, W, h, 0xFF6B5420);
        g.fill(0, 0, W, 2, GOLD);
        g.item(new ItemStack(Items.GOLDEN_APPLE), 8, 8);
        g.text(font, "Achievements", 30, 8, GOLD);
        if (st != null && st.has("serantis_apples_earned"))
            g.text(font, "Golden apples earned: " + st.get("serantis_apples_earned").getAsInt() + " of " + st.get("serantis_apples_total").getAsInt(),
                    30, 20, 0xFFB8B8C8);
        if (list.isEmpty()) g.text(font, "Loading…", 30, HEAD, 0xFF8890A0);

        for (int i = scroll; i < Math.min(rowCount, scroll + visible); i++) row(g, list.get(i).getAsJsonObject(), i, HEAD + (i - scroll) * ROW);
        if (rowCount > visible) {
            String more = (scroll > 0 ? "▲ " : "") + "Scroll for more (" + (scroll + 1) + "–" + Math.min(rowCount, scroll + visible) + " of " + rowCount + ")"
                    + (scroll + visible < rowCount ? " ▼" : "");
            g.text(font, more, W / 2 - font.width(more) / 2, h - 13, 0xFF8890A0);
        }
        g.pose().popMatrix();
        if (tip != null) g.setComponentTooltipForNextFrame(font, tip, mouseX, mouseY);
    }

    private void row(GuiGraphicsExtractor g, JsonObject a, int i, int y) {
        JsonArray tiers = a.getAsJsonArray("tiers"), apples = a.getAsJsonArray("apples");
        int claimed = a.get("claimed").getAsInt(), n = tiers.size();
        long value = a.get("value").getAsLong();
        boolean done = claimed >= n;
        g.fill(4, y - 2, W - 4, y + ROW - 4, i % 2 == 0 ? 0x26FFFFFF : 0x12FFFFFF);
        g.item(icon(a), 8, y + 4);

        // the title of the last tier earned (or the track's name before the first)
        JsonArray titles = a.has("titles") ? a.getAsJsonArray("titles") : null;
        String title = claimed > 0 && titles != null ? titles.get(Math.min(claimed, titles.size()) - 1).getAsString()
                : a.get("name").getAsString();
        g.text(font, font.plainSubstrByWidth(title, 186), 30, y + 1, done ? GOLD : claimed > 0 ? 0xFFFFFFFF : 0xFFB8BCC8);

        // progress toward the next tier
        long prev = claimed == 0 ? 0 : tiers.get(claimed - 1).getAsLong();
        long next = done ? tiers.get(n - 1).getAsLong() : tiers.get(claimed).getAsLong();
        float f = done ? 1f : Math.max(0f, Math.min(1f, (value - prev) / (float) Math.max(1, next - prev)));
        int bx = 30, bw = 180, by = y + 13;
        g.fill(bx, by, bx + bw, by + 6, 0xFF26262E);
        g.fillGradient(bx, by, bx + Math.round(bw * f), by + 6, done ? 0xFFF2C94C : 0xFF8FD16A, done ? 0xFFB8902C : 0xFF4E9A35);
        String count = done ? String.format("%,d %s · complete!", value, a.get("verb").getAsString())
                : String.format("%,d / %,d %s", value, next, a.get("verb").getAsString());
        g.text(font, font.plainSubstrByWidth(count, 200), 30, by + 8, 0xFF9AA4B4);

        // the tiers: gold once earned, with what each pays
        int px = 222;
        for (int k = 0; k < n; k++) {
            int cx = px + k * 22;
            boolean got = k < claimed;
            g.fill(cx, y + 1, cx + 20, y + 21, got ? 0xFF6B5420 : 0xFF22242C);
            g.outline(cx, y + 1, 20, 21, got ? GOLD : (k == claimed ? 0xFF8FD16A : 0xFF3A3D48));
            String num = a.has("badge") ? a.get("badge").getAsString() : "+" + apples.get(k).getAsInt();
            g.text(font, num, cx + 10 - font.width(num) / 2, y + 7, got ? GOLD : 0xFFB0B4C0);
        }
        // hover a tier to see what it takes
        int lx = hoverX, ly = hoverY;
        if (ly >= y + 1 && ly < y + 22)
            for (int k = 0; k < n; k++) {
                int cx = px + k * 22;
                if (lx >= cx && lx < cx + 20)
                    tip = List.of(Component.literal(titles != null && k < titles.size() ? titles.get(k).getAsString() : a.get("name").getAsString()).withColor(GOLD),
                            Component.literal(a.get("name").getAsString() + " " + ROMAN[k]).withColor(0xFF9AA4B4),
                            Component.literal(String.format("%,d %s", tiers.get(k).getAsLong(), a.get("verb").getAsString())).withColor(0xFFFFFFFF),
                            Component.literal("Reward: " + (a.has("reward") ? a.get("reward").getAsString() : apples.get(k).getAsInt() + " golden apples")).withColor(0xFFE8C860),
                            Component.literal(k < claimed ? "✔ Earned" : "Not yet").withColor(k < claimed ? 0xFF7FE08A : 0xFF9AA4B4));
            }
    }

    @Override
    public void onClose() { Minecraft.getInstance().gui.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
