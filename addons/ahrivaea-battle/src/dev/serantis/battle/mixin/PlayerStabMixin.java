package dev.serantis.battle.mixin;

import dev.ahrivaea.battle.Battles;
import dev.ahrivaea.creature.CreatureEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Spears don't swing like other weapons: their jab and their running charge are "stab" attacks, which skip the
 * ordinary hit that starts a battle. A stab on a wild creature starts one too, under the same rules as any other hit.
 */
@Mixin(Player.class)
public abstract class PlayerStabMixin {
    @Inject(method = "stabAttack", at = @At("HEAD"), cancellable = true)
    private void serantis$stabStartsBattle(EquipmentSlot slot, Entity target, float damage, boolean a, boolean b, boolean c,
                                           CallbackInfoReturnable<Boolean> cir) {
        try {
            if (!((Object) this instanceof ServerPlayer sp) || !(target instanceof CreatureEntity creature) || creature.isTame() || sp.isSpectator()) return;
            cir.setReturnValue(false);                          // a wild creature is never just hurt: it's a battle or nothing
            if (Battles.of(sp) != null || Battles.inBattle(creature)) return;
            if (!dev.serantis.battle.Weapons.isWeapon(sp.getItemBySlot(slot))) return;
            Battles.start(sp, creature);                        // start() explains itself if it refuses
        } catch (Throwable t) {
            dev.serantis.battle.Safe.caught("a spear starting a battle", t);
        }
    }
}
