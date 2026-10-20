package dev.serantis.battle.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.ahrivaea.client.base.BaseScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The player skill tree, opened from your Base Core: seven coloured branches of six skills, joined
 * top to bottom, with each capstone also linked to the skill it needs in the branch next door.
 * Learned skills are filled in, skills you can learn now glow, the rest are dimmed. Hover a skill
 * for its details; click a glowing one to learn it.
 */
public class SkillTreeScreen extends Screen {
    /** Branch order puts every capstone next to the branch it also needs. */
    private static final List<String> BRANCHES = List.of("Vitality", "Guard", "Might", "Arcana", "Fortune", "Swiftness", "Industry");
    private static final Map<String, Integer> COLOR = Map.of(
            "Vitality", 0xFFE0485A, "Guard", 0xFF8CA0B8, "Might", 0xFFE88A3A, "Arcana", 0xFFB070F0,
            "Fortune", 0xFF4CCB7A, "Swiftness", 0xFF4CC4EC, "Industry", 0xFFE0C050);
    private static final Map<String, Item> ICON = Map.of(
            "Vitality", Items.GOLDEN_APPLE, "Guard", Items.SHIELD, "Might", Items.IRON_SWORD, "Arcana", Items.AMETHYST_SHARD,
            "Fortune", Items.EMERALD, "Swiftness", Items.FEATHER, "Industry", Items.IRON_PICKAXE);
    private static final int GOLD = 0xFFF2C94C;

    private record Tile(JsonObject node, String id, int col, int row, int x, int y, int w, int h) {
        boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private BaseScreen parent;
    private final List<Tile> tiles = new ArrayList<>();
    private final Map<String, Long> learnedAt = new HashMap<>();
    private final java.util.Set<String> knownOwned = new java.util.HashSet<>();
    private String pending;
    private long pendingAt;
    private String lastMessage;
    private int colW, left, top, gap, tileW, tileH;

    public SkillTreeScreen(BaseScreen parent) {
        super(Component.literal("Skill Tree"));
        this.parent = parent;
    }

    /** The base screen is rebuilt whenever the base sends news (e.g. after learning); keep the newest. */
    public void setParent(BaseScreen next) {
        this.parent = next;
        clearWidgets();
        init();
    }

    private JsonObject data() {
        JsonObject st = parent == null ? null : parent.state();
        return st != null && st.has("serantis_skills") ? st.getAsJsonObject("serantis_skills") : null;
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init() {
        colW = Math.max(56, Math.min(104, (width - 16) / BRANCHES.size()));
        left = (width - colW * BRANCHES.size()) / 2;
        top = 92;
        gap = Math.max(20, Math.min(34, (height - top - 40) / 6));
        tileH = Math.max(16, gap - 10);
        tileW = colW - 12;
        tiles.clear();
        JsonObject d = data();
        if (d != null && d.has("message")) {
            String m = d.get("message").getAsString();
            if (!m.equals(lastMessage)) { lastMessage = m; pendingAt = 0; }
        }
        if (d != null) {
            for (JsonElement el : d.getAsJsonArray("nodes")) {
                JsonObject n = el.getAsJsonObject();
                int col = BRANCHES.indexOf(n.get("branch").getAsString());
                if (col < 0) continue;
                int row = n.get("index").getAsInt() - 1;
                String id = n.get("id").getAsString();
                tiles.add(new Tile(n, id, col, row, left + col * colW + 6, top + row * gap, tileW, tileH));
                boolean owned = n.get("owned").getAsBoolean();
                if (owned && !knownOwned.contains(id) && id.equals(pending)) learnedAt.put(id, System.currentTimeMillis());
                if (owned) knownOwned.add(id);
            }
        }
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> Minecraft.getInstance().gui.setScreen(parent))
                .bounds(width / 2 - 40, height - 26, 80, 20).build());
    }

    private Tile tile(String id) {
        for (Tile t : tiles) if (t.id.equals(id)) return t;
        return null;
    }

    private static boolean owned(Tile t) { return t.node.get("owned").getAsBoolean(); }
    private static boolean learnable(Tile t) { return !owned(t) && !t.node.has("why"); }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() <= 1)          // the main click (Minecraft 26.3 reports it as button 1)
            for (Tile t : tiles)
                if (t.contains(event.x(), event.y()) && learnable(t)) {
                    pending = t.id;
                    pendingAt = System.currentTimeMillis();
                    lastMessage = null;
                    if (parent != null) parent.send("serantis_skill", 0, t.id);
                    return true;
                }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void onClose() { Minecraft.getInstance().gui.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }

    // ------------------------------------------------------------------ drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        g.fillGradient(0, 0, width, height, 0xF00C0B14, 0xF0181226);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        JsonObject d = data();
        long now = System.currentTimeMillis();

        // Title
        g.pose().pushMatrix();
        g.pose().translate(width / 2f, 8);
        g.pose().scale(1.6f, 1.6f);
        g.centeredText(font, "Skill Tree", 0, 0, GOLD);
        g.pose().popMatrix();
        if (d == null) return;

        // XP bar toward the next skill
        int xp = d.get("xp").getAsInt(), next = d.get("next").getAsInt();
        int barW = Math.min(240, width - 40), bx = width / 2 - barW / 2, by = 30;
        g.fill(bx - 1, by - 1, bx + barW + 1, by + 9, 0xFF000000);
        g.fill(bx, by, bx + barW, by + 8, 0xFF2A2A35);
        int filled = (int) (barW * Math.min(1.0, xp / (double) Math.max(1, next)));
        boolean ready = xp >= next;
        g.fillGradient(bx, by, bx + filled, by + 8, ready ? 0xFFB8F060 : 0xFF6FBF3A, ready ? 0xFF6FBF3A : 0xFF3F7F22);
        String xpLine = String.format("%,d XP  ·  next skill %,d XP  ·  learned %d / %d  ·  Base Level %d",
                xp, next, d.get("count").getAsInt(), d.get("total").getAsInt(), d.get("baseLevel").getAsInt());
        g.centeredText(font, xpLine, width / 2, by + 12, ready ? 0xFFD8F0A0 : 0xFFB8B8C8);
        String sum = d.get("summary").getAsString();
        if (font.width(sum) > width - 20) sum = font.plainSubstrByWidth(sum, width - 26) + "…";
        g.centeredText(font, sum, width / 2, by + 24, 0xFF9FB4E0);

        // Branch columns: a faint lane, an icon and the name
        for (int i = 0; i < BRANCHES.size(); i++) {
            String b = BRANCHES.get(i);
            int c = COLOR.get(b), x0 = left + i * colW + 2, x1 = left + (i + 1) * colW - 2;
            g.fillGradient(x0, top - 26, x1, top + 6 * gap - 4, withAlpha(c, 0x30), withAlpha(c, 0x08));
            g.fill(x0, top - 26, x1, top - 25, withAlpha(c, 0xA0));
            g.item(new ItemStack(ICON.get(b)), x0 + (x1 - x0) / 2 - 8, top - 24);
            g.centeredText(font, b, x0 + (x1 - x0) / 2, top - 10, c);
        }

        // Links: down each branch, and from each capstone to the skill it needs next door
        for (Tile t : tiles) {
            if (t.row > 0) {
                Tile above = tileAt(t.col, t.row - 1);
                if (above != null) {
                    int cx = t.x + t.w / 2;
                    int col = owned(t) ? COLOR.get(branch(t)) : learnable(t) ? withAlpha(COLOR.get(branch(t)), 0xB0) : 0xFF3A3A48;
                    g.fill(cx - 1, above.y + above.h, cx + 1, t.y, col);
                }
            }
            for (JsonElement r : t.node.getAsJsonArray("requires")) {
                Tile req = byName(r.getAsString());
                if (req == null || req.col == t.col) continue;
                int lineCol = owned(t) ? GOLD : owned(req) ? withAlpha(GOLD, 0x90) : 0xFF4A4A5A;
                boolean right = req.col > t.col;
                int gapX = right ? t.x + t.w + 6 + (t.col % 2 == 0 ? 1 : -1) : t.x - 6 + (t.col % 2 == 0 ? 1 : -1);
                int ty = t.y + t.h / 2, ry = req.y + req.h / 2;
                hLine(g, right ? t.x + t.w : gapX, right ? gapX : t.x, ty, lineCol);
                g.fill(gapX - 1, Math.min(ry, ty), gapX, Math.max(ry, ty) + 1, lineCol);
                hLine(g, right ? gapX : req.x + req.w, right ? req.x : gapX, ry, lineCol);
            }
        }

        // What happened with the last skill you tried to learn
        String status = null;
        int statusColor = 0xFFB0B0C0;
        if (pendingAt > 0 && now - pendingAt > 3000) { status = "No answer from the server yet. Try again, or reopen your Base Core."; statusColor = 0xFFFF8A7A; }
        else if (pendingAt > 0) status = "Learning…";
        else if (lastMessage != null) { status = lastMessage; statusColor = lastMessage.startsWith("Learned") ? 0xFF7FE08A : 0xFFFF8A7A; }
        if (status != null) {
            if (font.width(status) > width - 20) status = font.plainSubstrByWidth(status, width - 26) + "…";
            g.centeredText(font, status, width / 2, top + 6 * gap + 2, statusColor);
        }

        // Skill tiles
        Tile hovered = null;
        for (Tile t : tiles) {
            if (t.contains(mouseX, mouseY)) hovered = t;
            drawTile(g, t, now, t == hovered || (hovered == null && t.contains(mouseX, mouseY)));
        }
        if (hovered != null) tooltip(g, hovered, d, mouseX, mouseY);
    }

    private void drawTile(GuiGraphicsExtractor g, Tile t, long now, boolean hover) {
        int c = COLOR.get(branch(t));
        boolean own = owned(t), can = learnable(t), cap = t.row == 5;
        int border, fillTop, fillBottom, text;
        if (own) {
            border = cap ? GOLD : lighten(c);
            fillTop = darken(c, 0.55f); fillBottom = darken(c, 0.35f); text = 0xFFFFFFFF;
        } else if (can) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(now / 260.0);
            border = blend(c, 0xFFFFFFFF, pulse * 0.45f);
            fillTop = 0xFF22222E; fillBottom = 0xFF181822; text = lighten(c);
        } else {
            border = 0xFF3C3C4A; fillTop = 0xFF1A1A22; fillBottom = 0xFF141419; text = 0xFF6E6E80;
        }
        if (hover) { fillTop = blend(fillTop, 0xFFFFFFFF, 0.10f); fillBottom = blend(fillBottom, 0xFFFFFFFF, 0.06f); }
        // soft glow under learnable tiles
        if (can) g.fill(t.x - 2, t.y - 2, t.x + t.w + 2, t.y + t.h + 2, withAlpha(c, 0x40));
        g.fillGradient(t.x, t.y, t.x + t.w, t.y + t.h, fillTop, fillBottom);
        g.outline(t.x, t.y, t.w, t.h, border);
        if (cap) g.outline(t.x + 1, t.y + 1, t.w - 2, t.h - 2, withAlpha(border, 0x80));
        // flash when a skill has just been learned
        Long at = learnedAt.get(t.id);
        if (at != null) {
            float k = (now - at) / 700f;
            if (k < 1) {
                int grow = (int) (k * 6);
                g.outline(t.x - grow, t.y - grow, t.w + 2 * grow, t.h + 2 * grow, withAlpha(GOLD, (int) (255 * (1 - k))));
            } else learnedAt.remove(t.id);
        }
        String label = (own ? "✔ " : cap ? "★ " : "") + t.node.get("name").getAsString();
        float scale = Math.min(1f, (t.w - 6) / (float) Math.max(1, font.width(label)));
        g.pose().pushMatrix();
        g.pose().translate(t.x + t.w / 2f, t.y + (t.h - 8 * scale) / 2f + 0.5f);
        g.pose().scale(scale, scale);
        g.centeredText(font, label, 0, 0, text);
        g.pose().popMatrix();
        // base level badge on skills your base isn't high enough for yet
        JsonObject n = t.node;
        if (!own && n.has("why") && n.get("why").getAsString().startsWith("Needs your base")) {
            String badge = "L" + n.get("tier").getAsInt();
            g.fill(t.x + t.w - font.width(badge) - 3, t.y - 4, t.x + t.w + 1, t.y + 5, 0xFF5A2A2A);
            g.pose().pushMatrix();
            g.pose().translate(t.x + t.w - font.width(badge) * 0.75f - 1, t.y - 3);
            g.pose().scale(0.75f, 0.75f);
            g.text(font, badge, 0, 0, 0xFFFFB0A0);
            g.pose().popMatrix();
        }
    }

    private void tooltip(GuiGraphicsExtractor g, Tile t, JsonObject d, int mx, int my) {
        JsonObject n = t.node;
        int c = COLOR.get(branch(t));
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(n.get("name").getAsString()).withColor(lighten(c)));
        lines.add(Component.literal(branch(t) + " · skill " + n.get("index").getAsInt() + " of 6").withColor(0xFF8888A0));
        lines.add(Component.literal(n.get("desc").getAsString()).withColor(0xFFFFFFFF));
        List<String> needs = new ArrayList<>();
        for (JsonElement r : n.getAsJsonArray("requires")) needs.add(r.getAsString());
        if (!needs.isEmpty()) lines.add(Component.literal("Needs: " + String.join(", ", needs)).withColor(0xFFB0B0C0));
        lines.add(Component.literal("Base Level " + n.get("tier").getAsInt() + " or higher").withColor(0xFFB0B0C0));
        if (owned(t)) lines.add(Component.literal("✔ Learned").withColor(0xFF7FE08A));
        else if (n.has("why")) lines.add(Component.literal(n.get("why").getAsString()).withColor(0xFFFF8A7A));
        else lines.add(Component.literal("Click to learn for " + String.format("%,d", d.get("next").getAsInt()) + " XP").withColor(GOLD));
        g.setComponentTooltipForNextFrame(font, lines, mx, my);
    }

    // ------------------------------------------------------------------ helpers

    private Tile tileAt(int col, int row) {
        for (Tile t : tiles) if (t.col == col && t.row == row) return t;
        return null;
    }

    private Tile byName(String name) {
        for (Tile t : tiles) if (t.node.get("name").getAsString().equals(name)) return t;
        return null;
    }

    private static String branch(Tile t) { return t.node.get("branch").getAsString(); }

    private static void hLine(GuiGraphicsExtractor g, int x0, int x1, int y, int color) {
        g.fill(Math.min(x0, x1), y, Math.max(x0, x1), y + 1, color);
    }

    private static int withAlpha(int argb, int alpha) { return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0xFFFFFF); }

    private static int blend(int a, int b, float k) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return (a & 0xFF000000) | ((int) (ar + (br - ar) * k) << 16) | ((int) (ag + (bg - ag) * k) << 8) | (int) (ab + (bb - ab) * k);
    }

    private static int lighten(int c) { return blend(c, 0xFFFFFFFF, 0.25f); }
    private static int darken(int c, float k) { return blend(c, 0xFF000000, 1 - k); }
}
