package dev.serantis.battle.coop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ahrivaea.party.PartySync;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Co-op groups: up to 4 players who play together. Right-click a player to invite them; they accept or decline.
 * Group members can join each other's battles (see CoopBattles), use each other's bases, and share a group
 * contract on the bounty boards. Groups are saved with the world (serantis_groups.json).
 *
 * Also the pop-up prompts co-op uses: group invites, spar challenges and "join my battle" offers.
 */
public final class Groups {
    public static final int MAX = 4;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final class Group {
        int id;
        UUID leader;
        final LinkedHashMap<UUID, String> members = new LinkedHashMap<>();
        long bountyDay = -1;
        String bounty = "";                 // the group contract, Bounties.Contract encoded
        final Set<UUID> claimed = new HashSet<>();

        public List<UUID> members() { return new ArrayList<>(members.keySet()); }
        public String name(UUID id) { return members.getOrDefault(id, "?"); }
        public int id() { return id; }
        public long bountyDay() { return bountyDay; }
        public String bounty() { return bounty; }
        public Set<UUID> claimed() { return claimed; }
        public void setBounty(long day, String b) {
            if (day != bountyDay) claimed.clear();
            bountyDay = day;
            bounty = b;
        }
    }

    /** A question waiting for a player's yes or no. kind: invite, spar, join. */
    public record Prompt(int id, String kind, UUID from, String fromName, long created, long lifeMs) {
        int secondsLeft() { return (int) Math.max(0, (lifeMs - (System.currentTimeMillis() - created)) / 1000); }
        boolean expired() { return System.currentTimeMillis() - created > lifeMs; }
    }

    private static final Map<UUID, Group> BY_PLAYER = new ConcurrentHashMap<>();
    private static final List<Group> ALL = new ArrayList<>();
    private static final Map<UUID, Prompt> PROMPTS = new ConcurrentHashMap<>();
    private static MinecraftServer loadedFor;
    private static int nextId = 1, nextPrompt = 1;

    private Groups() {}

    // ------------------------------------------------------------------ saving

    private static Path file(MinecraftServer server) {
        return server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("serantis_groups.json");
    }

    private static synchronized void ensure(MinecraftServer server) {
        if (server == null || loadedFor == server) return;
        loadedFor = server;
        BY_PLAYER.clear();
        ALL.clear();
        PROMPTS.clear();
        nextId = 1;
        try {
            Path f = file(server);
            if (!Files.exists(f)) return;
            JsonObject root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var el : root.getAsJsonArray("groups")) {
                JsonObject o = el.getAsJsonObject();
                Group g = new Group();
                g.id = o.get("id").getAsInt();
                g.leader = UUID.fromString(o.get("leader").getAsString());
                for (var m : o.getAsJsonArray("members")) {
                    JsonObject mo = m.getAsJsonObject();
                    g.members.put(UUID.fromString(mo.get("uuid").getAsString()), mo.get("name").getAsString());
                }
                g.bountyDay = o.has("bountyDay") ? o.get("bountyDay").getAsLong() : -1;
                g.bounty = o.has("bounty") ? o.get("bounty").getAsString() : "";
                if (o.has("claimed")) for (var c : o.getAsJsonArray("claimed")) g.claimed.add(UUID.fromString(c.getAsString()));
                if (g.members.size() < 2) continue;
                ALL.add(g);
                for (UUID m : g.members.keySet()) BY_PLAYER.put(m, g);
                nextId = Math.max(nextId, g.id + 1);
            }
        } catch (Exception e) {
            dev.ahrivaea.Ahrivaea.LOGGER.warn("Couldn't read serantis_groups.json", e);
        }
    }

    public static synchronized void save(MinecraftServer server) {
        if (server == null) return;
        JsonObject root = new JsonObject();
        JsonArray gs = new JsonArray();
        for (Group g : ALL) {
            JsonObject o = new JsonObject();
            o.addProperty("id", g.id);
            o.addProperty("leader", g.leader.toString());
            JsonArray ms = new JsonArray();
            g.members.forEach((id, name) -> { JsonObject m = new JsonObject(); m.addProperty("uuid", id.toString()); m.addProperty("name", name); ms.add(m); });
            o.add("members", ms);
            o.addProperty("bountyDay", g.bountyDay);
            o.addProperty("bounty", g.bounty);
            JsonArray cl = new JsonArray();
            for (UUID c : g.claimed) cl.add(c.toString());
            o.add("claimed", cl);
            gs.add(o);
        }
        root.add("groups", gs);
        try {
            Files.writeString(file(server), GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception e) {
            dev.ahrivaea.Ahrivaea.LOGGER.warn("Couldn't save serantis_groups.json", e);
        }
    }

    private static MinecraftServer server(Player p) { return ((ServerLevel) p.level()).getServer(); }

    // ------------------------------------------------------------------ queries

    public static Group of(ServerPlayer p) {
        if (p == null) return null;
        ensure(server(p));
        return BY_PLAYER.get(p.getUUID());
    }

    public static Group of(MinecraftServer server, UUID id) {
        ensure(server);
        return id == null ? null : BY_PLAYER.get(id);
    }

    /** Are these two players in the same group? */
    public static boolean same(MinecraftServer server, UUID a, UUID b) {
        if (a == null || b == null) return false;
        if (a.equals(b)) return true;
        Group g = of(server, a);
        return g != null && g.members.containsKey(b);
    }

    public static boolean same(ServerPlayer a, UUID b) { return a != null && same(server(a), a.getUUID(), b); }

    /** Can this player use this base: it's theirs, or a group member's. */
    public static boolean canUseBase(ServerPlayer p, dev.ahrivaea.base.BaseCoreBlockEntity core) {
        return p != null && core != null && core.owner() != null && same(p, core.owner());
    }

    /** The owner as the base's own checks should see it: a group member counts as the owner (see BaseShareMixin). */
    public static UUID baseOwnerFor(dev.ahrivaea.base.BaseCoreBlockEntity core, ServerPlayer p) {
        UUID owner = core.owner();
        return owner != null && p != null && !owner.equals(p.getUUID()) && same(p, owner) ? p.getUUID() : owner;
    }

    private static void tell(ServerPlayer p, String msg, ChatFormatting c) {
        if (p != null) p.sendSystemMessage(Component.literal(msg).withStyle(c));
    }

    private static void syncGroup(MinecraftServer server, Group g) {
        if (g == null) return;
        for (UUID m : g.members.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(m);
            if (p != null) PartySync.sync(p);
        }
    }

    // ------------------------------------------------------------------ prompts

    public static Prompt prompt(ServerPlayer p) {
        Prompt pr = PROMPTS.get(p.getUUID());
        if (pr != null && pr.expired()) { PROMPTS.remove(p.getUUID(), pr); return null; }
        return pr;
    }

    public static void ask(ServerPlayer to, String kind, ServerPlayer from, long lifeMs) {
        PROMPTS.put(to.getUUID(), new Prompt(nextPrompt++, kind, from.getUUID(), from.getPlainTextName(), System.currentTimeMillis(), lifeMs));
        PartySync.sync(to);
    }

    /** The player answered a prompt (id from the screen, so an old screen can't answer a newer prompt). */
    public static void answer(ServerPlayer p, int id, boolean yes) {
        Prompt pr = prompt(p);
        if (pr == null || pr.id() != id) { PartySync.sync(p); return; }
        PROMPTS.remove(p.getUUID(), pr);
        MinecraftServer server = server(p);
        ServerPlayer from = server.getPlayerList().getPlayer(pr.from());
        switch (pr.kind()) {
            case "invite" -> {
                if (!yes) { tell(from, p.getPlainTextName() + " declined your group invite.", ChatFormatting.YELLOW); break; }
                if (from == null) { tell(p, pr.fromName() + " has left.", ChatFormatting.YELLOW); break; }
                join(p, from);
            }
            case "spar" -> {
                if (!yes) { tell(from, p.getPlainTextName() + " declined your spar.", ChatFormatting.YELLOW); break; }
                if (from == null) { tell(p, pr.fromName() + " has left.", ChatFormatting.YELLOW); break; }
                CoopBattles.startSpar(from, p);
            }
            case "join" -> { if (yes) CoopBattles.join(p, pr.from()); }
            default -> { }
        }
        PartySync.sync(p);
    }

    public static void forget(ServerPlayer p) { PROMPTS.remove(p.getUUID()); }

    // ------------------------------------------------------------------ actions

    public static void invite(ServerPlayer from, String targetId) {
        UUID id;
        try { id = UUID.fromString(targetId); } catch (IllegalArgumentException e) { return; }
        ServerPlayer to = server(from).getPlayerList().getPlayer(id);
        if (to == null || to == from) return;
        Group g = of(from), theirs = of(to);
        if (g != null && g.members.containsKey(id)) { tell(from, to.getPlainTextName() + " is already in your group.", ChatFormatting.YELLOW); return; }
        if (theirs != null) { tell(from, to.getPlainTextName() + " is already in a group.", ChatFormatting.RED); return; }
        if (g != null && g.members.size() >= MAX) { tell(from, "Your group is full (" + MAX + " players).", ChatFormatting.RED); return; }
        ask(to, "invite", from, 60_000);
        tell(from, "Group invite sent to " + to.getPlainTextName() + ".", ChatFormatting.GREEN);
    }

    private static synchronized void join(ServerPlayer p, ServerPlayer leader) {
        MinecraftServer server = server(p);
        if (of(p) != null) { tell(p, "Leave your group first.", ChatFormatting.RED); return; }
        Group g = of(leader);
        if (g == null) {
            g = new Group();
            g.id = nextId++;
            g.leader = leader.getUUID();
            g.members.put(leader.getUUID(), leader.getPlainTextName());
            ALL.add(g);
            BY_PLAYER.put(leader.getUUID(), g);
        }
        if (g.members.size() >= MAX) { tell(p, "That group is full.", ChatFormatting.RED); return; }
        g.members.put(p.getUUID(), p.getPlainTextName());
        BY_PLAYER.put(p.getUUID(), g);
        save(server);
        for (UUID m : g.members.keySet()) tell(server.getPlayerList().getPlayer(m), p.getPlainTextName() + " joined the group!", ChatFormatting.GREEN);
        syncGroup(server, g);
    }

    public static synchronized void leave(ServerPlayer p) {
        Group g = of(p);
        if (g == null) return;
        MinecraftServer server = server(p);
        g.members.remove(p.getUUID());
        BY_PLAYER.remove(p.getUUID());
        tell(p, "You left the group.", ChatFormatting.YELLOW);
        for (UUID m : g.members.keySet()) tell(server.getPlayerList().getPlayer(m), p.getPlainTextName() + " left the group.", ChatFormatting.YELLOW);
        if (g.members.size() < 2) {
            List<UUID> rest = g.members();
            ALL.remove(g);
            g.members.clear();
            for (UUID m : rest) {
                BY_PLAYER.remove(m);
                ServerPlayer mp = server.getPlayerList().getPlayer(m);
                tell(mp, "The group has disbanded.", ChatFormatting.YELLOW);
                if (mp != null) PartySync.sync(mp);
            }
        } else if (g.leader.equals(p.getUUID())) {
            g.leader = g.members.keySet().iterator().next();
        }
        save(server);
        syncGroup(server, g);
        PartySync.sync(p);
    }

    /** Keeps member names fresh (players can rename). */
    public static void seen(ServerPlayer p) {
        Group g = of(p);
        if (g != null && !p.getPlainTextName().equals(g.members.get(p.getUUID()))) {
            g.members.put(p.getUUID(), p.getPlainTextName());
            save(server(p));
        }
    }

    // ------------------------------------------------------------------ the client's view

    /** "serantis_coop": the group, and any prompt waiting for an answer. */
    public static void describe(ServerPlayer p, JsonObject state) {
        JsonObject o = new JsonObject();
        Group g = of(p);
        MinecraftServer server = server(p);
        JsonArray ms = new JsonArray();
        if (g != null) {
            for (var e : g.members.entrySet()) {
                JsonObject m = new JsonObject();
                ServerPlayer mp = server.getPlayerList().getPlayer(e.getKey());
                m.addProperty("uuid", e.getKey().toString());
                m.addProperty("name", mp != null ? mp.getPlainTextName() : e.getValue());
                m.addProperty("me", e.getKey().equals(p.getUUID()));
                m.addProperty("leader", e.getKey().equals(g.leader));
                m.addProperty("online", mp != null);
                m.addProperty("battle", mp != null && dev.ahrivaea.battle.Battles.of(mp) != null);
                m.addProperty("joinable", mp != null && !e.getKey().equals(p.getUUID()) && CoopBattles.joinable(p, mp) == null);
                m.addProperty("distance", mp != null && mp.level() == p.level() ? (int) Math.round(mp.distanceTo(p)) : -1);
                ms.add(m);
            }
        }
        o.add("members", ms);
        o.addProperty("max", MAX);
        Prompt pr = prompt(p);
        if (pr != null) {
            JsonObject q = new JsonObject();
            q.addProperty("id", pr.id());
            q.addProperty("kind", pr.kind());
            q.addProperty("from", pr.fromName());
            q.addProperty("secondsLeft", pr.secondsLeft());
            o.add("prompt", q);
        }
        state.add("serantis_coop", o);
    }

    /** Actions from the co-op screens ("serantis_coop_..."). */
    public static void handle(ServerPlayer p, JsonObject o) {
        String kind = o.get("kind").getAsString();
        String target = o.has("target") ? o.get("target").getAsString() : "";
        switch (kind) {
            case "serantis_coop_invite" -> invite(p, target);
            case "serantis_coop_spar" -> CoopBattles.challenge(p, target);
            case "serantis_coop_answer" -> answer(p, o.has("id") ? o.get("id").getAsInt() : -1, o.has("yes") && o.get("yes").getAsBoolean());
            case "serantis_coop_leave" -> leave(p);
            case "serantis_coop_join" -> {
                try { CoopBattles.join(p, UUID.fromString(target)); } catch (IllegalArgumentException ignored) { }
            }
            default -> { }
        }
        PartySync.sync(p);
    }
}
