package dev.serantis.battle.mixin;

import dev.ahrivaea.battle.Battle;
import dev.ahrivaea.battle.Battles;
import dev.ahrivaea.battle.Unit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Set;
import java.util.UUID;

/** Private parts of Battles that co-op battles need (see CoopBattles). */
@Mixin(Battles.class)
public interface BattlesAccess {
    @Accessor("PREVIEWING")
    static Set<UUID> serantis$previewing() { throw new AssertionError(); }

    @Accessor("SPAWNING")
    static void serantis$setSpawning(boolean spawning) { throw new AssertionError(); }

    @Invoker("placeEntity")
    static void serantis$placeEntity(Battle b, Unit u) { throw new AssertionError(); }

    @Invoker("removeEntity")
    static void serantis$removeEntity(Battle b, Unit u) { throw new AssertionError(); }
}
