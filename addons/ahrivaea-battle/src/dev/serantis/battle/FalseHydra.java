package dev.serantis.battle;

import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Species;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The False Hydra is summoned exactly like the Wither: a T of four soul sand or soul soil blocks with three wither
 * skeleton skulls on top. The blocks vanish and, in place of the Wither, the False Hydra appears (Legendary, Arcane,
 * Level 20). Like the Wither, it charges for 11 seconds first, glowing and untouchable, its health and boss bar
 * filling, then bursts out in a great explosion (power 7, like the Wither's) with the Wither's world-wide spawn sound.
 * After that it's an ordinary wild creature: battle it, beat it, recruit it. The boss bar shows its health to
 * everyone within 64 blocks while it's wild.
 */
public final class FalseHydra {
    public static final String SPECIES = "false_hydra";
    public static final int CHARGE_TICKS = 220, BAR_RANGE = 64;

    private record Pending(ServerLevel level, double x, double y, double z) {}
    private static final class Charging { final CreatureEntity e; int t; Charging(CreatureEntity e) { this.e = e; } }

    private static final List<Pending> PENDING = new ArrayList<>();
    private static final List<Charging> CHARGING = new ArrayList<>();
    /** Wild False Hydras around and their boss bars. */
    private static final Map<CreatureEntity, ServerBossEvent> BARS = new LinkedHashMap<>();

    private FalseHydra() {}

    public static boolean is(Entity e) {
        return e instanceof CreatureEntity c && SPECIES.equals(c.getSpeciesId());
    }

    /** Still charging up: can't be fought yet. */
    public static boolean charging(Entity e) {
        for (Charging c : CHARGING) if (c.e == e) return true;
        return false;
    }

    /** A Wither was summoned: the False Hydra comes instead (spawned on the next tick). */
    public static void queue(ServerLevel level, double x, double y, double z) {
        synchronized (PENDING) { PENDING.add(new Pending(level, x, y, z)); }
    }

    /** Every server tick. */
    public static void tick() {
        List<Pending> todo;
        synchronized (PENDING) { todo = new ArrayList<>(PENDING); PENDING.clear(); }
        for (Pending p : todo) spawn(p);
        if (!CHARGING.isEmpty()) charge();
        if (!BARS.isEmpty()) bars();
    }

    private static void spawn(Pending p) {
        Species sp = Species.get(SPECIES).orElse(null);
        if (sp == null) return;
        CreatureEntity e = Ahrivaea.spawn(p.level(), sp, sp.formOrDefault(Influence.ARCANE), 20, -1f, p.x(), p.y(), p.z());
        if (e == null) return;
        e.setNoAi(true);
        e.setInvulnerable(true);
        e.setGlowingTag(true);
        e.setHealth(e.getMaxHealth() / 3f);
        CHARGING.add(new Charging(e));
        bar(e);
    }

    private static void charge() {
        for (var it = CHARGING.iterator(); it.hasNext(); ) {
            Charging c = it.next();
            CreatureEntity e = c.e;
            if (e.isRemoved() || !(e.level() instanceof ServerLevel level)) { it.remove(); continue; }
            int t = ++c.t;
            float f = Math.min(1f, t / (float) CHARGE_TICKS);
            e.setDeltaMovement(0, 0, 0);
            e.setHealth(Math.max(1f, e.getMaxHealth() * (1f / 3f + 2f / 3f * f)));
            if (t % 2 == 0) level.sendParticles(ParticleTypes.WITCH, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 6,
                    e.getBbWidth() * 0.5, e.getBbHeight() * 0.4, e.getBbWidth() * 0.5, 0.05);
            if (t >= CHARGE_TICKS) {
                level.explode(e, e.getX(), e.getEyeY(), e.getZ(), 7f, false, Level.ExplosionInteraction.MOB);
                if (!e.isSilent()) level.globalLevelEvent(1023, e.blockPosition(), 0);   // the Wither's spawn sound, heard everywhere
                e.setInvulnerable(false);
                e.setGlowingTag(false);
                e.setNoAi(false);
                e.setHealth(e.getMaxHealth());
                it.remove();
            }
        }
    }

    private static void bar(CreatureEntity e) {
        BARS.computeIfAbsent(e, k -> new ServerBossEvent(UUID.randomUUID(), Component.literal("False Hydra").withStyle(ChatFormatting.LIGHT_PURPLE),
                BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS));
    }

    private static void bars() {
        for (var it = BARS.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            CreatureEntity e = en.getKey();
            ServerBossEvent bar = en.getValue();
            if (e.isRemoved() || e.isTame() || !(e.level() instanceof ServerLevel level)) { bar.removeAllPlayers(); it.remove(); continue; }
            bar.setProgress(Math.max(0f, Math.min(1f, e.getHealth() / e.getMaxHealth())));
            if (e.tickCount % 10 != 0 && !charging(e)) continue;
            for (ServerPlayer p : new ArrayList<>(bar.getPlayers()))
                if (p.isRemoved() || p.level() != level || p.distanceToSqr(e) > BAR_RANGE * BAR_RANGE) bar.removePlayer(p);
            for (ServerPlayer p : level.players())
                if (p.distanceToSqr(e) <= BAR_RANGE * BAR_RANGE && DragonParties.allowed(e, p.getUUID()) && !bar.getPlayers().contains(p)) bar.addPlayer(p);
        }
    }

    /** Every second, per player: give nearby wild Hydras their bar, and free one saved mid-charge (after a restart). */
    public static void check(ServerPlayer p) {
        if (!(p.level() instanceof ServerLevel level) || p.tickCount % 100 >= 20) return;
        for (CreatureEntity c : level.getEntitiesOfClass(CreatureEntity.class, p.getBoundingBox().inflate(BAR_RANGE), FalseHydra::is)) {
            if (c.isTame()) continue;
            if (!BARS.containsKey(c)) bar(c);
            if (c.isNoAi() && !charging(c) && !dev.ahrivaea.battle.Battles.inBattle(c)) {
                c.setInvulnerable(false);
                c.setGlowingTag(false);
                c.setNoAi(false);
            }
        }
    }
}
