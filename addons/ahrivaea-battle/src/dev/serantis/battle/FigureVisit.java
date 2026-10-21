package dev.serantis.battle;

import com.google.gson.JsonObject;
import dev.ahrivaea.battle.Battles;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.party.PartySync;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Mysterious Figure's visits after the opening scene (serantis_battle/figure_scenes.json): at milestones it
 * walks up to you wherever you are (you can't move while it does; client/FigureScreen), talks, and vanishes.
 * Each plays once: after 10 battles, a base at Level 2, the first Gym Master Stone, the first tower rebuilt,
 * the first steps in the Nether, three towers rebuilt. At most one every 5 minutes.
 */
public final class FigureVisit {
    public static final int BATTLES = 10;
    private static final double STEP = 0.11, STOP = 2.2;

    /** The scenes, by id: their flag bits. */
    private static java.util.Map<String, Integer> scenes;
    private static final java.util.Map<UUID, Long> LAST_SCENE = new ConcurrentHashMap<>();
    private static final long GAP = 20 * 60 * 5;

    private static synchronized java.util.Map<String, Integer> scenes() {
        if (scenes == null) {
            java.util.Map<String, Integer> out = new java.util.LinkedHashMap<>();
            try (var in = FigureVisit.class.getResourceAsStream("/serantis_battle/figure_scenes.json")) {
                if (in != null) for (var e : com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                        .getAsJsonObject().getAsJsonArray("scenes")) {
                    var o = e.getAsJsonObject();
                    out.put(o.get("id").getAsString(), o.get("bit").getAsInt());
                }
            } catch (Exception ignored) { }
            scenes = out;
        }
        return scenes;
    }

    /** A milestone met for this scene? */
    private static boolean reached(ServerPlayer p, String id) {
        return switch (id) {
            case "battles" -> ((Welcome.Holder) p).serantis$battlesFought() >= BATTLES;
            case "base" -> ownsBase(p, 2);
            case "stone" -> stones(p) >= 1;
            case "tower" -> Achievements.value(p, "towers") >= 1;
            case "towers" -> Achievements.value(p, "towers") >= 3;
            case "nether" -> p.level().dimension() == net.minecraft.world.level.Level.NETHER;
            default -> false;
        };
    }

    private static int stones(ServerPlayer p) {
        int n = 0;
        for (String inf : Masters.INFLUENCES) n += ((Masters.Holder) p).serantis$stones(inf);
        return n;
    }

    /** A base of theirs (loaded right now) at this level or higher. */
    private static boolean ownsBase(ServerPlayer p, int lvl) {
        for (ServerLevel level : p.level().getServer().getAllLevels())
            for (BlockPos pos : dev.ahrivaea.base.Bases.cores(level)) {
                if (!level.isLoaded(pos)) continue;
                var core = dev.ahrivaea.base.Bases.coreAt(level, pos);
                if (core != null && p.getUUID().equals(core.owner()) && core.baseLevel() >= lvl) return true;
            }
        return false;
    }

    private static final class Visit {
        String scene;
        UUID figure;
        boolean arrived;
        int ticks;
    }

    private static final Map<UUID, Visit> ACTIVE = new ConcurrentHashMap<>();
    /** When each player's last battle ended (game time), so the visit waits a moment after it. */
    private static final Map<UUID, Long> LAST_BATTLE = new ConcurrentHashMap<>();

    private FigureVisit() {}

    public static boolean owns(Entity e) {
        for (Visit v : ACTIVE.values()) if (e.getUUID().equals(v.figure)) return true;
        return false;
    }

    /** A battle ended (won or lost): count it. */
    public static void battleOver(ServerPlayer p) {
        if (p == null) return;
        Welcome.Holder h = (Welcome.Holder) p;
        seed(p);
        h.serantis$setBattlesFought(h.serantis$battlesFought() + 1);
        LAST_BATTLE.put(p.getUUID(), p.level().getGameTime());
    }

    /** Players from before the count existed start from their battles won. */
    private static void seed(ServerPlayer p) {
        Welcome.Holder h = (Welcome.Holder) p;
        if (h.serantis$battlesFought() < 0)
            h.serantis$setBattlesFought(p.getStats().getValue(net.minecraft.stats.Stats.CUSTOM, dev.ahrivaea.villager.Villagers.BATTLES_WON));
    }

    /** Every tick, per player. */
    public static void tick(ServerPlayer p) {
        Visit v = ACTIVE.get(p.getUUID());
        if (v == null) {
            if (p.tickCount % 20 != 13) return;
            Welcome.Holder h = (Welcome.Holder) p;
            seed(p);
            if (Intro.pending(p) || Battles.of(p) != null || p.isSpectator()) return;
            long now = p.level().getGameTime();
            if (now - LAST_BATTLE.getOrDefault(p.getUUID(), -1000L) < 60 || now - LAST_SCENE.getOrDefault(p.getUUID(), -GAP) < GAP) return;
            if (!p.onGround() || p.isPassenger() || p.isSleeping()) return;
            for (var e : scenes().entrySet())
                if ((h.serantis$figureMet() & e.getValue()) == 0 && reached(p, e.getKey())) { start(p, e.getKey()); return; }
            return;
        }
        ServerLevel level = (ServerLevel) p.level();
        Entity e = level.getEntity(v.figure);
        if (e == null || !e.isAlive()) { ACTIVE.remove(p.getUUID()); PartySync.sync(p); return; }
        v.ticks++;
        Vec3 to = p.position().subtract(e.position());
        double flat = Math.sqrt(to.x * to.x + to.z * to.z);
        float yaw = (float) Math.toDegrees(Math.atan2(to.z, to.x)) - 90f;
        e.setYRot(yaw);
        e.setYHeadRot(yaw);
        if (e instanceof CreatureEntity c) c.setYBodyRot(yaw);
        if (!v.arrived) {
            if (flat <= STOP || v.ticks > 20 * 20) {
                v.arrived = true;
                PartySync.sync(p);
            } else {
                double step = Math.min(STEP, flat - STOP);
                double ny = e.getY() + Math.max(-0.3, Math.min(0.3, p.getY() - e.getY()));
                e.snapTo(e.getX() + to.x / flat * step, ny, e.getZ() + to.z / flat * step);
            }
        }
        if (v.ticks > 20 * 120) done(p);                  // never left hanging
    }

    private static void start(ServerPlayer p, String scene) {
        ServerLevel level = (ServerLevel) p.level();
        // it comes from the first direction with open ground ~9 blocks away, else from close by
        Vec3 from = null;
        for (int i = 0; i < 8 && from == null; i++) {
            double a = (p.getYRot() + 90 + i * 45) * Math.PI / 180.0;
            for (double d = 9; d >= 4 && from == null; d -= 1) {
                Vec3 c = p.position().add(Math.cos(a) * d, 0, Math.sin(a) * d);
                if (clearPath(level, p.position(), c)) from = c;
            }
        }
        if (from == null) from = p.position().add(p.getLookAngle().x * 3, 0, p.getLookAngle().z * 3);
        CreatureEntity f = Intro.figureAt(level, from.x, from.y, from.z);
        if (f == null) return;
        Visit v = new Visit();
        v.scene = scene;
        v.figure = f.getUUID();
        LAST_SCENE.put(p.getUUID(), p.level().getGameTime());
        ACTIVE.put(p.getUUID(), v);
        PartySync.sync(p);
    }

    /** Open ground all the way (feet and head clear, something to stand on). */
    private static boolean clearPath(ServerLevel level, Vec3 a, Vec3 b) {
        int n = (int) Math.ceil(a.distanceTo(b));
        for (int i = 1; i <= n; i++) {
            Vec3 c = a.lerp(b, i / (double) n);
            BlockPos at = BlockPos.containing(c);
            if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty()) return false;
            if (!level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty()) return false;
            if (level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty()) return false;
        }
        return true;
    }

    /** The talk is over: it vanishes in a puff of smoke. */
    public static void done(ServerPlayer p) {
        Visit v = ACTIVE.remove(p.getUUID());
        if (v == null) return;
        Welcome.Holder h = (Welcome.Holder) p;
        h.serantis$setFigureMet(h.serantis$figureMet() | scenes().getOrDefault(v.scene, 0));
        ServerLevel level = (ServerLevel) p.level();
        Entity e = level.getEntity(v.figure);
        if (e != null) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, e.getX(), e.getY() + 1, e.getZ(), 30, 0.3, 0.6, 0.3, 0.02);
            level.sendParticles(ParticleTypes.PORTAL, e.getX(), e.getY() + 1, e.getZ(), 40, 0.4, 0.8, 0.4, 0.3);
            level.playSound(null, e.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.NEUTRAL, 1f, 0.8f);
            e.discard();
        }
        PartySync.sync(p);
    }

    /** "serantis_visit" in the party state during the visit: the figure's entity id, and whether it's arrived. */
    public static void describe(ServerPlayer p, JsonObject state) {
        Visit v = ACTIVE.get(p.getUUID());
        if (v == null) return;
        Entity e = ((ServerLevel) p.level()).getEntity(v.figure);
        JsonObject o = new JsonObject();
        o.addProperty("entity", e == null ? -1 : e.getId());
        o.addProperty("arrived", v.arrived);
        o.addProperty("scene", v.scene);
        state.add("serantis_visit", o);
    }
}
