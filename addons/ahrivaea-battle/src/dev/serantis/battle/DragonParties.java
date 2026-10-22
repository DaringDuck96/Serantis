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
 * more, the first party keeps it and every other party gets its own dragon rising out of the ground beside it, of a random other kind (each party a different one while there are kinds to go round), which only that
 * party can see, hear asked its riddle or fight. A party's dragon remembers its people (entity tags "serantis_for_<id>",
 * every member of the group), so they still see it after a restart.
 *
 * In a group, one member answering the riddle starts the battle and pulls in every group member near the dragon
 * (Riddles.done).
 */
public final class DragonParties {
    public static final String FOR = "serantis_for_";
    public static final int RANGE = 64;

    private static final int RISE_TICKS = 60, DEPTH = 7;
    private record Rising(CreatureEntity dragon, double x, double z, double fromY, double toY, List<UUID> party, int[] ticks) {}
    private static final List<Rising> RISING = new ArrayList<>();

    private DragonParties() {}

    private static void sound(ServerLevel level, double x, double y, double z, net.minecraft.sounds.SoundEvent s, float volume, float pitch) {
        level.playSound(null, x, y, z, s, net.minecraft.sounds.SoundSource.HOSTILE, volume, pitch);
    }

    /** Every server tick: each party's own dragon climbs out of the ground (as the first one did), then asks its riddle. */
    public static void tick() {
        if (RISING.isEmpty()) return;
        for (var it = RISING.iterator(); it.hasNext(); ) {
            Rising r = it.next();
            CreatureEntity d = r.dragon();
            if (d.isRemoved() || !(d.level() instanceof ServerLevel level)) { it.remove(); continue; }
            int t = ++r.ticks()[0];
            double f = Math.min(1.0, t / (double) RISE_TICKS), eased = 1 - Math.pow(1 - f, 3);
            d.setDeltaMovement(0, 0, 0);
            d.setPos(r.x(), r.fromY() + (r.toY() - r.fromY()) * eased, r.z());
            d.setHealth(d.getMaxHealth());
            var crack = new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                    net.minecraft.world.level.block.Blocks.END_STONE.defaultBlockState());
            level.sendParticles(crack, r.x(), r.toY() - 0.9, r.z(), 30, 2.2, 0.2, 2.2, 0.15);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL, r.x(), r.toY(), r.z(), 25, 2.5, 1.5, 2.5, 0.6);
            if (t % 3 == 0) level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, r.x(), d.getY() + d.getBbHeight() * 0.5, r.z(), 8, 1.5, 1.5, 1.5, 0.05);
            if (t % 10 == 1) sound(level, r.x(), r.toY(), r.z(), net.minecraft.sounds.SoundEvents.WARDEN_EMERGE, 4f, 0.6f);
            if (t < RISE_TICKS) continue;
            d.noPhysics = false;
            d.setNoGravity(false);
            d.setNoAi(false);
            d.removeTag("serantis_rising");
            sound(level, r.x(), r.toY(), r.z(), net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL, 6f, 0.8f);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER, r.x(), r.toY(), r.z(), 1, 0, 0, 0, 0);
            for (UUID id : r.party()) {
                ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
                if (p != null && p.level() == level && p.distanceToSqr(d) < 48 * 48) Riddles.ask(p, d);
            }
            it.remove();
        }
    }

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
                double a = n * 2.1, x = dragon.getX() + Math.cos(a) * 4, z = dragon.getZ() + Math.sin(a) * 4, top = dragon.getY();
                mine = dev.ahrivaea.Ahrivaea.spawn(level, species, species.formOrDefault(dev.ahrivaea.creature.Influence.NONE),
                        20, -1f, x, top - DEPTH, z);
                if (mine == null) continue;
                claim(mine, party);                                       // only its party sees it rise
                mine.setNoAi(true);                                       // it climbs out of the ground like the first one
                mine.setNoGravity(true);
                mine.noPhysics = true;
                mine.addTag("serantis_rising");
                List<UUID> who = new ArrayList<>();
                for (ServerPlayer p : party) who.add(p.getUUID());
                RISING.add(new Rising(mine, x, z, top - DEPTH, top, who, new int[]{0}));
                n++;
                continue;                                                 // asked its riddle once it's up (see tick)
            }
            claim(mine, party);
            out.put(mine, party);
            n++;
        }
        return out;
    }
}
