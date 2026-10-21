package dev.ahrivaea.stellar;

import com.mojang.serialization.Codec;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AfterDamage;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.After;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class StellarAbilities {
   public static final int MAX_TREE = 256;
   public static final AttachmentType<Boolean> FLIGHT = AttachmentRegistry.create(
      StellarMod.id("stellar_flight"), b -> b.persistent(Codec.BOOL).copyOnDeath().initializer(() -> false)
   );
   private static boolean busy;

   private StellarAbilities() {
   }

   static void init() {
      PlayerBlockBreakEvents.AFTER.register((After)(level, player, pos, state, blockEntity) -> {
         if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
            boolean placedLog = state.is(BlockTags.LOGS) && PlacedLogs.isPlaced(level, pos);
            if (placedLog) {
               PlacedLogs.unmark(level, pos);
            }

            if (!busy && !sp.isShiftKeyDown()) {
               ItemStack tool = sp.getMainHandItem();
               busy = true;

               try {
                  if (tool.is(Stellar.PICKAXE) || tool.is(Stellar.SHOVEL)) {
                     areaMine(server, sp, pos, state, tool);
                  } else if (tool.is(Stellar.AXE) && state.is(BlockTags.LOGS) && !placedLog) {
                     fellTree(server, sp, pos);
                  }
               } finally {
                  busy = false;
               }

               return;
            }

            return;
         }
      });
      UseBlockCallback.EVENT
         .register(
            (UseBlockCallback)(player, level, hand, hit) -> {
               if (hand != InteractionHand.MAIN_HAND || !(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
                  return InteractionResult.PASS;
               }

               return (InteractionResult)(sp.getMainHandItem().is(Stellar.HOE) && !sp.isShiftKeyDown()
                  ? hoe(server, sp, hit.getBlockPos(), hit.getDirection())
                  : InteractionResult.PASS);
            }
         );
      ServerLivingEntityEvents.AFTER_DAMAGE.register((AfterDamage)(entity, source, base, taken, blocked) -> {
         if (taken > 0.0F && source.getEntity() instanceof Player p && p != entity && p.getMainHandItem().is(Stellar.SWORD)) {
            p.heal(taken * 0.2F);
         }
      });
      ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
         if (server.getTickCount() % 5 == 0) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
               updateFlight(p);
            }
         }
      });
   }

   private static void areaMine(ServerLevel level, ServerPlayer player, BlockPos center, BlockState centerState, ItemStack tool) {
      float centerHardness = centerState.getDestroySpeed(level, center);
      Axis axis = Math.abs(player.getXRot()) > 50.0F ? Axis.Y : player.getDirection().getAxis();

      for (int a = -1; a <= 1; a++) {
         for (int b = -1; b <= 1; b++) {
            if (a != 0 || b != 0) {
               BlockPos p = switch (axis) {
                  case Y -> center.offset(a, 0, b);
                  case X -> center.offset(0, a, b);
                  case Z -> center.offset(a, b, 0);
                  default -> throw new MatchException(null, null);
               };
               BlockState s = level.getBlockState(p);
               float hardness = s.getDestroySpeed(level, p);
               if (!s.isAir() && !(hardness < 0.0F) && !(hardness > Math.max(centerHardness * 2.5F, 5.0F)) && !(tool.getDestroySpeed(s) <= 1.0F)) {
                  if (tool.isEmpty() || !player.getMainHandItem().is(tool.getItem())) {
                     return;
                  }

                  player.gameMode.destroyBlock(p);
               }
            }
         }
      }
   }

   private static void fellTree(ServerLevel level, ServerPlayer player, BlockPos start) {
      if (!inStructure(level, start)) {
         Set<BlockPos> logs = new HashSet<>();
         ArrayDeque<BlockPos> queue = new ArrayDeque<>();
         queue.add(start);
         Set<BlockPos> seen = new HashSet<>();
         seen.add(start);
         int naturalLeaves = 0;

         while (!queue.isEmpty() && logs.size() < 256) {
            BlockPos p = queue.poll();

            for (BlockPos n : BlockPos.betweenClosed(p.offset(-1, -1, -1), p.offset(1, 1, 1))) {
               BlockPos q = n.immutable();
               if (seen.add(q)) {
                  BlockState s = level.getBlockState(q);
                  if (s.getBlock() instanceof LeavesBlock && s.hasProperty(LeavesBlock.PERSISTENT) && !(Boolean)s.getValue(LeavesBlock.PERSISTENT)) {
                     naturalLeaves++;
                  }

                  if (s.is(BlockTags.LOGS) && q.getY() >= start.getY() - 1 && !PlacedLogs.isPlaced(level, q) && !inStructure(level, q)) {
                     logs.add(q);
                     queue.add(q);
                  }
               }
            }
         }

         if (naturalLeaves >= 3 && !logs.isEmpty()) {
            List<BlockPos> order = new ArrayList<>(logs);
            order.sort(Comparator.comparingInt(Vec3i::getY));

            for (BlockPos p : order) {
               if (!player.getMainHandItem().is(Stellar.AXE)) {
                  return;
               }

               player.gameMode.destroyBlock(p);
            }
         }
      }
   }

   private static boolean inStructure(ServerLevel level, BlockPos pos) {
      return level.structureManager().getStructureWithPieceAt(pos, holder -> true).isValid();
   }

   private static InteractionResult hoe(ServerLevel level, ServerPlayer player, BlockPos center, Direction face) {
      BlockState clicked = level.getBlockState(center);
      ItemStack hoe = player.getMainHandItem();
      if (clicked.getBlock() instanceof CropBlock) {
         int harvested = 0;

         for (BlockPos p : BlockPos.betweenClosed(center.offset(-1, 0, -1), center.offset(1, 0, 1))) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof CropBlock crop && crop.isMaxAge(s)) {
               List<ItemStack> drops = new ArrayList<>(Block.getDrops(s, level, p, null, player, hoe));
               Item seed = s.getBlock().asItem();

               for (ItemStack d : drops) {
                  if (d.is(seed)) {
                     d.shrink(1);
                     break;
                  }
               }

               for (ItemStack dx : drops) {
                  if (!dx.isEmpty()) {
                     Block.popResource(level, p, dx);
                  }
               }

               level.setBlock(p.immutable(), crop.getStateForAge(0), 3);
               harvested++;
            }
         }

         if (harvested == 0) {
            return InteractionResult.PASS;
         } else {
            level.playSound(null, center, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
            hoe.hurtAndBreak(1, player, InteractionHand.MAIN_HAND);
            return InteractionResult.SUCCESS;
         }
      } else if (face == Direction.DOWN) {
         return InteractionResult.PASS;
      } else {
         int tilled = 0;

         for (BlockPos px : BlockPos.betweenClosed(center.offset(-1, 0, -1), center.offset(1, 0, 1))) {
            BlockState s = level.getBlockState(px);
            if (level.getBlockState(px.above()).isAir()) {
               BlockState to = null;
               if (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.DIRT_PATH)) {
                  to = Blocks.FARMLAND.defaultBlockState();
               } else if (s.is(Blocks.COARSE_DIRT)) {
                  to = Blocks.DIRT.defaultBlockState();
               }

               if (to != null) {
                  level.setBlock(px.immutable(), to, 3);
                  tilled++;
               }
            }
         }

         if (tilled == 0) {
            return InteractionResult.PASS;
         } else {
            level.playSound(null, center, (SoundEvent)SoundEvents.HOE_TILL.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
            hoe.hurtAndBreak(1, player, InteractionHand.MAIN_HAND);
            return InteractionResult.SUCCESS;
         }
      }
   }

   private static void updateFlight(ServerPlayer p) {
      boolean full = Stellar.wearingFullSet(p);
      boolean ours = (Boolean)p.getAttachedOrCreate(FLIGHT);
      Abilities abilities = p.getAbilities();
      if (full && !abilities.mayfly) {
         abilities.mayfly = true;
         p.setAttached(FLIGHT, true);
         p.onUpdateAbilities();
      } else if (!full && ours) {
         p.setAttached(FLIGHT, false);
         if (!p.isCreative() && !p.isSpectator()) {
            if (abilities.flying) {
               p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0));
            }

            abilities.mayfly = false;
            abilities.flying = false;
            p.onUpdateAbilities();
         }
      }
   }
}
