package dev.serantis.battle;

import dev.ahrivaea.base.BaseCoreBlockEntity;
import dev.ahrivaea.base.Bases;
import dev.ahrivaea.creature.CreatureEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Achievement credit for what your bases do: seeds your workers plant count as Seeds Planted and items
 * smelted in furnaces at your bases count as Items Smelted (the owner's, while online). Also counts distance
 * ridden on creatures for Distance Traveled.
 */
public final class BaseCredit {
    private static final Map<UUID, Vec3> LAST_RIDE = new ConcurrentHashMap<>();

    private BaseCredit() {}

    /** The base this position is in (null: none loaded). */
    public static BaseCoreBlockEntity baseAt(ServerLevel level, BlockPos pos) {
        for (BlockPos c : Bases.cores(level)) {
            if (!level.isLoaded(c)) continue;
            BaseCoreBlockEntity core = Bases.coreAt(level, c);
            if (core == null) continue;
            int r = core.radius();
            if (Math.abs(pos.getX() - c.getX()) <= r && Math.abs(pos.getZ() - c.getZ()) <= r
                    && pos.getY() >= c.getY() - BaseCoreBlockEntity.DOWN && pos.getY() <= c.getY() + BaseCoreBlockEntity.UP) return core;
        }
        return null;
    }

    private static ServerPlayer owner(ServerLevel level, BaseCoreBlockEntity core) {
        return core == null || core.owner() == null ? null : level.getServer().getPlayerList().getPlayer(core.owner());
    }

    /** A worker at this base planted a seed. */
    public static void planted(ServerLevel level, BlockPos core) {
        ServerPlayer p = owner(level, core == null ? null : Bases.coreAt(level, core));
        if (p != null) ((Achievements.Holder) p).serantis$addPlanted(1);
    }

    /** A furnace finished smelting an item here. */
    public static void smelted(ServerLevel level, BlockPos furnace) {
        ServerPlayer p = owner(level, baseAt(level, furnace));
        if (p != null) ((Achievements.Holder) p).serantis$addSmelted(1);
    }

    /** Is this furnace in one of this player's bases (its smelting is already counted)? */
    public static boolean inOwnBase(ServerPlayer p, ServerLevel level, BlockPos furnace) {
        BaseCoreBlockEntity core = baseAt(level, furnace);
        return core != null && p.getUUID().equals(core.owner());
    }

    /** Every tick: distance ridden on a creature. */
    public static void ride(ServerPlayer p) {
        if (!(p.getVehicle() instanceof CreatureEntity mount)) { LAST_RIDE.remove(p.getUUID()); return; }
        Vec3 now = mount.position(), was = LAST_RIDE.put(p.getUUID(), now);
        if (was == null) return;
        double d = Math.sqrt((now.x - was.x) * (now.x - was.x) + (now.z - was.z) * (now.z - was.z) + (now.y - was.y) * (now.y - was.y));
        if (d > 0.01 && d < 10) ((Achievements.Holder) p).serantis$addRodeCm(Math.round(d * 100));
    }

    /** Spots each player planted recently (packed pos -> game time): replanting the same spot within a day doesn't count again. */
    private static final Map<UUID, Map<Long, Long>> PLANTED = new ConcurrentHashMap<>();

    public static boolean freshPlanting(net.minecraft.server.level.ServerPlayer p, net.minecraft.core.BlockPos pos) {
        long now = p.level().getGameTime();
        Map<Long, Long> mine = PLANTED.computeIfAbsent(p.getUUID(), k -> new java.util.HashMap<>());
        if (mine.size() > 4096) mine.values().removeIf(t -> now - t > 24000);
        Long before = mine.put(pos.asLong(), now);
        return before == null || now - before > 24000 || now < before;
    }

    public static void forgetAll() { PLANTED.clear(); LAST_RIDE.clear(); }
}
