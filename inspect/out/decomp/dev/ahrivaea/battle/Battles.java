package dev.ahrivaea.battle;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.base.Bases;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Species;
import dev.ahrivaea.creature.Variant;
import dev.ahrivaea.party.CaughtCreature;
import dev.ahrivaea.party.Party;
import dev.ahrivaea.party.RecruitRules;
import dev.ahrivaea.villager.Villagers;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AllowDamage;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.Load;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStarted;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.Before;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Disconnect;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Join;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class Battles {
   public static final int RADIUS = 10;
   private static final Map<UUID, Battle> ACTIVE = new HashMap<>();
   private static final Set<UUID> PREVIEWING = new HashSet<>();
   private static final Map<UUID, Battle> FINISHED = new HashMap<>();
   private static boolean SPAWNING;

   private Battles() {
   }

   public static Battle of(Player p) {
      return ACTIVE.get(p.getUUID());
   }

   public static boolean inBattle(Entity e) {
      for (Battle b : ACTIVE.values()) {
         for (Unit u : b.units) {
            if (e.getUUID().equals(u.entity)) {
               return true;
            }
         }
      }

      return false;
   }

   public static void init() {
      Ahrivaea.LOGGER.debug("Arena return point: {}", ArenaReturn.ATTACHMENT.identifier());
      PayloadTypeRegistry.clientboundPlay().register(Battles.StatePayload.TYPE, Battles.StatePayload.CODEC);
      PayloadTypeRegistry.serverboundPlay().register(Battles.ActionPayload.TYPE, Battles.ActionPayload.CODEC);
      ServerPlayNetworking.registerGlobalReceiver(Battles.ActionPayload.TYPE, (payload, ctx) -> handle(ctx.player(), payload.json()));
      AttackEntityCallback.EVENT.register((AttackEntityCallback)(player, level, hand, entity, hit) -> {
         if (!(player instanceof ServerPlayer sp) || !(entity instanceof CreatureEntity c) || c.isTame() || player.isSpectator()) {
            return InteractionResult.PASS;
         }

         if (of(player) != null || inBattle(c)) {
            return InteractionResult.FAIL;
         } else if (!player.getItemInHand(hand).is(ItemTags.SWORDS)) {
            sp.sendOverlayMessage(Component.literal("Hit it with a sword to battle it."));
            return InteractionResult.FAIL;
         } else {
            start(sp, c);
            return InteractionResult.FAIL;
         }
      });
      ServerLivingEntityEvents.ALLOW_DAMAGE
         .register(
            (AllowDamage)(entity, source, amount) -> !inBattle(entity) && (!(entity instanceof CreatureEntity) || !(source.getEntity() instanceof Player))
         );
      PlayerBlockBreakEvents.BEFORE
         .register(
            (Before)(level, player, pos, state, be) -> {
               if (level.dimension().equals(Arena.DIMENSION)) {
                  return false;
               } else {
                  for (Battle b : ACTIVE.values()) {
                     if (b.level == level
                        && pos.getX() >= b.grid.originX
                        && pos.getZ() >= b.grid.originZ
                        && pos.getX() < b.grid.originX + b.grid.size
                        && pos.getZ() < b.grid.originZ + b.grid.size) {
                        return false;
                     }
                  }

                  return true;
               }
            }
         );
      UseBlockCallback.EVENT
         .register(
            (UseBlockCallback)(player, level, hand, hit) -> (InteractionResult)(level.dimension().equals(Arena.DIMENSION)
                  ? InteractionResult.FAIL
                  : InteractionResult.PASS)
         );
      UseItemCallback.EVENT
         .register(
            (UseItemCallback)(player, level, hand) -> (InteractionResult)(level.dimension().equals(Arena.DIMENSION) && !player.isCreative()
                  ? InteractionResult.FAIL
                  : InteractionResult.PASS)
         );
      AttackBlockCallback.EVENT
         .register(
            (AttackBlockCallback)(player, level, hand, pos, dir) -> (InteractionResult)(level.dimension().equals(Arena.DIMENSION)
                  ? InteractionResult.FAIL
                  : InteractionResult.PASS)
         );
      ServerTickEvents.END_SERVER_TICK.register(Battles::tick);
      ServerLifecycleEvents.SERVER_STARTED.register((ServerStarted)server -> {
         if (server.getLevel(Arena.DIMENSION) != null) {
            Ahrivaea.LOGGER.info("Battle arena dimension ready");
         } else {
            Ahrivaea.LOGGER.error("Battle arena dimension missing: battles will be fought on the spot");
         }
      });
      ServerPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, server) -> {
         Battle b = ACTIVE.get(handler.getPlayer().getUUID());
         if (b != null) {
            b.say(handler.getPlayer().getPlainTextName() + " left.");
            b.outcome = Battle.Outcome.FLED;
            b.phase = Battle.Phase.OVER;
            cleanUp(b);
         }

         PREVIEWING.remove(handler.getPlayer().getUUID());
         Battle done = FINISHED.remove(handler.getPlayer().getUUID());
         if (done != null) {
            goHome(null, done);
         }
      });
      ServerPlayConnectionEvents.JOIN.register((Join)(handler, sender, server) -> {
         ServerPlayer p = handler.getPlayer();
         if (!ACTIVE.containsKey(p.getUUID()) && (p.hasAttached(ArenaReturn.ATTACHMENT) || p.level().dimension().equals(Arena.DIMENSION))) {
            ArenaReturn.sendBack(p);
         }
      });
      ServerEntityEvents.ENTITY_LOAD.register((Load)(entity, level) -> {
         if (!SPAWNING && level.dimension().equals(Arena.DIMENSION) && !(entity instanceof Player)) {
            if (!inBattle(entity)) {
               entity.discard();
            }
         }
      });
   }

   public static Battle start(ServerPlayer player, CreatureEntity wild) {
      if (ACTIVE.containsKey(player.getUUID())) {
         return null;
      } else {
         Optional<Species> wildSpecies = wild.getSpecies();
         if (wildSpecies.isEmpty()) {
            return null;
         } else {
            ServerLevel home = player.level();
            ServerLevel arena = home.getServer().getLevel(Arena.DIMENSION);
            List<CaughtCreature> party = Party.get(player);
            Holder<Biome> biome = home.getBiome(wild.blockPosition());
            int extra = reinforcements(wild.getCreatureLevel(), home.getRandom());
            List<CreatureEntity> out = home.getEntitiesOfClass(
               CreatureEntity.class,
               player.getBoundingBox().inflate(64.0),
               ex -> ex != wild && ex.isTame() && ex.isOwnedBy(player) && !ex.hasAttached(Bases.WORKER_OF) && !inBattle(ex) && ex.getSpecies().isPresent()
            );
            out.sort(Comparator.comparingDouble(ex -> ex.distanceToSqr(player)));
            if (out.size() > 4) {
               out = out.subList(0, 4);
            }

            int fromParty = Math.min(4 - out.size(), party.size());
            int slot = -1;
            ServerLevel level;
            BattleGrid grid;
            int[] playerCell;
            int[] enemyCell;
            if (arena != null && !home.dimension().equals(Arena.DIMENSION)) {
               slot = freeSlot();
               Arena.Theme theme = Arena.Theme.of(home, wild.blockPosition());
               int field = arenaField(theme, 1 + extra, out.size() + fromParty + 1);
               BlockPos c = Arena.build(arena, slot, theme, biome, home.getRandom().nextLong(), field);
               grid = BattleGrid.read(arena, c, field);
               level = arena;
               playerCell = new int[]{field, grid.size - 4};
               enemyCell = new int[]{field, 4};
               Ahrivaea.LOGGER.info("Arena {} ({}, {}x{}) for {}", new Object[]{slot, theme, grid.size, grid.size, player.getPlainTextName()});
            } else {
               BlockPos center = BlockPos.containing(player.position().add(wild.position()).scale(0.5));
               grid = BattleGrid.read(home, center, 10);
               level = home;
               playerCell = new int[]{
                  Math.clamp((long)(player.getBlockX() - grid.originX), 0, grid.size - 1),
                  Math.clamp((long)(player.getBlockZ() - grid.originZ), 0, grid.size - 1)
               };
               enemyCell = new int[]{
                  Math.clamp((long)(wild.getBlockX() - grid.originX), 0, grid.size - 1), Math.clamp((long)(wild.getBlockZ() - grid.originZ), 0, grid.size - 1)
               };
            }

            Battle b = new Battle(player, level, grid, !party.isEmpty() || !out.isEmpty());
            b.arenaSlot = slot;
            int[] pc = b.freeCellNear(playerCell[0], playerCell[1], Unit.Side.PLAYER);
            b.addPlayer(player, pc[0], pc[1]);
            if (slot >= 0) {
               player.stopRiding();
               if (!player.hasAttached(ArenaReturn.ATTACHMENT)) {
                  player.setAttached(ArenaReturn.ATTACHMENT, ArenaReturn.of(player));
               }

               BlockPos at = b.cellPos(b.playerUnit());
               player.teleportTo(level, (double)at.getX() + 0.5, (double)at.getY(), (double)at.getZ() + 0.5, Set.of(), 180.0F, 30.0F, true);
            }

            SPAWNING = true;

            try {
               List<CaughtCreature> allies = new ArrayList<>();
               List<Integer> slots = new ArrayList<>();

               for (CreatureEntity e : out) {
                  CaughtCreature c = e.toCaught();
                  e.ejectPassengers();
                  e.discard();
                  slots.add(Battle.outSlot(b.outCreatures.size()));
                  b.outCreatures.add(c);
                  allies.add(c);
               }

               for (int i = 0; i < fromParty; i++) {
                  allies.add(party.get(i));
                  slots.add(i);
               }

               for (int i = 0; i < allies.size(); i++) {
                  CaughtCreature c = allies.get(i);
                  Optional<Species> species = Species.get(c.species());
                  if (!species.isEmpty()) {
                     int[] cell = b.freeCellNear(pc[0], pc[1], Unit.Side.PLAYER);
                     Unit u = b.addCreature(Unit.Side.PLAYER, species.get(), c.influence(), c.variant(), c.level(), c.health(), slots.get(i), cell[0], cell[1]);
                     BlockPos at = b.cellPos(u);
                     CreatureEntity e = Ahrivaea.spawn(
                        level, species.get(), c.influence(), c.level(), (float)u.hp, (double)at.getX() + 0.5, (double)at.getY(), (double)at.getZ() + 0.5
                     );
                     if (e != null) {
                        e.setVariant(c.variant());
                        e.tame(player);
                        e.setNoAi(true);
                        e.capSize(1.4F);
                        u.entity = e.getUUID();
                     }
                  }
               }

               int[] wc = b.freeCellNear(enemyCell[0], enemyCell[1], Unit.Side.ENEMY);
               Unit lead = b.addCreature(
                  Unit.Side.ENEMY, wildSpecies.get(), wild.getInfluence(), wild.getVariant(), wild.getCreatureLevel(), wild.getHealth(), -1, wc[0], wc[1]
               );
               b.lead = lead;
               b.wild = new Battle.Wild(
                  home, wild.getSpeciesId(), wild.getInfluence(), wild.getCreatureLevel(), wild.getX(), wild.getY(), wild.getZ(), wild.getVariant()
               );
               if (level == home) {
                  lead.entity = wild.getUUID();
                  wild.setNoAi(true);
               } else {
                  BlockPos at = b.cellPos(lead);
                  CreatureEntity e = Ahrivaea.spawn(
                     level,
                     wildSpecies.get(),
                     wild.getInfluence(),
                     wild.getCreatureLevel(),
                     (float)lead.hp,
                     (double)at.getX() + 0.5,
                     (double)at.getY(),
                     (double)at.getZ() + 0.5
                  );
                  if (e != null) {
                     e.setVariant(wild.getVariant());
                     e.setNoAi(true);
                     e.capSize(1.4F);
                     lead.entity = e.getUUID();
                  }

                  wild.discard();
               }

               for (int ix = 0; ix < extra; ix++) {
                  Optional<Species.LevelledPick> pick = RecruitRules.reinforcement(biome, home, b.random, wildSpecies.get(), lead.influence, lead.level);
                  Species s = pick.map(Species.LevelledPick::species).orElse(wildSpecies.get());
                  Influence inf = pick.map(Species.LevelledPick::influence).orElse(lead.influence);
                  int lvl = pick.map(Species.LevelledPick::level).orElse(Math.max(0, lead.level - b.random.nextInt(3)));
                  int[] cell = b.freeCellNear(wc[0], wc[1], Unit.Side.ENEMY);
                  Variant variant = Variant.roll(home, b.random);
                  Unit u = b.addCreature(Unit.Side.ENEMY, s, inf, variant, lvl, -1.0F, -1, cell[0], cell[1]);
                  BlockPos at = b.cellPos(u);
                  CreatureEntity e = Ahrivaea.spawn(level, s, inf, lvl, -1.0F, (double)at.getX() + 0.5, (double)at.getY(), (double)at.getZ() + 0.5);
                  if (e != null) {
                     e.setVariant(variant);
                     e.setNoAi(true);
                     e.capSize(1.4F);
                     u.entity = e.getUUID();
                  }
               }

               b.say(
                  "A wild "
                     + lead.name
                     + " appears"
                     + (extra == 0 ? "!" : " with " + extra + " more!")
                     + (out.isEmpty() ? "" : " " + out.size() + " of your creatures that were out join you.")
                     + " Turn 1: your phase"
               );
            } finally {
               SPAWNING = false;
            }

            for (Unit u : b.units) {
               placeEntity(b, u);
            }

            ACTIVE.put(player.getUUID(), b);
            FINISHED.remove(player.getUUID());
            Ahrivaea.LOGGER
               .info(
                  "Battle started for {}: {} units on a {}x{} field in {}",
                  new Object[]{player.getPlainTextName(), b.units.size(), grid.size, grid.size, level.dimension().identifier()}
               );
            send(b);
            return b;
         }
      }
   }

   static int arenaField(Arena.Theme theme, int enemies, int allies) {
      int units = enemies + allies;
      int f = theme.baseField() + (units >= 9 ? 1 : (units <= 4 ? -1 : 0));
      return Math.clamp((long)f, 7, 13);
   }

   private static int freeSlot() {
      Set<Integer> used = new HashSet<>();

      for (Battle b : ACTIVE.values()) {
         used.add(b.arenaSlot);
      }

      for (Battle b : FINISHED.values()) {
         used.add(b.arenaSlot);
      }

      int s = 0;

      while (used.contains(s)) {
         s++;
      }

      return s;
   }

   public static int reinforcements(int level, RandomSource random) {
      int tier = Math.clamp((long)((level + 9) / 10), 1, 5);
      double chance = 0.4 + 0.1 * (double)tier;
      int n = 0;

      for (int i = 0; i < tier; i++) {
         if (random.nextDouble() < chance) {
            n++;
         }
      }

      return n;
   }

   static void goHome(ServerPlayer p, Battle b) {
      if (b != null && b.wild != null && b.lead != null && b.outcome != Battle.Outcome.WON) {
         boolean stillThere = b.level == b.wild.level() && b.lead.alive() && b.level.getEntity(b.lead.entity) != null;
         Optional<Species> species = Species.get(b.wild.species());
         Vec3 at = ArenaReturn.safeSpot(b.wild.level(), new Vec3(b.wild.x(), b.wild.y(), b.wild.z()));
         if (!stillThere && species.isPresent() && at != null) {
            CreatureEntity back = Ahrivaea.spawn(
               b.wild.level(), species.get(), b.wild.influence(), b.wild.creatureLevel(), b.lead.alive() ? (float)b.lead.hp : -1.0F, at.x, at.y, at.z
            );
            if (back != null) {
               back.setVariant(b.wild.variant());
            }
         }
      }

      if (b != null) {
         b.wild = null;
      }

      ServerPlayer owner = p != null ? p : (b == null ? null : b.player());
      if (b != null && !b.outCreatures.isEmpty() && owner != null && p == null) {
         for (CaughtCreature c : b.outCreatures) {
            Party.addAnywhere(owner, c);
         }

         b.outCreatures.clear();
      }

      if (p != null) {
         Ahrivaea.LOGGER
            .info(
               "Battle over for {}: back from {} (return point {})",
               new Object[]{p.getPlainTextName(), p.level().dimension().identifier(), p.getAttached(ArenaReturn.ATTACHMENT)}
            );

         try {
            ArenaReturn.sendBack(p);
         } catch (RuntimeException var7) {
            Ahrivaea.LOGGER.error("Couldn't send {} back from the arena", p.getPlainTextName(), var7);
         }

         if (b != null) {
            for (ItemStack stack : b.loot) {
               if (!p.getInventory().add(stack) || !stack.isEmpty()) {
                  p.spawnAtLocation(p.level(), stack);
               }
            }

            b.loot.clear();

            for (CaughtCreature c : b.outCreatures) {
               Optional<Species> species = Species.get(c.species());
               CreatureEntity e = species.isEmpty()
                  ? null
                  : Ahrivaea.spawn(p.level(), species.get(), c.influence(), c.level(), c.health(), p.getX(), p.getY(), p.getZ());
               if (e == null) {
                  Party.addAnywhere(p, c);
               } else {
                  e.tame(p);
                  e.applyCaught(c);
               }
            }

            b.outCreatures.clear();
         }
      }
   }

   private static void tick(MinecraftServer server) {
      ServerLevel arenaLevel = server.getLevel(Arena.DIMENSION);
      if (arenaLevel != null && server.getTickCount() % 20 == 0) {
         for (ServerPlayer p : List.copyOf(arenaLevel.players())) {
            UUID id = p.getUUID();
            if (!ACTIVE.containsKey(id) && !FINISHED.containsKey(id) && !PREVIEWING.contains(id)) {
               Ahrivaea.LOGGER.info("{} is in the battle arenas without a battle; sending them back", p.getPlainTextName());
               ArenaReturn.sendBack(p);
            }
         }
      }

      for (Battle b : List.copyOf(ACTIVE.values())) {
         ServerPlayer px = b.player();
         if (px != null) {
            Unit me = b.playerUnit();
            if (me != null && me.alive()) {
               BlockPos c = b.cellPos(me);
               if (px.position().distanceToSqr((double)c.getX() + 0.5, (double)c.getY(), (double)c.getZ() + 0.5) > 0.04) {
                  px.connection.teleport((double)c.getX() + 0.5, (double)c.getY(), (double)c.getZ() + 0.5, px.getYRot(), px.getXRot());
               }

               px.setDeltaMovement(0.0, 0.0, 0.0);
            }

            if (b.phase == Battle.Phase.ENEMY && --b.enemyDelay <= 0) {
               b.enemyStep();
               b.enemyDelay = 15;
               send(b);
            }
         }
      }
   }

   static void placeEntity(Battle b, Unit u) {
      if (!u.isPlayer) {
         Entity e = b.level.getEntity(u.entity);
         if (e != null) {
            BlockPos c = b.cellPos(u);
            e.snapTo((double)c.getX() + 0.5, (double)c.getY(), (double)c.getZ() + 0.5);
            e.setDeltaMovement(0.0, 0.0, 0.0);
         }
      }
   }

   static void removeEntity(Battle b, Unit u) {
      if (!u.isPlayer) {
         Entity e = b.level.getEntity(u.entity);
         if (e != null) {
            e.discard();
         }
      }
   }

   static void cleanUp(Battle b) {
      ACTIVE.remove(b.playerId);
      ServerPlayer p = b.player();
      if (p != null) {
         b.saveParty(p);
      }

      for (Unit u : b.units) {
         if (!u.isPlayer && u.alive()) {
            Entity e = b.level.getEntity(u.entity);
            if (e != null) {
               if (u.side == Unit.Side.PLAYER || b.arenaSlot >= 0) {
                  e.discard();
               } else if (e instanceof CreatureEntity c) {
                  c.setNoAi(false);
               }
            }
         }
      }

      if (p != null) {
         FINISHED.put(b.playerId, b);
         send(b);
      }
   }

   public static void send(Battle b) {
      ServerPlayer p = b.player();
      if (p != null) {
         ServerPlayNetworking.send(p, new Battles.StatePayload(b.view(p.getUUID()).toString()));
      }
   }

   public static String handle(ServerPlayer p, String json) {
      JsonObject j = JsonParser.parseString(json).getAsJsonObject();
      String kind = j.get("kind").getAsString();
      if (!kind.equals("recruit") && !kind.equals("leave")) {
         Battle b = ACTIVE.get(p.getUUID());
         if (b == null) {
            return "You're not in a battle.";
         } else {
            Battle.Action a = new Battle.Action(kind, opt(j, "unit"), opt(j, "x"), opt(j, "z"), opt(j, "move"), opt(j, "target"));
            String problem = b.act(a);
            if (problem != null) {
               p.sendOverlayMessage(Component.literal(problem));
            }

            send(b);
            return problem;
         }
      } else {
         Battle done = FINISHED.get(p.getUUID());
         if (done == null) {
            return "No battle to leave.";
         } else {
            String msg = null;
            if (kind.equals("recruit")) {
               msg = recruit(p, done, j.get("index").getAsInt());
            } else {
               FINISHED.remove(p.getUUID());
               ServerPlayNetworking.send(p, new Battles.StatePayload("{}"));
               goHome(p, done);
            }

            if (msg != null) {
               p.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.RED));
            } else if (FINISHED.containsKey(p.getUUID())) {
               send(done);
            }

            return msg;
         }
      }
   }

   private static int opt(JsonObject j, String k) {
      return j.has(k) ? j.get(k).getAsInt() : -1;
   }

   private static String recruit(ServerPlayer p, Battle b, int index) {
      if (index >= 0 && index < b.defeated.size()) {
         Battle.Defeated d = b.defeated.get(index);
         String why = b.whyNotRecruit(d);
         if (why != null) {
            return "You can't recruit " + d.name() + ": " + why + ".";
         } else if (Party.xp(p) < d.cost()) {
            return "Recruiting " + d.name() + " costs " + d.cost() + " XP; you have " + Party.xp(p) + ".";
         } else {
            Optional<Species> species = Species.get(d.species());
            if (species.isEmpty()) {
               return "That creature no longer exists.";
            } else {
               boolean party = Party.addAnywhere(
                  p,
                  new CaughtCreature(
                     d.species(), d.influence(), d.level(), 0, (float)species.get().statsFor(d.influence()).maxHealth(d.level()), 0, 0, false, d.variant()
                  )
               );
               Party.addXp(p, -d.cost());
               Villagers.award(p, Villagers.CREATURES_RECRUITED);
               b.defeated.remove(index);
               if (!party) {
                  p.sendSystemMessage(
                     Component.literal("Your party is full, so " + d.name() + " went to your box (/party box).").withStyle(ChatFormatting.YELLOW)
                  );
               }

               p.sendSystemMessage(Component.literal(d.name() + " joined your party! (-" + d.cost() + " XP)").withStyle(ChatFormatting.GREEN));
               return null;
            }
         }
      } else {
         return "Pick a creature to recruit.";
      }
   }

   public static String previewArena(ServerPlayer p, String themeName) {
      Arena.Theme theme;
      try {
         theme = Arena.Theme.valueOf(themeName.toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException var6) {
         return "Unknown arena theme: " + themeName;
      }

      ServerLevel arena = p.level().getServer().getLevel(Arena.DIMENSION);
      if (arena == null) {
         return "The battle arena dimension is missing.";
      } else if (of(p) != null) {
         return "Not during a battle.";
      } else {
         Optional<Reference<Biome>> biome = arena.registryAccess()
            .lookupOrThrow(Registries.BIOME)
            .get(ResourceKey.create(Registries.BIOME, Identifier.withDefaultNamespace(theme.sampleBiome())));
         BlockPos c = Arena.build(
            arena,
            1000 + theme.ordinal(),
            theme,
            biome.<Holder<Biome>>map(h -> (Holder<Biome>)h).orElse(null),
            (long)theme.ordinal() * 31L + 7L,
            theme.baseField()
         );
         PREVIEWING.add(p.getUUID());
         if (!p.hasAttached(ArenaReturn.ATTACHMENT) && !p.level().dimension().equals(Arena.DIMENSION)) {
            p.setAttached(ArenaReturn.ATTACHMENT, ArenaReturn.of(p));
         }

         p.teleportTo(arena, (double)c.getX() + 0.5, (double)(c.getY() + 18), (double)(c.getZ() + theme.baseField()) + 8.5, Set.of(), 180.0F, 45.0F, true);
         if (p.getAbilities().mayfly) {
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
         }

         Ahrivaea.LOGGER.info("Arena preview {} at {} for {}", new Object[]{theme, c.toShortString(), p.getPlainTextName()});
         return null;
      }
   }

   public static void registerCommands(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                              "battle"
                           )
                           .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)))
                        .then(Commands.literal("start").executes(ctx -> {
                           ServerPlayer p = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
                           List<CreatureEntity> near = p.level()
                              .getEntitiesOfClass(CreatureEntity.class, new AABB(p.blockPosition()).inflate(16.0), c -> !c.isTame());
                           if (near.isEmpty()) {
                              ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("No wild creature within 16 blocks."));
                              return 0;
                           } else {
                              near.sort((x, y) -> Double.compare(x.distanceToSqr(p), y.distanceToSqr(p)));
                              return start(p, near.getFirst()) != null ? 1 : 0;
                           }
                        })))
                     .then(Commands.literal("end").executes(ctx -> {
                        Battle b = of(((CommandSourceStack)ctx.getSource()).getPlayerOrException());
                        if (b == null) {
                           return 0;
                        } else {
                           b.outcome = Battle.Outcome.FLED;
                           b.phase = Battle.Phase.OVER;
                           cleanUp(b);
                           return 1;
                        }
                     })))
                  .then(
                     Commands.literal("arena")
                        .then(
                           Commands.argument("theme", StringArgumentType.word())
                              .suggests(
                                 (ctx, sb) -> SharedSuggestionProvider.suggest(
                                       Arrays.stream(Arena.Theme.values()).map(t -> t.name().toLowerCase(Locale.ROOT)), sb
                                    )
                              )
                              .executes(ctx -> {
                                 String msg = previewArena(
                                    ((CommandSourceStack)ctx.getSource()).getPlayerOrException(), StringArgumentType.getString(ctx, "theme")
                                 );
                                 if (msg != null) {
                                    ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal(msg));
                                 }

                                 return msg == null ? 1 : 0;
                              })
                        )
                  ))
               .then(Commands.literal("home").executes(ctx -> {
                  ServerPlayer p = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
                  if (of(p) != null) {
                     return 0;
                  } else {
                     PREVIEWING.remove(p.getUUID());
                     goHome(p, FINISHED.remove(p.getUUID()));
                     return 1;
                  }
               })))
            .then(Commands.literal("recruit").then(Commands.argument("index", IntegerArgumentType.integer(0)).executes(ctx -> {
               ServerPlayer p = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
               Battle b = FINISHED.get(p.getUUID());
               String msg = b == null ? "No finished battle." : recruit(p, b, IntegerArgumentType.getInteger(ctx, "index"));
               if (msg != null) {
                  ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal(msg));
               }

               return msg == null ? 1 : 0;
            })))
      );
   }

   public static record ActionPayload(String json) implements CustomPacketPayload {
      public static final Type<Battles.ActionPayload> TYPE = new Type(Ahrivaea.id("battle_action"));
      public static final StreamCodec<RegistryFriendlyByteBuf, Battles.ActionPayload> CODEC = StreamCodec.composite(
         ByteBufCodecs.stringUtf8(4096), Battles.ActionPayload::json, Battles.ActionPayload::new
      );

      public Type<Battles.ActionPayload> type() {
         return TYPE;
      }
   }

   public static record StatePayload(String json) implements CustomPacketPayload {
      public static final Type<Battles.StatePayload> TYPE = new Type(Ahrivaea.id("battle_state"));
      public static final StreamCodec<RegistryFriendlyByteBuf, Battles.StatePayload> CODEC = StreamCodec.composite(
         ByteBufCodecs.stringUtf8(1048576), Battles.StatePayload::json, Battles.StatePayload::new
      );

      public Type<Battles.StatePayload> type() {
         return TYPE;
      }
   }
}
