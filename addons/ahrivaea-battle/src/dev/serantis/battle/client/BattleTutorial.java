package dev.serantis.battle.client;

import com.google.gson.JsonObject;
import dev.ahrivaea.client.battle.BattleClient;
import dev.ahrivaea.client.battle.BattleScreen;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * A player's first battle walks them through it: a tip box under the turn banner that follows what they're doing
 * (pick a unit, pick a tile, pick an action, pick a target, end the turn, watch the enemies, the result). The
 * server marks the battle with "serantis_tutorial" while the player has never finished a battle.
 */
public final class BattleTutorial {
    private static java.lang.reflect.Field stepField, selectedField;

    private BattleTutorial() {}

    private static String step(BattleScreen screen) {
        try {
            if (stepField == null) { stepField = BattleScreen.class.getDeclaredField("step"); stepField.setAccessible(true); }
            Object s = stepField.get(screen);
            return s == null ? "UNIT" : ((Enum<?>) s).name();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "UNIT";
        }
    }

    private static int selected(BattleScreen screen) {
        try {
            if (selectedField == null) { selectedField = BattleScreen.class.getDeclaredField("selected"); selectedField.setAccessible(true); }
            return selectedField.getInt(screen);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }

    static String tip(BattleScreen screen, JsonObject st) {
        if (st.has("result")) return "That's a battle! Win to earn XP, and beaten creatures can be recruited right here for XP. Press Leave when you're done.";
        String phase = st.has("phase") ? st.get("phase").getAsString() : "";
        if (!"PLAYER".equals(phase))
            return "The enemies' turn: watch what they do. Tiles flash orange for damage, red for a critical hit and white for a miss.";
        int mine = 0, acted = 0;
        boolean meDone = false;
        for (var el : st.getAsJsonArray("units")) {
            JsonObject u = el.getAsJsonObject();
            if (!"PLAYER".equals(u.get("side").getAsString()) || u.get("hp").getAsInt() <= 0) continue;
            mine++;
            if (u.get("acted").getAsBoolean()) { acted++; if (u.get("player").getAsBoolean()) meDone = true; }
        }
        return switch (step(screen)) {
            case "DESTINATION" -> "Step 2: the blue tiles show how far it can move. Click one to move there, or click its own tile to stay put.";
            case "ACTION" -> "Step 3: choose what to do. Moves show their range and power. You (not your creatures) can also Rally a creature next to you for extra damage on its next attack, or drink a healing potion.";
            case "TARGET" -> "Step 4: click a highlighted target. Your attackers can be countered, so watch their health bars.";
            default -> acted == 0
                    ? "Your turn! Step 1: click one of your units (you or a creature with a blue frame) to select it. Scroll to zoom and drag to look around."
                    : acted < mine
                    ? "Nice! Do the same with your other units (" + acted + " of " + mine + " have acted). Press End Turn whenever you're done; the enemies move once everyone has acted."
                    : "All your units have acted: the enemies move next.";
        };
    }

    public static void draw(BattleScreen screen, GuiGraphicsExtractor g, Font font) {
        JsonObject st = BattleClient.state();
        if (st == null || !st.has("serantis_tutorial")) return;
        String tip = tip(screen, st);
        int w = Math.min(340, screen.width - 260), x = (screen.width - w) / 2, y = 96;
        if (w < 160) { w = Math.min(340, screen.width - 20); x = (screen.width - w) / 2; }
        List<String> rows = wrap(font, tip, w - 12);
        int h = 16 + rows.size() * 10;
        g.fill(x, y, x + w, y + h, 0xE0101828);
        g.outline(x, y, w, h, 0xFF4A8CCF);
        g.text(font, "First battle", x + 6, y + 4, 0xFF8FD3FF);
        for (int i = 0; i < rows.size(); i++) g.text(font, rows.get(i), x + 6, y + 14 + i * 10, 0xFFE6E2D8);
    }

    private static List<String> wrap(Font font, String text, int w) {
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
}
