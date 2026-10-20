package dev.serantis.battle.mixin;

import com.google.gson.JsonObject;
import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Species;
import dev.ahrivaea.creature.Stats;
import dev.ahrivaea.party.CaughtCreature;
import dev.ahrivaea.party.PartySync;
import dev.serantis.battle.Pace;
import dev.serantis.battle.Temperament;
import dev.serantis.battle.moves.MovesHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The party screen shows each creature's temperament, its stats with it, and its battle movement. */
@Mixin(PartySync.class)
public abstract class PartySyncMixin {
    @Redirect(method = "describe", at = @At(value = "INVOKE",
            target = "Ldev/ahrivaea/creature/Species;statsFor(Ldev/ahrivaea/creature/Influence;)Ldev/ahrivaea/creature/Stats;"))
    private static Stats serantis$stats(Species species, Influence influence, CaughtCreature caught) {
        return Temperament.apply(species.statsFor(influence), ((MovesHolder) (Object) caught).serantis$temperament(), caught.level());
    }

    @Redirect(method = "describe", at = @At(value = "INVOKE", target = "Ldev/ahrivaea/creature/Stats;squaresPerTurn()I"))
    private static int serantis$squares(Stats stats, CaughtCreature caught) {
        return Pace.squares(caught.level(), stats.speed());
    }

    @Inject(method = "describe", at = @At("RETURN"))
    private static void serantis$temperament(CaughtCreature caught, CallbackInfoReturnable<JsonObject> cir) {
        JsonObject o = cir.getReturnValue();
        if (o == null || !o.has("name")) return;
        MovesHolder h = (MovesHolder) (Object) caught;
        Temperament.Kind k = Temperament.of(h.serantis$temperament());
        String species = o.get("name").getAsString(), nick = h.serantis$nickname();
        if (k != null) o.addProperty("temperament", k.name());
        String extra = (k != null ? k.name() : "") + (nick != null && !nick.isEmpty() ? (k != null ? " " : "") + species : "");
        String shown = nick != null && !nick.isEmpty() ? nick : species;
        o.addProperty("name", extra.isEmpty() ? shown : shown + " (" + extra + ")");
    }
}
