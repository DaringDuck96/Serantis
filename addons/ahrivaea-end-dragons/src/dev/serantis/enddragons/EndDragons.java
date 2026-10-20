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
 */
public final class EndDragons {
    private record Pending(ServerLevel level, double x, double z) {}

    private static final List<Pending> PENDING = new ArrayList<>();
    private static List<String> ids;

    private EndDragons() {}

    public static void queue(ServerLevel level, double x, double z) {
        synchronized (PENDING) {
            PENDING.add(new Pending(level, x, z));
        }
    }

    public static void tick() {
        List<Pending> todo;
        synchronized (PENDING) {
            if (PENDING.isEmpty()) return;
            todo = new ArrayList<>(PENDING);
            PENDING.clear();
        }
        for (Pending p : todo) spawn(p);
    }

    private static void spawn(Pending p) {
        List<Species> pool = new ArrayList<>();
        for (String id : ids()) Species.get(id).ifPresent(pool::add);
        if (pool.isEmpty()) return;

        ServerLevel level = p.level();
        RandomSource random = level.getRandom();
        Species dragon = pool.get(random.nextInt(pool.size()));
        Influence influence = dragon.formOrDefault(Influence.NONE);
        int lvl = 30 + random.nextInt(16);                       // Legendary range, 30-45
        int x = (int) Math.floor(p.x());
        int z = (int) Math.floor(p.z());
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);   // on top of the exit portal
        Ahrivaea.spawn(level, dragon, influence, lvl, -1.0F, x + 0.5, y + 1, z + 0.5);

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
