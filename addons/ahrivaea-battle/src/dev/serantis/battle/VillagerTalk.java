package dev.serantis.battle;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ahrivaea.battle.Battles;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.party.PartySync;
import dev.ahrivaea.villager.VillagerJob;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Before you battle a villager to recruit it, it talks to you (client/DialogueScreen): one of five conversations
 * for its profession (serantis_battle/villager_dialogue.json, "none" for a villager without one), ending with
 * your choice to fight it or not. Merchants, Town Guards and others that can't be recruited don't have any.
 */
public final class VillagerTalk {
    private static JsonObject all;
    /** Who's talking to which villager (entity id). */
    private static final Map<UUID, Integer> TALKING = new ConcurrentHashMap<>();
    /** Set while a battle the player chose from the conversation is starting. */
    public static final ThreadLocal<Boolean> GO = ThreadLocal.withInitial(() -> false);

    private VillagerTalk() {}

    private static synchronized JsonObject all() {
        if (all == null) {
            try (var in = VillagerTalk.class.getResourceAsStream("/serantis_battle/villager_dialogue.json")) {
                all = in == null ? new JsonObject() : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception e) {
                all = new JsonObject();
            }
        }
        return all;
    }

    /** The conversations for this villager, or null if it has none (not a villager, a merchant, a guard...). */
    public static String key(Entity e) {
        if (!(e instanceof CreatureEntity c) || c.isTame() || !dev.ahrivaea.villager.Villagers.SPECIES.equals(c.getSpeciesId())) return null;
        if (Masters.isMaster(c) || Villages.isGuard(c) || Intro.isFigure(c) || Market.isBlackMarket(c) || Wanderer.isWanderer(c)) return null;
        VillagerJob job = Villages.jobOf(c);
        String k = job == null ? "none" : job.profession();
        return all().has(k) ? k : null;
    }

    /** About to battle a villager: it talks first. True if the battle should wait. */
    public static boolean ask(ServerPlayer p, Entity e) {
        if (GO.get()) return false;
        String k = key(e);
        if (k == null) return false;
        TALKING.put(p.getUUID(), e.getId());
        PartySync.sync(p);
        return true;
    }

    /** The talk ended: fight, or leave it be. */
    public static void answer(ServerPlayer p, boolean fight) {
        Integer id = TALKING.remove(p.getUUID());
        PartySync.sync(p);
        if (!fight || id == null) return;
        Entity e = p.level().getEntity(id);
        if (!(e instanceof CreatureEntity c) || !c.isAlive() || p.distanceToSqr(c) > 12 * 12 || Battles.of(p) != null) return;
        GO.set(true);
        try { Battles.start(p, c); } finally { GO.set(false); }
    }

    /** "serantis_villager_talk" in the party state while a villager is talking: its entity id and profession. */
    public static void describe(ServerPlayer p, JsonObject state) {
        Integer id = TALKING.get(p.getUUID());
        if (id == null) return;
        Entity e = p.level().getEntity(id);
        String k = e == null ? null : key(e);
        if (k == null) { TALKING.remove(p.getUUID()); return; }
        JsonObject o = new JsonObject();
        o.addProperty("entity", id);
        o.addProperty("key", k);
        state.add("serantis_villager_talk", o);
    }
}
