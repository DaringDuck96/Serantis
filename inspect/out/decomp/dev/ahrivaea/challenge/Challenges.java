package dev.ahrivaea.challenge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.ints.IntList;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Map.Entry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.Before;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Join;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.Vec3i;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.Prediction;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.component.FireworkExplosion.Shape;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.scores.PlayerTeam;

public final class Challenges {
   public static final AttachmentType<Map<String, ChallengeState>> STATE = AttachmentRegistry.create(
      TowersMod.id("challenges"), b -> b.persistent(Codec.unboundedMap(Codec.STRING, ChallengeState.CODEC)).initializer(Map::of)
   );
   public static final int PROTECT_RADIUS = 64;
   public static final int PROTECT_BELOW = 8;
   private static final Map<String, BoundingBox> AREAS = new HashMap<>();
   public static final Identifier BASE_CORE = Identifier.fromNamespaceAndPath("ahrivaea", "base_core");

   private Challenges() {
   }

   public static void init() {
      UseBlockCallback.EVENT
         .register(
            (UseBlockCallback)(player, level, hand, hit) -> {
               if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
                  String id = chestAt(server, hit.getBlockPos());
                  if (id == null) {
                     return InteractionResult.PASS;
                  }

                  if (state(server.getServer(), id).map(st -> st.complete).orElse(true)
                     && !(server.getBlockState(hit.getBlockPos()).getBlock() instanceof ChestBlock)) {
                     return InteractionResult.PASS;
                  }

                  open(sp, id);
                  return InteractionResult.SUCCESS;
               }

               return InteractionResult.PASS;
            }
         );
      PlayerBlockBreakEvents.BEFORE.register((Before)(level, player, pos, state, blockEntity) -> {
         if (level instanceof ServerLevel server) {
            if (protectedFrom(server, player, pos, false) != null) {
               return false;
            } else {
               String id = chestAt(server, pos);
               if (id != null && !player.isCreative() && state(server.getServer(), id).map(st -> st.complete).orElse(false)) {
                  player.sendOverlayMessage(Component.literal("This base belongs to the tower; it can't be moved."));
                  return false;
               } else {
                  return true;
               }
            }
         } else {
            return true;
         }
      });
      ServerPlayConnectionEvents.JOIN.register((Join)(handler, sender, server) -> deliverPending(handler.getPlayer()));
      ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
         if (server.getTickCount() % 100 == 13) {
            for (Entry<String, ChallengeState> e : states(server).entrySet()) {
               ChallengeState st = e.getValue();
               if (st.complete) {
                  Optional<ChallengeDef> def = defOf(e.getKey(), st);
                  ServerLevel lvl = level(server, st);
                  if (!def.isEmpty() && lvl != null) {
                     BlockPos chest = st.origin.offset(def.get().chest());
                     if (lvl.isLoaded(chest) && lvl.getBlockState(chest).getBlock() instanceof ChestBlock && makeBase(lvl, e.getKey(), def.get(), st)) {
                        save(server, e.getKey(), st);

                        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                           deliverPending(p);
                        }
                     }
                  }
               }
            }
         }
      });
   }

   public static String protectedFrom(ServerLevel level, Entity entity, BlockPos pos, boolean tell) {
      if (entity instanceof ServerPlayer player && !player.isCreative() && !player.isSpectator()) {
         for (Entry<String, ChallengeState> e : states(level.getServer()).entrySet()) {
            ChallengeState s = e.getValue();
            if (inLevel(s, level)) {
               Optional<ChallengeDef> def = defOf(e.getKey(), s);
               if (!def.isEmpty()) {
                  BoundingBox room = s.portalRoomBox();
                  if (!s.complete && room != null && room.inflatedBy(1).isInside(pos)) {
                     if (tell) {
                        player.sendOverlayMessage(Component.literal("The way to the End is sealed until " + def.get().name() + " above is rebuilt."));
                     }

                     return e.getKey();
                  }

                  BoundingBox area = AREAS.computeIfAbsent(e.getKey() + "@" + s.origin.asLong(), k -> {
                     BoundingBox b = box(level, def.get(), s);
                     return new BoundingBox(b.minX() - 64, b.minY() - 8, b.minZ() - 64, b.maxX() + 64, level.getMaxY() + 1, b.maxZ() + 64);
                  });
                  if (area.isInside(pos)) {
                     if (!s.complete && s.origin.offset(def.get().chest()).equals(pos)) {
                        return null;
                     }

                     String owner = s.controller();
                     if (owner.isEmpty() || !owner.equals(player.getUUID().toString()) && !sameGuild(level.getServer(), player, owner, s)) {
                        if (tell) {
                           player.sendOverlayMessage(
                              Component.literal(
                                 owner.isEmpty()
                                    ? def.get().name() + " and the land around it are protected until it's rebuilt. Its top donor claims it."
                                    : def.get().name() + " belongs to " + s.names.getOrDefault(owner, "its top donor") + " and their guild."
                              )
                           );
                        }

                        return e.getKey();
                     }

                     return null;
                  }
               }
            }
         }

         return null;
      }

      return null;
   }

   static boolean sameGuild(MinecraftServer server, ServerPlayer player, String ownerKey, ChallengeState s) {
      PlayerTeam team = player.getTeam();
      if (team == null) {
         return false;
      } else {
         ServerPlayer online = null;

         try {
            online = server.getPlayerList().getPlayer(UUID.fromString(ownerKey));
         } catch (IllegalArgumentException var8) {
         }

         String name = online != null ? online.getScoreboardName() : s.names.get(ownerKey);
         if (name == null) {
            return false;
         } else {
            PlayerTeam ownerTeam = server.getScoreboard().getPlayersTeam(name);
            return ownerTeam != null && ownerTeam.getName().equals(team.getName());
         }
      }
   }

   static boolean makeBase(ServerLevel level, String id, ChallengeDef def, ChallengeState s) {
      Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(BASE_CORE);
      if (!block.isEmpty() && level != null) {
         BlockPos pos = s.origin.offset(def.chest());
         level.setBlock(pos, block.get().defaultBlockState(), 3);
         String owner = s.controller();
         BlockEntity be = level.getBlockEntity(pos);
         if (be != null && !owner.isEmpty()) {
            try {
               CompoundTag tag = new CompoundTag();
               tag.store("owner", UUIDUtil.CODEC, UUID.fromString(owner));
               tag.putString("owner_name", s.names.getOrDefault(owner, ""));
               be.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
               be.setChanged();
            } catch (IllegalArgumentException var9) {
            }
         }

         if (!owner.isEmpty()) {
            s.pendingRewards.computeIfAbsent(owner, k -> new ArrayList<>()).add(new ChallengeDef.RewardItem(BASE_CORE, 1));
         }

         level.sendParticles(ParticleTypes.END_ROD, (double)pos.getX() + 0.5, (double)(pos.getY() + 1), (double)pos.getZ() + 0.5, 40, 0.4, 0.6, 0.4, 0.05);
         level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.0F);
         TowersMod.LOGGER
            .info("Tower {}: the offering chest at {} is now a base for {}", new Object[]{id, pos.toShortString(), s.names.getOrDefault(owner, "nobody")});
         return true;
      } else {
         return false;
      }
   }

   public static void sealPortalRoom(ServerLevel level, String id, BoundingBox room) {
      ChallengeState s = state(level.getServer(), id).orElse(null);
      if (s != null && !s.complete && room != null) {
         List<BlockPos> gates = new ArrayList<>(s.gates);

         for (BlockPos p : BlockPos.betweenClosed(room.minX(), room.minY(), room.minZ(), room.maxX(), room.maxY(), room.maxZ())) {
            boolean shell = p.getX() == room.minX()
               || p.getX() == room.maxX()
               || p.getY() == room.minY()
               || p.getY() == room.maxY()
               || p.getZ() == room.minZ()
               || p.getZ() == room.maxZ();
            if (shell) {
               BlockState st = level.getBlockState(p);
               if ((st.getCollisionShape(level, p).isEmpty() || st.getBlock() instanceof DoorBlock)
                  && !st.is(Blocks.END_PORTAL_FRAME)
                  && !st.is(Blocks.END_PORTAL)) {
                  level.setBlock(p, Blocks.REINFORCED_DEEPSLATE.defaultBlockState(), 2);
                  gates.add(p.immutable());
               }
            }
         }

         s.portalRoom = List.of(room.minX(), room.minY(), room.minZ(), room.maxX(), room.maxY(), room.maxZ());
         s.gates = List.copyOf(gates);
         save(level.getServer(), id, s);
         TowersMod.LOGGER.info("Tower {}: sealed the End portal room at {} ({} openings)", new Object[]{id, room, gates.size()});
      }
   }

   private static void openPortalRoom(MinecraftServer server, ServerLevel level, ChallengeDef def, ChallengeState s) {
      if (!s.gates.isEmpty() && level != null) {
         for (BlockPos p : s.gates) {
            if (level.getBlockState(p).is(Blocks.REINFORCED_DEEPSLATE)) {
               level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
         }

         BoundingBox room = s.portalRoomBox();
         if (room != null) {
            BlockPos c = room.getCenter();
            level.playSound(null, c, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 1.0F, 1.0F);
         }

         TowersMod.LOGGER.info("Tower {}: the End portal room is open", def.name());
         s.gates = List.of();
         server.getPlayerList()
            .broadcastSystemMessage(
               Component.literal("The way to the End beneath " + def.name() + " is open!")
                  .withStyle(new ChatFormatting[]{ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD}),
               false
            );
      }
   }

   static Map<String, ChallengeState> states(MinecraftServer server) {
      return (Map<String, ChallengeState>)server.overworld().getAttachedOrCreate(STATE);
   }

   public static Optional<ChallengeState> state(MinecraftServer server, String id) {
      return Optional.ofNullable(states(server).get(id));
   }

   static void save(MinecraftServer server, String id, ChallengeState state) {
      Map<String, ChallengeState> copy = new HashMap<>(states(server));
      if (state == null) {
         copy.remove(id);
      } else {
         copy.put(id, state);
      }

      server.overworld().setAttached(STATE, copy);
   }

   static Optional<ChallengeDef> defOf(String towerId, ChallengeState s) {
      return ChallengeDef.get(s.challenge.isEmpty() ? towerId : s.challenge);
   }

   private static boolean inLevel(ChallengeState s, ServerLevel level) {
      return s.dimension.equals(level.dimension().identifier().toString());
   }

   private static ServerLevel level(MinecraftServer server, ChallengeState s) {
      return server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(s.dimension)));
   }

   private static String chestAt(ServerLevel level, BlockPos pos) {
      for (Entry<String, ChallengeState> e : states(level.getServer()).entrySet()) {
         Optional<ChallengeDef> def = defOf(e.getKey(), e.getValue());
         if (def.isPresent() && inLevel(e.getValue(), level) && e.getValue().origin.offset(def.get().chest()).equals(pos)) {
            return e.getKey();
         }
      }

      return null;
   }

   private static Optional<StructureTemplate> template(ServerLevel level, Identifier id) {
      return level.getServer().getStructureTemplateManager().get(id);
   }

   static BoundingBox box(ServerLevel level, ChallengeDef def, ChallengeState s) {
      Vec3i size = template(level, def.finished()).<Vec3i>map(StructureTemplate::getSize).orElse(new Vec3i(1, 1, 1));
      BlockPos o = s.origin;
      return new BoundingBox(o.getX(), o.getY(), o.getZ(), o.getX() + size.getX() - 1, o.getY() + size.getY() - 1, o.getZ() + size.getZ() - 1);
   }

   public static boolean place(ServerLevel level, ChallengeDef def, BlockPos origin, String towerId) {
      Optional<StructureTemplate> ruined = template(level, def.ruined());
      if (ruined.isEmpty()) {
         TowersMod.LOGGER.error("Challenge {}: structure {} not found", def.id(), def.ruined());
         return false;
      } else {
         Vec3i size = ruined.get().getSize();
         BlockPos center = origin.offset(size.getX() / 2, 1, size.getZ() / 2);
         TowerPalette palette = TowerPalette.pick(level.getBiome(center));
         BlockState foundation = palette.foundationState();

         for (int x = 0; x < size.getX(); x++) {
            for (int z = 0; z < size.getZ(); z++) {
               MutableBlockPos p = new MutableBlockPos(origin.getX() + x, origin.getY() - 1, origin.getZ() + z);
               int d = 0;

               while (d < 64 && p.getY() > level.getMinY()) {
                  BlockState st = level.getBlockState(p);
                  if (!st.canBeReplaced() && !st.is(BlockTags.LEAVES) && !st.is(BlockTags.LOGS) && st.getFluidState().isEmpty()) {
                     break;
                  }

                  level.setBlock(p, foundation, 2);
                  d++;
                  p.move(0, -1, 0);
               }
            }
         }

         ruined.get().placeInWorld(level, origin, origin, palette.settings(), level.getRandom(), 2);
         save(level.getServer(), towerId, ChallengeState.fresh(def.id(), palette.id(), level.dimension().identifier().toString(), origin));
         TowersMod.LOGGER
            .info(
               "Tower {} ({}) placed at {} in {} with the {} palette",
               new Object[]{
                  towerId, def.id(), origin.toShortString(), level.getBiome(center).unwrapKey().map(k -> k.identifier().toString()).orElse("?"), palette.id()
               }
            );
         return true;
      }
   }

   private static void buildStage(ServerLevel level, ChallengeDef def, ChallengeState s, int stage) {
      Optional<StructureTemplate> finished = template(level, def.finished());
      if (finished.isEmpty()) {
         TowersMod.LOGGER.error("Challenge {}: structure {} not found", def.id(), def.finished());
      } else {
         BoundingBox full = box(level, def, s);
         int height = full.getYSpan();
         int top = s.origin.getY() + (int)Math.ceil((double)height * (double)stage / (double)def.stages()) - 1;
         BoundingBox clip = new BoundingBox(full.minX(), full.minY(), full.minZ(), full.maxX(), Math.min(top, full.maxY()), full.maxZ());
         finished.get().placeInWorld(level, s.origin, s.origin, TowerPalette.get(s.palette).settings().setBoundingBox(clip), level.getRandom(), 2);
         BlockPos c = full.getCenter();
         level.sendParticles(
            ParticleTypes.CLOUD,
            (double)c.getX() + 0.5,
            (double)top,
            (double)c.getZ() + 0.5,
            120,
            (double)full.getXSpan() / 2.0,
            1.0,
            (double)full.getZSpan() / 2.0,
            0.05
         );
         level.playSound(null, c, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1.5F, 0.7F);
      }
   }

   private static void open(ServerPlayer player, String id) {
      ChallengeState s = state(player.level().getServer(), id).orElseThrow();
      ChallengeDef def = defOf(id, s).orElseThrow();
      if (s.complete) {
         player.sendSystemMessage(Component.literal(def.name() + " is complete! Thank you to everyone who helped.").withStyle(ChatFormatting.GOLD));
         sendLeaderboard(player.createCommandSourceStack(), id, def, s, player);
      } else {
         Component title = Component.literal("Offer " + def.unit() + ": " + s.total + "/" + def.goal());
         player.openMenu(new SimpleMenuProvider((syncId, inv, p) -> new TributeMenu(syncId, inv, new SimpleContainer(27), id), title));
      }
   }

   static void offer(ServerPlayer player, String id, List<ItemStack> stacks) {
      ChallengeState s = state(player.level().getServer(), id).orElse(null);
      ChallengeDef def = s == null ? null : defOf(id, s).orElse(null);
      int given = 0;
      List<ItemStack> back = new ArrayList<>();

      for (ItemStack stack : stacks) {
         int worth = def == null ? 0 : def.worth(stack);
         if (worth > 0 && s != null && !s.complete) {
            int remaining = def.goal() - s.total - given;
            int take = Math.min(stack.getCount(), Math.max(0, (remaining + worth - 1) / worth));
            given += take * worth;
            if (take > 0) {
               ChallengeState.addStacks(s.deposits.computeIfAbsent(player.getUUID().toString(), k -> new ArrayList<>()), stack.copyWithCount(take));
            }

            if (take < stack.getCount()) {
               back.add(stack.copyWithCount(stack.getCount() - take));
            }
         } else {
            back.add(stack);
         }
      }

      for (ItemStack b : back) {
         player.getInventory().placeItemBackInInventory(b, Prediction.SERVER_ONLY);
      }

      if (given > 0) {
         if (!back.isEmpty()) {
            player.sendSystemMessage(Component.literal("Only " + def.unit() + " count; the rest went back in your inventory.").withStyle(ChatFormatting.GRAY));
         }

         credit(player.level().getServer(), id, player.getUUID().toString(), player.getPlainTextName(), given);
      } else if (!back.isEmpty() && def != null) {
         player.sendOverlayMessage(Component.literal("This chest only takes " + def.unit() + "."));
      }
   }

   public static void credit(MinecraftServer server, String id, String key, String name, int amount) {
      ChallengeState s = state(server, id).orElse(null);
      ChallengeDef def = s == null ? null : defOf(id, s).orElse(null);
      if (def != null && s != null && !s.complete && amount > 0) {
         ServerLevel level = level(server, s);
         s.total = Math.min(def.goal(), s.total + amount);
         s.donations.merge(key, amount, Integer::sum);
         s.names.put(key, name);
         TowersMod.LOGGER.info("Challenge {}: {} gave {} {} ({}/{})", new Object[]{id, name, amount, def.unit(), s.total, def.goal()});
         server.getPlayerList()
            .broadcastSystemMessage(
               Component.literal(name + " offered " + amount + " " + def.unit() + " to " + def.name() + " (" + s.total + "/" + def.goal() + ")")
                  .withStyle(ChatFormatting.YELLOW),
               false
            );

         while (s.stage < def.stages() && s.total >= def.threshold(s.stage + 1)) {
            s.stage++;
            if (level != null) {
               buildStage(level, def, s, s.stage);
            }

            TowersMod.LOGGER.info("Challenge {}: stage {}/{} built", new Object[]{id, s.stage, def.stages()});
            if (s.stage < def.stages()) {
               server.getPlayerList()
                  .broadcastSystemMessage(
                     Component.literal(def.name() + " rises! Stage " + s.stage + " of " + def.stages() + " rebuilt.")
                        .withStyle(new ChatFormatting[]{ChatFormatting.AQUA, ChatFormatting.BOLD}),
                     false
                  );
            }
         }

         if (s.total >= def.goal()) {
            s.complete = true;
            save(server, id, s);
            complete(server, level, id, def, s);
         } else {
            save(server, id, s);
         }
      }
   }

   private static void complete(MinecraftServer server, ServerLevel level, String id, ChallengeDef def, ChallengeState s) {
      List<String> ranking = s.ranking();
      openPortalRoom(server, level, def, s);
      if (!ranking.isEmpty()) {
         s.owner = ranking.getFirst();
         server.getPlayerList()
            .broadcastSystemMessage(
               Component.literal(
                     s.names.getOrDefault(s.owner, "?")
                        + " claims "
                        + def.name()
                        + " as its top donor. Only they and their guild can build in and around it now."
                  )
                  .withStyle(ChatFormatting.GOLD),
               false
            );
      }

      if (makeBase(level, id, def, s) && !s.owner.isEmpty()) {
         server.getPlayerList()
            .broadcastSystemMessage(
               Component.literal(
                     "The offering chest of "
                        + def.name()
                        + " has become a Base Core for "
                        + s.names.getOrDefault(s.owner, "?")
                        + ", who also gets a Base Core of their own to place anywhere."
                  )
                  .withStyle(ChatFormatting.GOLD),
               false
            );
      }

      s.deposits.forEach((keyx, items) -> items.forEach(it -> ChallengeState.addStacks(s.returns.computeIfAbsent(keyx, k -> new ArrayList<>()), it)));
      if (!s.deposits.isEmpty()) {
         server.getPlayerList()
            .broadcastSystemMessage(
               Component.literal("Everything offered to " + def.name() + " goes back to the players who gave it.").withStyle(ChatFormatting.YELLOW), false
            );
      }

      s.deposits.clear();
      TowersMod.LOGGER
         .info(
            "Challenge {} complete. Top donors: {}",
            id,
            String.join(", ", ranking.stream().limit(5L).map(k -> s.names.getOrDefault(k, k) + " " + s.donations.get(k)).toList())
         );
      server.getPlayerList()
         .broadcastSystemMessage(
            Component.literal(def.name() + " has been fully rebuilt! Thank you all!").withStyle(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD}),
            false
         );

      for (ServerPlayer p : server.getPlayerList().getPlayers()) {
         p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(def.name()).withStyle(ChatFormatting.GOLD)));
         p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("has been rebuilt!")));
         p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1.0F, 1.0F);
      }

      if (level != null) {
         BoundingBox b = box(level, def, s);
         RandomSource rnd = level.getRandom();
         int[] colors = new int[]{16766720, 16733491, 5614335, 7864183, 16777215};

         for (int i = 0; i < 12; i++) {
            ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
            rocket.set(
               DataComponents.FIREWORKS,
               new Fireworks(
                  1 + rnd.nextInt(2),
                  List.of(
                     new FireworkExplosion(
                        Shape.values()[rnd.nextInt(Shape.values().length)],
                        IntList.of(colors[rnd.nextInt(colors.length)], colors[rnd.nextInt(colors.length)]),
                        IntList.of(16777215),
                        true,
                        true
                     )
                  )
               )
            );
            double x = (double)b.minX() + rnd.nextDouble() * (double)b.getXSpan();
            double z = (double)b.minZ() + rnd.nextDouble() * (double)b.getZSpan();
            level.addFreshEntity(new FireworkRocketEntity(level, x, (double)(b.maxY() + 1), z, rocket));
         }
      }

      for (ChallengeDef.Reward r : def.rewards()) {
         for (String key : r.rank() == 0 ? ranking : (r.rank() <= ranking.size() ? List.of(ranking.get(r.rank() - 1)) : List.of())) {
            s.pendingRewards.computeIfAbsent(key, k -> new ArrayList<>()).addAll(r.items());
            if (r.rank() > 0) {
               server.getPlayerList()
                  .broadcastSystemMessage(
                     Component.literal(
                           "#" + r.rank() + " " + s.names.getOrDefault(key, "?") + " with " + s.donations.get(key) + " " + def.unit() + " earns a reward!"
                        )
                        .withStyle(ChatFormatting.GOLD),
                     false
                  );
            }
         }
      }

      save(server, id, s);

      for (ServerPlayer p : server.getPlayerList().getPlayers()) {
         deliverPending(p);
      }
   }

   static void deliverPending(ServerPlayer player) {
      MinecraftServer server = player.level().getServer();
      String key = player.getUUID().toString();

      for (Entry<String, ChallengeState> e : states(server).entrySet()) {
         List<ItemStack> back = e.getValue().returns.remove(key);
         if (back != null && !back.isEmpty()) {
            int count = 0;

            for (ItemStack it : back) {
               count += it.getCount();
               player.getInventory().placeItemBackInInventory(it.copy(), Prediction.SERVER_ONLY);
            }

            String name = defOf(e.getKey(), e.getValue()).map(ChallengeDef::name).orElse(e.getKey());
            player.sendSystemMessage(
               Component.literal("The " + count + " items you offered to " + name + " came back to you (anything that didn't fit is at your feet).")
                  .withStyle(ChatFormatting.GOLD)
            );
            save(server, e.getKey(), e.getValue());
         }

         List<ChallengeDef.RewardItem> items = e.getValue().pendingRewards.remove(key);
         if (items != null && !items.isEmpty()) {
            for (ChallengeDef.RewardItem it : items) {
               Item item = (Item)BuiltInRegistries.ITEM.getValue(it.item());
               player.getInventory().placeItemBackInInventory(new ItemStack(item, it.count()), Prediction.SERVER_ONLY);
            }

            String name = defOf(e.getKey(), e.getValue()).map(ChallengeDef::name).orElse(e.getKey());
            player.sendSystemMessage(Component.literal("You received your reward for helping rebuild " + name + "!").withStyle(ChatFormatting.GOLD));
            save(server, e.getKey(), e.getValue());
         }
      }
   }

   static void sendLeaderboard(CommandSourceStack source, String id, ChallengeDef def, ChallengeState s, ServerPlayer viewer) {
      int pct = (int)(100L * (long)s.total / (long)def.goal());
      int bars = pct / 5;
      MutableComponent text = Component.literal("\n" + def.name())
         .withStyle(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD})
         .append(Component.literal("  " + id + " at " + s.origin.getX() + ", " + s.origin.getZ() + "\n").withStyle(ChatFormatting.DARK_GRAY))
         .append(Component.literal("█".repeat(bars)).withStyle(ChatFormatting.GREEN))
         .append(Component.literal("█".repeat(20 - bars)).withStyle(ChatFormatting.DARK_GRAY))
         .append(
            Component.literal(
                  " "
                     + s.total
                     + "/"
                     + def.goal()
                     + " "
                     + def.unit()
                     + " ("
                     + pct
                     + "%), stage "
                     + s.stage
                     + "/"
                     + def.stages()
                     + (s.complete ? " - COMPLETE" : "")
               )
               .withStyle(ChatFormatting.WHITE)
         );
      String owner = s.controller();
      text.append(
         Component.literal(
               owner.isEmpty()
                  ? "\nProtected until rebuilt; the top donor then claims it."
                  : "\nClaimed by " + s.names.getOrDefault(owner, "?") + " (only they and their guild can build around it)."
            )
            .withStyle(ChatFormatting.LIGHT_PURPLE)
      );
      List<String> ranking = s.ranking();
      if (ranking.isEmpty()) {
         text.append(Component.literal("\nNo offerings yet. Be the first!").withStyle(ChatFormatting.GRAY));
      }

      for (int i = 0; i < Math.min(10, ranking.size()); i++) {
         String k = ranking.get(i);
         ChatFormatting color = i == 0 ? ChatFormatting.GOLD : (i == 1 ? ChatFormatting.GRAY : (i == 2 ? ChatFormatting.RED : ChatFormatting.WHITE));
         text.append(Component.literal("\n " + (i + 1) + ". " + s.names.getOrDefault(k, "?") + " - " + s.donations.get(k)).withStyle(color));
      }

      if (viewer != null) {
         String me = viewer.getUUID().toString();
         int rank = s.rankOf(me);
         text.append(
            Component.literal(rank > 0 ? "\nYou: " + s.donations.get(me) + " " + def.unit() + " (#" + rank + ")" : "\nYou haven't offered anything yet.")
               .withStyle(ChatFormatting.AQUA)
         );
      }

      source.sendSuccess(() -> text, false);
      TowersMod.LOGGER
         .info(
            "Challenge {} leaderboard: {}/{} stage {}; {}",
            new Object[]{
               id, s.total, def.goal(), s.stage, String.join(", ", ranking.stream().map(k -> s.names.getOrDefault(k, k) + " " + s.donations.get(k)).toList())
            }
         );
   }

   public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
      SuggestionProvider<CommandSourceStack> challenges = (ctx, b) -> SharedSuggestionProvider.suggest(ChallengeDef.all().keySet(), b);
      SuggestionProvider<CommandSourceStack> towers = (ctx, b) -> SharedSuggestionProvider.suggest(
            states(((CommandSourceStack)ctx.getSource()).getServer()).keySet(), b
         );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                                 "challenge"
                              )
                              .executes(ctx -> {
                                 CommandSourceStack src = (CommandSourceStack)ctx.getSource();
                                 String best = null;
                                 double bestDist = Double.MAX_VALUE;

                                 for (Entry<String, ChallengeState> e : states(src.getServer()).entrySet()) {
                                    if (inLevel(e.getValue(), src.getLevel()) && !defOf(e.getKey(), e.getValue()).isEmpty()) {
                                       double d = e.getValue().origin.distToCenterSqr(src.getPosition());
                                       if (d < bestDist) {
                                          bestDist = d;
                                          best = e.getKey();
                                       }
                                    }
                                 }

                                 if (best == null) {
                                    src.sendSuccess(
                                       () -> Component.literal("No towers have been found yet. They stand above the strongholds: follow an Eye of Ender."),
                                       false
                                    );
                                    return 0;
                                 } else {
                                    ChallengeState s = states(src.getServer()).get(best);
                                    sendLeaderboard(src, best, defOf(best, s).get(), s, src.getPlayer());
                                    int count = states(src.getServer()).size();
                                    if (count > 1) {
                                       src.sendSuccess(
                                          () -> Component.literal(count + " towers found so far: /challenge list").withStyle(ChatFormatting.GRAY), false
                                       );
                                    }

                                    return 1;
                                 }
                              }))
                           .then(
                              Commands.literal("list")
                                 .executes(
                                    ctx -> {
                                       CommandSourceStack src = (CommandSourceStack)ctx.getSource();
                                       MutableComponent text = Component.literal("Towers found so far:").withStyle(ChatFormatting.GOLD);
                                       List<String> ids = new ArrayList<>(states(src.getServer()).keySet());
                                       ids.sort(Comparator.naturalOrder());

                                       for (String id : ids) {
                                          ChallengeState s = states(src.getServer()).get(id);
                                          Optional<ChallengeDef> def = defOf(id, s);
                                          if (!def.isEmpty()) {
                                             text.append(
                                                Component.literal(
                                                      "\n "
                                                         + def.get().name()
                                                         + " ("
                                                         + id
                                                         + ") at "
                                                         + s.origin.getX()
                                                         + ", "
                                                         + s.origin.getZ()
                                                         + ": "
                                                         + (s.complete ? "complete" : s.total + "/" + def.get().goal() + " " + def.get().unit())
                                                   )
                                                   .withStyle(s.complete ? ChatFormatting.GREEN : ChatFormatting.WHITE)
                                             );
                                          }
                                       }

                                       if (ids.isEmpty()) {
                                          text.append(Component.literal("\n none yet").withStyle(ChatFormatting.GRAY));
                                       }

                                       src.sendSuccess(() -> text, false);
                                       return ids.size();
                                    }
                                 )
                           ))
                        .then(Commands.literal("top").then(Commands.argument("tower", StringArgumentType.word()).suggests(towers).executes(ctx -> {
                           String id = StringArgumentType.getString(ctx, "tower");
                           Optional<ChallengeState> s = state(((CommandSourceStack)ctx.getSource()).getServer(), id);
                           Optional<ChallengeDef> def = s.flatMap(st -> defOf(id, st));
                           if (def.isEmpty()) {
                              ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("No tower called " + id + "."));
                              return 0;
                           } else {
                              sendLeaderboard((CommandSourceStack)ctx.getSource(), id, def.get(), s.get(), ((CommandSourceStack)ctx.getSource()).getPlayer());
                              return 1;
                           }
                        }))))
                     .then(
                        ((LiteralArgumentBuilder)Commands.literal("place").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)))
                           .then(
                              ((RequiredArgumentBuilder)Commands.argument("challenge", StringArgumentType.word())
                                    .suggests(challenges)
                                    .executes(
                                       ctx -> placeCommand(
                                             (CommandSourceStack)ctx.getSource(),
                                             StringArgumentType.getString(ctx, "challenge"),
                                             StringArgumentType.getString(ctx, "challenge")
                                          )
                                    ))
                                 .then(
                                    Commands.argument("tower", StringArgumentType.word())
                                       .executes(
                                          ctx -> placeCommand(
                                                (CommandSourceStack)ctx.getSource(),
                                                StringArgumentType.getString(ctx, "challenge"),
                                                StringArgumentType.getString(ctx, "tower")
                                             )
                                       )
                                 )
                           )
                     ))
                  .then(
                     ((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("generate")
                              .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)))
                           .then(Commands.literal("nearest").executes(ctx -> {
                              CommandSourceStack src = (CommandSourceStack)ctx.getSource();
                              BlockPos found = src.getLevel()
                                 .findNearestMapStructure(StructureTags.EYE_OF_ENDER_LOCATED, BlockPos.containing(src.getPosition()), 100, false);
                              if (found == null) {
                                 src.sendFailure(Component.literal("No stronghold nearby."));
                                 return 0;
                              } else {
                                 return generateCommand(src, StrongholdTowers.indexOf(src.getLevel(), ChunkPos.containing(found)));
                              }
                           })))
                        .then(
                           Commands.argument("number", IntegerArgumentType.integer(0))
                              .executes(ctx -> generateCommand((CommandSourceStack)ctx.getSource(), IntegerArgumentType.getInteger(ctx, "number")))
                        )
                  ))
               .then(
                  ((LiteralArgumentBuilder)Commands.literal("remove").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)))
                     .then(Commands.argument("tower", StringArgumentType.word()).suggests(towers).executes(ctx -> {
                        String id = StringArgumentType.getString(ctx, "tower");
                        save(((CommandSourceStack)ctx.getSource()).getServer(), id, null);
                        ((CommandSourceStack)ctx.getSource())
                           .sendSuccess(() -> Component.literal("Stopped " + id + " (the blocks stay where they are)."), true);
                        return 1;
                     }))
               ))
            .then(
               ((LiteralArgumentBuilder)Commands.literal("credit").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)))
                  .then(
                     Commands.argument("tower", StringArgumentType.word())
                        .suggests(towers)
                        .then(
                           Commands.argument("player", StringArgumentType.word())
                              .then(
                                 Commands.argument("amount", IntegerArgumentType.integer(1))
                                    .executes(
                                       ctx -> {
                                          String id = StringArgumentType.getString(ctx, "tower");
                                          String name = StringArgumentType.getString(ctx, "player");
                                          MinecraftServer server = ((CommandSourceStack)ctx.getSource()).getServer();
                                          if (state(server, id).isEmpty()) {
                                             ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("No tower called " + id + "."));
                                             return 0;
                                          } else {
                                             ServerPlayer online = server.getPlayerList().getPlayerByName(name);
                                             String key = online != null
                                                ? online.getUUID().toString()
                                                : UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)).toString();
                                             credit(
                                                server,
                                                id,
                                                key,
                                                online != null ? online.getPlainTextName() : name,
                                                IntegerArgumentType.getInteger(ctx, "amount")
                                             );
                                             return 1;
                                          }
                                       }
                                    )
                              )
                        )
                  )
            )
      );
   }

   private static int placeCommand(CommandSourceStack src, String challenge, String tower) {
      Optional<ChallengeDef> def = ChallengeDef.get(challenge);
      if (def.isEmpty()) {
         src.sendFailure(Component.literal("No challenge called " + challenge + "."));
         return 0;
      } else {
         BlockPos origin = BlockPos.containing(src.getPosition());
         if (!place(src.getLevel(), def.get(), origin, tower)) {
            src.sendFailure(Component.literal("Couldn't find the structure files for " + challenge + "."));
            return 0;
         } else {
            src.sendSuccess(
               () -> Component.literal(
                     "Placed "
                        + def.get().name()
                        + " as "
                        + tower
                        + " at "
                        + origin.toShortString()
                        + ". Its offering chest is at "
                        + origin.offset(def.get().chest()).toShortString()
                        + "."
                  ),
               true
            );
            return 1;
         }
      }
   }

   private static int generateCommand(CommandSourceStack src, int index) {
      BlockPos pos = StrongholdTowers.generate(src.getLevel(), index);
      if (pos == null) {
         src.sendFailure(Component.literal("Couldn't build a tower for stronghold " + index + "."));
         return 0;
      } else {
         src.sendSuccess(() -> Component.literal("Tower " + StrongholdTowers.towerId(index) + " stands at " + pos.toShortString() + "."), true);
         return 1;
      }
   }
}
