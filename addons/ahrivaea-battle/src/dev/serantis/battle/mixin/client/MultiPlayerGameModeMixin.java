package dev.serantis.battle.mixin.client;

import dev.serantis.battle.client.TradeClient;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Right-clicking another player offers to trade creatures with them. */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void serantis$trade(Player player, Entity target, EntityHitResult hit, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (hand == InteractionHand.MAIN_HAND && !player.isShiftKeyDown() && dev.serantis.battle.Tutor.isTutor(target)) {
            dev.serantis.battle.client.TutorScreen.open(target.getId());
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }
        if (hand == InteractionHand.MAIN_HAND && dev.serantis.battle.Wanderer.isWanderer(target)) {
            dev.serantis.battle.client.WandererScreen.open(target.getId());
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }
        if (hand == InteractionHand.MAIN_HAND && dev.serantis.battle.Market.isBlackMarket(target)) {
            dev.serantis.battle.client.BlackMarketScreen.open(target.getId());
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }
        if (hand != InteractionHand.MAIN_HAND || !(target instanceof Player other) || other == player || player.isSpectator()) return;
        if (TradeClient.askToTrade(other)) cir.setReturnValue(InteractionResult.SUCCESS);
    }

    // Right-clicking an enchanting table with a Magic Staff that has no spell yet: choose its spell (see Staffs).
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void serantis$linkStaff(net.minecraft.client.player.LocalPlayer player, InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit,
                                    CallbackInfoReturnable<InteractionResult> cir) {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null || player.isShiftKeyDown()) return;
        if (!level.getBlockState(hit.getBlockPos()).is(net.minecraft.world.level.block.Blocks.ENCHANTING_TABLE)) return;
        boolean blank = false;
        for (var stack : java.util.List.of(player.getMainHandItem(), player.getOffhandItem()))
            if (dev.serantis.battle.Staffs.isStaff(stack) && dev.serantis.battle.Staffs.spellOf(stack) == null) blank = true;
        if (!blank) return;
        if (hand == InteractionHand.MAIN_HAND) dev.serantis.battle.client.StaffScreen.open(hit.getBlockPos());
        cir.setReturnValue(InteractionResult.SUCCESS);
    }
}
