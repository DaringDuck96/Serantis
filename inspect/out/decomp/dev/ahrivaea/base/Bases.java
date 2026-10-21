package dev.ahrivaea.base;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.battle.ArenaReturn;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Job;
import dev.ahrivaea.creature.Species;
import dev.ahrivaea.creature.WildSpawner;
import dev.ahrivaea.party.CaughtCreature;
import dev.ahrivaea.party.Party;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.function.Function;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.ModifyOutput;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AllowDamage;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.Load;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopped;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.After;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.Before;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.ClickEvent.RunCommand;
import net.minecraft.network.chat.HoverEvent.ShowText;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.phys.Vec3;

public final class Bases {
   public static final Block BASE_CORE = block(
      "base_core", BaseCoreBlock::new, Properties.of().strength(4.0F, 1200.0F).sound(SoundType.AMETHYST).lightLevel(s -> 10)
   );
   public static final Block MINING_POST = block("mining_post", Block::new, Properties.of().strength(2.0F, 6.0F).sound(SoundType.WOOD));
   public static final Block CREATURE_DYNAMO = block(
      "creature_dynamo",
      CreatureDynamoBlock::new,
      Properties.of().strength(3.0F, 6.0F).sound(SoundType.COPPER).lightLevel(s -> s.getValue(CreatureDynamoBlock.POWERED) ? 9 : 0)
   );
   public static final BlockEntityType<BaseCoreBlockEntity> BASE_CORE_ENTITY = (BlockEntityType<BaseCoreBlockEntity>)Registry.register(
      BuiltInRegistries.BLOCK_ENTITY_TYPE, Ahrivaea.id("base_core"), new BlockEntityType(BaseCoreBlockEntity::new, Set.of(BASE_CORE))
   );
   public static final AttachmentType<BlockPos> WORKER_OF = AttachmentRegistry.create(Ahrivaea.id("worker_of"), b -> b.persistent(BlockPos.CODEC));
   public static final TicketType BASE_TICKET = (TicketType)Registry.register(BuiltInRegistries.TICKET_TYPE, Ahrivaea.id("base"), new TicketType(0L, 14));
   public static final AttachmentType<Map<String, Bases.Loader>> LOADERS = AttachmentRegistry.create(
      Ahrivaea.id("base_loaders"), b -> b.persistent(Codec.unboundedMap(Codec.STRING, Bases.Loader.CODEC)).initializer(Map::of)
   );
   private static final Map<String, Bases.Loader> TICKETS = new HashMap<>();
   private static final Map<ResourceKey<Level>, Set<BlockPos>> CORES = new HashMap<>();

   private Bases() {
   }

   private static Block block(String name, Function<Properties, Block> make, Properties props) {
      ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Ahrivaea.id(name));
      Block b = (Block)Registry.register(BuiltInRegistries.BLOCK, key, make.apply(props.setId(key)));
      ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Ahrivaea.id(name));
      Registry.register(
         BuiltInRegistries.ITEM, itemKey, new BlockItem(b, new net.minecraft.world.item.Item.Properties().setId(itemKey).useBlockDescriptionPrefix())
      );
      return b;
   }

   private static Map<String, Bases.Loader> loaders(MinecraftServer server) {
      return (Map<String, Bases.Loader>)server.overworld().getAttachedOrCreate(LOADERS);
   }

   static void updateLoader(ServerLevel level, BaseCoreBlockEntity be) {
      if (be.owner() != null) {
         Bases.Loader l = new Bases.Loader(level.dimension().identifier().toString(), be.getBlockPos().immutable(), be.owner(), be.chunks(), be.chunkLoading());
         Map<String, Bases.Loader> all = loaders(level.getServer());
         if (!l.equals(all.get(l.key()))) {
            Map<String, Bases.Loader> copy = new HashMap<>(all);
            copy.put(l.key(), l);
            level.getServer().overworld().setAttached(LOADERS, copy);
            refreshTickets(level.getServer());
         }
      }
   }

   static void forgetLoader(ServerLevel level, BlockPos pos) {
      String key = level.dimension().identifier().toString() + "|" + pos.asLong();
      Map<String, Bases.Loader> all = loaders(level.getServer());
      if (all.containsKey(key)) {
         Map<String, Bases.Loader> copy = new HashMap<>(all);
         copy.remove(key);
         level.getServer().overworld().setAttached(LOADERS, copy);
         refreshTickets(level.getServer());
      }
   }

   static void refreshTickets(MinecraftServer server) {
      Map<String, Bases.Loader> want = new HashMap<>();

      for (Bases.Loader l : loaders(server).values()) {
         if (l.on() && server.getPlayerList().getPlayer(l.owner()) != null) {
            want.put(l.key(), l);
         }
      }

      for (Entry<String, Bases.Loader> e : new ArrayList<>(TICKETS.entrySet())) {
         if (!e.getValue().equals(want.get(e.getKey()))) {
            ServerLevel lv = levelOf(server, e.getValue());
            if (lv != null) {
               lv.getChunkSource().removeTicketWithRadius(BASE_TICKET, ChunkPos.containing(e.getValue().pos()), e.getValue().ticketRadius());
            }

            TICKETS.remove(e.getKey());
         }
      }

      for (Bases.Loader lx : want.values()) {
         if (!TICKETS.containsKey(lx.key())) {
            ServerLevel lv = levelOf(server, lx);
            if (lv != null) {
               lv.getChunkSource().addTicketWithRadius(BASE_TICKET, ChunkPos.containing(lx.pos()), lx.ticketRadius());
               TICKETS.put(lx.key(), lx);
            }
         }
      }
   }

   private static ServerLevel levelOf(MinecraftServer server, Bases.Loader l) {
      return server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(l.dimension())));
   }

   public static int loadedBases(ServerPlayer p) {
      String me = p.getUUID().toString();
      return (int)TICKETS.values().stream().filter(l -> l.owner().toString().equals(me)).count();
   }

   static void index(ServerLevel level, BlockPos pos) {
      CORES.computeIfAbsent(level.dimension(), k -> new HashSet<>()).add(pos.immutable());
   }

   static void unindex(ServerLevel level, BlockPos pos) {
      Set<BlockPos> set = CORES.get(level.dimension());
      if (set != null) {
         set.remove(pos);
      }
   }

   public static BaseCoreBlockEntity coreAt(ServerLevel level, BlockPos pos) {
      return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof BaseCoreBlockEntity be ? be : null;
   }

   public static boolean inAnyBaseColumn(ServerLevel level, BlockPos pos) {
      for (BlockPos c : CORES.getOrDefault(level.dimension(), Set.of())) {
         BaseCoreBlockEntity be = coreAt(level, c);
         if (be != null && pos.getX() >= be.minX() && pos.getX() <= be.maxX() && pos.getZ() >= be.minZ() && pos.getZ() <= be.maxZ()) {
            return true;
         }
      }

      return false;
   }

   public static BaseCoreBlockEntity baseAt(ServerLevel level, BlockPos pos) {
      for (BlockPos c : CORES.getOrDefault(level.dimension(), Set.of())) {
         BaseCoreBlockEntity be = coreAt(level, c);
         if (be != null && pos.getX() >= be.minX() && pos.getX() <= be.maxX() && pos.getZ() >= be.minZ() && pos.getZ() <= be.maxZ()) {
            return be;
         }
      }

      return null;
   }

   public static boolean inAnyBase(ServerLevel level, BlockPos pos) {
      for (BlockPos c : CORES.getOrDefault(level.dimension(), Set.of())) {
         BaseCoreBlockEntity be = coreAt(level, c);
         if (be != null && be.contains(pos)) {
            return true;
         }
      }

      return false;
   }

   public static void init() {
      PayloadTypeRegistry.clientboundPlay().register(Bases.StatePayload.TYPE, Bases.StatePayload.CODEC);
      PayloadTypeRegistry.serverboundPlay().register(Bases.ActionPayload.TYPE, Bases.ActionPayload.CODEC);
      ServerPlayNetworking.registerGlobalReceiver(Bases.ActionPayload.TYPE, (payload, ctx) -> handle(ctx.player(), payload.json()));
      CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register((ModifyOutput)out -> {
         out.accept(BASE_CORE);
         out.accept(MINING_POST);
         out.accept(CREATURE_DYNAMO);
      });
      WildSpawner.BLOCKED.add(Bases::inAnyBase);
      WildSpawner.BLOCKED.add(Bases::inAnyBaseColumn);
      PlayerBlockBreakEvents.AFTER.register((After)(level, player, pos, state, be) -> {
         if (level instanceof ServerLevel sl) {
            PlacedBlocks.removed(sl, pos);
         }
      });
      UseBlockCallback.EVENT
         .register(
            (UseBlockCallback)(player, level, hand, hit) -> {
               if (level instanceof ServerLevel sl
                  && player instanceof ServerPlayer sp
                  && hand == InteractionHand.MAIN_HAND
                  && sl.getBlockState(hit.getBlockPos()).is(MINING_POST)) {
                  quarryInfo(sp, hit.getBlockPos());
                  return InteractionResult.SUCCESS;
               }

               return InteractionResult.PASS;
            }
         );
      CommandRegistrationCallback.EVENT
         .register(
            (CommandRegistrationCallback)(d, reg, env) -> d.register(
                  (LiteralArgumentBuilder)Commands.literal("quarry")
                     .then(
                        ((RequiredArgumentBuilder)((RequiredArgumentBuilder)((RequiredArgumentBuilder)Commands.argument("post", BlockPosArgument.blockPos())
                                    .then(
                                       Commands.literal("pause")
                                          .executes(
                                             ctx -> quarryCommand((CommandSourceStack)ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "post"), "pause")
                                          )
                                    ))
                                 .then(
                                    Commands.literal("structures")
                                       .executes(
                                          ctx -> quarryCommand((CommandSourceStack)ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "post"), "structures")
                                       )
                                 ))
                              .then(
                                 Commands.literal("restart")
                                    .executes(ctx -> quarryCommand((CommandSourceStack)ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "post"), "restart"))
                              ))
                           .executes(ctx -> quarryCommand((CommandSourceStack)ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "post"), "info"))
                     )
               )
         );
      ServerLivingEntityEvents.ALLOW_DAMAGE
         .register((AllowDamage)(entity, source, amount) -> !entity.hasAttached(WORKER_OF) || source.getEntity() != null && Raids.isRaider(source.getEntity()));
      Raids.init();
      ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
         if (server.getTickCount() % 20 == 3) {
            refreshTickets(server);
         }
      });
      ServerLifecycleEvents.SERVER_STOPPED.register((ServerStopped)server -> TICKETS.clear());
      CommandRegistrationCallback.EVENT
         .register(
            (CommandRegistrationCallback)(d, reg, env) -> d.register(
                  (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("base").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)))
                     .then(
                        Commands.literal("raid")
                           .executes(
                              ctx -> {
                                 ServerPlayer p = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();

                                 for (BlockPos c : cores(p.level())) {
                                    BaseCoreBlockEntity be = coreAt(p.level(), c);
                                    if (be != null && be.contains(p.blockPosition())) {
                                       int n = Raids.start(p.level(), be);
                                       ((CommandSourceStack)ctx.getSource())
                                          .sendSuccess(
                                             () -> Component.literal(n > 0 ? "Raid started: " + n + " raiders." : "Couldn't start a raid here."), false
                                          );
                                       return n;
                                    }
                                 }

                                 ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("Stand in a base to raid it."));
                                 return 0;
                              }
                           )
                     )
               )
         );
      PlayerBlockBreakEvents.BEFORE.register((Before)(level, player, pos, state, be) -> {
         if (be instanceof BaseCoreBlockEntity core && !core.workers.isEmpty()) {
            player.sendOverlayMessage(Component.literal("Send this base's " + core.workers.size() + " workers away first (right-click the core)."));
            return false;
         }

         return true;
      });
      ServerEntityEvents.ENTITY_LOAD.register((Load)(entity, level) -> {
         if (entity instanceof CreatureEntity c && c.hasAttached(WORKER_OF)) {
            BlockPos core = (BlockPos)c.getAttached(WORKER_OF);
            if (!level.isLoaded(core)) {
               return;
            }

            BaseCoreBlockEntity be = coreAt(level, core);
            if (be != null && be.isWorker(c.getUUID())) {
               c.capSize(1.25F);
            } else {
               c.discard();
            }

            return;
         }
      });
   }

   public static void quarryInfo(ServerPlayer p, BlockPos post) {
      ServerLevel level = p.level();
      BaseCoreBlockEntity be = baseAt(level, post);
      if (be == null) {
         p.sendSystemMessage(
            Component.literal("This Mining Post isn't in a base. Put it in your base and give it a chest: your miners will dig out the chunk below it.")
         );
      } else if (be.owner() != null && !be.owner().equals(p.getUUID())) {
         p.sendOverlayMessage(Component.literal("This is " + be.ownerName() + "'s quarry."));
      } else {
         Quarry q = be.quarry(post);
         String at = post.getX() + " " + post.getY() + " " + post.getZ();
         int chests = Quarry.chests(level, post).size();
         MutableComponent text = Component.literal("Quarry: ")
            .withStyle(ChatFormatting.GOLD)
            .append(Component.literal(q.describe(level, post)).withStyle(ChatFormatting.WHITE))
            .append(
               Component.literal(
                     "\n"
                        + q.mined
                        + " blocks dug; "
                        + chests
                        + (chests == 1 ? " chest" : " chests")
                        + " by the post"
                        + (chests == 0 ? " (miners need one: put a chest or barrel next to the post)" : "")
                  )
                  .withStyle(ChatFormatting.GRAY)
            )
            .append(
               Component.literal(
                     "\nDigs out this chunk below the post, down to bedrock, leaving a wall around it and a staircase. Never strongholds, spawners or blocks players placed."
                  )
                  .withStyle(ChatFormatting.DARK_GRAY)
            )
            .append(Component.literal("\n"))
            .append(button(q.paused ? "[Resume]" : "[Pause]", "/quarry " + at + " pause"))
            .append(Component.literal(" "))
            .append(button(q.structures ? "[Structures: dug]" : "[Structures: kept]", "/quarry " + at + " structures"))
            .append(Component.literal(" "))
            .append(button("[Restart]", "/quarry " + at + " restart"));
         p.sendSystemMessage(text);
      }
   }

   private static MutableComponent button(String label, String cmd) {
      return Component.literal(label)
         .withStyle(s -> s.withColor(ChatFormatting.AQUA).withClickEvent(new RunCommand(cmd)).withHoverEvent(new ShowText(Component.literal(cmd))));
   }

   private static int quarryCommand(CommandSourceStack src, BlockPos post, String what) throws CommandSyntaxException {
      ServerPlayer p = src.getPlayerOrException();
      ServerLevel level = p.level();
      BaseCoreBlockEntity be = baseAt(level, post);
      if (be != null && level.getBlockState(post).is(MINING_POST) && (be.owner() == null || be.owner().equals(p.getUUID()) || p.isCreative())) {
         Quarry q = be.quarry(post);
         switch (what) {
            case "pause":
               q.paused = !q.paused;
               break;
            case "structures":
               q.structures = !q.structures;
               break;
            case "restart":
               q.phase = 0;
               q.step = 0;
               q.layer = Integer.MIN_VALUE;
         }

         be.setChanged();
         quarryInfo(p, post);
         return 1;
      } else {
         src.sendFailure(Component.literal("That isn't a Mining Post in your base."));
         return 0;
      }
   }

   static void alertNoChest(ServerLevel level, BaseCoreBlockEntity be, BlockPos post, Quarry q) {
      if (level.getGameTime() - q.lastAlert >= 2400L) {
         q.lastAlert = level.getGameTime();
         ServerPlayer owner = be.owner() == null ? null : level.getServer().getPlayerList().getPlayer(be.owner());
         if (owner != null) {
            owner.sendSystemMessage(
               Component.literal(
                     "Your quarry at "
                        + post.getX()
                        + ", "
                        + post.getZ()
                        + " has stopped: the chests by its Mining Post are full (or there are none). Put another chest next to the post."
                  )
                  .withStyle(ChatFormatting.YELLOW)
            );
         }
      }
   }

   static void open(ServerPlayer p, BaseCoreBlockEntity be) {
      if (be.owner() != null && !be.owner().equals(p.getUUID())) {
         p.sendOverlayMessage(Component.literal("This is " + be.ownerName() + "'s base."));
      } else {
         send(p, be);
      }
   }

   public static void send(ServerPlayer p, BaseCoreBlockEntity be) {
      ServerPlayNetworking.send(p, new Bases.StatePayload(view(p, be).toString()));
   }

   public static JsonObject view(ServerPlayer p, BaseCoreBlockEntity be) {
      JsonObject o = new JsonObject();
      BlockPos c = be.getBlockPos();
      JsonArray pos = new JsonArray();
      pos.add(c.getX());
      pos.add(c.getY());
      pos.add(c.getZ());
      o.add("pos", pos);
      o.addProperty("level", be.baseLevel());
      o.addProperty("radius", be.radius());
      o.addProperty("chunks", 2 * be.chunks() + 1);
      o.addProperty("loading", be.chunkLoading());
      o.addProperty("cap", be.cap());
      BaseCoreBlockEntity.Upgrade up = BaseCoreBlockEntity.upgradeFrom(be.baseLevel());
      o.addProperty(
         "upgrade",
         up == null
            ? "Fully upgraded"
            : "Next level: right-click the core with " + up.count() + " " + up.item().getDefaultInstance().getHoverName().getString()
      );
      JsonArray workers = new JsonArray();
      ServerLevel level = (ServerLevel)be.getLevel();

      for (Worker w : be.workers) {
         JsonObject j = describe(w.creature());
         String status = "Coming back to the base";
         if (level.getEntity(w.entity()) instanceof CreatureEntity e && e.brain() != null) {
            status = e.brain().status();
         }

         j.addProperty("status", status);
         JsonArray jobList = new JsonArray();

         for (Job job : w.ordered(jobsOf(w.creature()))) {
            JsonObject jj = new JsonObject();
            jj.addProperty("id", job.getSerializedName());
            jj.addProperty("name", job.displayName());
            jj.addProperty("desc", job.description());
            jj.addProperty("on", !w.off().contains(job.getSerializedName()));
            jj.addProperty("works", job.working());
            jobList.add(jj);
         }

         j.add("jobList", jobList);
         j.addProperty("speed", (int)Math.round(WorkerBrain.speedAt(w.creature().level()) * 100.0));
         if (jobsOf(w.creature()).contains(Job.MINING)) {
            j.addProperty("silkLevel", w.creature().level() >= 20);
            j.addProperty("silk", !w.off().contains("silk_touch"));
         }

         workers.add(j);
      }

      o.add("workers", workers);
      JsonArray party = new JsonArray();

      for (CaughtCreature cc : Party.get(p)) {
         party.add(describe(cc));
      }

      o.add("party", party);
      JsonArray box = new JsonArray();

      for (CaughtCreature cc : Party.box(p)) {
         box.add(describe(cc));
      }

      o.add("box", box);
      return o;
   }

   public static List<Job> jobsOf(CaughtCreature c) {
      return Species.get(c.species()).map(s -> s.jobsFor(c.influence())).orElse(List.of());
   }

   public static String changeJob(BaseCoreBlockEntity be, int index, String jobId, String kind) {
      if (index >= 0 && index < be.workers.size()) {
         Worker w = be.workers.get(index);
         List<Job> jobs = jobsOf(w.creature());
         Job job = jobs.stream().filter(j -> j.getSerializedName().equals(jobId)).findFirst().orElse(null);
         if (job == null) {
            return "It doesn't have that job.";
         } else {
            be.setWorker(index, switch (kind) {
               case "job_toggle" -> w.toggled(job);
               case "job_up" -> w.moved(jobs, job, -1);
               default -> w.moved(jobs, job, 1);
            });
            return null;
         }
      } else {
         return "No such worker.";
      }
   }

   private static JsonObject describe(CaughtCreature c) {
      JsonObject j = new JsonObject();
      Optional<Species> sp = Species.get(c.species());
      j.addProperty("name", sp.<String>map(s -> s.nameFor(c.influence())).orElse(c.species()));
      j.addProperty("level", c.level());
      StringBuilder jobs = new StringBuilder();
      boolean works = false;

      for (Job job : sp.map(s -> s.jobsFor(c.influence())).orElse(List.of())) {
         jobs.append(jobs.isEmpty() ? "" : ", ").append(job.displayName()).append(job.working() ? "" : " (soon)");
         works |= job.working();
      }

      j.addProperty("jobs", jobs.isEmpty() ? "no jobs" : jobs.toString());
      j.addProperty("works", works);
      return j;
   }

   static void handle(ServerPlayer p, String json) {
      JsonObject j = JsonParser.parseString(json).getAsJsonObject();
      JsonArray at = j.getAsJsonArray("pos");
      BlockPos pos = new BlockPos(at.get(0).getAsInt(), at.get(1).getAsInt(), at.get(2).getAsInt());
      ServerLevel level = p.level();
      BaseCoreBlockEntity be = coreAt(level, pos);
      if (be != null && !(p.distanceToSqr(Vec3.atCenterOf(pos)) > 4096.0)) {
         if (be.owner() == null || be.owner().equals(p.getUUID())) {
            String kind = j.get("kind").getAsString();
            int index = j.has("index") ? j.get("index").getAsInt() : -1;

            String problem = switch (kind) {
               case "assign_party" -> index >= 0 && index < Party.get(p).size()
               ? assign(p, be, Party.get(p).get(index), () -> Party.remove(p, index))
               : "No such creature.";
               case "assign_box" -> index >= 0 && index < Party.box(p).size()
               ? assign(p, be, Party.box(p).get(index), () -> Party.removeFromBox(p, index))
               : "No such creature.";
               case "unassign" -> unassign(p, be, index);
               case "job_toggle", "job_up", "job_down" -> changeJob(be, index, j.has("job") ? j.get("job").getAsString() : "", kind);
               case "silk_toggle" -> {
                  if (index >= 0 && index < be.workers.size()) {
                     be.setWorker(index, be.workers.get(index).toggled("silk_touch"));
                     yield null;
                  } else {
                     yield "No such worker.";
                  }
               }
               case "toggle_loading" -> {
                  be.setChunkLoading(!be.chunkLoading());
                  p.sendSystemMessage(
                     Component.literal(
                        be.chunkLoading()
                           ? "This base stays loaded while you're online (" + loadedBases(p) + " of your bases are now)."
                           : "This base is only loaded when someone is nearby now."
                     )
                  );
                  yield null;
               }
               default -> null;
            };
            if (problem != null) {
               p.sendOverlayMessage(Component.literal(problem));
            }

            send(p, be);
         }
      }
   }

   public static String assign(ServerPlayer p, BaseCoreBlockEntity be, CaughtCreature c, Runnable takeFromPlayer) {
      if (be.workers.size() >= be.cap()) {
         return "This base is full (" + be.cap() + " workers). Upgrade the core for more.";
      } else {
         Optional<Species> species = Species.get(c.species());
         if (species.isEmpty()) {
            return "That creature's species no longer exists.";
         } else {
            CreatureEntity e = spawnWorker((ServerLevel)be.getLevel(), be, c);
            if (e == null) {
               return "There's no room by the core for it.";
            } else {
               takeFromPlayer.run();
               be.workers.add(new Worker(c, e.getUUID()));
               be.setChanged();
               boolean works = species.get().jobsFor(c.influence()).stream().anyMatch(Job::working);
               p.sendSystemMessage(
                  Component.literal(e.displayName() + " is working at your base" + (works ? "." : ", but its only job (riding) isn't a base job yet."))
               );
               return null;
            }
         }
      }
   }

   static String unassign(ServerPlayer p, BaseCoreBlockEntity be, int index) {
      if (index >= 0 && index < be.workers.size()) {
         Worker w = be.workers.remove(index);
         CaughtCreature c = w.creature();
         if (((ServerLevel)be.getLevel()).getEntity(w.entity()) instanceof CreatureEntity e) {
            c = e.toCaught();
            e.discard();
         }

         be.release(w.entity());
         be.setChanged();
         boolean party = Party.addAnywhere(p, c);
         String name = Species.get(c.species()).map(s -> s.nameFor(w.creature().influence())).orElse(c.species());
         p.sendSystemMessage(Component.literal(name + (party ? " rejoined your party." : " went to your box.")));
         return null;
      } else {
         return "No such worker.";
      }
   }

   static CreatureEntity spawnWorker(ServerLevel level, BaseCoreBlockEntity be, CaughtCreature c) {
      Optional<Species> species = Species.get(c.species());
      if (species.isEmpty()) {
         return null;
      } else {
         Vec3 at = ArenaReturn.safeSpot(level, Vec3.atBottomCenterOf(be.getBlockPos().above()).add(1.5, 0.0, 0.0));
         if (at == null) {
            return null;
         } else {
            CreatureEntity e = Ahrivaea.spawn(level, species.get(), c.influence(), c.level(), c.health(), at.x, at.y, at.z);
            if (e == null) {
               return null;
            } else {
               if (be.owner() != null) {
                  e.setTame(true, false);
                  e.setOwnerReference(EntityReference.of(be.owner()));
               }

               e.setPersistenceRequired();
               e.applyCaught(c);
               e.setAttached(WORKER_OF, be.getBlockPos());
               e.capSize(1.25F);
               return e;
            }
         }
      }
   }

   public static List<BlockPos> cores(ServerLevel level) {
      return new ArrayList<>(CORES.getOrDefault(level.dimension(), Set.of()));
   }

   public static record ActionPayload(String json) implements CustomPacketPayload {
      public static final Type<Bases.ActionPayload> TYPE = new Type(Ahrivaea.id("base_action"));
      public static final StreamCodec<RegistryFriendlyByteBuf, Bases.ActionPayload> CODEC = StreamCodec.composite(
         ByteBufCodecs.stringUtf8(4096), Bases.ActionPayload::json, Bases.ActionPayload::new
      );

      public Type<Bases.ActionPayload> type() {
         return TYPE;
      }
   }

   public static record Loader(String dimension, BlockPos pos, UUID owner, int chunks, boolean on) {
      static final Codec<Bases.Loader> CODEC = RecordCodecBuilder.create(
         i -> i.group(
                  Codec.STRING.fieldOf("dimension").forGetter(Bases.Loader::dimension),
                  BlockPos.CODEC.fieldOf("pos").forGetter(Bases.Loader::pos),
                  UUIDUtil.CODEC.fieldOf("owner").forGetter(Bases.Loader::owner),
                  Codec.INT.fieldOf("chunks").forGetter(Bases.Loader::chunks),
                  Codec.BOOL.fieldOf("on").forGetter(Bases.Loader::on)
               )
               .apply(i, Bases.Loader::new)
      );

      String key() {
         return this.dimension + "|" + this.pos.asLong();
      }

      int ticketRadius() {
         return this.chunks + 2;
      }
   }

   public static record StatePayload(String json) implements CustomPacketPayload {
      public static final Type<Bases.StatePayload> TYPE = new Type(Ahrivaea.id("base_state"));
      public static final StreamCodec<RegistryFriendlyByteBuf, Bases.StatePayload> CODEC = StreamCodec.composite(
         ByteBufCodecs.stringUtf8(1048576), Bases.StatePayload::json, Bases.StatePayload::new
      );

      public Type<Bases.StatePayload> type() {
         return TYPE;
      }
   }
}
