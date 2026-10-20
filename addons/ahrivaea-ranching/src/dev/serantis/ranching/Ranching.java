package dev.serantis.ranching;

import dev.ahrivaea.base.Bases;
import dev.ahrivaea.battle.Battles;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Drops;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/**
 * Ranching: a creature leashed to a fence post that stands inside a base drops its drop rewards
 * every so often. It isn't hurt; the drops just appear at its feet.
 *
 * Each creature checks once a minute, with a 1 in 10 chance, so it drops about every 10 minutes on
 * average. Each roll uses the creature's normal drop table (each drop's own chance and amounts).
 */
public final class Ranching {
    public static final int CHECK_TICKS = 1200;        // once a minute
    public static final float CHANCE = 0.10f;          // ~ every 10 minutes

    private Ranching() {}

    public static void tick(CreatureEntity creature) {
        if (!(creature.level() instanceof ServerLevel level)) return;
        if ((creature.tickCount + creature.getId()) % CHECK_TICKS != 0) return;     // spread creatures over the minute
        if (!creature.isAlive() || !creature.isLeashed() || Battles.inBattle(creature)) return;
        Entity holder = creature.getLeashHolder();
        if (holder == null || holder instanceof net.minecraft.world.entity.player.Player) return;
        BlockPos post = holder.blockPosition();
        if (!level.getBlockState(post).is(BlockTags.FENCES)) return;                  // tied to a fence post
        if (!Bases.inAnyBase(level, post)) return;                                      // ...inside a base
        if (creature.getRandom().nextFloat() >= CHANCE) return;
        creature.getSpecies().ifPresent(species -> {
            for (ItemStack stack : Drops.roll(species, creature.getInfluence(), creature.getCreatureLevel(),
                    creature.getRandom(), level.registryAccess()))
                if (!stack.isEmpty()) creature.spawnAtLocation(level, stack);
        });
    }
}
