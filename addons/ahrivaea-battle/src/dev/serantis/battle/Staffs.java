package dev.serantis.battle;

import dev.ahrivaea.battle.BattleMove;
import dev.ahrivaea.battle.DamageCalc;
import dev.ahrivaea.creature.Influence;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Magic Staffs, one per influence (a Wizard starts with the one they choose). Held in battle, the player
 * casts a bolt of the staff's influence instead of swinging: magic damage (using the player's Magic) at
 * 1-3 tiles. Hitting a creature with one starts a battle, like any weapon.
 */
public final class Staffs {
    public static final Map<String, Item> STAFFS = new LinkedHashMap<>();
    public static final int POWER = 40;
    public static final double ACCURACY = 0.9;

    private Staffs() {}

    public static void register() {
        for (String inf : Masters.INFLUENCES) {
            ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.parse("serantis_battle:" + inf + "_staff"));
            STAFFS.put(inf, Registry.register(BuiltInRegistries.ITEM, key,
                    new Item(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON).setId(key))));
        }
    }

    /** The influence of the staff in this stack, or null. */
    public static String influenceOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        for (var e : STAFFS.entrySet()) if (stack.is(e.getValue())) return e.getKey();
        return null;
    }

    /** The player's attack with a staff in hand. */
    public static BattleMove move(String inf, String name) {
        Influence influence = Influence.NONE;
        for (Influence i : Influence.values()) if (i.getSerializedName().equals(inf)) influence = i;
        return new BattleMove(name, new DamageCalc.Move(POWER, DamageCalc.Category.MAGIC, influence, ACCURACY), 1, 3);
    }
}
