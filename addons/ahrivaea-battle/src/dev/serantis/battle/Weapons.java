package dev.serantis.battle;

import dev.ahrivaea.battle.Battles;
import dev.ahrivaea.creature.CreatureEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Starting a battle: hit a wild creature with any weapon (sword, axe, spear, mace, trident, bow,
 * crossbow), or shoot it with an arrow or a thrown trident.
 */
public final class Weapons {
    private Weapons() {}

    public static boolean isWeapon(ItemStack s) {
        if (s == null || s.isEmpty()) return false;
        return s.has(DataComponents.WEAPON) || s.is(ItemTags.SWORDS) || s.is(ItemTags.AXES) || s.is(ItemTags.SPEARS)
                || s.is(Items.MACE) || s.is(Items.TRIDENT) || s.getItem() instanceof BowItem || s.getItem() instanceof CrossbowItem;
    }

    /** A player's arrow or trident hit this creature: start a battle (same rules as hitting it). */
    public static boolean shot(ServerPlayer player, CreatureEntity target) {
        if (player == null || target == null || target.isTame() || player.isSpectator()) return false;
        if (Battles.of(player) != null || Battles.inBattle(target)) return false;
        Battles.start(player, target);              // start() explains itself if it refuses
        return true;
    }
}
