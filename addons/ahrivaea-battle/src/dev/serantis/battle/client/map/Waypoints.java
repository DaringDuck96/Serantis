package dev.serantis.battle.client.map;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Places marked on the map (right-click the full map, M): a name, a colour and a spot, per world (or server) and
 * dimension. Private ones are yours alone, saved on your computer next to the map's tiles
 * (ahrivaea_map/<world>/serantis_waypoints.json). Public ones are kept by the server (PublicMarks) and everyone sees
 * them; only whoever made one can change it. Both show on the full map and the minimap.
 */
public final class Waypoints {
    public static final int[] COLORS = {0xFFE04848, 0xFFF0962E, 0xFFF2D94C, 0xFF6BD16A, 0xFF4CD9E0, 0xFF4C7AF2, 0xFFB36BF2, 0xFFF0F0F0};

    public static final class Mark {
        public String name, dim;
        public int x, z, color;
        /** A public marker (from the server): its id, who made it, and whether that's you. */
        public boolean pub, mine = true;
        public String id, by;
    }

    private static String loadedFor;
    private static final List<Mark> MARKS = new ArrayList<>();

    private Waypoints() {}

    private static String clean(String s) { return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_"); }

    /** The world (or server) we're in, as the map's tiles name it; null when not in one. */
    private static String world() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        if (mc.getSingleplayerServer() != null)
            return "singleplayer_" + clean(String.valueOf(mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName()));
        return "server_" + clean(mc.getCurrentServer() == null ? "unknown" : mc.getCurrentServer().ip);
    }

    private static Path file(String world) {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("ahrivaea_map").resolve(world).resolve("serantis_waypoints.json");
    }

    private static void ensure() {
        String w = world();
        if (w == null || w.equals(loadedFor)) return;
        loadedFor = w;
        MARKS.clear();
        try {
            Path f = file(w);
            if (!Files.exists(f)) return;
            for (var el : JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("marks")) {
                JsonObject o = el.getAsJsonObject();
                Mark m = new Mark();
                m.name = o.get("name").getAsString();
                m.dim = o.get("dim").getAsString();
                m.x = o.get("x").getAsInt();
                m.z = o.get("z").getAsInt();
                m.color = o.get("color").getAsInt();
                MARKS.add(m);
            }
        } catch (Exception ignored) { }
    }

    private static void save() {
        if (loadedFor == null) return;
        JsonArray a = new JsonArray();
        for (Mark m : MARKS) {
            JsonObject o = new JsonObject();
            o.addProperty("name", m.name);
            o.addProperty("dim", m.dim);
            o.addProperty("x", m.x);
            o.addProperty("z", m.z);
            o.addProperty("color", m.color);
            a.add(o);
        }
        JsonObject root = new JsonObject();
        root.add("marks", a);
        try {
            Path f = file(loadedFor);
            Files.createDirectories(f.getParent());
            Files.writeString(f, new GsonBuilder().setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);
        } catch (Exception ignored) { }
    }

    /** The marks in the dimension you're in. */
    public static List<Mark> here() {
        ensure();
        Minecraft mc = Minecraft.getInstance();
        List<Mark> out = new ArrayList<>();
        if (mc.level == null) return out;
        String dim = mc.level.dimension().identifier().toString();
        for (Mark m : MARKS) if (m.dim.equals(dim)) out.add(m);
        var st = dev.serantis.battle.client.TradeClient.state();
        if (st != null && st.has("serantis_public_marks"))
            for (var el : st.getAsJsonArray("serantis_public_marks")) {
                JsonObject o = el.getAsJsonObject();
                if (!dim.equals(o.get("dim").getAsString())) continue;
                Mark m = new Mark();
                m.pub = true;
                m.id = o.get("id").getAsString();
                m.name = o.get("name").getAsString();
                m.dim = dim;
                m.x = o.get("x").getAsInt();
                m.z = o.get("z").getAsInt();
                m.color = o.get("color").getAsInt();
                m.by = o.get("by").getAsString();
                m.mine = o.get("mine").getAsBoolean();
                out.add(m);
            }
        return out;
    }

    /** A public marker added, changed ("edit") or removed ("delete") on the server. */
    public static void sendPublic(String op, Mark m, String name, int x, int z, int color) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        JsonObject o = new JsonObject();
        o.addProperty("kind", "serantis_mark");
        o.addProperty("op", op);
        if (m != null && m.id != null) o.addProperty("id", m.id);
        o.addProperty("name", name);
        o.addProperty("dim", mc.level.dimension().identifier().toString());
        o.addProperty("x", x);
        o.addProperty("z", z);
        o.addProperty("color", color);
        dev.ahrivaea.client.battle.BattleClient.send(o);
    }

    public static Mark add(String name, int x, int z, int color) {
        ensure();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        Mark m = new Mark();
        m.name = name; m.x = x; m.z = z; m.color = color;
        m.dim = mc.level.dimension().identifier().toString();
        MARKS.add(m);
        save();
        return m;
    }

    public static void changed() { save(); }

    public static void remove(Mark m) {
        MARKS.remove(m);
        save();
    }
}
