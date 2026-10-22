package dev.serantis.battle;

import dev.ahrivaea.battle.BattleMove;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.core.component.DataComponents;

import java.util.ArrayList;
import java.util.List;

/**
 * Gear in battle.
 *
 * Shields: a shield in your off hand gives you +20 Defense in battle (players start at 50).
 * Changing weapons: on your turn you can equip a weapon, shield or staff from your inventory into either hand,
 * or empty a hand, instead of attacking (you can still move first). Your attack and spells change at once.
 */
public final class Equipment {
    public static final int SHIELD_DEFENSE = 20;

    private Equipment() {}

    public static boolean isShield(ItemStack s) {
        return s != null && !s.isEmpty() && s.getItem() instanceof ShieldItem;
    }

    /** Can this go in a hand in battle? */
    public static boolean battleGear(ItemStack s) {
        return Weapons.isWeapon(s) || isShield(s) || Staffs.isStaff(s);
    }

    /** The player's moves for what they're holding now (as when the battle began). */
    public static List<BattleMove> playerMoves(Player p) {
        ItemStack hand = p.getMainHandItem();
        boolean ranged = hand.getItem() instanceof BowItem || hand.getItem() instanceof CrossbowItem;
        double damage = hand.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
                .compute(Attributes.ATTACK_DAMAGE, 1.0, EquipmentSlot.MAINHAND) + WeaponEnchants.sharpness(p, hand);   // Sharpness counts
        List<BattleMove> out = new ArrayList<>();
        var infused = WeaponEnchants.infusion(p, hand);                 // an infused weapon says so
        String name = hand.isEmpty() ? "Punch" : hand.getHoverName().getString() + (infused == null ? "" : " (" + infused.displayName() + ")");
        if (dev.serantis.battle.moves.MoveBook.isMoveName(name)) name += " (weapon)";   // a sword renamed "Pummel" is still just a sword
        out.add(WeaponStyles.build(hand, name, damage));                  // each kind of weapon fights its own way (see WeaponStyles)
        out.addAll(Staffs.spellsInHand(p));
        return out;
    }

    /**
     * Puts inventory slot {@code slot} (0-35) into a hand (0 main, 1 off), swapping out what was there, or with
     * slot -1 empties that hand into the inventory. Returns why it couldn't, or null.
     */
    public static String equip(ServerPlayer p, int slot, int hand) {
        InteractionHand h = hand == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        Inventory inv = p.getInventory();
        ItemStack held = p.getItemInHand(h);
        if (slot < 0) {
            if (held.isEmpty()) return "That hand is already empty.";
            ItemStack copy = held.copy();
            p.setItemInHand(h, ItemStack.EMPTY);
            if (!inv.add(copy)) { p.setItemInHand(h, copy); return "Your inventory is full."; }
            return null;
        }
        if (slot >= 36) return "Pick something from your inventory.";
        ItemStack pick = inv.getItem(slot);
        if (pick == held) return "You're already holding that.";
        if (!battleGear(pick)) return "That's not a weapon, shield or staff.";
        if (h == InteractionHand.MAIN_HAND && isShield(pick)) return "Shields go in your off hand.";
        inv.setItem(slot, held);
        p.setItemInHand(h, pick);
        return null;
    }

    public static String handName(ItemStack s) { return s.isEmpty() ? "bare hands" : s.getHoverName().getString(); }
}
