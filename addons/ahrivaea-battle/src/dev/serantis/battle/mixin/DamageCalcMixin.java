package dev.serantis.battle.mixin;

import dev.ahrivaea.battle.DamageCalc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Crits: a flat 10% chance (Luck no longer affects it) and double damage. */
@Mixin(DamageCalc.class)
public abstract class DamageCalcMixin {
    private static final String CALC = "calculate(Ldev/ahrivaea/battle/DamageCalc$Fighter;Ldev/ahrivaea/battle/DamageCalc$Fighter;Ldev/ahrivaea/battle/DamageCalc$Move;ZDDLdev/ahrivaea/battle/DamageCalc$Extras;)Ldev/ahrivaea/battle/DamageCalc$Result;";

    @ModifyConstant(method = CALC, constant = @Constant(doubleValue = 1.5))
    private static double serantis$critDamage(double original) { return 2.0; }

    @ModifyConstant(method = CALC, constant = @Constant(doubleValue = 0.05))
    private static double serantis$critBase(double original) { return 0.10; }

    @ModifyConstant(method = CALC, constant = @Constant(doubleValue = 0.0015))
    private static double serantis$critFromLuck(double original) { return 0.0; }

    @ModifyConstant(method = CALC, constant = @Constant(doubleValue = 5.0E-4))
    private static double serantis$critFromTargetLuck(double original) { return 0.0; }

    // Expected damage counts a crit as +100% (was +50%). The first 0.5 in calculate() is the in-water penalty.
    @ModifyConstant(method = CALC, constant = @Constant(doubleValue = 0.5, ordinal = 1))
    private static double serantis$critExpected(double original) { return 1.0; }
}
