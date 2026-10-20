package dev.serantis.battle.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.ahrivaea.client.battle.BattleClient;
import dev.serantis.battle.Apples;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Supplier;

/** Pick which stat a golden apple raises (opened from the party screen). See Apples. */
public class AppleScreen extends Screen {
    private static final String[] NAMES = {"HP", "Attack", "Defense", "Speed", "Magic", "Luck", "Charisma"};
    private static final int GOLD = 0xFFF2C94C, W = 300, ROW = 24;

    private final Screen parent;
    private final Supplier<JsonObject> creature;
    private final int slot;
    private String seen = "";

    public AppleScreen(Screen parent, Supplier<JsonObject> creature, int slot) {
        super(Component.literal("Golden Apple"));
        this.parent = parent;
        this.creature = creature;
        this.slot = slot;
    }

    private JsonObject c() {
        try { return creature.get(); } catch (RuntimeException e) { return null; }
    }

    private static int[] counts(JsonObject c) {
        int[] out = new int[7];
        if (c == null || !c.has("serantis_apples")) return out;
        JsonArray a = c.getAsJsonArray("serantis_apples");
        for (int i = 0; i < 7 && i < a.size(); i++) out[i] = a.get(i).getAsInt();
        return out;
    }

    private static double rate(JsonObject c) {
        return c != null && c.has("serantis_apple_rate") ? c.get("serantis_apple_rate").getAsDouble() : 0;
    }

    private static int applesHeld() {
        var p = Minecraft.getInstance().player;
        if (p == null) return 0;
        Inventory inv = p.getInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(Items.GOLDEN_APPLE)) n += s.getCount();
        }
        return n;
    }

    private int left() { return (width - W) / 2; }
    private int top() { return Math.max(8, (height - (60 + 7 * ROW + 40)) / 2); }

    @Override
    protected void init() {
        JsonObject c = c();
        int[] n = counts(c);
        seen = java.util.Arrays.toString(n) + applesHeld();
        double r = rate(c);
        boolean creative = Minecraft.getInstance().player != null && Minecraft.getInstance().player.getAbilities().instabuild;
        boolean have = applesHeld() > 0 || creative;
        int x = left() + W - 78, y = top() + 50;
        for (int i = 0; i < 7; i++) {
            String stat = Apples.STATS[i];
            Button b = Button.builder(Component.literal("Feed +" + Apples.number(r)), btn -> feed(stat))
                    .bounds(x, y + i * ROW, 70, 20).build();
            String why = c == null ? "This creature isn't in your party any more."
                    : r <= 0 ? "Golden apples have no effect on Legendary creatures."
                    : n[i] >= Apples.MAX_PER_STAT ? NAMES[i] + " has had all 10 golden apples."
                    : !have ? "You need a golden apple in your inventory." : null;
            b.active = why == null;
            b.setTooltip(Tooltip.create(Component.literal(why != null ? why
                    : "Feed a golden apple: " + NAMES[i] + " +" + Apples.number(r) + " (apple " + (n[i] + 1) + " of 10).")));
            addRenderableWidget(b);
        }
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(width / 2 - 40, y + 7 * ROW + 14, 80, 20).build());
    }

    private void feed(String stat) {
        JsonObject o = new JsonObject();
        o.addProperty("kind", "serantis_apple");
        o.addProperty("slot", slot);
        o.addProperty("stat", stat);
        BattleClient.send(o);
    }

    @Override
    public void tick() {
        super.tick();
        String now = java.util.Arrays.toString(counts(c())) + applesHeld();
        if (!now.equals(seen)) rebuildWidgets();       // the server answered (or the inventory changed)
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        g.fillGradient(0, 0, width, height, 0xC00C0B14, 0xC0181226);
        int l = left(), t = top();
        int h = 60 + 7 * ROW + 40;
        g.fill(l - 6, t - 6, l + W + 6, t + h, 0xF014161C);
        g.outline(l - 6, t - 6, W + 12, h + 6, 0xFF6B5420);
        g.fill(l - 6, t - 6, l + W + 6, t - 4, GOLD);
        int y = t + 50;
        for (int i = 0; i < 7; i++) {
            int ry = y + i * ROW;
            g.fill(l, ry - 2, l + W - 84, ry + 20, i % 2 == 0 ? 0x30FFFFFF : 0x18FFFFFF);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        JsonObject c = c();
        int l = left(), t = top();

        String name = c != null && c.has("name") ? c.get("name").getAsString() : "Creature";
        String rarity = c != null && c.has("serantis_rarity") ? c.get("serantis_rarity").getAsString() : "Common";
        double r = rate(c);
        g.centeredText(font, "Golden Apple", width / 2, t, GOLD);
        g.centeredText(font, name, width / 2, t + 12, 0xFFFFFFFF);
        String rule = r <= 0 ? rarity + ": golden apples have no effect"
                : rarity + ": +" + Apples.number(r) + " per apple, up to 10 apples per stat";
        g.centeredText(font, rule, width / 2, t + 24, r <= 0 ? 0xFFFF8A7A : 0xFFB8B8C8);
        g.text(font, "Golden apples held: " + applesHeld(), l, t + 37, 0xFF9AA4B4);

        int[] n = counts(c);
        JsonArray stats = c != null && c.has("stats") ? c.getAsJsonArray("stats") : null;
        int y = t + 50;
        for (int i = 0; i < 7; i++) {
            int ry = y + i * ROW;
            g.text(font, NAMES[i], l + 4, ry + 1, 0xFFFFFFFF);
            if (stats != null && i < stats.size()) {
                JsonArray s = stats.get(i).getAsJsonArray();
                String v = s.get(0).getAsInt() + " / " + s.get(1).getAsInt();
                g.text(font, v, l + 62, ry + 1, 0xFFDDDDDD);
            }
            // ten apple pips
            for (int k = 0; k < Apples.MAX_PER_STAT; k++) {
                int px = l + 4 + k * 8, py = ry + 12;
                g.fill(px, py, px + 6, py + 5, k < n[i] ? GOLD : 0x40FFFFFF);
            }
            String gain = n[i] == 0 ? "" : "+" + Apples.number(n[i] * r) + " from apples";
            g.text(font, gain, l + 92, ry + 11, 0xFFE8C860);
            g.text(font, n[i] + "/10", l + W - 112, ry + 1, n[i] >= 10 ? GOLD : 0xFF9AA4B4);
        }
        g.centeredText(font, "Stats show now / at Level 20. Apples raise the Level 20 value.", width / 2, y + 7 * ROW + 2, 0xFF7C8494);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
