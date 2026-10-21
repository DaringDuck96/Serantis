package dev.ahrivaea.creature;

import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.base.Bases;
import dev.ahrivaea.base.Raids;
import dev.ahrivaea.base.WorkerBrain;
import dev.ahrivaea.battle.Battles;
import dev.ahrivaea.party.CaughtCreature;
import dev.ahrivaea.party.Party;
import dev.ahrivaea.villager.VillagerJob;
import dev.ahrivaea.villager.Villagers;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.Goal.Flag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class CreatureEntity extends TamableAnimal {
   private static final EntityDataAccessor<String> SPECIES = SynchedEntityData.defineId(CreatureEntity.class, EntityDataSerializers.STRING);
   private static final EntityDataAccessor<Integer> LEVEL = SynchedEntityData.defineId(CreatureEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<String> INFLUENCE = SynchedEntityData.defineId(CreatureEntity.class, EntityDataSerializers.STRING);
   private static final EntityDataAccessor<Integer> RIDE_FLAGS = SynchedEntityData.defineId(CreatureEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Float> TOP_SPEED = SynchedEntityData.defineId(CreatureEntity.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Float> SEAT = SynchedEntityData.defineId(CreatureEntity.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Integer> MOUNT_TIER = SynchedEntityData.defineId(CreatureEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> MOUNT_FED = SynchedEntityData.defineId(CreatureEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> RIDE_TIER = SynchedEntityData.defineId(CreatureEntity.class, EntityDataSerializers.INT);
   private static final int RIDEABLE = 1;
   private static final int FLIES = 2;
   private static final int SWIMS = 4;
   private static final EntityDataAccessor<String> VARIANT = SynchedEntityData.defineId(CreatureEntity.class, EntityDataSerializers.STRING);
   private int xp;
   private boolean riddenFlying;
   private int takeoffTicks;
   private WorkerBrain brain;
   private boolean preview;
   public static final float MAX_HITBOX = 4.0F;
   public static final float BASE_HITBOX = 1.25F;
   public static final float BATTLE_HITBOX = 1.4F;
   private int stuckSeconds;
   private Vec3 lastCheck;
   public static final int STUCK_INSIDE_SECONDS = 2;
   public static final int STUCK_STILL_SECONDS = 6;
   public static final int UNSTICK_RANGE = 12;
   private static List<BlockPos> offsets;

   public CreatureEntity(EntityType<? extends TamableAnimal> type, Level level) {
      super(type, level);
   }

   protected void defineSynchedData(Builder builder) {
      super.defineSynchedData(builder);
      builder.define(SPECIES, "");
      builder.define(LEVEL, 5);
      builder.define(INFLUENCE, "none");
      builder.define(RIDE_FLAGS, 0);
      builder.define(VARIANT, "none");
      builder.define(TOP_SPEED, 0.0F);
      builder.define(SEAT, 1.0F);
      builder.define(MOUNT_TIER, 0);
      builder.define(MOUNT_FED, 0);
      builder.define(RIDE_TIER, 0);
   }

   protected void registerGoals() {
      this.goalSelector.addGoal(0, new FloatGoal(this));
      this.goalSelector.addGoal(1, new CreatureEntity.WorkGoal());
      this.goalSelector.addGoal(1, new CreatureEntity.RaidGoal());
      this.goalSelector.addGoal(1, new PanicGoal(this, 1.4));
      this.goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
      this.goalSelector.addGoal(3, new FollowOwnerGoal(this, 1.1, 6.0F, 2.0F));
      this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.9));
      this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
      this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
   }

   public WorkerBrain brain() {
      if (this.brain == null && !this.level().isClientSide() && this.hasAttached(Bases.WORKER_OF)) {
         this.brain = new WorkerBrain(this, (BlockPos)this.getAttached(Bases.WORKER_OF));
      }

      return this.brain;
   }

   public void previewAs(String speciesId, String influence) {
      this.preview = true;
      this.entityData.set(SPECIES, speciesId);
      this.entityData.set(INFLUENCE, influence);
   }

   public boolean isPreview() {
      return this.preview;
   }

   public String getSpeciesId() {
      return (String)this.entityData.get(SPECIES);
   }

   public Optional<Species> getSpecies() {
      return Species.get(this.getSpeciesId());
   }

   public Influence getInfluence() {
      return Influence.byName((String)this.entityData.get(INFLUENCE));
   }

   public String displayName() {
      return this.getVariant().prefix() + this.getSpecies().map(s -> s.nameFor(this.getInfluence())).orElse(this.getSpeciesId());
   }

   public Variant getVariant() {
      return Variant.byName((String)this.entityData.get(VARIANT));
   }

   public void setVariant(Variant v) {
      this.entityData.set(VARIANT, v.getSerializedName());
      if (!this.getSpeciesId().isEmpty()) {
         this.refreshName();
      }
   }

   public Stats getStats() {
      return this.getSpecies().map(s -> s.statsFor(this.getInfluence())).orElse(new Stats(50, 50, 50, 50, 50, 50, 50));
   }

   public int getCreatureLevel() {
      return (Integer)this.entityData.get(LEVEL);
   }

   public void capSize(float max) {
      AttributeInstance scale = this.getAttribute(Attributes.SCALE);
      if (scale != null && scale.getBaseValue() > (double)max) {
         scale.setBaseValue((double)max);
      }
   }

   public void setup(Species species, Influence influence, int level, float health) {
      influence = species.formOrDefault(influence);
      level = Math.clamp((long)level, 0, 50);
      this.entityData.set(SPECIES, species.id());
      this.entityData.set(INFLUENCE, influence.getSerializedName());
      this.entityData.set(LEVEL, level);
      AttributeInstance maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
      if (maxHealth != null) {
         maxHealth.setBaseValue((double)species.statsFor(influence).maxHealth(level));
      }

      AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
      if (speed != null) {
         speed.setBaseValue((double)species.walkSpeed(influence));
      }

      AttributeInstance scale = this.getAttribute(Attributes.SCALE);
      if (scale != null) {
         scale.setBaseValue((double)Math.min(species.sizeFor(influence), 4.0F));
      }

      this.setHealth(health > 0.0F ? Math.min(health, this.getMaxHealth()) : this.getMaxHealth());
      this.refreshName();
      this.refreshMount();
   }

   public void refreshMount() {
      Optional<Species> species = this.getSpecies();
      if (!species.isEmpty()) {
         Species s = species.get();
         float size = s.sizeFor(this.getInfluence());
         int flags = Mounts.rideable(size) ? 1 : 0;
         if (s.movement() == Species.Movement.FLY) {
            flags |= 2;
         }

         if (s.movement() == Species.Movement.SWIM) {
            flags |= 4;
         }

         double speed = this.ridingSpeedStat();
         int tier = Mounts.tier(this.getMountTier(), speed);
         this.entityData.set(RIDE_FLAGS, flags);
         this.entityData.set(RIDE_TIER, tier);
         this.entityData.set(TOP_SPEED, (float)Mounts.blocksPerSecond(speed, tier));
         this.entityData.set(SEAT, size * 0.8F * 0.7F);
      }
   }

   public double ridingSpeedStat() {
      Stats st = this.getStats();
      return st.effective(st.speed(), this.getCreatureLevel());
   }

   public boolean isRideable() {
      return ((Integer)this.entityData.get(RIDE_FLAGS) & 1) != 0;
   }

   public boolean ridesFlying() {
      return ((Integer)this.entityData.get(RIDE_FLAGS) & 2) != 0;
   }

   public boolean ridesSwimming() {
      return ((Integer)this.entityData.get(RIDE_FLAGS) & 4) != 0;
   }

   public int getMountTier() {
      return (Integer)this.entityData.get(MOUNT_TIER);
   }

   public int getMountFed() {
      return (Integer)this.entityData.get(MOUNT_FED);
   }

   public int getRideTier() {
      return (Integer)this.entityData.get(RIDE_TIER);
   }

   public float getTopSpeed() {
      return (Float)this.entityData.get(TOP_SPEED);
   }

   public void setMountTraining(int tier, int fed) {
      this.entityData.set(MOUNT_TIER, Math.clamp((long)tier, 0, 3));
      this.entityData.set(MOUNT_FED, Math.max(0, fed));
      this.refreshMount();
   }

   public boolean hasSaddle() {
      return this.getItemBySlot(EquipmentSlot.SADDLE).is(Items.SADDLE);
   }

   public void setSaddle(boolean on) {
      this.setItemSlot(EquipmentSlot.SADDLE, on ? new ItemStack(Items.SADDLE) : ItemStack.EMPTY);
      if (on) {
         this.setGuaranteedDrop(EquipmentSlot.SADDLE);
      }
   }

   public void applyCaught(CaughtCreature c) {
      this.xp = c.xp();
      this.setVariant(c.variant());
      this.setMountTraining(c.mountTier(), c.mountFed());
      if (c.saddled() && this.isRideable()) {
         this.setSaddle(true);
      }

      if (c.job().trained()) {
         this.setAttached(Villagers.JOB, c.job());
      }
   }

   public LivingEntity getControllingPassenger() {
      if (this.hasSaddle() && this.isRideable()) {
         Entity var2 = this.getFirstPassenger();
         if (var2 instanceof Player) {
            return (Player)var2;
         }
      }

      return super.getControllingPassenger();
   }

   protected boolean canAddPassenger(Entity passenger) {
      return this.getPassengers().isEmpty();
   }

   protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
      return new Vec3(0.0, (double)((Float)this.entityData.get(SEAT)).floatValue(), 0.0);
   }

   protected boolean isImmobile() {
      return super.isImmobile() || this.getControllingPassenger() instanceof Player;
   }

   public boolean isFlyingVehicle() {
      return this.ridesFlying();
   }

   public float maxUpStep() {
      return this.isVehicle() ? Math.max(1.05F, super.maxUpStep()) : super.maxUpStep();
   }

   public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source) {
      return this.ridesFlying() ? false : super.causeFallDamage(fallDistance, multiplier, source);
   }

   protected void tickRidden(Player rider, Vec3 input) {
      super.tickRidden(rider, input);
      this.setRot(rider.getYRot(), rider.getXRot() * 0.5F);
      this.yRotO = this.yBodyRot = this.yHeadRot = this.getYRot();
      boolean jump = rider.isJumping();
      if (this.ridesFlying()) {
         if (!this.riddenFlying && jump && !this.isInWater()) {
            this.riddenFlying = true;
            this.takeoffTicks = 10;
            this.setDeltaMovement(this.getDeltaMovement().add(0.0, 0.6, 0.0));
         } else if (this.riddenFlying && this.takeoffTicks > 0) {
            this.takeoffTicks--;
         } else if (this.riddenFlying && (this.onGround() || this.isInWater()) && !jump) {
            this.riddenFlying = false;
         }

         this.resetFallDistance();
      }

      boolean swimming = this.ridesSwimming() && this.isInWater();
      if (!this.riddenFlying && !swimming && jump && this.onGround()) {
         Vec3 v = this.getDeltaMovement();
         this.setDeltaMovement(v.x, 0.6, v.z);
      }

      this.setJumping(jump && this.isInWater() && !swimming);
   }

   protected Vec3 getRiddenInput(Player rider, Vec3 input) {
      float strafe = rider.xxa * 0.5F;
      float forward = rider.zza;
      if (forward <= 0.0F && !this.riddenFlying) {
         forward *= 0.25F;
      }

      return new Vec3((double)strafe, 0.0, (double)forward);
   }

   protected float getRiddenSpeed(Player rider) {
      return (float)((double)this.getTopSpeed() / 42.16);
   }

   public void travel(Vec3 input) {
      if (this.getControllingPassenger() instanceof Player rider) {
         if (this.ridesSwimming() && this.isInWater()) {
            this.move3d(rider, input, 0.85);
            return;
         }

         if (this.ridesFlying() && this.riddenFlying) {
            this.move3d(rider, input, 0.91);
            return;
         }
      } else {
         this.riddenFlying = false;
      }

      super.travel(input);
   }

   private void move3d(Player rider, Vec3 input, double drag) {
      double perTick = (double)this.getTopSpeed() / 20.0;
      double pitch = Math.toRadians((double)rider.getXRot());
      double forward = input.z;
      double up = -Math.sin(pitch) * forward + (rider.isJumping() ? 0.7 : 0.0);
      this.moveRelative((float)(perTick * (1.0 - drag)), new Vec3(input.x, up, forward * Math.cos(pitch)));
      this.move(MoverType.SELF, this.getDeltaMovement());
      this.setDeltaMovement(this.getDeltaMovement().scale(drag));
   }

   private InteractionResult feedTreat(Player player, ItemStack stack) {
      String name = this.displayName();
      if (!this.isRideable()) {
         player.sendOverlayMessage(Component.literal(name + " is too small to ride, so treats won't help."));
         return InteractionResult.FAIL;
      } else {
         int tier = this.getMountTier();
         if (tier >= 3) {
            player.sendOverlayMessage(
               Component.literal(
                  this.getRideTier() >= Mounts.MAX_TIER
                     ? name + " is as fast as it gets."
                     : "Treats can't take it past tier 3. Tier 4 needs Speed 60 and tier 5 Speed 80 (it has "
                        + Math.round(this.ridingSpeedStat())
                        + "; it grows as it levels up)."
               )
            );
            return InteractionResult.FAIL;
         } else {
            Mounts.Step step = Mounts.stepFrom(tier);
            if (!stack.is(step.treat())) {
               player.sendOverlayMessage(
                  Component.literal(name + " is tier " + tier + "; it needs ").append(step.treat().getDefaultInstance().getHoverName()).append(" next.")
               );
               return InteractionResult.FAIL;
            } else {
               stack.consume(1, player);
               ServerLevel level = (ServerLevel)this.level();
               int fed = this.getMountFed() + 1;
               double y = this.getY() + (double)this.getBbHeight() * 0.8;
               if (fed >= step.amount()) {
                  this.setMountTraining(tier + 1, 0);
                  level.playSound(null, this.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 1.0F, 1.2F);
                  level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, this.getX(), y, this.getZ(), 30, 0.5, 0.6, 0.5, 0.3);
                  player.sendOverlayMessage(
                     Component.literal("Speed tier " + this.getRideTier() + "! Top speed " + Math.round(this.getTopSpeed()) + " blocks/sec")
                        .withStyle(ChatFormatting.GOLD)
                  );
               } else {
                  this.setMountTraining(tier, fed);
                  level.playSound(null, this.blockPosition(), (SoundEvent)SoundEvents.GENERIC_EAT.value(), SoundSource.NEUTRAL, 1.0F, 1.0F);
                  level.sendParticles(ParticleTypes.HAPPY_VILLAGER, this.getX(), y, this.getZ(), 6, 0.4, 0.4, 0.4, 0.0);
                  player.sendOverlayMessage(
                     Component.literal("Tier " + tier + ": " + fed + "/" + step.amount() + " ")
                        .append(step.treat().getDefaultInstance().getHoverName())
                        .append(" to tier " + (tier + 1))
                  );
               }

               return InteractionResult.SUCCESS;
            }
         }
      }
   }

   public InteractionResult eat(Player player, ItemStack food) {
      String name = this.displayName();
      if (this.getHealth() >= this.getMaxHealth()) {
         player.sendOverlayMessage(Component.literal(name + " is already at full health."));
         return InteractionResult.FAIL;
      } else {
         float before = this.getHealth();
         this.setHealth(Math.min(this.getMaxHealth(), before + (float)Math.max(1, Math.round(this.getMaxHealth() * (float)Healing.foodPercent(food) / 100.0F))));
         ItemStack left = Healing.remainder(food);
         food.consume(1, player);
         Healing.giveBack(player, left);
         ServerLevel level = (ServerLevel)this.level();
         level.playSound(null, this.blockPosition(), (SoundEvent)SoundEvents.GENERIC_EAT.value(), SoundSource.NEUTRAL, 1.0F, 1.0F);
         level.sendParticles(ParticleTypes.HEART, this.getX(), this.getY() + (double)this.getBbHeight() * 0.8, this.getZ(), 4, 0.4, 0.3, 0.4, 0.0);
         player.sendOverlayMessage(
            Component.literal(
               name + ": +" + Math.round(this.getHealth() - before) + " HP (" + Math.round(this.getHealth()) + "/" + Math.round(this.getMaxHealth()) + ")"
            )
         );
         return InteractionResult.SUCCESS;
      }
   }

   private InteractionResult rideInteract(Player player, ItemStack stack) {
      String name = this.displayName();
      if (Mounts.isTreat(stack)) {
         return this.feedTreat(player, stack);
      } else if (stack.is(Items.SADDLE) && !this.hasSaddle()) {
         if (!this.isRideable()) {
            player.sendOverlayMessage(Component.literal(name + " is too small to ride. Medium and bigger creatures can be saddled."));
            return InteractionResult.FAIL;
         } else {
            this.setSaddle(true);
            stack.consume(1, player);
            this.level().playSound(null, this.blockPosition(), (SoundEvent)SoundEvents.HORSE_SADDLE.value(), SoundSource.NEUTRAL, 1.0F, 1.0F);
            player.sendOverlayMessage(
               Component.literal(
                  name + " is saddled. Right-click to ride" + (this.ridesFlying() ? "; jump to take off." : (this.ridesSwimming() ? "; it swims too." : "."))
               )
            );
            return InteractionResult.SUCCESS;
         }
      } else if (stack.is(Items.SHEARS) && this.hasSaddle()) {
         this.setSaddle(false);
         ItemStack saddle = new ItemStack(Items.SADDLE);
         if (!player.getInventory().add(saddle)) {
            this.spawnAtLocation((ServerLevel)this.level(), saddle);
         }

         this.level().playSound(null, this.blockPosition(), (SoundEvent)SoundEvents.SADDLE_UNEQUIP.value(), SoundSource.NEUTRAL, 1.0F, 1.0F);
         player.sendOverlayMessage(Component.literal("Took the saddle off " + name + "."));
         return InteractionResult.SUCCESS;
      } else if (this.hasSaddle() && !player.isShiftKeyDown() && !this.isVehicle()) {
         this.setOrderedToSit(false);
         player.startRiding(this);
         return InteractionResult.SUCCESS;
      } else {
         return null;
      }
   }

   private void refreshName() {
      this.setCustomName(Component.literal(this.displayName() + " Lv." + this.getCreatureLevel()).withColor(this.getInfluence().color()));
      this.setCustomNameVisible(true);
   }

   @Nullable
   public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData data) {
      if (this.getSpeciesId().isEmpty()) {
         Optional<Species.Pick> picked = Species.pickFor(level.getBiome(this.blockPosition()), level.getLevel(), this.random);
         if (picked.isEmpty() && !Species.all().isEmpty()) {
            List<Species> all = Species.all();
            Species s = all.get(this.random.nextInt(all.size()));
            Optional<Influence> inf = InfluenceRules.pick(level.getBiome(this.blockPosition()), level.getLevel(), s.forms().keySet(), this.random);
            picked = Optional.of(new Species.Pick(s, inf.orElse(s.formOrDefault(Influence.NONE))));
         }

         picked.ifPresent(p -> {
            int[] range = p.species().levelRange(p.influence());
            this.setup(p.species(), p.influence(), range[0] + this.random.nextInt(Math.max(1, range[1] - range[0] + 1)), -1.0F);
         });
      }

      return super.finalizeSpawn(level, difficulty, reason, data);
   }

   public boolean removeWhenFarAway(double distanceSqr) {
      return !this.isTame() && !Battles.inBattle(this);
   }

   public boolean checkSpawnRules(LevelAccessor level, EntitySpawnReason reason) {
      if ((reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.CHUNK_GENERATION)
         && level instanceof ServerLevelAccessor server
         && Species.pickFor(level.getBiome(this.blockPosition()), server.getLevel(), this.random).isEmpty()) {
         return false;
      }

      return super.checkSpawnRules(level, reason);
   }

   public InteractionResult mobInteract(Player player, InteractionHand hand) {
      if (this.isTame() && this.isOwnedBy(player) && this.hasAttached(Bases.WORKER_OF)) {
         if (!this.level().isClientSide()) {
            player.sendOverlayMessage(
               Component.literal(
                  this.displayName() + ": " + (this.brain() != null ? this.brain().status() : "working") + ". (Use the base core to send it away.)"
               )
            );
         }

         return InteractionResult.SUCCESS;
      } else if (this.isTame() && this.isOwnedBy(player)) {
         if (!this.level().isClientSide()) {
            ItemStack held = player.getItemInHand(hand);
            if (!player.isShiftKeyDown() && Healing.foodPercent(held) > 0) {
               return this.eat(player, held);
            }

            InteractionResult ride = this.rideInteract(player, player.getItemInHand(hand));
            if (ride != null) {
               return ride;
            }

            if (!player.isShiftKeyDown() && Villagers.isVillager(this.getSpeciesId()) && player instanceof ServerPlayer sp) {
               Villagers.open(sp, Villagers.Handle.out(this));
               return InteractionResult.SUCCESS;
            }

            if (player.isShiftKeyDown()) {
               this.recallTo(player);
            } else {
               this.setOrderedToSit(!this.isOrderedToSit());
               player.sendOverlayMessage(Component.literal(this.displayName() + (this.isOrderedToSit() ? " will wait here." : " will follow you.")));
            }
         }

         return InteractionResult.SUCCESS;
      } else {
         ItemStack heldx = player.getItemInHand(hand);
         if (!heldx.is(Items.SADDLE) && !Mounts.isTreat(heldx)) {
            return super.mobInteract(player, hand);
         } else {
            if (!this.level().isClientSide()) {
               player.sendOverlayMessage(Component.literal("Only your own creatures can be saddled and fed treats. Catch it first."));
            }

            return InteractionResult.FAIL;
         }
      }
   }

   public void tick() {
      super.tick();
      if (!this.level().isClientSide() && this.getSpeciesId().isEmpty() && !Species.all().isEmpty()) {
         List<Species> all = Species.all();
         Species s = all.get(this.random.nextInt(all.size()));
         this.setup(s, s.formOrDefault(Influence.NONE), 5, -1.0F);
      }

      if (!this.level().isClientSide() && this.tickCount % 20 == 0 && this.ridesSwimming() && this.getControllingPassenger() instanceof Player rider) {
         rider.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 60, 0, true, false));
      }

      if (!this.level().isClientSide() && this.tickCount % 20 == 0) {
         this.checkStuck();
      }
   }

   private void checkStuck() {
      if (!this.isNoAi() && !this.isPassenger() && !this.isVehicle() && !Battles.inBattle(this)) {
         Vec3 now = this.position();
         boolean inside = !this.level().noCollision(this, this.getBoundingBox().deflate(0.1));
         boolean trying = this.getNavigation().isInProgress() && !this.isOrderedToSit();
         boolean still = this.lastCheck != null && now.distanceToSqr(this.lastCheck) < 0.05;
         this.lastCheck = now;
         this.stuckSeconds = !inside && (!trying || !still) ? 0 : this.stuckSeconds + 1;
         if (this.stuckSeconds >= (inside ? 2 : 6)) {
            this.stuckSeconds = 0;
            BlockPos to = this.freeSpotNear(this.blockPosition());
            if (to != null) {
               Ahrivaea.LOGGER
                  .info("{} was stuck at {}; moved to {}", new Object[]{this.displayName(), this.blockPosition().toShortString(), to.toShortString()});
               this.getNavigation().stop();
               this.teleportTo((double)to.getX() + 0.5, (double)to.getY(), (double)to.getZ() + 0.5);
               this.setDeltaMovement(Vec3.ZERO);
               this.lastCheck = null;
            }
         }
      } else {
         this.stuckSeconds = 0;
      }
   }

   public BlockPos freeSpotNear(BlockPos from) {
      if (offsets == null) {
         List<BlockPos> list = new ArrayList<>();

         for (int dx = -12; dx <= 12; dx++) {
            for (int dy = -6; dy <= 12; dy++) {
               for (int dz = -12; dz <= 12; dz++) {
                  if (dx != 0 || dy != 0 || dz != 0) {
                     list.add(new BlockPos(dx, dy, dz));
                  }
               }
            }
         }

         list.sort(
            Comparator.comparingDouble(
               ox -> (double)(ox.getX() * ox.getX() + ox.getZ() * ox.getZ()) + (ox.getY() < 0 ? 1.5 : 1.0) * (double)ox.getY() * (double)ox.getY()
            )
         );
         offsets = list;
      }

      boolean swims = this.getSpecies().map(sp -> sp.movement() == Species.Movement.SWIM).orElse(false);
      AABB box = this.getBoundingBox();
      Vec3 feet = this.position();

      for (BlockPos o : offsets) {
         BlockPos p = from.offset(o);
         if (this.level().isLoaded(p)) {
            boolean water = this.level().getFluidState(p).is(FluidTags.WATER);
            if ((swims ? water : this.level().getBlockState(p.below()).isFaceSturdy(this.level(), p.below(), Direction.UP))
               && (swims || this.level().getFluidState(p).isEmpty())) {
               AABB moved = box.move((double)p.getX() + 0.5 - feet.x, (double)p.getY() - feet.y, (double)p.getZ() + 0.5 - feet.z);
               if (this.level().noCollision(this, moved) && (swims || !this.level().containsAnyLiquid(moved.deflate(0.05)))) {
                  return p;
               }
            }
         }
      }

      return null;
   }

   public void recallTo(Player player) {
      this.ejectPassengers();
      boolean party = Party.addAnywhere(player, this.toCaught());
      player.sendOverlayMessage(Component.literal(this.displayName() + (party ? " came back to your party." : " went to your box (party full).")));
      this.discard();
   }

   public CaughtCreature toCaught() {
      return new CaughtCreature(
         this.getSpeciesId(),
         this.getInfluence(),
         this.getCreatureLevel(),
         this.xp,
         this.getHealth(),
         this.getMountTier(),
         this.getMountFed(),
         this.hasSaddle(),
         this.getVariant(),
         (VillagerJob)this.getAttachedOrElse(Villagers.JOB, VillagerJob.NONE)
      );
   }

   protected void addAdditionalSaveData(ValueOutput output) {
      super.addAdditionalSaveData(output);
      output.putString("Species", this.getSpeciesId());
      output.putInt("CreatureLevel", this.getCreatureLevel());
      output.putString("Influence", this.getInfluence().getSerializedName());
      output.putInt("CreatureXp", this.xp);
      output.putString("Variant", this.getVariant().getSerializedName());
      output.putInt("MountTier", this.getMountTier());
      output.putInt("MountFed", this.getMountFed());
   }

   protected void readAdditionalSaveData(ValueInput input) {
      super.readAdditionalSaveData(input);
      this.entityData.set(SPECIES, input.getStringOr("Species", ""));
      this.entityData.set(LEVEL, input.getIntOr("CreatureLevel", 5));
      this.entityData.set(INFLUENCE, input.getStringOr("Influence", "none"));
      this.xp = input.getIntOr("CreatureXp", 0);
      this.entityData.set(VARIANT, input.getStringOr("Variant", "none"));
      this.entityData.set(MOUNT_TIER, input.getIntOr("MountTier", 0));
      this.entityData.set(MOUNT_FED, input.getIntOr("MountFed", 0));
      this.refreshName();
      this.refreshMount();
   }

   public boolean isFood(ItemStack stack) {
      return false;
   }

   @Nullable
   public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
      return null;
   }

   private class RaidGoal extends Goal {
      RaidGoal() {
         Objects.requireNonNull(CreatureEntity.this);
         super();
         this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
      }

      public boolean canUse() {
         return !CreatureEntity.this.level().isClientSide() && CreatureEntity.this.hasAttached(Raids.RAIDER_OF);
      }

      public boolean requiresUpdateEveryTick() {
         return true;
      }

      public void tick() {
         Raids.raiderTick(CreatureEntity.this);
      }
   }

   private class WorkGoal extends Goal {
      WorkGoal() {
         Objects.requireNonNull(CreatureEntity.this);
         super();
         this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
      }

      public boolean canUse() {
         return CreatureEntity.this.brain() != null;
      }

      public boolean requiresUpdateEveryTick() {
         return true;
      }

      public void tick() {
         CreatureEntity.this.brain().tick((ServerLevel)CreatureEntity.this.level());
      }
   }
}
