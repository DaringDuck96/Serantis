package dev.serantis.battle.fx;

import dev.ahrivaea.battle.Battle;
import dev.ahrivaea.battle.Unit;
import net.minecraft.world.entity.Entity;

/**
 * Battle feedback on the creatures themselves:
 *   attacks make the attacker wiggle;
 *   damage flashes orange, healing green, boosts blue, a miss flashes the target white, a crit red.
 * Several in the same moment on one creature: the most important wins (crit, miss, damage, heal, boost).
 */
public final class Fx {
    public static final int BUFF = 1, HEAL = 2, DAMAGE = 3, MISS = 4, CRIT = 5;
    public static final float FLASH_TICKS = 12f, WIGGLE_TICKS = 10f;

    private Fx() {}

    public static int priority(int kind) { return kind; }   // kinds are numbered by importance

    /** ARGB the model is tinted toward (white is done with the overlay instead). */
    public static int color(int kind) {
        return switch (kind) {
            case DAMAGE -> 0xFFFF9030;   // orange
            case HEAL -> 0xFF50FF50;     // green
            case BUFF -> 0xFF5080FF;     // blue
            case CRIT -> 0xFFFF2020;     // red
            default -> 0xFFFFFFFF;
        };
    }

    private static FxHolder holder(Battle b, Unit u) {
        if (b == null || u == null || u.entity == null || b.level == null) return null;
        Entity e = b.level.getEntity(u.entity);
        return e instanceof FxHolder h ? h : null;
    }

    public static void flash(Battle b, Unit u, int kind) {
        FxHolder h = holder(b, u);
        if (h != null) h.serantis$flash(kind);
    }

    public static void wiggle(Battle b, Unit u) {
        FxHolder h = holder(b, u);
        if (h != null) h.serantis$wiggle();
    }
}
