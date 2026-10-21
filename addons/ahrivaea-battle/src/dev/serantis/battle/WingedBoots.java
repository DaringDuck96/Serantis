package dev.serantis.battle;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Winged Boots (leather boots + 2 feathers + 2 glowstone dust). Worn, they let you fly like in creative
 * (double-tap jump) for 10 seconds, then the wings give out and you fall. One flight per landing: touch
 * the ground to recharge. A fall that follows a flight never hurts, until you land.
 * Creative and spectator players are left alone.
 */
public final class WingedBoots {
    public static final int FLIGHT_TICKS = 200;
    public static Item BOOTS;

    private static final class State {
        boolean granted;    // we turned on mayfly
        boolean used;       // flew since last on the ground
        boolean safeFall;   // in the air after flying: no fall damage
        int ticks;          // ticks flown this flight
    }

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    private WingedBoots() {}

    public static void register() {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.parse("serantis_battle:winged_boots"));
        BOOTS = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties()
                .humanoidArmor(ArmorMaterials.LEATHER, ArmorType.BOOTS).rarity(Rarity.UNCOMMON).setId(key)
                .component(net.minecraft.core.component.DataComponents.EQUIPPABLE, InfluenceArmor.look(EquipmentSlot.FEET, "winged", net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_LEATHER))));
    }

    private static void abilities(ServerPlayer p, boolean mayfly) {
        var a = p.getAbilities();
        if (a.mayfly == mayfly && (mayfly || !a.flying)) return;
        a.mayfly = mayfly;
        if (!mayfly) a.flying = false;
        p.onUpdateAbilities();
    }

    /** Every tick, per player. */
    public static void tick(ServerPlayer p) {
        if (p.isCreative() || p.isSpectator()) { STATES.remove(p.getUUID()); return; }
        boolean worn = p.getItemBySlot(EquipmentSlot.FEET).is(BOOTS);
        State s = STATES.get(p.getUUID());
        if (s == null) {
            if (!worn) return;
            s = new State();
            s.granted = p.getAbilities().mayfly;     // e.g. rejoined mid-flight: the wings are ours
            STATES.put(p.getUUID(), s);
        }
        var a = p.getAbilities();
        if (!worn) {
            if (s.granted) { if (a.flying) s.safeFall = true; abilities(p, false); s.granted = false; }
        } else if (a.flying && s.granted) {
            s.used = true;
            s.safeFall = true;
            s.ticks++;
            int left = FLIGHT_TICKS - s.ticks;
            if (left <= 0) {
                abilities(p, false);
                p.sendOverlayMessage(Component.literal("Your wings give out!").withStyle(ChatFormatting.GOLD));
            } else if (left % 20 == 0) {
                p.sendOverlayMessage(Component.literal("Wings: " + (left / 20) + "s").withStyle(left <= 60 ? ChatFormatting.RED : ChatFormatting.AQUA));
            }
        } else if (p.onGround() || p.isInWater()) {
            s.used = false;
            s.ticks = 0;
            if (!s.granted || !a.mayfly) { abilities(p, true); s.granted = true; }
        } else if (s.used && a.mayfly) {
            abilities(p, false);       // stopped flying mid-air: no second flight until you land
        }
        if (s.safeFall) {
            p.resetFallDistance();
            if ((p.onGround() || p.isInWater()) && !a.flying) s.safeFall = false;
        }
        if (!worn && !s.safeFall) STATES.remove(p.getUUID());
    }

    /** True while a player is falling after a flight. */
    public static boolean safeFall(Object entity) {
        if (!(entity instanceof ServerPlayer p)) return false;
        State s = STATES.get(p.getUUID());
        return s != null && s.safeFall;
    }

    public static void forget(ServerPlayer p) { STATES.remove(p.getUUID()); }
}
