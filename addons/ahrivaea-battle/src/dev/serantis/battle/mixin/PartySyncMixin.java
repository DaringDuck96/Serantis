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
    // The "You" page: level = skill nodes owned, XP = your XP bar, next = what the next node costs.
    @Inject(method = "view", at = @At("RETURN"))
    private static void serantis$you(net.minecraft.server.level.ServerPlayer player, CallbackInfoReturnable<JsonObject> cir) {
        JsonObject o = cir.getReturnValue();
        if (o != null) dev.serantis.battle.Safe.run("Trades.describe", () -> dev.serantis.battle.Trades.describe(player, o));
        if (o != null) dev.serantis.battle.Safe.run("Achievements.describe", () -> dev.serantis.battle.Achievements.describe(player, o));
        if (o != null) dev.serantis.battle.Safe.run("Market.describe", () -> dev.serantis.battle.Market.describe(player, o));
        if (o != null) dev.serantis.battle.Safe.run("Wanderer.describe", () -> dev.serantis.battle.Wanderer.describe(player, o));
        if (o != null) dev.serantis.battle.Safe.run("Tutor.describe", () -> dev.serantis.battle.Tutor.describe(player, o));
        if (o != null) dev.serantis.battle.Safe.run("Honing.describe", () -> dev.serantis.battle.Honing.describe(player, o));
        if (o != null) dev.serantis.battle.Safe.run("GoblinRaids.describe", () -> dev.serantis.battle.GoblinRaids.describe(player, o));
        if (o != null) dev.serantis.battle.Safe.run("Intro.describe", () -> dev.serantis.battle.Intro.describe(player, o));
        if (o != null) dev.serantis.battle.Safe.run("FigureVisit.describe", () -> dev.serantis.battle.FigureVisit.describe(player, o));
        if (o != null) dev.serantis.battle.Safe.run("VillagerTalk.describe", () -> dev.serantis.battle.VillagerTalk.describe(player, o));
        if (o != null) dev.serantis.battle.Safe.run("Harbinger.describe", () -> dev.serantis.battle.Harbinger.describe(player, o));
        if (o != null) dev.serantis.battle.Safe.run("Bounties.describe", () -> dev.serantis.battle.Bounties.describe(player, o));
        if (o != null) dev.serantis.battle.Safe.run("Journal.describe", () -> dev.serantis.battle.Journal.describe(player, o));
        if (o != null) dev.serantis.battle.Safe.run("coop.Groups.describe", () -> dev.serantis.battle.coop.Groups.describe(player, o));
        if (o != null) dev.serantis.battle.Safe.run("Welcome.describe", () -> dev.serantis.battle.Welcome.describe(player, o));
        if (o != null) dev.serantis.battle.Safe.run("PlayerClass.describe", () -> dev.serantis.battle.PlayerClass.describe(player, o));
        if (o != null) dev.serantis.battle.Safe.run("Staffs.describe", () -> dev.serantis.battle.Staffs.describe(player, o));
        if (o != null) dev.serantis.battle.Safe.run("Riddles.describe", () -> dev.serantis.battle.Riddles.describe(player, o));
        if (o != null) dev.serantis.battle.Safe.run("Linx.describe", () -> dev.serantis.battle.Linx.describe(player, o));
        if (o != null) dev.serantis.battle.Safe.run("Backpacks.describe", () -> dev.serantis.battle.Backpacks.describe(player, o));
        if (o != null) dev.serantis.battle.Safe.run("Masters.describeGift", () -> dev.serantis.battle.Masters.describeGift(player, o));
        if (o != null) dev.serantis.battle.Safe.run("Ailments.describe", () -> dev.serantis.battle.Ailments.describe(player, o));
        if (o != null) dev.serantis.battle.Safe.run("BaseLooks.describe", () -> dev.serantis.battle.BaseLooks.describe(player, o));
        if (o != null) dev.serantis.battle.Safe.run("PublicMarks.describe", () -> dev.serantis.battle.PublicMarks.describe(player, o));
        if (o != null) dev.serantis.battle.VersionCheck.describe(player, o);
        if (o != null && o.has("out") && o.get("out").isJsonArray())        // creatures out with you: a knocked-out one shows 0 HP too
            for (var el : o.getAsJsonArray("out"))
                if (el.isJsonObject() && el.getAsJsonObject().has("serantis_ko")) el.getAsJsonObject().addProperty("hp", 0);
        if (o == null || !o.has("character") || !o.get("character").isJsonObject()) return;
        JsonObject c = o.getAsJsonObject("character");
        int owned = dev.serantis.battle.skills.Skills.owned(player).size();
        c.addProperty("level", owned);
        c.addProperty("xp", dev.serantis.battle.skills.XpBank.points(player));
        c.addProperty("xpNext", dev.serantis.battle.skills.Skills.nextCost(player));
        c.addProperty("points", 0);
    }

    @Redirect(method = "describe", at = @At(value = "INVOKE",
            target = "Ldev/ahrivaea/creature/Species;statsFor(Ldev/ahrivaea/creature/Influence;)Ldev/ahrivaea/creature/Stats;"))
    private static Stats serantis$stats(Species species, Influence influence, CaughtCreature caught) {
        return dev.serantis.battle.Apples.of(caught, species.statsFor(influence));
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
        dev.serantis.battle.Apples.describe(caught, o);
        String extra = (k != null ? k.name() : "") + (nick != null && !nick.isEmpty() ? (k != null ? " " : "") + species : "");
        String shown = nick != null && !nick.isEmpty() ? nick : species;
        o.addProperty("name", extra.isEmpty() ? shown : shown + " (" + extra + ")");
        String ail = dev.serantis.battle.Ailments.suffix(caught);         // a status lingering after a battle
        if (!ail.isEmpty()) o.addProperty("name", o.get("name").getAsString() + ail);
        if (dev.serantis.battle.Knockout.is(caught)) {                   // knocked out: 0 HP until it's back on its feet
            o.addProperty("hp", 0);
            o.addProperty("serantis_ko", dev.serantis.battle.Knockout.secondsLeft(caught));
            o.addProperty("name", o.get("name").getAsString() + " - knocked out");
        }
    }
}
