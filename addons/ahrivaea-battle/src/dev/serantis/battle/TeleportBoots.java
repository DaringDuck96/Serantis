package dev.serantis.battle;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Teleporting Boots (diamond boots + 3 ender pearls): as strong as diamond boots. Double-tap sneak while wearing
 * them to teleport to the safest spot about 10 blocks the way you're facing: solid ground, room to stand, no
 * lava, fire, cactus or magma. Up to 3 blocks up or down to find it, and closer if there's nowhere safe at 10.
 * Each jump costs 1 durability and the boots need 3 seconds to recharge.
 */
public final class TeleportBoots {
    public static final int RANGE = 10, COOLDOWN = 60, DOUBLE_TAP = 8;
    public static Item BOOTS;

    private static final Map<UUID, long[]> TAPS = new ConcurrentHashMap<>();   // {was sneaking (0/1), last tap tick}

    private TeleportBoots() {}

    public static void register() {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.parse("serantis_battle:teleporting_boots"));
        BOOTS = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties()
                .humanoidArmor(ArmorMaterials.DIAMOND, ArmorType.BOOTS).rarity(Rarity.RARE).setId(key)
                .component(DataComponents.LORE, new ItemLore(List.of(Component.literal("Double-tap sneak: teleport up to 10 blocks ahead")
                        .withStyle(st -> st.withItalic(false).withColor(ChatFormatting.GRAY)))))));
    }

    /** Every tick, per player. */
    public static void tick(ServerPlayer p) {
        ItemStack boots = p.getItemBySlot(EquipmentSlot.FEET);
        if (!boots.is(BOOTS) || p.isSpectator()) { TAPS.remove(p.getUUID()); return; }
        long[] t = TAPS.computeIfAbsent(p.getUUID(), k -> new long[]{0, -100});
        boolean sneaking = p.isShiftKeyDown();
        boolean pressed = sneaking && t[0] == 0;
        t[0] = sneaking ? 1 : 0;
        if (!pressed) return;
        long now = p.level().getGameTime();
        if (now - t[1] > DOUBLE_TAP) { t[1] = now; return; }
        t[1] = -100;
        if (p.getCooldowns().isOnCooldown(boots)) return;
        if (dev.ahrivaea.battle.Battles.of(p) != null) return;
        jump(p, boots);
    }

    private static void jump(ServerPlayer p, ItemStack boots) {
        ServerLevel level = (ServerLevel) p.level();
        Vec3 look = p.getLookAngle();
        double len = Math.sqrt(look.x * look.x + look.z * look.z);
        if (len < 1.0E-3) { p.sendOverlayMessage(Component.literal("Look where you want to go.")); return; }
        double dx = look.x / len, dz = look.z / len;
        BlockPos to = null;
        for (int d = RANGE; d >= 2 && to == null; d--) to = safeNear(level, p.getX() + dx * d, p.getY(), p.getZ() + dz * d);
        if (to == null) { p.sendOverlayMessage(Component.literal("Nowhere safe to land.").withStyle(ChatFormatting.RED)); return; }
        Vec3 from = p.position();
        puff(level, from);
        p.teleportTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
        p.resetFallDistance();
        puff(level, p.position());
        level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 1f);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 1.2f);
        p.getCooldowns().addCooldown(boots, COOLDOWN);
        boots.hurtAndBreak(1, p, EquipmentSlot.FEET);
    }

    private static void puff(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.PORTAL, at.x, at.y + 1, at.z, 40, 0.3, 0.8, 0.3, 0.5);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 0.5, at.z, 15, 0.3, 0.5, 0.3, 0.05);
    }

    /** The safest standing spot in this column or right next to it, within 3 blocks up or down (null: none). */
    private static BlockPos safeNear(ServerLevel level, double x, double y, double z) {
        BlockPos base = BlockPos.containing(x, y, z);
        int[] dy = {0, 1, -1, 2, -2, 3, -3};
        for (int[] off : new int[][]{{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}})
            for (int v : dy) {
                BlockPos at = base.offset(off[0], v, off[1]);
                if (safe(level, at)) return at;
            }
        return null;
    }

    private static boolean safe(ServerLevel level, BlockPos feet) {
        if (!level.isLoaded(feet) || !level.getWorldBorder().isWithinBounds(feet)) return false;
        BlockState f = level.getBlockState(feet), h = level.getBlockState(feet.above()), g = level.getBlockState(feet.below());
        if (!f.getCollisionShape(level, feet).isEmpty() || !h.getCollisionShape(level, feet.above()).isEmpty()) return false;
        if (!f.getFluidState().isEmpty() || !h.getFluidState().isEmpty()) return false;
        if (g.getCollisionShape(level, feet.below()).isEmpty()) return false;            // something to stand on
        if (danger(f) || danger(h) || danger(g)) return false;
        return true;
    }

    private static boolean danger(BlockState s) {
        return s.is(Blocks.LAVA) || s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.CACTUS) || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.POWDER_SNOW)
                || s.is(BlockTags.FIRE) || s.is(Blocks.CAMPFIRE) || s.is(Blocks.SOUL_CAMPFIRE) || s.is(Blocks.WITHER_ROSE) || s.is(Blocks.POINTED_DRIPSTONE);
    }
}
