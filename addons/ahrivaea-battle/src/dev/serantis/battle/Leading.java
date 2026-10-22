package dev.serantis.battle;

import dev.ahrivaea.creature.CreatureEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Leading animals with food, like vanilla: hold the right food and they follow you (wild or yours, never in battle).
 * Cows, sheep and goats follow wheat; chickens, wheat seeds; pigs, carrots or potatoes; horses, carrots.
 */
public final class Leading {
    private Leading() {}

    public static boolean tempts(CreatureEntity e, ItemStack s) {
        if (s.isEmpty() || dev.ahrivaea.battle.Battles.inBattle(e)) return false;
        return switch (e.getSpeciesId()) {
            case "cow", "sheep", "goat" -> s.is(Items.WHEAT);
            case "chicken" -> s.is(Items.WHEAT_SEEDS);
            case "pig" -> s.is(Items.CARROT) || s.is(Items.POTATO);
            case "horse" -> s.is(Items.CARROT);
            default -> false;
        };
    }
}
