package dev.serantis.battle.mixin;

import dev.ahrivaea.character.PlayerCharacter;
import dev.serantis.battle.skills.Skills;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The creatures mod's player character now comes from the skill tree: 50 in every stat plus what
 * the player's nodes add. Its old levels and stat points are no longer used. In the character it
 * hands out, "level" is the number of nodes owned, "xp" carries the walking-speed bonus (in %) and
 * "points" the extra health (in half-hearts), which the walking speed and health below read back.
 */
@Mixin(PlayerCharacter.class)
public abstract class PlayerCharacterMixin {
    @Shadow public abstract int xp();
    @Shadow public abstract int points();

    @Unique private static final Identifier SERANTIS_MINING = Identifier.parse("serantis:skill_mining");

    @Inject(method = "get", at = @At("RETURN"), cancellable = true)
    private static void serantis$fromSkills(Player player, CallbackInfoReturnable<PlayerCharacter> cir) {
        if (player == null) return;
        cir.setReturnValue(new PlayerCharacter(Skills.owned(player).size(), Skills.walkPercent(player), Skills.health(player), Skills.stats(player)));
    }

    @Inject(method = "speedBonus", at = @At("HEAD"), cancellable = true)
    private void serantis$walk(CallbackInfoReturnable<Double> cir) { cir.setReturnValue(xp() / 100.0); }

    @Inject(method = "extraHealth", at = @At("HEAD"), cancellable = true)
    private void serantis$hearts(CallbackInfoReturnable<Integer> cir) { cir.setReturnValue(points()); }

    // Health and battle HP are one stat: battle HP is health x 5 (10 hearts = 100 HP).
    @Inject(method = "maxHealth", at = @At("HEAD"), cancellable = true)
    private void serantis$battleHp(CallbackInfoReturnable<Integer> cir) { cir.setReturnValue(Skills.battleHp(20 + points())); }

    @Inject(method = "levelUp", at = @At("HEAD"), cancellable = true)
    private static void serantis$noLevelUp(ServerPlayer player, CallbackInfoReturnable<String> cir) {
        cir.setReturnValue("You grow stronger with your skill tree now: open your Base Core and press Skills.");
    }

    @Inject(method = "spend", at = @At("HEAD"), cancellable = true)
    private static void serantis$noSpend(ServerPlayer player, String stat, int amount, CallbackInfoReturnable<String> cir) {
        cir.setReturnValue("You grow stronger with your skill tree now: open your Base Core and press Skills.");
    }

    // Applied with walking speed (every so often, and after buying a node): mining and chopping speed.
    @Inject(method = "applySpeed", at = @At("RETURN"))
    private static void serantis$mining(ServerPlayer player, CallbackInfo ci) {
        AttributeInstance inst = player.getAttribute(Attributes.BLOCK_BREAK_SPEED);
        if (inst == null) return;
        double want = Skills.miningPercent(player) / 100.0;
        AttributeModifier had = inst.getModifier(SERANTIS_MINING);
        if (had != null && had.amount() == want) return;
        if (had != null) inst.removeModifier(SERANTIS_MINING);
        if (want > 0) inst.addTransientModifier(new AttributeModifier(SERANTIS_MINING, want, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }
}
