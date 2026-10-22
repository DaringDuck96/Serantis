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
}
