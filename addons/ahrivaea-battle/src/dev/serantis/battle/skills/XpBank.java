package dev.serantis.battle.skills;

import net.minecraft.world.entity.player.Player;

/**
 * The one experience currency: vanilla XP points. Battles add to it; recruiting, villager trades,
 * creature levels, the skill tree, enchanting and anvils all spend it.
 */
public final class XpBank {
    /** Lets the creatures mod's old party-XP storage be read once, to move it into vanilla XP. */
    public static final ThreadLocal<Boolean> RAW = ThreadLocal.withInitial(() -> false);

    private XpBank() {}

    /** Points needed to reach a level from zero (vanilla's curve). */
    public static long pointsForLevel(int level) {
        if (level <= 16) return (long) level * level + 6L * level;
        if (level <= 31) return Math.round(2.5 * level * level - 40.5 * level + 360);
        return Math.round(4.5 * level * level - 162.5 * level + 2220);
    }

    /** The player's XP right now, in points. */
    public static int points(Player p) {
        long total = pointsForLevel(p.experienceLevel) + Math.round(p.experienceProgress * p.getXpNeededForNextLevel());
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, total));
    }

    public static void give(Player p, int amount) {
        if (amount > 0) p.giveExperiencePoints(amount);
        else if (amount < 0) take(p, -amount);
    }

    /** Spends points (never below zero). */
    public static void take(Player p, int amount) {
        int left = Math.max(0, points(p) - Math.max(0, amount));
        p.experienceLevel = 0;
        p.experienceProgress = 0f;
        p.totalExperience = 0;
        if (left > 0) {
            dev.serantis.battle.Achievements.NOT_GAINED.set(true);   // giving back the rest isn't new XP
            try { p.giveExperiencePoints(left); } finally { dev.serantis.battle.Achievements.NOT_GAINED.set(false); }
        }
    }
}
