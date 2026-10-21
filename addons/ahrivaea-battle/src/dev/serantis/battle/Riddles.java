package dev.serantis.battle;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.party.PartySync;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * End dragon riddles (serantis_battle/dragon_riddles.json). Before you can fight an End dragon it asks you a riddle
 * whose answer is its influence or the stat it stands for ("Forever" for The First Dragon, "Never" for The Last,
 * "None" for the Lindworm). You pick from three answers; a wrong one gets a "try again" and you pick again. The
 * right one starts the battle, and after that you can fight that dragon without answering again.
 */
public final class Riddles {
    /** Tag the End dragon add-on puts on a dragon when it has finished rising: ask everyone nearby. */
    public static final String ASK_TAG = "serantis_riddle_ask";

    private static JsonObject data;

    private record Session(int entity, String species, List<String> options, int wrong, String line) {}
    private static final Map<UUID, Session> ASKING = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<UUID>> SOLVED = new ConcurrentHashMap<>();

    private Riddles() {}

    public static synchronized JsonObject data() {
        if (data == null) {
            try (var in = Riddles.class.getResourceAsStream("/serantis_battle/dragon_riddles.json")) {
                data = in == null ? new JsonObject() : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception e) {
                data = new JsonObject();
            }
        }
        return data;
    }

    private static JsonObject riddle(String species) {
        JsonObject d = data();
        return d.has("dragons") && d.getAsJsonObject("dragons").has(species) ? d.getAsJsonObject("dragons").getAsJsonObject(species) : null;
    }

    public static boolean isRiddleDragon(Entity e) {
        return e instanceof CreatureEntity c && !c.isTame() && riddle(c.getSpeciesId()) != null;
    }

    public static boolean solved(ServerPlayer p, Entity e) {
        return SOLVED.getOrDefault(p.getUUID(), Set.of()).contains(e.getUUID());
    }

    private static String pickLine(String key, net.minecraft.util.RandomSource r) {
        JsonArray a = data().has(key) ? data().getAsJsonArray(key) : new JsonArray();
        return a.isEmpty() ? "" : a.get(r.nextInt(a.size())).getAsString();
    }

    /** The dragon asks this player its riddle (three answers: the right one and two others, shuffled). */
    public static void ask(ServerPlayer p, CreatureEntity dragon) {
        JsonObject r = riddle(dragon.getSpeciesId());
        if (r == null || dev.ahrivaea.battle.Battles.of(p) != null) return;
        Session old = ASKING.get(p.getUUID());
        if (old != null && old.entity() == dragon.getId()) return;          // already being asked
        String answer = r.get("answer").getAsString();
        List<String> others = new ArrayList<>();
        for (var e : data().getAsJsonObject("dragons").entrySet()) {
            String a = e.getValue().getAsJsonObject().get("answer").getAsString();
            if (!a.equalsIgnoreCase(answer) && !others.contains(a)) others.add(a);
        }
        Collections.shuffle(others, new java.util.Random(p.getRandom().nextLong()));
        List<String> options = new ArrayList<>(List.of(answer));
        options.addAll(others.subList(0, Math.min(2, others.size())));
        Collections.shuffle(options, new java.util.Random(p.getRandom().nextLong()));
        ASKING.put(p.getUUID(), new Session(dragon.getId(), dragon.getSpeciesId(), options, 0, pickLine("greeting", p.getRandom())));
        PartySync.sync(p);
    }

    public static void answer(ServerPlayer p, int index) {
        Session s = ASKING.get(p.getUUID());
        if (s == null) return;
        Entity e = p.level().getEntity(s.entity());
        JsonObject r = riddle(s.species());
        if (!(e instanceof CreatureEntity dragon) || !dragon.isAlive() || r == null) { ASKING.remove(p.getUUID()); PartySync.sync(p); return; }
        if (index < 0 || index >= s.options().size()) return;
        if (s.options().get(index).equalsIgnoreCase(r.get("answer").getAsString())) {
            ASKING.put(p.getUUID(), new Session(s.entity(), s.species(), s.options(), -1, pickLine("correct", p.getRandom())));
            SOLVED.computeIfAbsent(p.getUUID(), k -> new HashSet<>()).add(dragon.getUUID());
        } else {
            ASKING.put(p.getUUID(), new Session(s.entity(), s.species(), s.options(), s.wrong() + 1, pickLine("try_again", p.getRandom())));
        }
        PartySync.sync(p);
    }

    /** After the right answer's last line: the battle begins. Also used to end the conversation without fighting. */
    public static void done(ServerPlayer p, boolean fight) {
        Session s = ASKING.remove(p.getUUID());
        PartySync.sync(p);
        if (s == null || !fight || s.wrong() >= 0) return;
        Entity e = p.level().getEntity(s.entity());
        if (e instanceof CreatureEntity dragon && dragon.isAlive() && p.distanceToSqr(dragon) < 48 * 48 && dev.ahrivaea.battle.Battles.of(p) == null)
            dev.ahrivaea.battle.Battles.start(p, dragon);
    }

    /** A dragon that has just risen asks everyone near it. */
    public static void tick(CreatureEntity e) {
        if (!e.getTags().contains(ASK_TAG) || !(e.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        e.removeTag(ASK_TAG);
        for (ServerPlayer p : level.players())
            if (p.distanceToSqr(e) < 48 * 48 && !p.isSpectator()) ask(p, e);
    }

    /** "serantis_riddle" in the party state while a dragon is asking this player. */
    public static void describe(ServerPlayer p, JsonObject state) {
        Session s = ASKING.get(p.getUUID());
        if (s == null) return;
        JsonObject r = riddle(s.species());
        if (r == null) return;
        JsonObject o = new JsonObject();
        o.addProperty("entity", s.entity());
        o.addProperty("riddle", r.get("riddle").getAsString());
        JsonArray opts = new JsonArray();
        s.options().forEach(opts::add);
        o.add("options", opts);
        o.addProperty("wrong", s.wrong());               // -1: answered correctly
        o.addProperty("line", s.line());
        state.add("serantis_riddle", o);
    }
}
