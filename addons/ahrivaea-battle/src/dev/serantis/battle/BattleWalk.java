package dev.serantis.battle;

import dev.ahrivaea.battle.Battle;
import dev.ahrivaea.battle.Unit;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Battle pacing: a creature (or you) walks across the field tile by tile to where it's going instead of jumping
 * there, and what it does when it gets there (the hit, the heal, the numbers and flashes) shows up once it has
 * arrived: the battle's next update to the players waits for its walks to finish. Enemies also wait for one
 * another, so their turns play out one after the other.
 */
public final class BattleWalk {
    /** Game ticks to cross one tile. */
    public static final int TICKS_PER_TILE = 5;

    private static final class Walk {
        final Battle battle;
        final List<Vec3> points = new ArrayList<>();   // one per tile, the last is where it stops
        int tick;
        Walk(Battle b) { this.battle = b; }
        int ticks() { return points.size() * TICKS_PER_TILE; }
        boolean done() { return tick >= ticks(); }
        Vec3 at(Vec3 start) {                          // where it is now, sliding from one tile's middle to the next
            int i = Math.min(points.size() - 1, tick / TICKS_PER_TILE);
            double f = (tick % TICKS_PER_TILE + 1) / (double) TICKS_PER_TILE;
            Vec3 from = i == 0 ? start : points.get(i - 1), to = points.get(i);
            return done() ? points.getLast() : from.lerp(to, f);
        }
    }

    private static final Map<UUID, Walk> WALKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Vec3> STARTS = new ConcurrentHashMap<>();
    /** Battles with an update held back until their walks are done. */
    private static final Map<Battle, Boolean> PENDING = new IdentityHashMap<>();

    private BattleWalk() {}

    /** The tiles from here to there, one step at a time (straight across, diagonals allowed). */
    private static Walk path(Battle b, Vec3 from, Vec3 to) {
        Walk w = new Walk(b);
        int steps = (int) Math.round(Math.max(Math.abs(to.x - from.x), Math.abs(to.z - from.z)));
        if (steps < 1) return w;
        for (int i = 1; i <= steps; i++) {
            double f = i / (double) steps;
            double x = from.x + (to.x - from.x) * f, z = from.z + (to.z - from.z) * f;
            double y = to.y;
            if (i < steps) {                                       // the ground under it on the way
                int cx = (int) Math.floor(x) - b.grid.originX, cz = (int) Math.floor(z) - b.grid.originZ;
                y = b.grid.inside(cx, cz) ? Math.max(b.grid.worldPos(cx, cz).getY(), Math.min(from.y, to.y)) : to.y;
            }
            w.points.add(new Vec3(x, y, z));
        }
        return w;
    }

    /** A creature should be at (x, y, z): walk it there. False if it's close enough to just be put there. */
    public static boolean walk(Battle b, Entity e, double x, double y, double z) {
        if (b == null || e == null || dev.serantis.battle.mixin.BattlesAccess.serantis$spawning() || b.phase == Battle.Phase.OVER) return false;
        Vec3 from = e.position(), to = new Vec3(x, y, z);
        if (from.distanceToSqr(to) < 0.36 || from.distanceToSqr(to) > 40 * 40) return false;
        Walk w = path(b, from, to);
        if (w.points.isEmpty()) return false;
        WALKS.put(e.getUUID(), w);
        STARTS.put(e.getUUID(), from);
        return true;
    }

    /** The player's unit moved (players are kept on their tile every tick): walk them there too. */
    public static void walkPlayer(Battle b, Unit u) {
        ServerPlayer p = dev.serantis.battle.coop.CoopBattles.playerOf(b, u);
        if (p == null) p = b.player();
        if (p == null) return;
        var c = b.cellPos(u);
        walk(b, p, c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
    }

    /** For the per-tick "keep the player on their tile": where they should be right now. */
    public static Vec3 playerAt(ServerPlayer p, double x, double y, double z) {
        Walk w = WALKS.get(p.getUUID());
        if (w == null) return new Vec3(x, y, z);
        return w.at(STARTS.getOrDefault(p.getUUID(), p.position()));
    }

    public static boolean busy(Battle b) {
        for (Walk w : WALKS.values()) if (w.battle == b && !w.done()) return true;
        return false;
    }

    /** Battles.send: hold an update back while someone in the battle is still walking. */
    public static boolean hold(Battle b) {
        if (b == null || b.phase == Battle.Phase.OVER || !busy(b)) return false;
        synchronized (PENDING) { PENDING.put(b, true); }
        return true;
    }

    /** Every server tick: step the walkers, and send held updates once a battle's walks are done. */
    public static void tick(net.minecraft.server.MinecraftServer server) {
        for (var it = WALKS.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next(); try {
            Walk w = en.getValue();
            Entity e = w.battle.level.getEntity(en.getKey());
            if (e == null) e = server.getPlayerList().getPlayer(en.getKey());
            if (e == null || w.battle.phase == Battle.Phase.OVER) { it.remove(); STARTS.remove(en.getKey()); dev.serantis.battle.fx.Fx.flush(w.battle); continue; }
            Vec3 start = STARTS.getOrDefault(en.getKey(), e.position());
            w.tick++;
            Vec3 now = w.at(start);
            if (!(e instanceof ServerPlayer)) {
                Vec3 d = now.subtract(e.position());
                if (d.horizontalDistanceSqr() > 1.0E-4) {          // face the way it's walking
                    float yaw = (float) Math.toDegrees(Math.atan2(d.z, d.x)) - 90f;
                    e.setYRot(yaw);
                    e.setYHeadRot(yaw);
                    if (e instanceof net.minecraft.world.entity.LivingEntity le) le.setYBodyRot(yaw);
                }
                e.setPos(now.x, now.y, now.z);
            }
            if (w.done()) { it.remove(); STARTS.remove(en.getKey()); if (w.battle.phase == Battle.Phase.OVER || !busy(w.battle)) dev.serantis.battle.fx.Fx.flush(w.battle); }
            } catch (Throwable t) { it.remove(); STARTS.remove(en.getKey()); dev.serantis.battle.Safe.caught("walking a battle step", t); }
        }
        List<Battle> ready = new ArrayList<>();
        synchronized (PENDING) {
            for (Battle b : PENDING.keySet()) if (!busy(b)) ready.add(b);
            for (Battle b : ready) PENDING.remove(b);
        }
        for (Battle b : ready) {
            dev.serantis.battle.fx.Fx.flush(b);                         // its hit sounds, now that the hit shows
            dev.ahrivaea.battle.Battles.send(b);
        }
    }
}
