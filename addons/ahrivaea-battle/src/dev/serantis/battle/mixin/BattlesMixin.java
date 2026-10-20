package dev.serantis.battle.mixin;

import dev.ahrivaea.battle.Battle;
import dev.ahrivaea.battle.Battles;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.party.CaughtCreature;
import dev.ahrivaea.party.Party;
import dev.serantis.battle.moves.BattleAccess;
import dev.serantis.battle.moves.MovesHolder;
import dev.serantis.battle.moves.MoveEngine;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Battles.class)
public abstract class BattlesMixin {
    // Wild creatures fight with the moves saved on them.
    @Inject(method = "start", at = @At("RETURN"))
    private static void serantis$start(ServerPlayer player, CreatureEntity wild, CallbackInfoReturnable<Battle> cir) {
        MoveEngine.assignFromWorld(cir.getReturnValue());
    }

    // A recruited creature keeps the moves it fought with.
    @Redirect(method = "recruit", at = @At(value = "INVOKE",
            target = "Ldev/ahrivaea/party/Party;addAnywhere(Lnet/minecraft/world/entity/player/Player;Ldev/ahrivaea/party/CaughtCreature;)Z"))
    private static boolean serantis$recruit(Player player, CaughtCreature caught, ServerPlayer sp, Battle battle, int index) {
        if (index >= 0 && index < battle.defeated.size()) {
            String moves = ((BattleAccess) (Object) battle).serantis$defeatedMoves().get(battle.defeated.get(index));
            if (moves != null) ((MovesHolder) (Object) caught).serantis$setMoves(moves);
        }
        return Party.addAnywhere(player, caught);
    }
}
