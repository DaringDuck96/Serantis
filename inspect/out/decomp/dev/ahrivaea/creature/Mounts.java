package dev.ahrivaea.creature;

import dev.ahrivaea.Ahrivaea;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.ModifyOutput;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Item.Properties;

public final class Mounts {
   public static final float MIN_SIZE = 1.9F;
   public static final double[] MULTIPLIER = new double[]{1.5, 1.76, 2.02, 2.28, 2.54, 2.8};
   public static final int MAX_TIER = MULTIPLIER.length - 1;
   public static final int MAX_FED_TIER = 3;
   public static final int TIER_4_SPEED = 60;
   public static final int TIER_5_SPEED = 80;
   public static final double BLOCKS_PER_SECOND = 42.16;
   public static final Item SWIFT_TREAT = item("swift_treat", Rarity.COMMON);
   public static final Item GOLDEN_TREAT = item("golden_treat", Rarity.UNCOMMON);
   public static final Item RACING_TREAT = item("racing_treat", Rarity.RARE);

   private Mounts() {
   }

   private static Item item(String name, Rarity rarity) {
      ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Ahrivaea.id(name));
      return (Item)Registry.register(BuiltInRegistries.ITEM, key, new Item(new Properties().setId(key).rarity(rarity)));
   }

   public static void init() {
      CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register((ModifyOutput)out -> {
         out.accept(SWIFT_TREAT);
         out.accept(GOLDEN_TREAT);
         out.accept(RACING_TREAT);
      });
   }

   public static Mounts.Step stepFrom(int tier) {
      return switch (tier) {
         case 0 -> new Mounts.Step(SWIFT_TREAT, 8);
         case 1 -> new Mounts.Step(GOLDEN_TREAT, 8);
         case 2 -> new Mounts.Step(RACING_TREAT, 4);
         default -> null;
      };
   }

   public static boolean isTreat(ItemStack s) {
      return s.is(SWIFT_TREAT) || s.is(GOLDEN_TREAT) || s.is(RACING_TREAT);
   }

   public static boolean rideable(float size) {
      return size >= 1.9F;
   }

   public static int tier(int fedTier, double speed) {
      if (fedTier < 3) {
         return Math.max(0, fedTier);
      } else {
         return speed >= 80.0 ? 5 : (speed >= 60.0 ? 4 : 3);
      }
   }

   public static double blocksPerSecond(double speed, int tier) {
      return (5.0 + 0.09 * speed) * MULTIPLIER[Math.clamp((long)tier, 0, MAX_TIER)];
   }

   public static record Step(Item treat, int amount) {
   }
}
