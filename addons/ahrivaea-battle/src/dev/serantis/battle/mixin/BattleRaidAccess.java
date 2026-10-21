package dev.serantis.battle.mixin;

import dev.ahrivaea.battle.Battle;
import dev.ahrivaea.battle.Unit;
import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Species;
import dev.ahrivaea.creature.Variant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Package-private Battle methods, for adding a raid's base defenders. */
@Mixin(Battle.class)
public interface BattleRaidAccess {
    @Invoker("addCreature")
    Unit serantis$addCreature(Unit.Side side, Species species, Influence influence, Variant variant, int level, float health, int partySlot, int x, int z);

    @Invoker("freeCellNear")
    int[] serantis$freeCellNear(int x, int z, Unit.Side side);
}
