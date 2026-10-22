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
        a.serantis$addRodeCm(b.serantis$rodeCm() - a.serantis$rodeCm());
        a.serantis$addLoreBooks(b.serantis$loreBooks() - a.serantis$loreBooks());
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
        ((dev.serantis.battle.Welcome.Holder) (Object) this).serantis$setLoreBook(((dev.serantis.battle.Welcome.Holder) (Object) old).serantis$loreBook());
        ((dev.serantis.battle.Welcome.Holder) (Object) this).serantis$setBattlesFought(((dev.serantis.battle.Welcome.Holder) (Object) old).serantis$battlesFought());
        ((dev.serantis.battle.Welcome.Holder) (Object) this).serantis$setFigureMet(((dev.serantis.battle.Welcome.Holder) (Object) old).serantis$figureMet());
        ((dev.serantis.battle.Welcome.Holder) (Object) this).serantis$setRaid(((dev.serantis.battle.Welcome.Holder) (Object) old).serantis$raid());
        ((dev.serantis.battle.Welcome.Holder) (Object) this).serantis$setBounty(((dev.serantis.battle.Welcome.Holder) (Object) old).serantis$bounty());
        ((dev.serantis.battle.Welcome.Holder) (Object) this).serantis$loreTitles().addAll(((dev.serantis.battle.Welcome.Holder) (Object) old).serantis$loreTitles());
        var co = (dev.serantis.battle.Companions.Holder) (Object) this;
        var cb = (dev.serantis.battle.Companions.Holder) (Object) old;
        co.serantis$out().putAll(cb.serantis$out());
        var pc = (dev.serantis.battle.PlayerClass.Holder) (Object) this;
        var pb = (dev.serantis.battle.PlayerClass.Holder) (Object) old;
        pc.serantis$setPlayerClass(pb.serantis$playerClass());
        pc.serantis$setPendingTemperament(pb.serantis$pendingTemperament());
        ((dev.serantis.battle.Backpacks.Holder) (Object) this).serantis$setBackpack(((dev.serantis.battle.Backpacks.Holder) (Object) old).serantis$backpack());   // never lost
        ((dev.serantis.battle.TypeMastery.Holder) (Object) this).serantis$typeGained().putAll(((dev.serantis.battle.TypeMastery.Holder) (Object) old).serantis$typeGained());
        ((dev.serantis.battle.TypeMastery.Holder) (Object) this).serantis$typeTrophies().putAll(((dev.serantis.battle.TypeMastery.Holder) (Object) old).serantis$typeTrophies());
        co.serantis$gone().addAll(cb.serantis$gone());
        for (var t : dev.serantis.battle.Achievements.TRACKS) {
            a.serantis$setClaimed(t.id(), b.serantis$claimed(t.id()));
            a.serantis$setClaimed("paid_" + t.id(), b.serantis$claimed("paid_" + t.id()));   // golden apples still to claim
        }
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
        dev.serantis.battle.WorldScope.check(self.level().getServer());   // a different world in the same run: forget the last one's state
        if (self.tickCount % 20 == 5)       // note the biome the player is standing in
            ((dev.serantis.battle.Achievements.Holder) self).serantis$biomes().add(self.level().getBiome(self.blockPosition()).getRegisteredName());
        if (self.tickCount % 100 == 37) dev.serantis.battle.Achievements.check(self);
        if (self.tickCount % 20 == 11) dev.serantis.battle.Welcome.tick(self);
        if (self.tickCount % 20 == 3) dev.serantis.battle.Companions.check(self);
        if (self.tickCount % 100 == 61) dev.serantis.battle.Towns.check(self);
        dev.serantis.battle.WingedBoots.tick(self);
        dev.serantis.battle.TeleportBoots.tick(self);
        dev.serantis.battle.Linx.tick(self);
        dev.serantis.battle.coop.CoopBattles.tick(self);
        if (self.tickCount % 200 == 17) dev.serantis.battle.coop.Groups.seen(self);
        dev.serantis.battle.FigureVisit.tick(self);
        dev.serantis.battle.BaseCredit.ride(self);
        if (self.tickCount % 20 == 7) dev.serantis.battle.GoblinRaids.tick(self);
        if (self.tickCount % 20 == 9) dev.serantis.battle.Harbinger.check(self);
        if (self.tickCount % 100 == 83) dev.serantis.battle.GoblinRaids.sweep(self);
        if (self.tickCount % 100 == 47) dev.serantis.battle.WildChests.tick(self);
    }
}
