package dev.serantis.battle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Tunneler (pickaxes, I-III): mining a block also mines the blocks around it in a 3x3 pane facing you,
 * 1 deep at I, 2 deep at II and 3 deep (a 3x3x3 cube) at III, going away from you. Each extra block is
 * mined as if you broke it yourself: it drops normally and costs durability. Blocks the pickaxe can't
 * harvest, unbreakable blocks and blocks much harder than the one you mined (obsidian in a stone wall)
 * are left alone. Sneak to mine a single block.
 */
public final class Tunneler {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse("serantis_battle:tunneler"));
    private static final ThreadLocal<Boolean> BUSY = ThreadLocal.withInitial(() -> false);

    private Tunneler() {}

    public static int level(ServerPlayer p, ItemStack tool) {
        if (tool.isEmpty() || !tool.is(ItemTags.PICKAXES)) return 0;
        var holder = p.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(KEY);
        return holder.map(h -> EnchantmentHelper.getItemEnchantmentLevel(h, tool)).orElse(0);
    }

    /** Before the block is broken: is this a Tunneler swing? Returns {level, hardness} or null. */
    public static float[] before(ServerPlayer p, BlockPos pos) {
        if (BUSY.get() || p.isShiftKeyDown() || p.isSpectator()) return null;
        int lvl = level(p, p.getMainHandItem());
        if (lvl <= 0) return null;
        BlockState st = p.level().getBlockState(pos);
        float hard = st.getDestroySpeed(p.level(), pos);
        if (st.isAir() || hard < 0) return null;
        int face = -1;                                                  // the face you hit decides which way the pane goes
        var hit = p.pick(p.blockInteractionRange() + 1, 1f, false);
        if (hit instanceof net.minecraft.world.phys.BlockHitResult bh && bh.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && bh.getBlockPos().equals(pos))
            face = bh.getDirection().getOpposite().ordinal();
        return new float[]{lvl, hard, face};
    }

    /** After the block broke: mine the rest of the pane. */
    public static void after(ServerPlayer p, BlockPos pos, float[] info) {
        int lvl = (int) info[0];
        float maxHard = Math.max(1.5f, info[1] * 3f);
        Direction facing = info.length > 2 && info[2] >= 0 ? Direction.values()[(int) info[2]]   // into the face you hit
                : Direction.getApproximateNearest(p.getLookAngle());
        Direction.Axis axis = facing.getAxis();
        BUSY.set(true);
        try {
            for (int d = 0; d < lvl; d++) {
                for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) {
                    if (d == 0 && a == 0 && b == 0) continue;
                    BlockPos t = switch (axis) {
                        case X -> pos.offset(facing.getStepX() * d, a, b);
                        case Y -> pos.offset(a, facing.getStepY() * d, b);
                        case Z -> pos.offset(a, b, facing.getStepZ() * d);
                    };
                    ItemStack tool = p.getMainHandItem();
                    if (tool.isEmpty() || level(p, tool) <= 0) return;       // the pickaxe broke
                    BlockState st = p.level().getBlockState(t);
                    if (st.isAir() || st.hasBlockEntity() || !p.level().mayInteract(p, t)) continue;   // never chests, furnaces or Base Cores; never spawn protection
                    float h = st.getDestroySpeed(p.level(), t);
                    if (h < 0 || h > maxHard) continue;
                    if (st.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(st)) continue;
                    p.gameMode.destroyBlock(t);
                }
            }
        } finally {
            BUSY.set(false);
        }
    }
}
