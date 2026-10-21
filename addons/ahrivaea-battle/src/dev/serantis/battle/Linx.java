package dev.serantis.battle;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Helm of Linx (5 Orichalcum Ingots around a Nether Star): diamond-strong, and while it's worn, four abilities
 * on their own keys (Z, X, C, V by default; Controls > Serantis to change them):
 * <ol>
 *   <li><b>Phase Jump</b>: with a ceiling within 10 blocks above your head, rise up through it and come out standing
 *   safely on top. Never through bedrock, never somewhere that would hurt. Sneak + the key within a minute: go back.</li>
 *   <li><b>Shape Lift</b>: pick a shape and size (sphere, cube, diamond, pillar, disc; 3, 5 or 7 across) and lift that
 *   shape of natural blocks (stone, dirt, sand, logs, ores... never water, lava, bedrock or anything with a block
 *   entity). Carry it, and set it down; any carried block that lands where a block already is breaks.</li>
 *   <li><b>Waymark</b>: sneak + the key marks where you stand; the key takes you back there (any dimension, once a minute).</li>
 *   <li><b>High Jump</b>: toggles jumping 2 blocks high (and the longer fall that comes with it doesn't hurt).</li>
 * </ol>
 */
public final class Linx {
    public static final int CEILING = 10, CLIMB = 64, REACH = 16, RECALL_COOLDOWN = 1200, UNDO_TICKS = 1200;
    public static final String[] SHAPES = {"sphere", "cube", "diamond", "pillar", "disc"};
    public static Item HELM;

    private static final TagKey<net.minecraft.world.level.block.Block> MOVABLE =
            TagKey.create(Registries.BLOCK, Identifier.parse("serantis_battle:linx_movable"));
    private static final Identifier JUMP = Identifier.parse("serantis:linx_jump"), FALL = Identifier.parse("serantis:linx_fall");

    /** A shape being aimed or carried. */
    private static final class Lift {
        String shape = "sphere";
        int size = 5;
        ServerLevel level;
        BlockPos origin;                               // where it was lifted from (null: still aiming)
        final List<BlockPos> offsets = new ArrayList<>();
        final List<BlockState> states = new ArrayList<>();
        double hold;                                   // how far ahead it's carried
    }

    private static final Map<UUID, Lift> LIFTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Object[]> UNDO = new ConcurrentHashMap<>();   // {level, Vec3, yaw, pitch, tick}

    private Linx() {}

    public static void register() {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.parse("serantis_battle:helm_of_linx"));
        HELM = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties()
                .humanoidArmor(ArmorMaterials.DIAMOND, ArmorType.HELMET).rarity(Rarity.EPIC).setId(key)
                .component(DataComponents.LORE, new ItemLore(List.of(
                        line("Phase Jump, Shape Lift, Waymark, High Jump"),
                        line("Keys in Controls > Serantis (Z, X, C, V)"))))));
    }

    private static Component line(String s) {
        return Component.literal(s).withStyle(st -> st.withItalic(false).withColor(ChatFormatting.GRAY));
    }

    private static ItemStack helm(ServerPlayer p) {
        ItemStack s = p.getItemBySlot(EquipmentSlot.HEAD);
        return s.is(HELM) ? s : null;
    }

    private static CompoundTag data(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        return d == null ? new CompoundTag() : d.copyTag();
    }

    private static void say(ServerPlayer p, String text, ChatFormatting color) {
        p.sendOverlayMessage(Component.literal(text).withStyle(color));
    }

    /** A key was pressed (from the client). */
    public static void use(ServerPlayer p, int ability, boolean sneak, String shape, int size) {
        ItemStack helm = helm(p);
        if (helm == null) return;
        if (dev.ahrivaea.battle.Battles.of(p) != null) { say(p, "The Helm is quiet during battle.", ChatFormatting.GRAY); return; }
        switch (ability) {
            case 1 -> { if (sneak) undo(p); else phase(p); }
            case 2 -> lift(p, shape, size);
            case 3 -> { if (sneak) mark(p, helm); else recall(p, helm); }
            case 4 -> toggleJump(p, helm);
            default -> {}
        }
    }

    // ---------------------------------------------------------------- 1: Phase Jump

    private static void phase(ServerPlayer p) {
        ServerLevel level = (ServerLevel) p.level();
        BlockPos head = BlockPos.containing(p.getX(), p.getEyeY(), p.getZ());
        BlockPos ceiling = null;
        for (int d = 1; d <= CEILING; d++) {
            BlockPos at = head.above(d);
            if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty()) { ceiling = at; break; }
        }
        if (ceiling == null) { say(p, "No ceiling within " + CEILING + " blocks to rise through.", ChatFormatting.YELLOW); return; }
        BlockPos to = null;
        for (int y = 0; y <= CLIMB && ceiling.getY() + y < level.getMaxY(); y++) {
            BlockPos at = ceiling.above(y);
            if (level.getBlockState(at).is(Blocks.BEDROCK)) { say(p, "Bedrock: the Helm can't pass through it.", ChatFormatting.RED); return; }
            if (y > 0 && TeleportBoots.safe(level, at)) { to = at; break; }
        }
        if (to == null) { say(p, "Nowhere safe to come out above.", ChatFormatting.RED); return; }
        UNDO.put(p.getUUID(), new Object[]{level, p.position(), p.getYRot(), p.getXRot(), level.getGameTime()});
        Vec3 from = p.position();
        for (double y = from.y + 1; y < to.getY(); y += 0.5)
            level.sendParticles(ParticleTypes.END_ROD, from.x, y, from.z, 1, 0.15, 0.1, 0.15, 0.01);
        p.teleportTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
        p.resetFallDistance();
        level.sendParticles(ParticleTypes.GLOW, p.getX(), p.getY() + 0.2, p.getZ(), 25, 0.4, 0.1, 0.4, 0.02);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2f, 0.7f);
        say(p, "Phase Jump. Sneak + the key within a minute to go back.", ChatFormatting.AQUA);
    }

    private static void undo(ServerPlayer p) {
        Object[] u = UNDO.remove(p.getUUID());
        if (u == null || ((ServerLevel) u[0]).getGameTime() - (long) u[4] > UNDO_TICKS || u[0] != p.level()) {
            say(p, "Nothing to undo.", ChatFormatting.GRAY);
            return;
        }
        Vec3 at = (Vec3) u[1];
        p.teleportTo(at.x, at.y, at.z);
        p.resetFallDistance();
        ((ServerLevel) u[0]).playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2f, 0.5f);
        say(p, "Back where you were.", ChatFormatting.AQUA);
    }

    // ---------------------------------------------------------------- 2: Shape Lift

    /** The cells of a shape, around its centre. */
    static List<BlockPos> cells(String shape, int size) {
        int r = Math.max(1, Math.min(3, size / 2));
        List<BlockPos> out = new ArrayList<>();
        for (int dy = -r; dy <= r; dy++)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    boolean in = switch (shape) {
                        case "cube" -> true;
                        case "diamond" -> Math.abs(dx) + Math.abs(dy) + Math.abs(dz) <= r;
                        case "pillar" -> dx * dx + dz * dz <= r * r + r;
                        case "disc" -> dy == 0 && dx * dx + dz * dz <= r * r + r;
                        default -> dx * dx + dy * dy + dz * dz <= r * r + r;          // sphere
                    };
                    if (in) out.add(new BlockPos(dx, dy, dz));
                }
        return out;
    }

    private static boolean movable(ServerLevel level, BlockPos pos, BlockState s) {
        if (s.isAir() || s.is(Blocks.BEDROCK) || s.hasBlockEntity() || !s.is(MOVABLE)) return false;
        if (!s.getFluidState().isEmpty() && !s.hasProperty(BlockStateProperties.WATERLOGGED)) return false;
        return s.getDestroySpeed(level, pos) >= 0;
    }

    /** X: start aiming a shape (from the menu), lift the aimed one, or set the carried one down. shape "stop": put it back. */
    private static void lift(ServerPlayer p, String shape, int size) {
        Lift l = LIFTS.get(p.getUUID());
        if (shape != null && !shape.isEmpty()) {
            if (l != null && l.origin != null) putBack(p, l);
            if ("stop".equals(shape)) { LIFTS.remove(p.getUUID()); say(p, "Shape Lift put away.", ChatFormatting.GRAY); return; }
            Lift n = new Lift();
            n.shape = java.util.Arrays.asList(SHAPES).contains(shape) ? shape : "sphere";
            n.size = size <= 3 ? 3 : size <= 5 ? 5 : 7;
            LIFTS.put(p.getUUID(), n);
            say(p, "Shape Lift: look at the ground and press the key to lift.", ChatFormatting.AQUA);
            return;
        }
        if (l == null) return;
        ServerLevel level = (ServerLevel) p.level();
        if (l.origin == null) {                                     // lift what's aimed at
            BlockPos centre = aimed(p);
            if (centre == null) { say(p, "Look at blocks within " + REACH + ".", ChatFormatting.YELLOW); return; }
            for (BlockPos off : cells(l.shape, l.size)) {
                BlockPos at = centre.offset(off);
                BlockState s = level.getBlockState(at);
                if (!movable(level, at, s) || !level.mayInteract(p, at)) continue;
                if (s.hasProperty(BlockStateProperties.WATERLOGGED)) s = s.setValue(BlockStateProperties.WATERLOGGED, false);
                l.offsets.add(off);
                l.states.add(s);
            }
            if (l.offsets.isEmpty()) { say(p, "Nothing natural there to lift.", ChatFormatting.YELLOW); return; }
            for (int i = l.offsets.size() - 1; i >= 0; i--)       // top down, so nothing drops out of the shape
                level.setBlock(centre.offset(l.offsets.get(i)), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            l.level = level;
            l.origin = centre;
            l.hold = Math.max(l.size / 2 + 2.5, Math.min(REACH, p.getEyePosition().distanceTo(Vec3.atCenterOf(centre))));
            level.playSound(null, centre, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, 1.3f);
            say(p, "Lifted " + l.offsets.size() + " blocks. Press the key again to set them down.", ChatFormatting.AQUA);
            return;
        }
        if (level != l.level) { putBack(p, l); return; }
        BlockPos centre = carried(p, l);
        int broke = place(p, l, centre);
        level.playSound(null, centre, SoundEvents.STONE_PLACE, SoundSource.PLAYERS, 1f, 0.8f);
        say(p, "Set down." + (broke > 0 ? " " + broke + " block" + (broke == 1 ? "" : "s") + " broke where something was already in the way." : ""), ChatFormatting.AQUA);
        l.origin = null;
        l.level = null;
        l.offsets.clear();
        l.states.clear();
    }

    private static BlockPos aimed(ServerPlayer p) {
        HitResult hit = p.pick(REACH, 1f, false);
        return hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK ? b.getBlockPos() : null;
    }

    private static BlockPos carried(ServerPlayer p, Lift l) {
        return BlockPos.containing(p.getEyePosition().add(p.getLookAngle().scale(l.hold)));
    }

    /** Sets the carried blocks down here; ones landing on a block break instead. Returns how many broke. */
    private static int place(ServerPlayer p, Lift l, BlockPos centre) {
        ServerLevel level = l.level;
        int broke = 0;
        for (int i = 0; i < l.offsets.size(); i++) {
            BlockPos at = centre.offset(l.offsets.get(i));
            BlockState s = l.states.get(i), there = level.getBlockState(at);
            boolean free = level.isInWorldBounds(at) && level.getWorldBorder().isWithinBounds(at) && (p == null || level.mayInteract(p, at))
                    && (there.isAir() || there.canBeReplaced()) && !there.is(Blocks.BEDROCK);
            if (free) {
                if (!there.getFluidState().isEmpty() && s.hasProperty(BlockStateProperties.WATERLOGGED) && there.getFluidState().is(net.minecraft.tags.FluidTags.WATER))
                    s = s.setValue(BlockStateProperties.WATERLOGGED, true);
                level.setBlock(at, s, Block.UPDATE_ALL);
            } else {
                Block.dropResources(s, level, level.isInWorldBounds(at) ? at : l.origin.offset(l.offsets.get(i)));
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 8, 0.25, 0.25, 0.25, 0.05);
                broke++;
            }
        }
        return broke;
    }

    /** Puts a carried shape back where it came from (helm off, logged out, changed dimension). */
    private static void putBack(ServerPlayer p, Lift l) {
        if (l.origin == null || l.level == null) return;
        place(null, l, l.origin);
        l.origin = null;
        l.level = null;
        l.offsets.clear();
        l.states.clear();
        if (p != null) say(p, "The lifted blocks fell back into place.", ChatFormatting.GRAY);
    }

    /** The player left the world (logged out, changed dimension, died): put anything carried back. */
    public static void left(ServerPlayer p) {
        Lift l = LIFTS.remove(p.getUUID());
        if (l != null) putBack(null, l);
    }

    // ---------------------------------------------------------------- 3: Waymark

    private static void mark(ServerPlayer p, ItemStack helm) {
        String where = p.level().dimension().identifier() + "|" + p.getX() + "|" + p.getY() + "|" + p.getZ() + "|" + p.getYRot();
        CustomData.update(DataComponents.CUSTOM_DATA, helm, t -> t.putString("waymark", where));
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 1f, 1f);
        ((ServerLevel) p.level()).sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 0.1, p.getZ(), 20, 0.3, 0.05, 0.3, 0.02);
        say(p, "Waymark set at " + p.blockPosition().toShortString() + ".", ChatFormatting.AQUA);
    }

    private static void recall(ServerPlayer p, ItemStack helm) {
        CompoundTag t = data(helm);
        String w = t.getStringOr("waymark", "");
        if (w.isEmpty()) { say(p, "No Waymark yet: sneak + the key to set one.", ChatFormatting.YELLOW); return; }
        long now = p.level().getServer().overworld().getGameTime(), last = t.getLongOr("recalled", -RECALL_COOLDOWN);
        if (now - last < RECALL_COOLDOWN) { say(p, "Waymark recharging: " + ((RECALL_COOLDOWN - (now - last)) / 20 + 1) + "s.", ChatFormatting.YELLOW); return; }
        String[] f = w.split("\\|");
        ServerLevel to = p.level().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(f[0])));
        if (to == null) return;
        double x = Double.parseDouble(f[1]), y = Double.parseDouble(f[2]), z = Double.parseDouble(f[3]);
        BlockPos spot = BlockPos.containing(x, y, z);
        if (!TeleportBoots.safe(to, spot)) {
            spot = TeleportBoots.safeNear(to, x, y, z);
            if (spot == null) { say(p, "Your Waymark isn't safe to land on any more.", ChatFormatting.RED); return; }
            x = spot.getX() + 0.5; y = spot.getY(); z = spot.getZ() + 0.5;
        }
        left(p);                                                    // anything carried drops back into place first
        ServerLevel from = (ServerLevel) p.level();
        from.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 40, 0.3, 0.8, 0.3, 0.05);
        from.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 0.8f);
        p.teleportTo(to, x, y, z, java.util.Set.of(), Float.parseFloat(f[4]), p.getXRot(), true);
        p.resetFallDistance();
        to.sendParticles(ParticleTypes.END_ROD, x, y + 1, z, 30, 0.3, 0.8, 0.3, 0.03);
        to.playSound(null, x, y, z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 1.2f);
        CustomData.update(DataComponents.CUSTOM_DATA, helm, d -> d.putLong("recalled", now));
    }

    // ---------------------------------------------------------------- 4: High Jump

    private static void toggleJump(ServerPlayer p, ItemStack helm) {
        boolean on = !data(helm).getBooleanOr("highJump", false);
        CustomData.update(DataComponents.CUSTOM_DATA, helm, t -> t.putBoolean("highJump", on));
        jump(p, on);
        say(p, "High Jump " + (on ? "on" : "off") + ".", on ? ChatFormatting.AQUA : ChatFormatting.GRAY);
    }

    private static void jump(ServerPlayer p, boolean on) {
        set(p.getAttribute(Attributes.JUMP_STRENGTH), JUMP, on ? 0.18 : 0);       // 0.42 -> 0.60: clears 2 blocks
        set(p.getAttribute(Attributes.SAFE_FALL_DISTANCE), FALL, on ? 1.5 : 0);
    }

    private static void set(AttributeInstance inst, Identifier id, double want) {
        if (inst == null) return;
        AttributeModifier had = inst.getModifier(id);
        if (had != null && had.amount() == want) return;
        if (had != null) inst.removeModifier(id);
        if (want != 0) inst.addTransientModifier(new AttributeModifier(id, want, AttributeModifier.Operation.ADD_VALUE));
    }

    // ---------------------------------------------------------------- every tick

    public static void tick(ServerPlayer p) {
        ItemStack helm = helm(p);
        if (p.tickCount % 10 == 4) jump(p, helm != null && data(helm).getBooleanOr("highJump", false));
        Lift l = LIFTS.get(p.getUUID());
        if (l == null) return;
        if (helm == null || p.isSpectator()) { left(p); return; }
        if (l.level != null && l.level != p.level()) { putBack(p, l); return; }
        if (p.tickCount % 4 != 0) return;
        ServerLevel level = (ServerLevel) p.level();
        if (l.origin == null) {                                     // aiming: the outline, where it would lift
            BlockPos c = aimed(p);
            if (c == null) return;
            DustParticleOptions dust = new DustParticleOptions(0x6FE3FF, 0.9f);
            for (BlockPos off : cells(l.shape, l.size)) {
                BlockPos at = c.offset(off);
                if (movable(level, at, level.getBlockState(at)))
                    level.sendParticles(p, dust, true, false, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 1, 0, 0, 0, 0);
            }
        } else {                                                    // carrying: the blocks, where they'd land
            BlockPos c = carried(p, l);
            DustParticleOptions dust = new DustParticleOptions(0xFFC24A, 1.0f);
            for (int i = 0; i < l.offsets.size(); i++) {
                BlockPos at = c.offset(l.offsets.get(i));
                level.sendParticles(p, new BlockParticleOption(ParticleTypes.BLOCK, l.states.get(i)), true, false, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 1, 0.2, 0.2, 0.2, 0);
                level.sendParticles(p, dust, true, false, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 1, 0, 0, 0, 0);
            }
        }
    }
}
