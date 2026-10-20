package dev.serantis.starters.mixin;

import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Job;
import dev.ahrivaea.creature.Species;
import dev.ahrivaea.party.CaughtCreature;
import dev.ahrivaea.party.Starters;
import dev.serantis.starters.StarterList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * Makes the starter screen offer the Serantis starters, each in its own influence.
 *
 * The creatures mod offers every creature with a Common no-influence form and always hands the
 * starter over in its no-influence form. The Serantis starters each have a single influenced
 * form, so the list is replaced and the places that ask for the no-influence form are pointed
 * at the creature's actual form instead.
 */
@Mixin(Starters.class)
public abstract class StartersMixin {

    /**
     * @author Serantis
     * @reason Offer the creatures listed in serantis_starters/starters.txt.
     */
    @Overwrite
    public static List<Species> options() {
        return StarterList.options();
    }

    // Starter screen: show the creature's real jobs and ride size.
    @Redirect(method = "offer", at = @At(value = "INVOKE",
            target = "Ldev/ahrivaea/creature/Species;jobsFor(Ldev/ahrivaea/creature/Influence;)Ljava/util/List;"))
    private static List<Job> serantis$jobsFor(Species species, Influence influence) {
        return species.jobsFor(species.formOrDefault(influence));
    }

    @Redirect(method = "offer", at = @At(value = "INVOKE",
            target = "Ldev/ahrivaea/creature/Species;sizeFor(Ldev/ahrivaea/creature/Influence;)F"))
    private static float serantis$sizeFor(Species species, Influence influence) {
        return species.sizeFor(species.formOrDefault(influence));
    }

    // Picking: hand the creature over in its own influence.
    @Redirect(method = "pick", at = @At(value = "NEW",
            target = "(Ljava/lang/String;Ldev/ahrivaea/creature/Influence;IIF)Ldev/ahrivaea/party/CaughtCreature;"))
    private static CaughtCreature serantis$caught(String species, Influence influence, int level, int xp, float health) {
        Influence real = Species.get(species).map(s -> s.formOrDefault(influence)).orElse(influence);
        return new CaughtCreature(species, real, level, xp, health);
    }
}
