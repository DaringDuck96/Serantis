package dev.serantis.battle;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Dying sends you back to your bed. The game itself gives up on a bed that's blocked (a block where you'd stand
 * up, a slab over it, a full anchor) and sends you to world spawn instead, forgetting the bed. Here the last bed
 * (or respawn anchor) you set is remembered, and if the game was about to give up on it while it's still there,
 * you wake up on a safe spot right next to it and it stays your respawn point. Only if the bed is really gone do
 * you go to world spawn.
 */
public final class BedSpawn {
    /** Added to players (mixin/BedRememberMixin): the last respawn point the game set, kept through death and saved. */
    public interface Holder {
        GlobalPos serantis$bed();
        void serantis$setBed(GlobalPos bed);
    }

    private BedSpawn() {}

    private static GlobalPos get(ServerPlayer p) { return ((Holder) p).serantis$bed(); }

    /** The game set this player's respawn point (a bed, an anchor, /spawnpoint): remember it. */
    public static void set(ServerPlayer p, ServerPlayer.RespawnConfig cfg) {
        if (cfg == null || cfg.respawnData() == null) return;          // the game forgetting it doesn't make us forget
        ((Holder) p).serantis$setBed(cfg.respawnData().globalPos());
    }

    /** Just respawned. If the game sent them to world spawn although they had a bed, put them by the bed instead. */
    public static void respawned(ServerPlayer old, ServerPlayer p) {
        if (p.getRespawnConfig() != null) return;                       // the game used their bed: nothing to do
        GlobalPos bed = get(p);
        if (bed == null) bed = get(old);
        if (bed == null) return;                                        // never had one: world spawn is right
        ServerLevel level = p.level().getServer().getLevel(bed.dimension());
        if (level == null) return;
        BlockPos pos = bed.pos();
        level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        var state = level.getBlockState(pos);
        if (!state.is(BlockTags.BEDS) && !state.is(Blocks.RESPAWN_ANCHOR)) {
            ((Holder) p).serantis$setBed(null);
            p.sendSystemMessage(Component.literal("Your bed was gone, so you woke up at the world spawn.").withStyle(ChatFormatting.YELLOW));
            return;
        }
        Vec3 at = dev.ahrivaea.battle.ArenaReturn.safeSpot(level, Vec3.atBottomCenterOf(pos.above()));
        if (at == null) at = Vec3.atBottomCenterOf(level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos));
        p.teleportTo(level, at.x, at.y, at.z, java.util.Set.of(), p.getYRot(), p.getXRot(), true);
        p.resetFallDistance();
        p.setRespawnPosition(new ServerPlayer.RespawnConfig(
                net.minecraft.world.level.storage.LevelData.RespawnData.of(bed.dimension(), pos, p.getYRot(), 0f), false), false);
        p.sendSystemMessage(Component.literal("You woke up by your bed. (Something was in the way of it: clear the space around it.)").withStyle(ChatFormatting.YELLOW));
    }
}
