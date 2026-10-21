package dev.serantis.battle;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Weapon enchantments count in creature battles (your weapon attack, not staff spells):
 *  - Sharpness: +0.5 per level + 0.5 attack damage (as in Minecraft), added to the weapon's power.
 *  - Banes (Quenching, Thawing, Grounding, ... one per influence, I-III, only one per weapon): +10% damage per level
 *    against creatures of that influence. (Smite, Bane of Arthropods and Impaling only matter outside battles.)
 *  - Infusions (Emberbrand, Rimebite, Thunderstrike, ... one per influence but Normal, only one per weapon): the weapon
 *    attack takes on that influence, with its type matchups (super-effective, resisted...).
 *  - Fire Aspect (+8% per level) and Flame (+15%): against anything but Fire creatures.
 *  - Power: +12% per level (bows and crossbows).
 *  - Breach: +8% per level (it cuts through armour; here, defence). Density: +5% per level.
 *  - Looting: +5% per level that each creature you beat drops its loot twice.
 */
public final class WeaponEnchants {
    private WeaponEnchants() {}

    public static int level(Player p, ItemStack stack, ResourceKey<Enchantment> key) {
        if (p == null || stack == null || stack.isEmpty()) return 0;
        return p.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key)
                .map(h -> EnchantmentHelper.getItemEnchantmentLevel(h, stack)).orElse(0);
    }

    /** Extra attack damage from Sharpness. */
    public static double sharpness(Player p, ItemStack weapon) {
        int l = level(p, weapon, Enchantments.SHARPNESS);
        return l > 0 ? 0.5 * l + 0.5 : 0;
    }

    private static ResourceKey<Enchantment> key(String id) {
        return ResourceKey.create(Registries.ENCHANTMENT, net.minecraft.resources.Identifier.parse("serantis_battle:" + id));
    }

    /** The influence this weapon is infused with (null: none). */
    public static dev.ahrivaea.creature.Influence infusion(Player p, ItemStack weapon) {
        if (p == null || weapon == null || weapon.isEmpty()) return null;
        for (var inf : dev.ahrivaea.creature.Influence.values())
            if (inf != dev.ahrivaea.creature.Influence.NONE && level(p, weapon, key("infusion_" + inf.getSerializedName())) > 0) return inf;
        return null;
    }

    /** Damage multiplier for the player's weapon attack on a creature of this influence. */
    public static double multiplier(Player p, ItemStack weapon, dev.ahrivaea.creature.Influence target) {
        if (weapon == null || weapon.isEmpty()) return 1;
        String t = target == null ? "none" : target.getSerializedName();
        double m = 1;
        m += 0.10 * level(p, weapon, key("bane_" + t));                  // its Bane
        if (!"fire".equals(t)) {
            m += 0.08 * level(p, weapon, Enchantments.FIRE_ASPECT);
            m += 0.15 * Math.min(1, level(p, weapon, Enchantments.FLAME));
        }
        m += 0.12 * level(p, weapon, Enchantments.POWER);
        m += 0.08 * level(p, weapon, Enchantments.BREACH);
        m += 0.05 * level(p, weapon, Enchantments.DENSITY);
        var infused = infusion(p, weapon);                               // an infused weapon hits as its influence
        if (infused != null && target != null) {
            double plain = dev.ahrivaea.creature.Influence.NONE.against(target);
            m *= infused.against(target) / (plain <= 0 ? 1 : plain);
        }
        return m;
    }

    /** Extra chance (0-1) that a beaten creature drops its loot twice, from Looting on the weapon in hand. */
    public static float looting(Player p) {
        return p == null ? 0 : 0.05f * level(p, p.getMainHandItem(), Enchantments.LOOTING);
    }
}
