package dev.ahrivaea.horses;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.Donkey;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.equine.Mule;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import net.minecraft.world.entity.animal.equine.ZombieHorse;
import net.minecraft.world.item.Item;

public record HorseSpeed(int tier, int fed) {
   public static final Codec<HorseSpeed> CODEC = RecordCodecBuilder.create(
      i -> i.group(Codec.INT.fieldOf("tier").forGetter(HorseSpeed::tier), Codec.INT.fieldOf("fed").forGetter(HorseSpeed::fed)).apply(i, HorseSpeed::new)
   );
   public static final StreamCodec<RegistryFriendlyByteBuf, HorseSpeed> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.VAR_INT, HorseSpeed::tier, ByteBufCodecs.VAR_INT, HorseSpeed::fed, HorseSpeed::new
   );
   public static final AttachmentType<HorseSpeed> ATTACHMENT = AttachmentRegistry.create(
      HorsesMod.id("horse_speed"), b -> b.persistent(CODEC).initializer(() -> new HorseSpeed(0, 0)).syncWith(STREAM_CODEC, AttachmentSyncPredicate.all())
   );
   public static final HorseSpeed START = new HorseSpeed(0, 0);
   public static final double[] MULTIPLIER = new double[]{1.5, 1.76, 2.02, 2.28, 2.54, 2.8};
   public static final int MAX_TIER = MULTIPLIER.length - 1;
   public static final int MAX_FED_TIER = 3;
   public static final double BREED_BONUS_CHANCE = 0.3;
   public static final double VANILLA_MAX_SPEED = 0.3375;
   public static final double BLOCKS_PER_SECOND = 42.16;

   public static HorseSpeed.Step stepFrom(int tier) {
      return switch (tier) {
         case 0 -> new HorseSpeed.Step(Feeds.SWIFT, 8);
         case 1 -> new HorseSpeed.Step(Feeds.GOLDEN, 8);
         case 2 -> new HorseSpeed.Step(Feeds.RACING, 4);
         default -> null;
      };
   }

   public static boolean covers(Entity e) {
      return e instanceof Horse || e instanceof Donkey || e instanceof Mule || e instanceof SkeletonHorse || e instanceof ZombieHorse;
   }

   public static HorseSpeed of(AbstractHorse h) {
      return (HorseSpeed)h.getAttachedOrElse(ATTACHMENT, START);
   }

   public static void set(AbstractHorse h, HorseSpeed s) {
      h.setAttached(ATTACHMENT, s);
      apply(h);
   }

   public static void apply(AbstractHorse h) {
      AttributeInstance speed = h.getAttribute(Attributes.MOVEMENT_SPEED);
      if (speed != null) {
         if (h instanceof SkeletonHorse) {
            speed.setBaseValue(0.3375);
            if (of(h).tier() != MAX_TIER) {
               h.setAttached(ATTACHMENT, new HorseSpeed(MAX_TIER, 0));
            }
         }

         int tier = Math.clamp((long)of(h).tier(), 0, MAX_TIER);
         speed.addOrUpdateTransientModifier(new AttributeModifier(HorsesMod.id("speed_tier"), MULTIPLIER[tier] - 1.0, Operation.ADD_MULTIPLIED_BASE));
      }
   }

   public static double blocksPerSecond(AbstractHorse h) {
      return h.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) * MULTIPLIER[Math.clamp((long)of(h).tier(), 0, MAX_TIER)] * 42.16;
   }

   public static double jumpHeight(AbstractHorse h) {
      double j = h.getAttributeValue(Attributes.JUMP_STRENGTH);
      return -0.1817584952 * j * j * j + 3.689713992 * j * j + 2.128599134 * j - 0.343930367;
   }

   public static int foalTier(int a, int b, RandomSource random) {
      int t = Math.max(a, b);
      if (a == b && t < MAX_TIER && random.nextDouble() < 0.3) {
         t++;
      }

      return t;
   }

   public static record Step(Item feed, int amount) {
   }
}
