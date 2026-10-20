package dev.serantis.battle.mixin;

import dev.serantis.battle.skills.SkillEffects;
import dev.serantis.battle.skills.Skills;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Industry: Prospector (+1 XP from XP blocks) and Tireless (half the hunger for breaking blocks). */
@Mixin(Block.class)
public abstract class BlockSkillsMixin {
    @Inject(method = "playerDestroy", at = @At("HEAD"))
    private void serantis$breaking(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, BlockEntity be, ItemStack tool, CallbackInfo ci) {
        SkillEffects.BREAKER.set(player);
    }

    @Inject(method = "playerDestroy", at = @At("RETURN"))
    private void serantis$broken(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, BlockEntity be, ItemStack tool, CallbackInfo ci) {
        SkillEffects.BREAKER.remove();
    }

    @Redirect(method = "playerDestroy", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;causeFoodExhaustion(F)V"))
    private void serantis$tireless(ServerPlayer player, float amount) {
        player.causeFoodExhaustion(Skills.has(player, "industry_6") ? amount / 2 : amount);
    }

    @ModifyVariable(method = "popExperience", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int serantis$prospector(int amount) {
        Player p = SkillEffects.BREAKER.get();
        return amount > 0 && p != null && Skills.has(p, "industry_3") ? amount + 1 : amount;
    }
}
