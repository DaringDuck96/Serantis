package dev.ahrivaea.item;

import dev.ahrivaea.Ahrivaea;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.ModifyOutput;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.food.FoodProperties.Builder;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;

public final class DropItems {
   public static final Item BANANA = item("banana", p -> p.food(new Builder().nutrition(4).saturationModifier(0.3F).build()));
   public static final Item RAW_RAT = item("raw_rat", p -> p.food(new Builder().nutrition(2).saturationModifier(0.1F).build()));
   public static final Item IVORY = item("ivory", p -> p);
   public static final Item PURPUR_SHARD = item("purpur_shard", p -> p);

   private DropItems() {
   }

   private static Item item(String name, UnaryOperator<Properties> props) {
      ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Ahrivaea.id(name));
      return (Item)Registry.register(BuiltInRegistries.ITEM, key, new Item(props.apply(new Properties().setId(key))));
   }

   public static void init() {
      CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register((ModifyOutput)out -> {
         out.accept(BANANA);
         out.accept(RAW_RAT);
      });
      CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register((ModifyOutput)out -> {
         out.accept(IVORY);
         out.accept(PURPUR_SHARD);
      });
   }
}
