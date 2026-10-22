package dev.serantis.battle;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Public map markers: places marked on the big map (M) that every player on the server sees. Private markers stay on
 * each player's own computer (client/map/Waypoints); public ones are kept here, in serantis_public_markers.json in
 * the world folder, and sent to everyone with the party state ("serantis_public_marks"). Only the player who made a
 * public marker can change or remove it.
 */
public final class PublicMarks {
    private static final class Mark {
        String id, name, dim, ownerName;
        UUID owner;
        int x, z, color;
    }

    private static final List<Mark> MARKS = new ArrayList<>();
    private static MinecraftServer loadedFor;
    private static final int MAX_PER_PLAYER = 64;

    private PublicMarks() {}

    private static Path file(MinecraftServer server) {
        return server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("serantis_public_markers.json");
    }

    private static synchronized void load(MinecraftServer server) {
        if (loadedFor == server) return;
        loadedFor = server;
        MARKS.clear();
        try {
            Path f = file(server);
            if (!Files.exists(f)) return;
            for (var el : JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("marks")) {
                JsonObject o = el.getAsJsonObject();
                Mark m = new Mark();
                m.id = o.get("id").getAsString();
                m.name = o.get("name").getAsString();
                m.dim = o.get("dim").getAsString();
                m.owner = UUID.fromString(o.get("owner").getAsString());
                m.ownerName = o.has("ownerName") ? o.get("ownerName").getAsString() : "?";
                m.x = o.get("x").getAsInt();
                m.z = o.get("z").getAsInt();
                m.color = o.get("color").getAsInt();
                MARKS.add(m);
            }
        } catch (Exception e) {
            // nothing saved
        }
    }

    private static synchronized void save(MinecraftServer server) {
        JsonArray a = new JsonArray();
        for (Mark m : MARKS) {
            JsonObject o = new JsonObject();
            o.addProperty("id", m.id);
            o.addProperty("name", m.name);
            o.addProperty("dim", m.dim);
            o.addProperty("owner", m.owner.toString());
            o.addProperty("ownerName", m.ownerName);
            o.addProperty("x", m.x);
            o.addProperty("z", m.z);
            o.addProperty("color", m.color);
            a.add(o);
        }
        JsonObject root = new JsonObject();
        root.add("marks", a);
        try {
            Files.writeString(file(server), new GsonBuilder().setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // couldn't save: kept until a restart
        }
    }

    private static String clean(String name) {
        name = name == null ? "" : name.trim();
        if (name.length() > 32) name = name.substring(0, 32);
        return name.isEmpty() ? "Marker" : name;
    }

    /** From the map: {"op": "add"|"edit"|"delete", "id", "name", "dim", "x", "z", "color"}. */
    public static synchronized void handle(ServerPlayer p, JsonObject o) {
        MinecraftServer server = p.level().getServer();
        load(server);
        String op = o.has("op") ? o.get("op").getAsString() : "";
        Mark found = null;
        if (o.has("id")) for (Mark m : MARKS) if (m.id.equals(o.get("id").getAsString())) found = m;
        switch (op) {
            case "add" -> {
                long mine = MARKS.stream().filter(m -> m.owner.equals(p.getUUID())).count();
                if (mine >= MAX_PER_PLAYER) { p.sendOverlayMessage(net.minecraft.network.chat.Component.literal("You have " + MAX_PER_PLAYER + " public markers already.")); return; }
                Mark m = new Mark();
                m.id = UUID.randomUUID().toString();
                m.owner = p.getUUID();
                m.ownerName = p.getPlainTextName();
                m.name = clean(o.get("name").getAsString());
                m.dim = o.get("dim").getAsString();
                m.x = o.get("x").getAsInt();
                m.z = o.get("z").getAsInt();
                m.color = o.get("color").getAsInt();
                MARKS.add(m);
            }
            case "edit" -> {
                if (found == null || !found.owner.equals(p.getUUID())) return;
                found.name = clean(o.get("name").getAsString());
                found.color = o.get("color").getAsInt();
            }
            case "delete" -> {
                if (found == null || !found.owner.equals(p.getUUID())) return;
                MARKS.remove(found);
            }
            default -> { return; }
        }
        save(server);
        for (ServerPlayer q : server.getPlayerList().getPlayers()) dev.ahrivaea.party.PartySync.sync(q);
    }

    /** "serantis_public_marks" in the party state: every public marker, and whether it's this player's. */
    public static synchronized void describe(ServerPlayer p, JsonObject state) {
        load(p.level().getServer());
        if (MARKS.isEmpty()) return;
        JsonArray a = new JsonArray();
        for (Mark m : MARKS) {
            JsonObject o = new JsonObject();
            o.addProperty("id", m.id);
            o.addProperty("name", m.name);
            o.addProperty("dim", m.dim);
            o.addProperty("x", m.x);
            o.addProperty("z", m.z);
            o.addProperty("color", m.color);
            o.addProperty("by", m.ownerName);
            o.addProperty("mine", m.owner.equals(p.getUUID()));
            a.add(o);
        }
        state.add("serantis_public_marks", a);
    }
}
