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
 * more, the first party keeps it and every other party gets its own dragon of the same kind beside it, which only that
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
        Species species = Species.get(dragon.getSpeciesId()).orElse(null);
        int n = 0;
        for (List<ServerPlayer> party : parties.values()) {
            CreatureEntity mine = dragon;
            if (n > 0 && species != null) {                             // the next party's own dragon, a few steps away
                double a = n * 2.1;
                mine = dev.ahrivaea.Ahrivaea.spawn(level, species, dragon.getInfluence(), dragon.getCreatureLevel(), -1f,
                        dragon.getX() + Math.cos(a) * 3, dragon.getY(), dragon.getZ() + Math.sin(a) * 3);
                if (mine == null) continue;
                mine.setVariant(dragon.getVariant());
            }
            claim(mine, party);
            out.put(mine, party);
            n++;
        }
        return out;
    }
}
