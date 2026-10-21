package dev.serantis.enddragons;

import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Species;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Replaces the Ender Dragon with one of the End dragons, picked at random from
 * serantis_end_dragons/dragons.txt. Spawns are queued and made on the server tick, the same way the
 * creatures mod replaces vanilla mobs, so nothing is added to the world while an entity is loading.
 *
 * The dragon rises out of the ground: the earth shakes and cracks, and it climbs up through it over three
 * seconds amid End particles, with a roar. Once it's up it asks the players nearby its riddle (the Serantis
 * battle add-on's Riddles, told by the entity tag "serantis_riddle_ask").
 */
public final class EndDragons {
    private record Pending(ServerLevel level, double x, double z) {}

    private static final List<Pending> PENDING = new ArrayList<>();
    private record Rising(dev.ahrivaea.creature.CreatureEntity dragon, double x, double z, double fromY, double toY, int[] ticks) {}
    private static final List<Rising> RISING = new ArrayList<>();
    private static final int RISE_TICKS = 60, DEPTH = 7;
    private static List<String> ids;

    private EndDragons() {}

    public static void queue(ServerLevel level, double x, double z) {
        synchronized (PENDING) {
            PENDING.add(new Pending(level, x, z));
        }
    }

    public static void tick() {
        if (!RISING.isEmpty()) rise();
        List<Pending> todo;
        synchronized (PENDING) {
            if (PENDING.isEmpty()) return;
            todo = new ArrayList<>(PENDING);
            PENDING.clear();
        }
        for (Pending p : todo) spawn(p);
    }

    private static void sound(ServerLevel level, double x, double y, double z, String id, float volume, float pitch) {
        var s = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getValue(net.minecraft.resources.Identifier.parse("minecraft:" + id));
        if (s != null) level.playSound(null, x, y, z, s, net.minecraft.sounds.SoundSource.HOSTILE, volume, pitch);
    }

    /** Every tick: rising dragons climb out of the ground. */
    private static void rise() {
        for (var it = RISING.iterator(); it.hasNext(); ) {
            Rising r = it.next();
            var d = r.dragon();
            if (d.isRemoved() || !(d.level() instanceof ServerLevel level)) { it.remove(); continue; }
            int t = ++r.ticks()[0];
            double f = Math.min(1.0, t / (double) RISE_TICKS);
            double eased = 1 - Math.pow(1 - f, 3);
            d.setDeltaMovement(0, 0, 0);
            d.setPos(r.x(), r.fromY() + (r.toY() - r.fromY()) * eased, r.z());
            d.setHealth(d.getMaxHealth());                                   // no harm from the ground it climbs through
            var crack = new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                    net.minecraft.world.level.block.Blocks.END_STONE.defaultBlockState());
            level.sendParticles(crack, r.x(), r.toY() - 0.9, r.z(), 30, 2.2, 0.2, 2.2, 0.15);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL, r.x(), r.toY(), r.z(), 25, 2.5, 1.5, 2.5, 0.6);
            if (t % 3 == 0) level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, r.x(), d.getY() + d.getBbHeight() * 0.5, r.z(), 8, 1.5, 1.5, 1.5, 0.05);
            if (t % 10 == 1) sound(level, r.x(), r.toY(), r.z(), "entity.warden.emerge", 4f, 0.6f);
            if (t == 1) {
                for (ServerPlayer pl : level.players())
                    if (pl.distanceToSqr(r.x(), r.toY(), r.z()) < 64 * 64)
                        pl.sendOverlayMessage(Component.literal("The ground shakes...").withStyle(ChatFormatting.DARK_PURPLE));
            }
            if (t >= RISE_TICKS) {
                d.noPhysics = false;
                d.setNoGravity(false);
                d.setNoAi(false);
                sound(level, r.x(), r.toY(), r.z(), "entity.ender_dragon.growl", 6f, 0.8f);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER, r.x(), r.toY(), r.z(), 1, 0, 0, 0, 0);
                d.addTag("serantis_riddle_ask");                                 // now it asks its riddle
                it.remove();
            }
        }
    }

    private static void spawn(Pending p) {
        List<Species> pool = new ArrayList<>();
        for (String id : ids()) Species.get(id).ifPresent(pool::add);
        if (pool.isEmpty()) return;

        ServerLevel level = p.level();
        RandomSource random = level.getRandom();
        Species dragon = pool.get(random.nextInt(pool.size()));
        Influence influence = dragon.formOrDefault(Influence.NONE);
        int lvl = 20;                                            // the max level; End dragons are the strongest fights
        int x = (int) Math.floor(p.x());
        int z = (int) Math.floor(p.z());
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);   // on top of the exit portal
        var e = Ahrivaea.spawn(level, dragon, influence, lvl, -1.0F, x + 0.5, y + 1 - DEPTH, z + 0.5);
        if (e != null) {                                          // it climbs up out of the ground
            e.setNoAi(true);
            e.setNoGravity(true);
            e.noPhysics = true;
            RISING.add(new Rising(e, x + 0.5, z + 0.5, y + 1 - DEPTH, y + 1, new int[]{0}));
        }

        Component message = Component.literal(dragon.nameFor(influence) + " has appeared in the End! (level " + lvl + ")")
                .withStyle(ChatFormatting.LIGHT_PURPLE);
        for (ServerPlayer player : level.players()) player.sendSystemMessage(message);
    }

    private static synchronized List<String> ids() {
        if (ids == null) {
            List<String> out = new ArrayList<>();
            try (InputStream in = EndDragons.class.getResourceAsStream("/serantis_end_dragons/dragons.txt")) {
                if (in != null) {
                    BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    for (String line; (line = r.readLine()) != null; ) {
                        line = line.trim();
                        if (!line.isEmpty() && !line.startsWith("#")) out.add(line);
                    }
                }
            } catch (Exception e) {
                // keep whatever was read
            }
            ids = List.copyOf(out);
        }
        return ids;
    }
}
