package dev.serantis.battle;

import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Species;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * How wild creatures move in the world.
 *
 * Flyers (every creature that flies): 60% take to the air when they appear and stay airborne,
 * cruising 4-14 blocks above the ground and swooping low around nearby players (so you can still
 * reach one with a sword to battle it). The rest stay on the ground as before.
 *
 * Swimmers: 80% stay beneath the surface. Bottom dwellers (crabs, octopuses, tortoises, nautilopers,
 * leeches, reef spitters, revenants, sahuagin, river zorlings) sink and crawl along the sea floor or
 * river bed; the rest swim around below the surface. Ducks always paddle on top. The other 20% float
 * at the surface as before.
 *
 * Tamed creatures, workers, ridden or leashed creatures, and creatures in battle are left alone.
 */
public final class Locomotion {
    public static final byte UNSET = -1, NONE = 0, AIR = 1, DIVE = 2, CRAWL = 3, SURFACE = 4;
    public static final double FLY_CHANCE = 0.6, UNDERWATER_CHANCE = 0.8;

    private static final Set<String> CRAWLERS = Set.of("crab", "giant_octopus", "tortoise", "nautiloper", "whirlpool_leech",
            "reef_spitter", "revenant", "sahuagin", "river_zorling");
    private static final Set<String> SURFACE_ONLY = Set.of("duck");

    /** Added to creatures: their way of moving and where they're heading. */
    public interface Holder {
        byte serantis$moveMode();
        void serantis$setMoveMode(byte mode);
        Vec3 serantis$moveTarget();
        void serantis$setMoveTarget(Vec3 target);
        int serantis$retarget();
        void serantis$setRetarget(int ticks);
    }

    private Locomotion() {}

    public static byte decide(CreatureEntity e, RandomSource random) {
        Species sp = e.getSpecies().orElse(null);
        if (sp == null) return NONE;
        String id = sp.id();
        return switch (sp.movement()) {
            case FLY -> random.nextDouble() < FLY_CHANCE ? AIR : NONE;
            case SWIM -> SURFACE_ONLY.contains(id) || random.nextDouble() >= UNDERWATER_CHANCE ? SURFACE : CRAWLERS.contains(id) ? CRAWL : DIVE;
            default -> NONE;
        };
    }

    private static boolean free(CreatureEntity e) {
        return !e.isNoAi() && !e.isTame() && !e.isPassenger() && !e.isVehicle() && !e.isLeashed() && !e.isPreview() && e.brain() == null && e.isAlive();
    }

    /** Every server tick, before the creature's own AI. */
    public static void tick(CreatureEntity e) {
        if (!(e.level() instanceof ServerLevel level)) return;
        Holder h = (Holder) e;
        byte mode = h.serantis$moveMode();
        if (mode == UNSET) {
            mode = e.isTame() || e.brain() != null ? NONE : decide(e, level.getRandom());
            h.serantis$setMoveMode(mode);
        }
        if (mode == NONE || mode == SURFACE) return;
        if (!free(e)) {
            if (e.isTame() || e.brain() != null) { h.serantis$setMoveMode(NONE); e.setNoGravity(false); }
            return;
        }
        switch (mode) {
            case AIR -> fly(e, h, level);
            case DIVE -> dive(e, h, level);
            case CRAWL -> crawl(e, h, level);
            default -> { }
        }
    }

    private static double speed(CreatureEntity e, double scale) {
        return Mth.clamp(e.getAttributeValue(Attributes.MOVEMENT_SPEED) * scale, 0.06, 0.4);
    }

    private static void face(CreatureEntity e, Vec3 v) {
        if (v.horizontalDistanceSqr() < 1.0E-4) return;
        float yaw = (float) (Mth.atan2(v.z, v.x) * Mth.RAD_TO_DEG) - 90f;
        e.setYRot(yaw);
        e.setYHeadRot(yaw);
        e.yBodyRot = yaw;
    }

    private static boolean needsTarget(CreatureEntity e, Holder h, double reach) {
        Vec3 t = h.serantis$moveTarget();
        int left = h.serantis$retarget() - 1;
        h.serantis$setRetarget(left);
        return t == null || left <= 0 || e.horizontalCollision || e.position().distanceToSqr(t) < reach * reach;
    }

    private static int ground(ServerLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
    }

    // ------------------------------------------------------------------ flying

    private static void fly(CreatureEntity e, Holder h, ServerLevel level) {
        if (!e.isNoGravity()) e.setNoGravity(true);
        e.getNavigation().stop();
        RandomSource r = level.getRandom();
        if (needsTarget(e, h, 1.5)) {
            Player near = level.getNearestPlayer(e, 10);
            double x, z, y;
            if (near != null && !near.isSpectator() && r.nextFloat() < 0.6f) {      // swoop down around a nearby player
                x = near.getX() + r.nextInt(7) - 3;
                z = near.getZ() + r.nextInt(7) - 3;
                y = ground(level, Mth.floor(x), Mth.floor(z)) + 1.5 + r.nextDouble() * 1.5;
            } else {
                x = e.getX() + r.nextInt(33) - 16;
                z = e.getZ() + r.nextInt(33) - 16;
                if (!level.hasChunkAt(BlockPos.containing(x, e.getY(), z))) { x = e.getX(); z = e.getZ(); }
                y = ground(level, Mth.floor(x), Mth.floor(z)) + 4 + r.nextDouble() * 10;
            }
            y = Math.min(y, level.getMaxY() - 2);
            h.serantis$setMoveTarget(new Vec3(x, y, z));
            h.serantis$setRetarget(80 + r.nextInt(120));
            if (e.horizontalCollision) e.setDeltaMovement(e.getDeltaMovement().add(0, 0.25, 0));
        }
        Vec3 to = h.serantis$moveTarget().subtract(e.position());
        Vec3 want = to.lengthSqr() < 1.0E-4 ? Vec3.ZERO : to.normalize().scale(speed(e, 1.4));
        Vec3 v = e.getDeltaMovement().scale(0.85).add(want.scale(0.15));
        int floor = ground(level, e.getBlockX(), e.getBlockZ());
        if (e.getY() < floor + 1) v = new Vec3(v.x, Math.max(v.y, 0.08), v.z);      // never scrape the ground
        e.setDeltaMovement(v);
        face(e, v);
    }

    // ------------------------------------------------------------------ swimming below the surface

    private static boolean water(ServerLevel level, BlockPos p) {
        return level.getFluidState(p).is(FluidTags.WATER);
    }

    private static void dive(CreatureEntity e, Holder h, ServerLevel level) {
        if (!e.isInWater()) { if (e.isNoGravity()) e.setNoGravity(false); return; }     // beached: walk back as usual
        if (!e.isNoGravity()) e.setNoGravity(true);
        e.getNavigation().stop();
        RandomSource r = level.getRandom();
        if (needsTarget(e, h, 1.2)) {
            Vec3 pick = null;
            for (int i = 0; i < 10 && pick == null; i++) {
                BlockPos p = BlockPos.containing(e.getX() + r.nextInt(21) - 10, e.getY() + r.nextInt(7) - 4, e.getZ() + r.nextInt(21) - 10);
                if (level.hasChunkAt(p) && water(level, p) && water(level, p.above())) pick = Vec3.atCenterOf(p);   // at least a block under
            }
            if (pick == null) pick = e.position().add(0, -1, 0);
            h.serantis$setMoveTarget(pick);
            h.serantis$setRetarget(60 + r.nextInt(100));
        }
        Vec3 to = h.serantis$moveTarget().subtract(e.position());
        Vec3 want = to.lengthSqr() < 1.0E-4 ? Vec3.ZERO : to.normalize().scale(speed(e, 0.6));
        Vec3 v = e.getDeltaMovement().scale(0.8).add(want.scale(0.2));
        if (!water(level, BlockPos.containing(e.getX(), e.getY() + e.getBbHeight() + 0.4, e.getZ()))) v = new Vec3(v.x, Math.min(v.y, -0.03), v.z);   // stay under
        e.setDeltaMovement(v);
        face(e, v);
    }

    // ------------------------------------------------------------------ crawling along the bottom

    private static void crawl(CreatureEntity e, Holder h, ServerLevel level) {
        if (e.isNoGravity()) e.setNoGravity(false);
        if (!e.isInWater()) return;                                                     // on the bank: walk as usual
        e.getNavigation().stop();
        RandomSource r = level.getRandom();
        if (needsTarget(e, h, 1.0)) {
            Vec3 pick = null;
            for (int i = 0; i < 10 && pick == null; i++) {
                int x = Mth.floor(e.getX()) + r.nextInt(17) - 8, z = Mth.floor(e.getZ()) + r.nextInt(17) - 8;
                BlockPos p = new BlockPos(x, Mth.floor(e.getY()) + 2, z);
                if (!level.hasChunkAt(p)) continue;
                for (int d = 0; d < 24 && water(level, p.below()); d++) p = p.below();       // down to the bottom
                if (water(level, p)) pick = Vec3.atBottomCenterOf(p);
            }
            if (pick == null) pick = e.position();
            h.serantis$setMoveTarget(pick);
            h.serantis$setRetarget(80 + r.nextInt(120));
        }
        Vec3 to = h.serantis$moveTarget().subtract(e.position());
        Vec3 flat = new Vec3(to.x, 0, to.z);
        Vec3 want = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize().scale(speed(e, 0.35));
        Vec3 old = e.getDeltaMovement();
        double y = old.y;
        if (e.horizontalCollision && e.onGround()) y = 0.3;                                 // climb over a ledge
        else if (!e.onGround()) y = Math.min(y, -0.04);                                     // sink to the bottom
        e.setDeltaMovement(old.x * 0.7 + want.x * 0.3, y, old.z * 0.7 + want.z * 0.3);
        face(e, want);
    }
}
