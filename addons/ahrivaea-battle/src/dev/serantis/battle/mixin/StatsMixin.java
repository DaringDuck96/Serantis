package dev.serantis.battle.mixin;

import dev.ahrivaea.creature.Stats;
import dev.serantis.battle.BattleStats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(Stats.class)
public abstract class StatsMixin {
    /**
     * @author Serantis
     * @reason Stats are Level 20 values: 50% at Level 1, rising in a straight line to 100% at Level 20.
     */
    @Overwrite
    public static double levelScale(int level) {
        return BattleStats.levelScale(level);
    }
}
