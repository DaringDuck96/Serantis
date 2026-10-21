package dev.ahrivaea.item;

import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.party.Party;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;

public class VesselItem extends Item {
   public VesselItem(Properties properties) {
      super(properties);
   }

   public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
      if (target instanceof CreatureEntity creature) {
         if (player.level() instanceof ServerLevel level) {
            String var17 = creature.displayName();
            if (creature.isTame()) {
               player.sendOverlayMessage(Component.literal(var17 + " already has a trainer."));
               return InteractionResult.FAIL;
            } else if (Party.get(player).size() >= 6) {
               player.sendOverlayMessage(Component.literal("Your party is full (6). Release one with /party release <slot>."));
               return InteractionResult.FAIL;
            } else {
               if (!player.getAbilities().instabuild) {
                  stack.shrink(1);
               }

               float hpLeft = creature.getHealth() / creature.getMaxHealth();
               float chance = 0.45F * (1.6F - hpLeft);
               boolean caught = level.getRandom().nextFloat() < chance;
               double x = creature.getX();
               double y = creature.getY() + (double)(creature.getBbHeight() / 2.0F);
               double z = creature.getZ();
               if (caught) {
                  Party.addAnywhere(player, creature.toCaught());
                  level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 20, 0.4, 0.4, 0.4, 0.1);
                  level.playSound(null, x, y, z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.4F);
                  creature.discard();
                  player.sendSystemMessage(Component.literal("Bound " + var17 + " (Lv." + creature.getCreatureLevel() + ")!"));
               } else {
                  level.sendParticles(ParticleTypes.SMOKE, x, y, z, 15, 0.3, 0.3, 0.3, 0.02);
                  level.playSound(null, x, y, z, (SoundEvent)SoundEvents.ITEM_BREAK.value(), SoundSource.PLAYERS, 0.8F, 1.2F);
                  player.sendOverlayMessage(Component.literal(var17 + " broke free of the vessel! (" + Math.round(chance * 100.0F) + "% chance)"));
               }

               return InteractionResult.SUCCESS;
            }
         } else {
            return InteractionResult.SUCCESS;
         }
      } else {
         return InteractionResult.PASS;
      }
   }
}
