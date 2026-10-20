package dev.serantis.battle.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * The creature card on the battle screen: who it is, then every move with a details line and a
 * plain-English explanation of exactly what it does (written by the server, see MoveExplain).
 */
public final class MovePanel {
    private MovePanel() {}

    /** Where the last card was drawn: x, y, width, height (scaled). */
    public static final int[] RECT = new int[4];

    public static int influenceColor(String influence) {
        return 0xFF000000 | switch (influence == null ? "" : influence) {
            case "fire" -> 0xFF8A4A;
            case "frost" -> 0xA6E8FF;
            case "storm" -> 0xE6DC5A;
            case "earth" -> 0xD2A46A;
            case "water" -> 0x5AAEFF;
            case "nature" -> 0x7CD668;
            case "void" -> 0xB884FF;
            case "fae" -> 0xFF9EE6;
            case "darkness" -> 0xA593C0;
            case "arcane" -> 0x62E6D6;
            case "radiant" -> 0xFFE88A;
            default -> 0xF2F2F2;
        };
    }

    private static String cap(String s) {
        return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    private static List<String> wrap(Font font, String text, int width) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = line.length() == 0 ? word : line + " " + word;
            if (font.width(next) > width && line.length() > 0) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    private record Row(String text, int color, int indent, int gapBefore, boolean divider) {
        Row(String text, int color, int indent, int gapBefore) { this(text, color, indent, gapBefore, gapBefore > 0); }
    }

    /**
     * Draws the card with its bottom-left corner at (x, bottom), no wider than maxW and no taller than
     * maxH (it shrinks to fit). hint is a small line at the bottom, or null.
     */
    public static void draw(GuiGraphicsExtractor g, JsonObject u, int x, int bottom, int maxW, int maxH, String hint) {
        Font font = Minecraft.getInstance().font;
        int w = Math.max(160, Math.min(300, maxW));
        int inner = w - 12;

        List<Row> rows = new ArrayList<>();
        boolean player = u.has("player") && u.get("player").getAsBoolean();
        String name = u.get("name").getAsString();
        String head = name + (player ? "" : "  Lv " + u.get("level").getAsInt());
        rows.add(new Row(head, 0xFFFFFFFF, 0, 0));

        List<String> facts = new ArrayList<>();
        if (!player && u.has("influence")) facts.add(cap(u.get("influence").getAsString()));
        facts.add("HP " + u.get("hp").getAsInt() + "/" + u.get("max").getAsInt());
        facts.add("moves " + u.get("squares").getAsInt() + " tiles");
        int size = u.has("serantis_size") ? u.get("serantis_size").getAsInt() : 1;
        if (size > 1) facts.add(size + "×" + size + " body");
        if (u.get("flies").getAsBoolean()) facts.add("flies");
        if (u.has("serantis_swims") && u.get("serantis_swims").getAsBoolean()) facts.add("swims");
        if (u.get("lava").getAsBoolean()) facts.add("crosses lava");
        for (String l : wrap(font, String.join(" · ", facts), inner)) rows.add(new Row(l, 0xFFB8B8B8, 0, 0));

        if (u.has("moves")) {
            for (JsonElement el : u.getAsJsonArray("moves")) {
                JsonObject m = el.getAsJsonObject();
                String infl = m.has("serantis_influence") ? m.get("serantis_influence").getAsString() : null;
                rows.add(new Row(m.get("name").getAsString(), influenceColor(infl), 0, 5));
                String line = m.has("serantis_line") ? m.get("serantis_line").getAsString()
                        : (m.get("kind").getAsString().toLowerCase() + ", range " + m.get("min").getAsInt() + "–" + m.get("max").getAsInt());
                for (String l : wrap(font, line, inner - 6)) rows.add(new Row(l, 0xFF9AA4B4, 6, 0));
                String text = m.has("serantis_explain") ? m.get("serantis_explain").getAsString()
                        : m.has("info") ? m.get("info").getAsString() : "";
                for (String l : wrap(font, text, inner - 6)) rows.add(new Row(l, 0xFFE6E6E6, 6, 0));
            }
        }
        if (hint != null) rows.add(new Row(hint, 0xFF7C8494, 0, 5, false));

        int h = 8;
        for (Row r : rows) h += 10 + r.gapBefore;
        float scale = Math.min(1f, maxH / (float) h);
        scale = Math.max(0.55f, scale);
        int sw = Math.round(w * scale), sh = Math.round(h * scale);
        int top = bottom - sh;
        RECT[0] = x; RECT[1] = top; RECT[2] = sw; RECT[3] = sh;

        g.pose().pushMatrix();
        g.pose().translate(x, top);
        g.pose().scale(scale, scale);
        int edge = player ? 0xFF5A7BD8 : (u.has("side") && "PLAYER".equals(u.get("side").getAsString()) ? 0xFF5A7BD8 : 0xFFD8605A);
        g.fill(0, 0, w, h, 0xE0101218);
        g.fill(0, 0, 2, h, edge);
        g.outline(0, 0, w, h, 0xFF2C3140);
        int y = 4;
        for (Row r : rows) {
            y += r.gapBefore;
            if (r.divider) g.fill(6, y - 3, w - 6, y - 2, 0x40FFFFFF);
            g.text(font, r.text, 6 + r.indent, y, r.color);
            y += 10;
        }
        g.pose().popMatrix();
    }
}
