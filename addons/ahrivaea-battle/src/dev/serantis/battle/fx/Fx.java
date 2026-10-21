package dev.serantis.battle.fx;

import dev.ahrivaea.battle.Battle;
import dev.ahrivaea.battle.Unit;
import dev.serantis.battle.moves.Field;

/**
 * Battle feedback, shown on the battle grid tile under the creature (or player) it happened to:
 *   attacking shakes the attacker's tile;
 *   damage lights the tile orange, healing green, boosts blue, a miss white, a crit red.
 * Several flashes on one tile at the same moment: the most important wins (crit, miss, damage, heal, boost).
 * Labels (SUPER, WEAK, KO) pop up as words over the unit instead, and every hit plays a sound for its influence.
 */
public final class Fx {
    public static final int BUFF = 1, HEAL = 2, DAMAGE = 3, MISS = 4, CRIT = 5, ATTACK = 6;
    /** Words that pop up over a unit (not tile flashes): super effective, not very effective, knocked out. */
    public static final int SUPER = 7, WEAK = 8, KO = 9;

    public static boolean isLabel(int kind) { return kind >= SUPER; }

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

    /** A hit lands (or misses): a sound for the move's influence at the target, heard by everyone at the arena. */
    public static void hitSound(Battle b, Unit target, dev.ahrivaea.creature.Influence inf, boolean struck, boolean crit) {
        if (b == null || target == null) return;
        net.minecraft.core.BlockPos at = b.cellPos(target);
        net.minecraft.sounds.SoundEvent s;
        float pitch = 0.9f + b.random.nextFloat() * 0.2f;
        if (!struck) s = net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_NODAMAGE;
        else s = switch (inf == null ? "none" : inf.getSerializedName()) {
            case "fire" -> net.minecraft.sounds.SoundEvents.FIRECHARGE_USE;
            case "frost" -> net.minecraft.sounds.SoundEvents.GLASS_BREAK;
            case "storm" -> net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_IMPACT;
            case "earth" -> net.minecraft.sounds.SoundEvents.STONE_BREAK;
            case "water" -> net.minecraft.sounds.SoundEvents.PLAYER_SPLASH;
            case "nature" -> net.minecraft.sounds.SoundEvents.GRASS_BREAK;
            case "void" -> net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT;
            case "fae" -> net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME;
            case "darkness" -> net.minecraft.sounds.SoundEvents.WITHER_SHOOT;
            case "arcane" -> net.minecraft.sounds.SoundEvents.ENCHANTMENT_TABLE_USE;
            case "radiant" -> net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT;
            default -> net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_STRONG;
        };
        float vol = "darkness".equals(inf == null ? "" : inf.getSerializedName()) || "storm".equals(inf == null ? "" : inf.getSerializedName()) ? 0.45f : 0.9f;
        b.level.playSound(null, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, s, net.minecraft.sounds.SoundSource.PLAYERS, vol, pitch);
        if (crit) b.level.playSound(null, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT, net.minecraft.sounds.SoundSource.PLAYERS, 1f, 1f);
    }

    /** A unit is knocked out: a heavy thud. */
    public static void koSound(Battle b, Unit u) {
        if (b == null || u == null) return;
        net.minecraft.core.BlockPos at = b.cellPos(u);
        b.level.playSound(null, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_KNOCKBACK, net.minecraft.sounds.SoundSource.PLAYERS, 1f, 0.6f);
    }
}
