package dev.serantis.battle.mixin;

import dev.ahrivaea.party.CaughtCreature;
import dev.ahrivaea.party.Party;
import dev.ahrivaea.party.Starters;
import dev.serantis.battle.Naming;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** A freshly picked starter waits for its name. */
@Mixin(Starters.class)
public abstract class StartersNamingMixin {
    @Redirect(method = "pick", at = @At(value = "INVOKE",
            target = "Ldev/ahrivaea/party/Party;addAnywhere(Lnet/minecraft/world/entity/player/Player;Ldev/ahrivaea/party/CaughtCreature;)Z"))
    private static boolean serantis$offerName(Player player, CaughtCreature caught, ServerPlayer sp, String species) {
        dev.serantis.battle.PlayerClass.starterPicked(sp, caught);        // a Performer's chosen temperament
        boolean added = Party.addAnywhere(player, caught);
        if (added) Naming.offer(sp, caught);
        return added;
    }
}
