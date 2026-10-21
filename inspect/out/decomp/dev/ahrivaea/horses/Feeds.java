package dev.ahrivaea.horses;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.ModifyOutput;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Item.Properties;

public final class Feeds {
   public static final Item SWIFT = item("swift_feed", Rarity.COMMON);
   public static final Item GOLDEN = item("golden_feed", Rarity.UNCOMMON);
   public static final Item RACING = item("racing_feed", Rarity.RARE);

   private Feeds() {
   }

   private static Item item(String name, Rarity rarity) {
      ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, HorsesMod.id(name));
      return (Item)Registry.register(BuiltInRegistries.ITEM, key, new Item(new Properties().setId(key).rarity(rarity)));
   }

   static void init() {
      CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register((ModifyOutput)out -> {
         out.accept(SWIFT);
         out.accept(GOLDEN);
         out.accept(RACING);
      });
   }
}
