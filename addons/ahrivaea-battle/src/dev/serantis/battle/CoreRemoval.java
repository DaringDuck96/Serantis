package dev.serantis.battle;

import dev.ahrivaea.base.BaseCoreBlockEntity;
import dev.ahrivaea.base.Bases;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Breaking a Base Core: the client asks first (client/CoreBreakScreen). On yes, the owner's workers are all
 * sent back to their party (or box), then the core breaks and drops.
 */
public final class CoreRemoval {
    private CoreRemoval() {}

    public static void remove(ServerPlayer p, BlockPos pos) {
        if (!(p.level() instanceof ServerLevel level) || pos == null || p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) > 8 * 8) return;
        BaseCoreBlockEntity core = Bases.coreAt(level, pos);
        if (core == null) return;
        if (!p.getUUID().equals(core.owner()) && !p.getAbilities().instabuild) {
            p.sendSystemMessage(Component.literal("Only its owner can take down this base.").withStyle(ChatFormatting.RED));
            return;
        }
        int n = core.workers().size();
        for (int i = n - 1; i >= 0; i--) dev.serantis.battle.mixin.BasesInvoker.serantis$unassign(p, core, i);
        if (!core.workers().isEmpty()) {
            p.sendSystemMessage(Component.literal("Some workers couldn't be sent home, so the core was left standing. Send them away from the base screen first.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        level.destroyBlock(pos, !p.getAbilities().instabuild, p);
        p.sendSystemMessage(Component.literal(n == 0 ? "The base is taken down." : "The base is taken down; its " + n
                + (n == 1 ? " worker went" : " workers went") + " back to your party (or box).").withStyle(ChatFormatting.YELLOW));
    }
}
