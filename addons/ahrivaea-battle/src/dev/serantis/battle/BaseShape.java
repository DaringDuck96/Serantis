package dev.serantis.battle;

import dev.ahrivaea.base.BaseCoreBlockEntity;

/**
 * Bases are round: a base covers every column within its radius of the Base Core (Level 1: 56 blocks, so 112 across;
 * each level adds 16 to the radius). minX/maxX/minZ/maxZ are the square around that circle (see mixin/BaseAreaMixin).
 */
public final class BaseShape {
    private BaseShape() {}

    /** Is this column inside the base's circle? */
    public static boolean inside(BaseCoreBlockEntity be, int x, int z) {
        return inside(be.getBlockPos().getX(), be.getBlockPos().getZ(), be.radius(), x, z);
    }

    public static boolean inside(int cx, int cz, int r, int x, int z) {
        long dx = x - cx, dz = z - cz;
        return dx * dx + dz * dz <= (long) r * r + r;                  // (r + 0.5)^2, rounded: a smooth edge
    }

    /** Radius of a base at this level (see mixin/BaseAreaMixin). */
    public static int radiusAt(int level) {
        return (level + 2) * 16 + 8;
    }

    /**
     * Bases may overlap only their owner's other bases. The owner's name of a different player's base this circle would
     * overlap, or null if none. (Bases whose cores aren't loaded can't be checked.)
     */
    public static String clash(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos centre, int r, java.util.UUID owner,
                               net.minecraft.core.BlockPos self) {
        for (net.minecraft.core.BlockPos c : dev.ahrivaea.base.Bases.cores(level)) {
            if (c.equals(self)) continue;
            BaseCoreBlockEntity be = dev.ahrivaea.base.Bases.coreAt(level, c);
            if (be == null || be.owner() == null || be.owner().equals(owner)) continue;
            double dx = c.getX() - centre.getX(), dz = c.getZ() - centre.getZ();
            if (Math.sqrt(dx * dx + dz * dz) < r + be.radius()) return be.ownerName() == null ? "another player" : be.ownerName();
        }
        return null;
    }
}
