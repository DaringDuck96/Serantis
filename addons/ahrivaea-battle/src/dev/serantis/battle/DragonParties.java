package dev.serantis.battle;

import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Species;
import dev.serantis.battle.coop.Groups;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * End dragons and who they belong to. When a dragon finishes rising, the players around it are sorted into parties
 * (a group together, everyone else on their own). With one party there, the dragon is everyone's, as before. With
 * more, the first party keeps it and every other party gets its own dragon beside it, of a random other kind (each party a different one while there are kinds to go round), which only that
 * party can see, hear asked its riddle or fight. A party's dragon remembers its people (entity tags "serantis_for_<id>",
 * every member of the group), so they still see it after a restart.
 *
 * In a group, one member answering the riddle starts the battle and pulls in every group member near the dragon
 * (Riddles.done).
 */
public final class DragonParties {
    public static final String FOR = "serantis_for_";
    public static final int RANGE = 64;

    private DragonParties() {}

    /** Can this player see and deal with this entity? (Untagged ones are everyone's.) */
    public static boolean allowed(Entity e, UUID viewer) {
        boolean tagged = false;
        for (String t : e.entityTags()) {
            if (!t.startsWith(FOR)) continue;
            tagged = true;
            if (t.equals(FOR + viewer)) return true;
        }
        return !tagged;
    }

    /** The parties around a dragon that has just risen: party key -> its players there. */
    private static Map<String, List<ServerPlayer>> parties(ServerLevel level, Entity dragon) {
        Map<String, List<ServerPlayer>> out = new LinkedHashMap<>();
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || p.distanceToSqr(dragon) > RANGE * RANGE) continue;
            Groups.Group g = Groups.of(p);
            out.computeIfAbsent(g == null ? "p" + p.getUUID() : "g" + g.id(), k -> new ArrayList<>()).add(p);
        }
        return out;
    }

    private static void claim(Entity dragon, List<ServerPlayer> party) {
        for (ServerPlayer p : party) {
            dragon.addTag(FOR + p.getUUID());
            Groups.Group g = Groups.of(p);
            if (g != null) for (UUID m : g.members()) dragon.addTag(FOR + m);
        }
    }

    /** The dragon has risen: split it between the parties there. Returns who should be asked each dragon's riddle. */
    public static Map<CreatureEntity, List<ServerPlayer>> share(ServerLevel level, CreatureEntity dragon) {
        Map<CreatureEntity, List<ServerPlayer>> out = new LinkedHashMap<>();
        Map<String, List<ServerPlayer>> parties = parties(level, dragon);
        if (parties.size() <= 1) {                                       // one party (or nobody): it's everyone's, as before
            out.put(dragon, parties.isEmpty() ? List.of() : parties.values().iterator().next());
            return out;
        }
        List<String> pool = new ArrayList<>();                          // every dragon with a riddle, the risen one's kind last
        var dragons = Riddles.data().has("dragons") ? Riddles.data().getAsJsonObject("dragons").keySet() : java.util.Set.<String>of();
        for (String id : dragons) if (!id.equals(dragon.getSpeciesId()) && Species.get(id).isPresent()) pool.add(id);
        java.util.Collections.shuffle(pool);
        int n = 0;
        for (List<ServerPlayer> party : parties.values()) {
            CreatureEntity mine = dragon;
            if (n > 0) {                                                 // the next party's own dragon, a random kind, a few steps away
                String id = pool.isEmpty() ? dragon.getSpeciesId() : pool.get((n - 1) % pool.size());
                Species species = Species.get(id).orElse(null);
                if (species == null) continue;
                double a = n * 2.1;
                mine = dev.ahrivaea.Ahrivaea.spawn(level, species, species.formOrDefault(dev.ahrivaea.creature.Influence.NONE),
                        dragon.getCreatureLevel(), -1f, dragon.getX() + Math.cos(a) * 4, dragon.getY(), dragon.getZ() + Math.sin(a) * 4);
                if (mine == null) continue;
            }
            claim(mine, party);
            out.put(mine, party);
            n++;
        }
        return out;
    }
}
