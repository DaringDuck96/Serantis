package dev.serantis.battle.mixin;

import dev.serantis.battle.skills.SkillHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashSet;
import java.util.Set;

/** Players remember the skill tree nodes they've bought. */
@Mixin(Player.class)
public abstract class PlayerSkillsMixin implements SkillHolder, dev.serantis.battle.Achievements.Holder, dev.serantis.battle.Masters.Holder, dev.serantis.battle.Welcome.Holder, dev.serantis.battle.Companions.Holder, dev.serantis.battle.TypeMastery.Holder, dev.serantis.battle.PlayerClass.Holder, dev.serantis.battle.Backpacks.Holder {
    @Unique private final Set<String> serantis$skills = new LinkedHashSet<>();

    @Override public Set<String> serantis$skills() { return serantis$skills; }

    // Achievements: items smelted, experience gained, and tiers already paid.
    @Unique private long serantis$smelted, serantis$xpGained, serantis$planted, serantis$loreBooks, serantis$rodeCm;
    @Override public long serantis$rodeCm() { return serantis$rodeCm; }
    @Override public void serantis$addRodeCm(long cm) { serantis$rodeCm += cm; }
    @Unique private final java.util.Map<String, Integer> serantis$claimed = new java.util.HashMap<>();

    @Override public long serantis$smelted() { return serantis$smelted; }
    @Override public void serantis$addSmelted(long n) { serantis$smelted += n; }
    @Unique private final java.util.Set<String> serantis$biomes = new java.util.LinkedHashSet<>();
    @Override public java.util.Set<String> serantis$biomes() { return serantis$biomes; }
    @Override public long serantis$planted() { return serantis$planted; }
    @Unique private final java.util.Map<String, Long> serantis$recruits = new java.util.HashMap<>();
    @Unique private boolean serantis$recruitsSeeded;
    @Override public long serantis$recruitedOf(String influence) { return serantis$recruits.getOrDefault(influence, 0L); }
    @Override public void serantis$addRecruited(String influence, long n) { serantis$recruits.merge(influence, n, Long::sum); }
    @Override public boolean serantis$recruitsSeeded() { return serantis$recruitsSeeded; }
    @Override public void serantis$setRecruitsSeeded() { serantis$recruitsSeeded = true; }
    @Override public void serantis$addPlanted(long n) { serantis$planted += n; }
    @Override public long serantis$loreBooks() { return serantis$loreBooks; }
    @Override public void serantis$addLoreBooks(long n) { serantis$loreBooks += n; }
    @Override public long serantis$xpGained() { return serantis$xpGained; }
    @Override public void serantis$addXpGained(long n) { serantis$xpGained += n; }
    @Unique private final java.util.Map<String, Integer> serantis$stones = new java.util.HashMap<>();
    @Unique private boolean serantis$eternita;
    @Unique private boolean serantis$welcomed, serantis$welcomeSent, serantis$loreBook;
    @Unique private int serantis$battlesFought = -1, serantis$figureMet;
    @Override public int serantis$battlesFought() { return serantis$battlesFought; }
    @Override public void serantis$setBattlesFought(int n) { serantis$battlesFought = n; }
    @Override public int serantis$figureMet() { return serantis$figureMet; }
    @Override public void serantis$setFigureMet(int n) { serantis$figureMet = n; }
    @Override public boolean serantis$loreBook() { return serantis$loreBook; }
    @Override public void serantis$setLoreBook(boolean given) { serantis$loreBook = given; }
    @Unique private String serantis$playerClass, serantis$pendingTemperament;
    @Unique private net.minecraft.world.item.ItemStack serantis$backpack = net.minecraft.world.item.ItemStack.EMPTY;
    @Override public net.minecraft.world.item.ItemStack serantis$backpack() { return serantis$backpack; }
    @Override public void serantis$setBackpack(net.minecraft.world.item.ItemStack stack) { serantis$backpack = stack == null ? net.minecraft.world.item.ItemStack.EMPTY : stack; }
    @Override public String serantis$playerClass() { return serantis$playerClass; }
    @Override public void serantis$setPlayerClass(String id) { serantis$playerClass = id; }
    @Override public String serantis$pendingTemperament() { return serantis$pendingTemperament; }
    @Override public void serantis$setPendingTemperament(String t) { serantis$pendingTemperament = t; }
    @Unique private final java.util.Map<String, Integer> serantis$typeGained = new java.util.HashMap<>(), serantis$typeTrophies = new java.util.HashMap<>();
    @Override public java.util.Map<String, Integer> serantis$typeGained() { return serantis$typeGained; }
    @Override public java.util.Map<String, Integer> serantis$typeTrophies() { return serantis$typeTrophies; }
    @Unique private final java.util.Map<java.util.UUID, dev.ahrivaea.party.CaughtCreature> serantis$out = new java.util.LinkedHashMap<>();
    @Unique private final java.util.Set<java.util.UUID> serantis$gone = new java.util.HashSet<>();
    @Override public java.util.Map<java.util.UUID, dev.ahrivaea.party.CaughtCreature> serantis$out() { return serantis$out; }
    @Override public java.util.Set<java.util.UUID> serantis$gone() { return serantis$gone; }
    @Override public boolean serantis$welcomed() { return serantis$welcomed; }
    @Override public void serantis$setWelcomed(boolean seen) { serantis$welcomed = seen; }
    @Override public boolean serantis$welcomeSent() { return serantis$welcomeSent; }
    @Override public void serantis$setWelcomeSent(boolean sent) { serantis$welcomeSent = sent; }
    @Override public int serantis$stones(String influence) { return serantis$stones.getOrDefault(influence, 0); }
    @Override public void serantis$setStones(String influence, int count) { serantis$stones.put(influence, count); }
    @Override public boolean serantis$eternita() { return serantis$eternita; }
    @Override public void serantis$setEternita(boolean has) { serantis$eternita = has; }
    @Override public int serantis$claimed(String track) { return serantis$claimed.getOrDefault(track, 0); }
    @Override public void serantis$setClaimed(String track, int tiers) { serantis$claimed.put(track, tiers); }

    @Inject(method = "giveExperiencePoints", at = @At("HEAD"))
    private void serantis$countXp(int amount, CallbackInfo ci) {
        if (amount > 0 && !((Player) (Object) this).level().isClientSide() && !dev.serantis.battle.Achievements.NOT_GAINED.get()) serantis$xpGained += amount;
    }

    // XP is kept through death, so dying drops no XP orbs.
    @Inject(method = "getBaseExperienceReward", at = @At("HEAD"), cancellable = true)
    private void serantis$keepXp(net.minecraft.server.level.ServerLevel level, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(0);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
    private void serantis$saveSkills(ValueOutput out, CallbackInfo ci) {
        out.putString("SerantisSkills", String.join(",", serantis$skills));
        out.putLong("SerantisSmelted", serantis$smelted);
        out.putLong("SerantisXpGained", serantis$xpGained);
        out.putLong("SerantisPlanted", serantis$planted);
        out.putLong("SerantisRode", serantis$rodeCm);
        out.putLong("SerantisLoreBooks", serantis$loreBooks);
        out.putString("SerantisBiomes", String.join(",", serantis$biomes));
        StringBuilder rec = new StringBuilder(serantis$recruitsSeeded ? "seeded" : "");
        serantis$recruits.forEach((k, v) -> rec.append(',').append(k).append(':').append(v));
        out.putString("SerantisRecruits", rec.toString());
        out.putString("SerantisAchievements", dev.serantis.battle.Achievements.encode(this));
        StringBuilder st = new StringBuilder(serantis$eternita ? "eternita" : "");
        serantis$stones.forEach((k, v) -> st.append(',').append(k).append(':').append(v));
        out.putString("SerantisMastery", st.toString());
        out.putInt("SerantisWelcomed", serantis$welcomed ? 1 : 0);
        out.putInt("SerantisLoreBook", serantis$loreBook ? 1 : 0);
        out.putInt("SerantisBattlesFought", serantis$battlesFought);
        out.putInt("SerantisFigureMet", serantis$figureMet);
        if (serantis$playerClass != null) out.putString("SerantisClass", serantis$playerClass);
        if (!serantis$backpack.isEmpty()) out.store("SerantisBackpack", net.minecraft.world.item.ItemStack.OPTIONAL_CODEC, serantis$backpack);
        if (serantis$pendingTemperament != null) out.putString("SerantisStarterTemperament", serantis$pendingTemperament);
        out.putString("SerantisTypeGained", dev.serantis.battle.TypeMastery.encode(serantis$typeGained));
        out.putString("SerantisTypeTrophies", dev.serantis.battle.TypeMastery.encode(serantis$typeTrophies));
        java.util.List<String> outIds = new java.util.ArrayList<>(), goneIds = new java.util.ArrayList<>();
        for (var id : serantis$out.keySet()) outIds.add(id.toString());
        for (var id : serantis$gone) goneIds.add(id.toString());
        out.store("SerantisOutIds", com.mojang.serialization.Codec.STRING.listOf(), outIds);
        out.store("SerantisOut", dev.ahrivaea.party.CaughtCreature.CODEC.listOf(), new java.util.ArrayList<>(serantis$out.values()));
        out.store("SerantisGone", com.mojang.serialization.Codec.STRING.listOf(), goneIds);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
    private void serantis$loadSkills(ValueInput in, CallbackInfo ci) {
        serantis$skills.clear();
        for (String s : in.getStringOr("SerantisSkills", "").split(",")) if (!s.isBlank()) serantis$skills.add(s.trim());
        serantis$smelted = in.getLongOr("SerantisSmelted", 0L);
        serantis$xpGained = in.getLongOr("SerantisXpGained", 0L);
        serantis$planted = in.getLongOr("SerantisPlanted", 0L);
        serantis$rodeCm = in.getLongOr("SerantisRode", 0L);
        serantis$loreBooks = in.getLongOr("SerantisLoreBooks", 0L);
        serantis$recruits.clear();
        String rec = in.getStringOr("SerantisRecruits", "");
        serantis$recruitsSeeded = rec.startsWith("seeded");
        for (String part : rec.split(",")) {
            String[] kv = part.split(":");
            if (kv.length == 2) try { serantis$recruits.put(kv[0].trim(), Long.parseLong(kv[1].trim())); } catch (NumberFormatException ignored) { }
        }
        serantis$biomes.clear();
        for (String s : in.getStringOr("SerantisBiomes", "").split(",")) if (!s.isBlank()) serantis$biomes.add(s.trim());
        serantis$claimed.clear();
        dev.serantis.battle.Achievements.decode(this, in.getStringOr("SerantisAchievements", ""));
        serantis$stones.clear();
        serantis$welcomed = in.getIntOr("SerantisWelcomed", 0) == 1;
        serantis$loreBook = in.getIntOr("SerantisLoreBook", 0) == 1;
        serantis$battlesFought = in.getIntOr("SerantisBattlesFought", -1);
        serantis$figureMet = in.getIntOr("SerantisFigureMet", 0);
        serantis$backpack = in.read("SerantisBackpack", net.minecraft.world.item.ItemStack.OPTIONAL_CODEC).orElse(net.minecraft.world.item.ItemStack.EMPTY);
        String cls = in.getStringOr("SerantisClass", ""), pend = in.getStringOr("SerantisStarterTemperament", "");
        serantis$playerClass = cls.isEmpty() ? null : cls;
        serantis$pendingTemperament = pend.isEmpty() ? null : pend;
        dev.serantis.battle.TypeMastery.decode(serantis$typeGained, in.getStringOr("SerantisTypeGained", ""));
        dev.serantis.battle.TypeMastery.decode(serantis$typeTrophies, in.getStringOr("SerantisTypeTrophies", ""));
        serantis$out.clear();
        serantis$gone.clear();
        var outIds = in.read("SerantisOutIds", com.mojang.serialization.Codec.STRING.listOf()).orElse(java.util.List.of());
        var outCreatures = in.read("SerantisOut", dev.ahrivaea.party.CaughtCreature.CODEC.listOf()).orElse(java.util.List.of());
        for (int i = 0; i < Math.min(outIds.size(), outCreatures.size()); i++)
            try { serantis$out.put(java.util.UUID.fromString(outIds.get(i)), outCreatures.get(i)); } catch (IllegalArgumentException ignored) { }
        for (String s : in.read("SerantisGone", com.mojang.serialization.Codec.STRING.listOf()).orElse(java.util.List.of()))
            try { serantis$gone.add(java.util.UUID.fromString(s)); } catch (IllegalArgumentException ignored) { }
        String st = in.getStringOr("SerantisMastery", "");
        serantis$eternita = st.startsWith("eternita");
        for (String part : st.split(",")) {
            String[] kv = part.split(":");
            if (kv.length == 2) try { serantis$stones.put(kv[0].trim(), Integer.parseInt(kv[1].trim())); } catch (NumberFormatException ignored) { }
        }
    }
}
