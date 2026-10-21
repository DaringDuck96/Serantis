package dev.ahrivaea.stellar;

import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.ModifyOutput;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;

public final class Stellar {
   public static final ToolMaterial TOOL = new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 3046, 10.0F, 5.0F, 18, ItemTags.NETHERITE_TOOL_MATERIALS);
   public static final ArmorMaterial ARMOR = new ArmorMaterial(
      45,
      ArmorMaterials.NETHERITE.defense(),
      18,
      ArmorMaterials.NETHERITE.equipSound(),
      3.5F,
      0.15F,
      ArmorMaterials.NETHERITE.repairIngredient(),
      ArmorMaterials.NETHERITE.assetId()
   );
   public static final Item PICKAXE = item("stellar_pickaxe", p -> p.pickaxe(TOOL, 1.0F, -2.8F));
   public static final Item SHOVEL = item("stellar_shovel", p -> p.shovel(TOOL, 1.5F, -3.0F));
   public static final Item AXE = item("stellar_axe", p -> p.axe(TOOL, 5.0F, -3.0F));
   public static final Item HOE = item("stellar_hoe", p -> p.hoe(TOOL, -4.0F, 0.0F));
   public static final Item SWORD = item("stellar_sword", p -> p.sword(TOOL, 3.0F, -2.4F));
   public static final Item HELMET = item("stellar_helmet", p -> p.humanoidArmor(ARMOR, ArmorType.HELMET));
   public static final Item CHESTPLATE = item("stellar_chestplate", p -> p.humanoidArmor(ARMOR, ArmorType.CHESTPLATE));
   public static final Item LEGGINGS = item("stellar_leggings", p -> p.humanoidArmor(ARMOR, ArmorType.LEGGINGS));
   public static final Item BOOTS = item("stellar_boots", p -> p.humanoidArmor(ARMOR, ArmorType.BOOTS));

   private Stellar() {
   }

   private static Item item(String name, UnaryOperator<Properties> kind) {
      ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, StellarMod.id(name));
      Properties props = kind.apply(new Properties().setId(key)).fireResistant().rarity(Rarity.EPIC).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
      return (Item)Registry.register(BuiltInRegistries.ITEM, key, new Item(props));
   }

   public static void init() {
      CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register((ModifyOutput)out -> {
         out.accept(PICKAXE);
         out.accept(SHOVEL);
         out.accept(AXE);
         out.accept(HOE);
      });
      CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register((ModifyOutput)out -> {
         out.accept(SWORD);
         out.accept(AXE);
         out.accept(HELMET);
         out.accept(CHESTPLATE);
         out.accept(LEGGINGS);
         out.accept(BOOTS);
      });
      PlacedLogs.init();
      StellarAbilities.init();
   }

   public static boolean wearingFullSet(Player p) {
      return p.getItemBySlot(EquipmentSlot.HEAD).is(HELMET)
         && p.getItemBySlot(EquipmentSlot.CHEST).is(CHESTPLATE)
         && p.getItemBySlot(EquipmentSlot.LEGS).is(LEGGINGS)
         && p.getItemBySlot(EquipmentSlot.FEET).is(BOOTS);
   }
}
