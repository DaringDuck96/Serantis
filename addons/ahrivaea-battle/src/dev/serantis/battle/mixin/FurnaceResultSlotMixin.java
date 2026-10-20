package dev.serantis.battle.mixin;

import dev.serantis.battle.Achievements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts items a player takes out of a furnace, smoker or blast furnace (for the Items Smelted achievement). */
@Mixin(FurnaceResultSlot.class)
public abstract class FurnaceResultSlotMixin {
    @Shadow @Final private Player player;
    @Shadow private int removeCount;

    @Inject(method = "checkTakeAchievements", at = @At("HEAD"))
    private void serantis$smelted(ItemStack stack, CallbackInfo ci) {
        if (player instanceof ServerPlayer && removeCount > 0) ((Achievements.Holder) player).serantis$addSmelted(removeCount);
    }
}
