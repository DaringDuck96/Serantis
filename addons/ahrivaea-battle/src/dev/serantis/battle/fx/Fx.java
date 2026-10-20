package dev.serantis.battle.fx;

import dev.ahrivaea.battle.Battle;
import dev.ahrivaea.battle.Unit;
import dev.serantis.battle.moves.Field;

/**
 * Battle feedback, shown on the battle grid tile under the creature (or player) it happened to:
 *   attacking shakes the attacker's tile;
 *   damage lights the tile orange, healing green, boosts blue, a miss white, a crit red.
 * Several flashes on one tile at the same moment: the most important wins (crit, miss, damage, heal, boost).
 */
public final class Fx {
    public static final int BUFF = 1, HEAL = 2, DAMAGE = 3, MISS = 4, CRIT = 5, ATTACK = 6;

    private Fx() {}

    /** Colour of a tile flash (alpha is added when drawing). */
    public static int color(int kind) {
        return switch (kind) {
            case DAMAGE -> 0xFF8A1E;     // orange
            case HEAL -> 0x3CE05A;       // green
            case BUFF -> 0x3C78FF;       // blue
            case MISS -> 0xFFFFFF;       // white
            case CRIT -> 0xFF1E1E;       // red
            default -> 0xFFFFFF;         // the attack shake
        };
    }

    public static void flash(Battle b, Unit u, int kind) {
        if (b != null && u != null) Field.of(b).fx(b, u, kind);
    }

    public static void wiggle(Battle b, Unit u) { flash(b, u, ATTACK); }
}
