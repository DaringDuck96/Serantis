package dev.serantis.battle.mixin;

import dev.ahrivaea.battle.Battle;
import dev.ahrivaea.battle.Unit;
import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Species;
import dev.ahrivaea.creature.Variant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Package-private Battle methods, for adding a raid's base defenders and co-op players. */
@Mixin(Battle.class)
public interface BattleRaidAccess {
    @Invoker("addCreature")
    Unit serantis$addCreature(Unit.Side side, Species species, Influence influence, Variant variant, int level, float health, int partySlot, int x, int z);

    @org.spongepowered.asm.mixin.gen.Accessor("arenaSlot")
    int serantis$arenaSlot();

    @Invoker("freeCellNear")
    int[] serantis$freeCellNear(int x, int z, Unit.Side side);

    @Invoker("addPlayer")
    Unit serantis$addPlayer(net.minecraft.server.level.ServerPlayer player, int x, int z);

    @Invoker("newId")
    int serantis$newId();

    @Invoker("finish")
    void serantis$finish(Battle.Outcome outcome);

    @Invoker("startEnemyPhase")
    void serantis$startEnemyPhase();

    @org.spongepowered.asm.mixin.gen.Accessor("lead")
    Unit serantis$lead();

    @org.spongepowered.asm.mixin.gen.Accessor("outCreatures")
    java.util.List<dev.ahrivaea.party.CaughtCreature> serantis$outCreatures();
}
