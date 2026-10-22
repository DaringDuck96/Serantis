package dev.serantis.battle;

import dev.ahrivaea.base.BaseCoreBlockEntity;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Species;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Name signs at a base. Write a creature's nickname (or its kind, like "Fire Salamander") on any sign in your base,
 * and while it has no work to do, that creature stays within 5 blocks of the sign instead of wandering around the
 * base. Every creature with that name huddles at it; with several signs of the same name, each goes to the nearest.
 * The name can be the whole sign or any one line of it (capitals don't matter). Working creatures still go wherever
 * their work is, and come back when they're done.
 */
public final class NameSigns {
    public static final int RADIUS = 5;
    private static final long REFRESH_MS = 5000;

    private record Signs(long at, Map<String, List<BlockPos>> byName) {}
    /** Base core (dimension + position) -> its signs by name, read every few seconds. */
    private static final Map<String, Signs> CACHE = new ConcurrentHashMap<>();

    private NameSigns() {}

    private static String clean(String s) {
        return s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static void add(Map<String, List<BlockPos>> out, String name, BlockPos pos) {
        if (!name.isEmpty()) out.computeIfAbsent(name, k -> new ArrayList<>()).add(pos);
    }

    private static void read(Map<String, List<BlockPos>> out, Component[] lines, BlockPos pos) {
        StringBuilder all = new StringBuilder();
        for (Component c : lines) {
            String line = clean(c.getString());
            add(out, line, pos);
            if (!line.isEmpty()) all.append(line).append(' ');
        }
        String whole = clean(all.toString());
        if (whole.contains(" ")) add(out, whole, pos);
    }

    private static Map<String, List<BlockPos>> signs(ServerLevel level, BaseCoreBlockEntity be) {
        String key = level.dimension().identifier() + "@" + be.getBlockPos().asLong();
        Signs s = CACHE.get(key);
        long now = System.currentTimeMillis();
        if (s != null && now - s.at() < REFRESH_MS) return s.byName();
        Map<String, List<BlockPos>> out = new HashMap<>();
        for (int cx = be.minX() >> 4; cx <= be.maxX() >> 4; cx++)
            for (int cz = be.minZ() >> 4; cz <= be.maxZ() >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                LevelChunk chunk = level.getChunk(cx, cz);
                for (BlockEntity e : chunk.getBlockEntities().values()) {
                    if (!(e instanceof SignBlockEntity sign)) continue;
                    BlockPos p = sign.getBlockPos();
                    if (p.getX() < be.minX() || p.getX() > be.maxX() || p.getZ() < be.minZ() || p.getZ() > be.maxZ()) continue;
                    read(out, sign.getFrontText().getMessages(false), p);
                    read(out, sign.getBackText().getMessages(false), p);
                }
            }
        CACHE.put(key, new Signs(now, out));
        return out;
    }

    /** The sign this creature should stay by: the nearest with its nickname or its kind's name, or null. */
    private static BlockPos signFor(ServerLevel level, BaseCoreBlockEntity be, CreatureEntity c) {
        Map<String, List<BlockPos>> signs = signs(level, be);
        if (signs.isEmpty()) return null;
        List<BlockPos> found = new ArrayList<>();
        String nick = ((dev.serantis.battle.moves.MovesHolder) (Object) c).serantis$nickname();
        if (nick != null && !nick.isBlank()) found.addAll(signs.getOrDefault(clean(nick), List.of()));
        Species sp = Species.get(c.getSpeciesId()).orElse(null);
        if (found.isEmpty() && sp != null) found.addAll(signs.getOrDefault(clean(sp.nameFor(c.getInfluence())), List.of()));
        BlockPos best = null;
        for (BlockPos p : found) if (best == null || c.distanceToSqr(p.getCenter()) < c.distanceToSqr(best.getCenter())) best = p;
        return best;
    }

    /** WorkerBrain.wander: a creature with nothing to do. True if it has a name sign (it goes to, or stays by, it). */
    public static boolean idle(ServerLevel level, BaseCoreBlockEntity be, CreatureEntity c) {
        BlockPos sign = signFor(level, be, c);
        if (sign == null) return false;
        var nav = c.getNavigation();
        double d = Math.sqrt(c.distanceToSqr(sign.getCenter()));
        if (d > RADIUS - 0.5) {                                          // too far: walk back to the sign
            if (!nav.isInProgress() || c.tickCount % 40 == 0) nav.moveTo(sign.getX() + 0.5, sign.getY(), sign.getZ() + 0.5, 1.0);
            return true;
        }
        if (!nav.isInProgress() && level.getRandom().nextInt(6) == 0) {  // mill about near it
            var r = level.getRandom();
            int x = sign.getX() + r.nextInt(2 * (RADIUS - 2) + 1) - (RADIUS - 2), z = sign.getZ() + r.nextInt(2 * (RADIUS - 2) + 1) - (RADIUS - 2);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (Math.abs(y - sign.getY()) <= 3) nav.moveTo(x + 0.5, y, z + 0.5, 0.7);
        }
        return true;
    }
}
