package dev.serantis.battle.mixin;

import dev.ahrivaea.battle.Battle;
import dev.ahrivaea.battle.Battles;
import dev.ahrivaea.battle.Unit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Access to the creatures mod's package-private Battles.placeEntity (moves a unit's creature in the arena). */
@Mixin(Battles.class)
public interface BattlesInvoker {
    @Invoker("placeEntity")
    static void serantis$placeEntity(Battle battle, Unit unit) {
        throw new AssertionError();
    }
}
