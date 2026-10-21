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
 * The player skill tree, opened from your Base Core: eight coloured branches of six skills, joined
 * top to bottom, with each capstone also linked to the skill it needs in the branch next door.
 * Branches are laid out side by side at a readable width; when they don't all fit they wrap into a
 * second row and the tree scrolls (mouse wheel).
 * Learned skills are filled in, skills you can learn now glow, the rest are dimmed. Hover a skill
 * for its details; click a glowing one to learn it.
 */
public class SkillTreeScreen extends Screen {
    /** Branch order puts every capstone next to the branch it also needs. */
    private static final List<String> BRANCHES = List.of("Vitality", "Guard", "Might", "Arcana", "Fortune", "Swiftness", "Industry", "Presence");
    private static final Map<String, Integer> COLOR = Map.of(
            "Vitality", 0xFFE0485A, "Guard", 0xFF8CA0B8, "Might", 0xFFE88A3A, "Arcana", 0xFFB070F0,
            "Fortune", 0xFF4CCB7A, "Swiftness", 0xFF4CC4EC, "Industry", 0xFFE0C050, "Presence", 0xFFF07AB8);
    private static final Map<String, Item> ICON = Map.of(
            "Vitality", Items.GOLDEN_APPLE, "Guard", Items.SHIELD, "Might", Items.IRON_SWORD, "Arcana", Items.AMETHYST_SHARD,
            "Fortune", Items.EMERALD, "Swiftness", Items.FEATHER, "Industry", Items.IRON_PICKAXE, "Presence", Items.BELL);
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

    // Layout. Tiles are placed in "content" coordinates (y from the top of the scrolling area).
    private static final int COL_MIN = 104, COL_MAX = 132, TILE_H = 19, ROW_GAP = 28, BAND_HEAD = 30, BAND_GAP = 20;
    private int colW, perRow, bands, viewTop, viewBottom, contentH, scroll;
    private int[] bandLeft;

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

    /** Which band (row of branches) and column a branch sits in. */
    private int bandOf(int branch) { return branch / perRow; }
    private int colOf(int branch) { return branch % perRow; }
    private int bandY(int band) { return band * (BAND_HEAD + 6 * ROW_GAP + BAND_GAP); }

    @Override
    protected void init() {
        int n = BRANCHES.size();
        // As many branches side by side as fit at a readable width; the rest go in further rows (scroll down).
        perRow = Math.max(1, Math.min(n, (width - 16) / COL_MIN));
        if (perRow < n && perRow > n / 2) perRow = (n + 1) / 2;        // two even rows rather than 7 + 1
        bands = (n + perRow - 1) / perRow;
        colW = Math.min(COL_MAX, (width - 16) / perRow);
        bandLeft = new int[bands];
        for (int b = 0; b < bands; b++) {
            int cols = Math.min(perRow, n - b * perRow);
            bandLeft[b] = (width - cols * colW) / 2;
        }
        viewTop = 66;
        viewBottom = height - 38;
        contentH = bandY(bands) - BAND_GAP;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentH - (viewBottom - viewTop))));

        tiles.clear();
        JsonObject d = data();
        if (d != null && d.has("message")) {
            String m = d.get("message").getAsString();
            if (!m.equals(lastMessage)) { lastMessage = m; pendingAt = 0; }
        }
        if (d != null) {
            for (JsonElement el : d.getAsJsonArray("nodes")) {
                JsonObject node = el.getAsJsonObject();
                int br = BRANCHES.indexOf(node.get("branch").getAsString());
                if (br < 0) continue;
                int row = node.get("index").getAsInt() - 1;
                String id = node.get("id").getAsString();
                int x = bandLeft[bandOf(br)] + colOf(br) * colW + 8;
                int y = bandY(bandOf(br)) + BAND_HEAD + row * ROW_GAP;
                tiles.add(new Tile(node, id, br, row, x, y, colW - 16, TILE_H));
                boolean owned = node.get("owned").getAsBoolean();
                if (owned && !knownOwned.contains(id) && id.equals(pending)) learnedAt.put(id, System.currentTimeMillis());
                if (owned) knownOwned.add(id);
            }
        }
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> Minecraft.getInstance().gui.setScreen(parent))
                .bounds(width / 2 - 40, height - 23, 80, 20).build());
    }

    private Tile tile(String id) {
        for (Tile t : tiles) if (t.id.equals(id)) return t;
        return null;
    }

    private static boolean owned(Tile t) { return t.node.get("owned").getAsBoolean(); }
    private static boolean learnable(Tile t) { return !owned(t) && !t.node.has("why"); }

    /** Screen y of a content y. */
    private int sy(int contentY) { return viewTop + contentY - scroll; }
    private boolean visible(int contentY, int h) { return sy(contentY) >= viewTop - 1 && sy(contentY) + h <= viewBottom + 1; }

    private Tile tileUnder(double mx, double my) {
        if (my < viewTop || my > viewBottom) return null;
        for (Tile t : tiles)
            if (mx >= t.x && mx < t.x + t.w && my >= sy(t.y) && my < sy(t.y) + t.h && visible(t.y, t.h)) return t;
        return null;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() <= 1) {        // the main click (Minecraft 26.3 reports it as button 1)
            Tile t = tileUnder(event.x(), event.y());
            if (t != null && learnable(t)) {
                pending = t.id;
                pendingAt = System.currentTimeMillis();
                lastMessage = null;
                if (parent != null) parent.send("serantis_skill", 0, t.id);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        int max = Math.max(0, contentH - (viewBottom - viewTop));
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.round(dy * 24)));
        return true;
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
        g.pose().translate(width / 2f, 6);
        g.pose().scale(1.5f, 1.5f);
        g.centeredText(font, "Skill Tree", 0, 0, GOLD);
        g.pose().popMatrix();
        if (d == null) { g.centeredText(font, "Loading…", width / 2, 40, 0xFF9FB4E0); return; }

        // XP bar toward the next skill
        int xp = d.get("xp").getAsInt(), next = d.get("next").getAsInt();
        int barW = Math.min(260, width - 40), bx = width / 2 - barW / 2, by = 20;
        g.fill(bx - 1, by - 1, bx + barW + 1, by + 7, 0xFF000000);
        g.fill(bx, by, bx + barW, by + 6, 0xFF2A2A35);
        int filled = (int) (barW * Math.min(1.0, xp / (double) Math.max(1, next)));
        boolean ready = xp >= next;
        g.fillGradient(bx, by, bx + filled, by + 6, ready ? 0xFFB8F060 : 0xFF6FBF3A, ready ? 0xFF6FBF3A : 0xFF3F7F22);
        String xpLine = String.format("%,d XP  ·  next skill %,d XP  ·  learned %d / %d  ·  Base Level %d",
                xp, next, d.get("count").getAsInt(), d.get("total").getAsInt(), d.get("baseLevel").getAsInt());
        g.centeredText(font, fit(xpLine, width - 16), width / 2, by + 9, ready ? 0xFFD8F0A0 : 0xFFB8B8C8);
        // your stats, wrapped onto two lines
        List<String> sum = wrap(d.get("summary").getAsString(), width - 24);
        for (int i = 0; i < Math.min(2, sum.size()); i++)
            g.centeredText(font, i == 1 && sum.size() > 2 ? fit(sum.get(1) + " " + sum.get(2), width - 24) : sum.get(i), width / 2, by + 21 + i * 10, 0xFF9FB4E0);
        g.fill(8, viewTop - 3, width - 8, viewTop - 2, 0x30FFFFFF);

        // Branch lanes with an icon and name on top
        for (int i = 0; i < BRANCHES.size(); i++) {
            String b = BRANCHES.get(i);
            int c = COLOR.get(b), x0 = bandLeft[bandOf(i)] + colOf(i) * colW + 3, x1 = x0 + colW - 6;
            int laneTop = bandY(bandOf(i)), laneBottom = laneTop + BAND_HEAD + 6 * ROW_GAP - 6;
            int top = Math.max(viewTop, sy(laneTop)), bottom = Math.min(viewBottom, sy(laneBottom));
            if (bottom > top) g.fillGradient(x0, top, x1, bottom, withAlpha(c, 0x2A), withAlpha(c, 0x08));
            if (visible(laneTop, 24)) {
                g.fill(x0, sy(laneTop), x1, sy(laneTop) + 1, withAlpha(c, 0xB0));
                int mid = x0 + (x1 - x0) / 2, w = 16 + 4 + font.width(b);
                g.item(new ItemStack(ICON.get(b)), mid - w / 2, sy(laneTop) + 5);
                g.text(font, b, mid - w / 2 + 20, sy(laneTop) + 9, c);
            }
        }

        // Links: down each branch, and from each capstone to the skill it needs next door
        for (Tile t : tiles) {
            if (t.row > 0) {
                Tile above = tileAt(t.col, t.row - 1);
                if (above != null) {
                    int cx = t.x + t.w / 2;
                    int col = owned(t) ? COLOR.get(branch(t)) : learnable(t) ? withAlpha(COLOR.get(branch(t)), 0xB0) : 0xFF3A3A48;
                    vLine(g, cx, above.y + above.h, t.y, col, 2);
                }
            }
            for (JsonElement r : t.node.getAsJsonArray("requires")) {
                Tile req = byName(r.getAsString());
                if (req == null || req.col == t.col) continue;
                int lineCol = owned(t) ? GOLD : owned(req) ? withAlpha(GOLD, 0x90) : 0xFF5A5A6A;
                if (bandOf(req.col) != bandOf(t.col) || Math.abs(colOf(req.col) - colOf(t.col)) != 1) {
                    // in another row of branches: say so under the capstone instead of drawing a long line
                    String note = "+ needs " + r.getAsString();
                    if (visible(t.y + t.h + 1, 9)) {
                        g.pose().pushMatrix();
                        g.pose().translate(t.x + t.w / 2f, sy(t.y + t.h) + 2);
                        g.pose().scale(0.75f, 0.75f);
                        g.centeredText(font, note, 0, 0, lineCol);
                        g.pose().popMatrix();
                    }
                    continue;
                }
                boolean right = colOf(req.col) > colOf(t.col);
                int gapX = right ? t.x + t.w + 8 : t.x - 8;
                int ty = t.y + t.h / 2, ry = req.y + req.h / 2;
                hLine(g, right ? t.x + t.w : gapX, right ? gapX : t.x, ty, lineCol);
                vLine(g, gapX, Math.min(ry, ty), Math.max(ry, ty) + 1, lineCol, 1);
                hLine(g, right ? gapX : req.x + req.w, right ? req.x : gapX, ry, lineCol);
            }
        }

        // Skill tiles
        Tile hovered = tileUnder(mouseX, mouseY);
        for (Tile t : tiles) if (visible(t.y, t.h)) drawTile(g, t, now, t == hovered);

        // Scroll hints
        int max = Math.max(0, contentH - (viewBottom - viewTop));
        if (scroll > 0) g.centeredText(font, "▲ scroll up", width / 2, viewTop + 1, 0xFF8890A0);
        if (scroll < max) g.centeredText(font, "▼ scroll for more", width / 2, viewBottom - 9, 0xFF8890A0);

        // What happened with the last skill you tried to learn
        String status = null;
        int statusColor = 0xFFB0B0C0;
        if (pendingAt > 0 && now - pendingAt > 3000) { status = "No answer from the server yet. Try again, or reopen your Base Core."; statusColor = 0xFFFF8A7A; }
        else if (pendingAt > 0) status = "Learning…";
        else if (lastMessage != null) { status = lastMessage; statusColor = lastMessage.startsWith("Learned") ? 0xFF7FE08A : 0xFFFF8A7A; }
        else status = "Hover a skill for details · click a glowing skill to learn it";
        g.centeredText(font, fit(status, width - 16), width / 2, height - 34, statusColor);

        if (hovered != null) tooltip(g, hovered, d, mouseX, mouseY);
    }

    private void drawTile(GuiGraphicsExtractor g, Tile t, long now, boolean hover) {
        int c = COLOR.get(branch(t));
        int x = t.x, y = sy(t.y);
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
            border = 0xFF3C3C4A; fillTop = 0xFF1A1A22; fillBottom = 0xFF141419; text = 0xFF8A8A9C;
        }
        if (hover) { fillTop = blend(fillTop, 0xFFFFFFFF, 0.12f); fillBottom = blend(fillBottom, 0xFFFFFFFF, 0.08f); }
        if (can) g.fill(x - 2, y - 2, x + t.w + 2, y + t.h + 2, withAlpha(c, 0x40));     // soft glow under learnable tiles
        g.fillGradient(x, y, x + t.w, y + t.h, fillTop, fillBottom);
        g.outline(x, y, t.w, t.h, border);
        if (cap) g.outline(x + 1, y + 1, t.w - 2, t.h - 2, withAlpha(border, 0x80));
        Long at = learnedAt.get(t.id);                                                  // flash when just learned
        if (at != null) {
            float k = (now - at) / 700f;
            if (k < 1) {
                int grow = (int) (k * 6);
                g.outline(x - grow, y - grow, t.w + 2 * grow, t.h + 2 * grow, withAlpha(GOLD, (int) (255 * (1 - k))));
            } else learnedAt.remove(t.id);
        }
        String label = (own ? "✔ " : cap ? "★ " : "") + t.node.get("name").getAsString();
        float scale = Math.max(0.75f, Math.min(1f, (t.w - 6) / (float) Math.max(1, font.width(label))));
        if (font.width(label) * scale > t.w - 4) label = font.plainSubstrByWidth(label, (int) ((t.w - 8) / scale)) + "…";
        g.pose().pushMatrix();
        g.pose().translate(x + t.w / 2f, y + (t.h - 8 * scale) / 2f + 0.5f);
        g.pose().scale(scale, scale);
        g.centeredText(font, label, 0, 0, text);
        g.pose().popMatrix();
        JsonObject n = t.node;                                                          // base level badge
        if (!own && n.has("why") && n.get("why").getAsString().startsWith("Needs your base")) {
            String badge = "Base " + n.get("tier").getAsInt();
            int bw = (int) (font.width(badge) * 0.75f) + 4;
            g.fill(x + t.w - bw, y - 5, x + t.w + 1, y + 3, 0xFF5A2A2A);
            g.pose().pushMatrix();
            g.pose().translate(x + t.w - bw + 2, y - 4);
            g.pose().scale(0.75f, 0.75f);
            g.text(font, badge, 0, 0, 0xFFFFC0B0);
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

    private String fit(String s, int w) {
        return font.width(s) <= w ? s : font.plainSubstrByWidth(s, w - 6) + "…";
    }

    private List<String> wrap(String text, int w) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String part : text.split(" · ")) {
            String next = line.length() == 0 ? part : line + " · " + part;
            if (font.width(next) > w && line.length() > 0) { out.add(line.toString()); line = new StringBuilder(part); }
            else line = new StringBuilder(next);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    /** A vertical line in content coordinates, cut to the visible area. */
    private void vLine(GuiGraphicsExtractor g, int x, int y0, int y1, int color, int w) {
        int a = Math.max(viewTop, sy(y0)), b = Math.min(viewBottom, sy(y1));
        if (b > a) g.fill(x - w / 2, a, x - w / 2 + w, b, color);
    }

    /** A horizontal line in content coordinates (skipped when off screen). */
    private void hLine(GuiGraphicsExtractor g, int x0, int x1, int y, int color) {
        int s = sy(y);
        if (s >= viewTop && s <= viewBottom) g.fill(Math.min(x0, x1), s, Math.max(x0, x1), s + 1, color);
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

    private static int withAlpha(int argb, int alpha) { return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0xFFFFFF); }

    private static int blend(int a, int b, float k) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return (a & 0xFF000000) | ((int) (ar + (br - ar) * k) << 16) | ((int) (ag + (bg - ag) * k) << 8) | (int) (ab + (bb - ab) * k);
    }

    private static int lighten(int c) { return blend(c, 0xFFFFFFFF, 0.25f); }
    private static int darken(int c, float k) { return blend(c, 0xFF000000, 1 - k); }
}
