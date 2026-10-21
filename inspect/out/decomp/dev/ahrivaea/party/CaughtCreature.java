package dev.ahrivaea.party;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Variant;
import dev.ahrivaea.villager.VillagerJob;

public record CaughtCreature(
   String species, Influence influence, int level, int xp, float health, int mountTier, int mountFed, boolean saddled, Variant variant, VillagerJob job
) {
   public static final Codec<CaughtCreature> CODEC = RecordCodecBuilder.create(
      i -> i.group(
               Codec.STRING.fieldOf("species").forGetter(CaughtCreature::species),
               Influence.CODEC.optionalFieldOf("influence", Influence.NONE).forGetter(CaughtCreature::influence),
               Codec.INT.fieldOf("level").forGetter(CaughtCreature::level),
               Codec.INT.optionalFieldOf("xp", 0).forGetter(CaughtCreature::xp),
               Codec.FLOAT.fieldOf("health").forGetter(CaughtCreature::health),
               Codec.INT.optionalFieldOf("mount_tier", 0).forGetter(CaughtCreature::mountTier),
               Codec.INT.optionalFieldOf("mount_fed", 0).forGetter(CaughtCreature::mountFed),
               Codec.BOOL.optionalFieldOf("saddled", false).forGetter(CaughtCreature::saddled),
               Variant.CODEC.optionalFieldOf("variant", Variant.NONE).forGetter(CaughtCreature::variant),
               VillagerJob.CODEC.optionalFieldOf("villager", VillagerJob.NONE).forGetter(CaughtCreature::job)
            )
            .apply(i, CaughtCreature::new)
   );

   public CaughtCreature(String species, Influence influence, int level, int xp, float health, int mountTier, int mountFed, boolean saddled, Variant variant) {
      this(species, influence, level, xp, health, mountTier, mountFed, saddled, variant, VillagerJob.NONE);
   }

   public CaughtCreature(String species, Influence influence, int level, int xp, float health) {
      this(species, influence, level, xp, health, 0, 0, false);
   }

   public CaughtCreature(String species, Influence influence, int level, int xp, float health, int mountTier, int mountFed, boolean saddled) {
      this(species, influence, level, xp, health, mountTier, mountFed, saddled, Variant.NONE);
   }

   public CaughtCreature withProgress(int level, int xp) {
      return new CaughtCreature(this.species, this.influence, level, xp, this.health, this.mountTier, this.mountFed, this.saddled, this.variant, this.job);
   }

   public CaughtCreature withHealth(float health) {
      return new CaughtCreature(this.species, this.influence, this.level, this.xp, health, this.mountTier, this.mountFed, this.saddled, this.variant, this.job);
   }

   public CaughtCreature withJob(VillagerJob job) {
      return new CaughtCreature(this.species, this.influence, this.level, this.xp, this.health, this.mountTier, this.mountFed, this.saddled, this.variant, job);
   }
}
