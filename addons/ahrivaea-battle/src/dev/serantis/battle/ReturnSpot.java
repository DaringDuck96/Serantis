package dev.serantis.battle;

import dev.ahrivaea.battle.Arena;
import dev.ahrivaea.battle.ArenaReturn;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Coming back from a battle arena puts you where you were. The creatures mod gives up and sends you to world spawn
 * when it finds no "safe" spot within 8 blocks of where you stood (you were on slabs, a path, farmland, in water, on a
 * ledge or flying) or when it lost track of where you came from. Here every player's last spot on solid ground
 * outside the arenas is remembered (each second), and if you land at world spawn from an arena while you came from
 * somewhere else, you're put back there instead: on the nearest safe spot, or else on top of the ground there.
 */
public final class ReturnSpot {
    private record Spot(ResourceKey<Level> dim, Vec3 at) {}

    private static final Map<UUID, Spot> LAST = new ConcurrentHashMap<>();

    private ReturnSpot() {}

    /** Every second, from the player's tick. */
    public static void note(ServerPlayer p) {
        if (p.level().dimension().equals(Arena.DIMENSION) || dev.ahrivaea.battle.Battles.of(p) != null) return;
        if (!p.onGround() || p.isInWater() || p.isInLava() || p.isSpectator() || p.isSleeping()) return;
        LAST.put(p.getUUID(), new Spot(p.level().dimension(), p.position()));
    }

    public static void forget(ServerPlayer p) { LAST.remove(p.getUUID()); }

    /** ArenaReturn.sendBack just ran: if it left them at world spawn though they came from elsewhere, fix it. */
    public static void afterSendBack(ServerPlayer p, boolean wasInArena) {
        if (!wasInArena || p.level().dimension().equals(Arena.DIMENSION)) return;
        Spot s = LAST.get(p.getUUID());
        if (s == null) return;
        ServerLevel overworld = p.level().getServer().overworld();
        BlockPos spawn = overworld.getRespawnData().pos();
        boolean atSpawn = p.level() == overworld && p.position().distanceToSqr(Vec3.atBottomCenterOf(spawn)) < 24 * 24;
        if (!atSpawn) return;                                           // it put them somewhere real
        boolean cameFromSpawn = s.dim().equals(Level.OVERWORLD) && s.at().distanceToSqr(Vec3.atBottomCenterOf(spawn)) < 48 * 48;
        if (cameFromSpawn) return;                                      // they really were at spawn
        ServerLevel to = p.level().getServer().getLevel(s.dim());
        if (to == null) return;
        Vec3 at = ArenaReturn.safeSpot(to, s.at());
        if (at == null) {                                               // the spot itself: it was solid ground a moment ago
            BlockPos b = BlockPos.containing(s.at());
            to.getChunk(b.getX() >> 4, b.getZ() >> 4);
            at = to.noCollision(p.getBoundingBox().move(s.at().subtract(p.position()))) ? s.at()
                    : Vec3.atBottomCenterOf(to.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, b));
        }
        p.teleportTo(to, at.x, at.y, at.z, java.util.Set.of(), p.getYRot(), p.getXRot(), true);
        p.resetFallDistance();
    }
}
