package dev.ahrivaea.creature;

import dev.ahrivaea.battle.Battles;
import dev.ahrivaea.party.CaughtCreature;
import dev.ahrivaea.party.Party;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.UseRemainder;

public final class Healing {
   private Healing() {
   }

   public static int foodPercent(ItemStack stack) {
      FoodProperties food = (FoodProperties)stack.get(DataComponents.FOOD);
      return food == null ? 0 : Math.clamp(Math.round(2.5 * (double)food.nutrition() + 1.25 * (double)food.saturation()), 3, 60);
   }

   public static ItemStack remainder(ItemStack stack) {
      UseRemainder r = (UseRemainder)stack.get(DataComponents.USE_REMAINDER);
      return r == null ? ItemStack.EMPTY : r.convertInto().create();
   }

   public static Healing.PotionHeal potion(ItemStack stack) {
      boolean splash = stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION);
      if (!stack.is(Items.POTION) && !splash) {
         return null;
      } else {
         PotionContents c = (PotionContents)stack.get(DataComponents.POTION_CONTENTS);
         if (c == null) {
            return null;
         } else {
            int pct;
            String name;
            if (c.is(Potions.STRONG_HEALING)) {
               pct = 70;
               name = "a Potion of Healing II";
            } else if (c.is(Potions.HEALING)) {
               pct = 40;
               name = "a Potion of Healing";
            } else if (c.is(Potions.STRONG_REGENERATION)) {
               pct = 50;
               name = "a Potion of Regeneration II";
            } else if (c.is(Potions.LONG_REGENERATION)) {
               pct = 35;
               name = "a long Potion of Regeneration";
            } else {
               if (!c.is(Potions.REGENERATION)) {
                  return null;
               }

               pct = 30;
               name = "a Potion of Regeneration";
            }

            if (splash) {
               name = name.replace("a Potion", stack.is(Items.LINGERING_POTION) ? "a lingering Potion" : "a splash Potion");
            }

            return new Healing.PotionHeal(pct, splash, name);
         }
      }
   }

   public static ItemStack firstPotion(Player p) {
      Inventory inv = p.getInventory();

      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack s = inv.getItem(i);
         if (potion(s) != null) {
            return s;
         }
      }

      return ItemStack.EMPTY;
   }

   public static void giveBack(Player p, ItemStack left) {
      if (!left.isEmpty() && !p.getAbilities().instabuild) {
         p.getInventory().placeItemBackInInventory(left, Prediction.SERVER_ONLY);
      }
   }

   public static String feedParty(ServerPlayer p, int slot) {
      if (Battles.of(p) != null) {
         return "Food doesn't work in battle: use a healing potion.";
      } else {
         List<CaughtCreature> party = Party.get(p);
         if (slot >= 0 && slot < party.size()) {
            ItemStack food = p.getMainHandItem();
            int pct = foodPercent(food);
            if (pct <= 0) {
               return "Hold some food in your main hand to feed it.";
            } else {
               CaughtCreature c = party.get(slot);
               Optional<Species> species = Species.get(c.species());
               if (species.isEmpty()) {
                  return "That creature's species no longer exists.";
               } else {
                  int max = species.get().statsFor(c.influence()).maxHealth(c.level());
                  String name = species.get().nameFor(c.influence());
                  if (c.health() >= (float)max) {
                     return name + " is already at full health.";
                  } else {
                     float healed = Math.min((float)max, c.health() + (float)Math.max(1, Math.round((float)(max * pct) / 100.0F)));
                     Party.set(p, slot, c.withHealth(healed));
                     ItemStack left = remainder(food);
                     String what = food.getHoverName().getString();
                     food.consume(1, p);
                     giveBack(p, left);
                     return name + " ate the " + what + ": +" + Math.round(healed - c.health()) + " HP (" + Math.round(healed) + "/" + max + ")";
                  }
               }
            }
         } else {
            return "There's no creature in that slot.";
         }
      }
   }

   public static record PotionHeal(int percent, boolean splash, String name) {
   }
}
