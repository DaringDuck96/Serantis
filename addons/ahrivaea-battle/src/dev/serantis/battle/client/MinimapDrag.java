package dev.serantis.battle.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Moving the minimap: open chat (T), then drag the minimap with the left mouse button to anywhere on the screen.
 * Its offset from the top-right corner is saved on your computer (config/serantis_minimap.json); drag it back, or
 * delete that file, to put it back in the corner.
 */
public final class MinimapDrag {
    public static final int SIZE = 50, MARGIN = 4;
    private static int dx, dy;
    private static boolean loaded, dragging;
    private static double grabX, grabY;

    private MinimapDrag() {}

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("serantis_minimap.json");
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            dx = o.get("dx").getAsInt();
            dy = o.get("dy").getAsInt();
        } catch (Exception ignored) { }
    }

    private static void save() {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("dx", dx);
            o.addProperty("dy", dy);
            Files.createDirectories(file().getParent());
            Files.writeString(file(), o.toString(), StandardCharsets.UTF_8);
        } catch (Exception ignored) { }
    }

    /** The offset the minimap is drawn at, kept on screen. */
    public static int dx(int guiWidth) { load(); return Math.max(-(guiWidth - SIZE - 2 * MARGIN), Math.min(0, dx)); }

    public static int dy(int guiHeight) { load(); return Math.max(0, Math.min(guiHeight - SIZE - 2 * MARGIN - 12, dy)); }

    /** Every frame while chat is open: grab the minimap under the mouse and move it while the left button is held. */
    public static void chatFrame(int mouseX, int mouseY, int guiWidth, int guiHeight) {
        load();
        boolean down = Minecraft.getInstance().mouseHandler.isLeftPressed();
        int x0 = guiWidth - SIZE - MARGIN + dx(guiWidth), y0 = MARGIN + dy(guiHeight);
        if (down && !dragging && mouseX >= x0 && mouseX < x0 + SIZE && mouseY >= y0 && mouseY < y0 + SIZE) {
            dragging = true;
            grabX = mouseX - dx(guiWidth);
            grabY = mouseY - dy(guiHeight);
        }
        if (dragging && down) {
            dx = (int) Math.round(mouseX - grabX);
            dy = (int) Math.round(mouseY - grabY);
            dx = dx(guiWidth);
            dy = dy(guiHeight);
        } else if (dragging) {
            dragging = false;
            save();
        }
    }
}
