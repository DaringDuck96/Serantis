package dev.serantis.battle.mixin;

import dev.serantis.battle.skills.SkillHolder;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Skills are kept through death and returning from the End. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerSkillsMixin {
    @Inject(method = "restoreFrom", at = @At("RETURN"))
    private void serantis$keepSkills(ServerPlayer old, boolean keepEverything, CallbackInfo ci) {
        var mine = ((SkillHolder) (Object) this).serantis$skills();
        mine.clear();
        mine.addAll(((SkillHolder) (Object) old).serantis$skills());
        var a = (dev.serantis.battle.Achievements.Holder) (Object) this;
        var b = (dev.serantis.battle.Achievements.Holder) (Object) old;
        a.serantis$addSmelted(b.serantis$smelted() - a.serantis$smelted());
        a.serantis$addXpGained(b.serantis$xpGained() - a.serantis$xpGained());
        a.serantis$addPlanted(b.serantis$planted() - a.serantis$planted());
        a.serantis$biomes().addAll(b.serantis$biomes());
        if (b.serantis$recruitsSeeded()) a.serantis$setRecruitsSeeded();
        for (String inf : new String[]{"none", "fire", "frost", "storm", "earth", "water", "nature", "void", "fae", "darkness", "arcane", "radiant"})
            a.serantis$addRecruited(inf, b.serantis$recruitedOf(inf) - a.serantis$recruitedOf(inf));
        var ma = (dev.serantis.battle.Masters.Holder) (Object) this;
        var mb = (dev.serantis.battle.Masters.Holder) (Object) old;
        for (String inf : dev.serantis.battle.Masters.INFLUENCES) ma.serantis$setStones(inf, mb.serantis$stones(inf));
        ma.serantis$setEternita(mb.serantis$eternita());
        ((dev.serantis.battle.Welcome.Holder) (Object) this).serantis$setWelcomed(((dev.serantis.battle.Welcome.Holder) (Object) old).serantis$welcomed());
        ((dev.serantis.battle.Welcome.Holder) (Object) this).serantis$setWelcomeSent(true);
        for (var t : dev.serantis.battle.Achievements.TRACKS) a.serantis$setClaimed(t.id(), b.serantis$claimed(t.id()));
        // XP is the main currency: it's kept through death.
        ServerPlayer self = (ServerPlayer) (Object) this;
        self.experienceLevel = old.experienceLevel;
        self.experienceProgress = old.experienceProgress;
        self.totalExperience = old.totalExperience;
    }

    // Dying wears down armour and held items instead (before anything is dropped).
    @Inject(method = "die", at = @At("HEAD"))
    private void serantis$died(net.minecraft.world.damagesource.DamageSource source, CallbackInfo ci) {
        dev.serantis.battle.Wear.defeat((ServerPlayer) (Object) this, "You died.");
    }

    // Achievements are checked (and paid) every 5 seconds.
    @Inject(method = "tick", at = @At("TAIL"))
    private void serantis$achievements(CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (self.tickCount % 20 == 5)       // note the biome the player is standing in
            ((dev.serantis.battle.Achievements.Holder) self).serantis$biomes().add(self.level().getBiome(self.blockPosition()).getRegisteredName());
        if (self.tickCount % 100 == 37) dev.serantis.battle.Achievements.check(self);
        if (self.tickCount % 20 == 11) dev.serantis.battle.Welcome.tick(self);
    }
}
