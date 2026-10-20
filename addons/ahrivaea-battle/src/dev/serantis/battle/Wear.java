package dev.serantis.battle;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The price of being beaten: XP is the main currency, so it's never lost. Instead, everything the
 * player is wearing or holding loses half of its full durability (it never breaks outright; it's
 * left with at least 1 use).
 */
public final class Wear {
    public static final double SHARE = 0.5;
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};

    private Wear() {}

    /** Returns how many items were damaged. */
    public static int defeat(Player p, String why) {
        int hit = 0;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack s = p.getItemBySlot(slot);
            if (s.isEmpty() || !s.isDamageableItem()) continue;
            int max = s.getMaxDamage();
            int next = Math.min(max - 1, s.getDamageValue() + (int) Math.ceil(max * SHARE));
            if (next > s.getDamageValue()) { s.setDamageValue(next); hit++; }
        }
        if (hit > 0) p.sendSystemMessage(Component.literal(why + " Your gear took heavy damage, but you kept your XP."));
        return hit;
    }
}
