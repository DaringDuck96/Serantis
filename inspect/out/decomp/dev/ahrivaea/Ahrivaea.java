package dev.ahrivaea;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.ahrivaea.base.Bases;
import dev.ahrivaea.battle.Arena;
import dev.ahrivaea.battle.Battles;
import dev.ahrivaea.character.PlayerCharacter;
import dev.ahrivaea.creature.BattleSettings;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Healing;
import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.InfluenceRules;
import dev.ahrivaea.creature.Mounts;
import dev.ahrivaea.creature.Species;
import dev.ahrivaea.creature.Stats;
import dev.ahrivaea.creature.VanillaMobs;
import dev.ahrivaea.creature.Variant;
import dev.ahrivaea.creature.WildSpawner;
import dev.ahrivaea.item.DropItems;
import dev.ahrivaea.item.VesselItem;
import dev.ahrivaea.party.CaughtCreature;
import dev.ahrivaea.party.Dex;
import dev.ahrivaea.party.Party;
import dev.ahrivaea.party.PartySync;
import dev.ahrivaea.party.Starters;
import dev.ahrivaea.villager.Profession;
import dev.ahrivaea.villager.Villagers;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.ModifyOutput;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType.Builder;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Ahrivaea implements ModInitializer {
   public static final String MOD_ID = "ahrivaea";
   public static final Logger LOGGER = LoggerFactory.getLogger("ahrivaea");
   public static final ResourceKey<EntityType<?>> CREATURE_KEY = ResourceKey.create(Registries.ENTITY_TYPE, id("creature"));
   public static final EntityType<CreatureEntity> CREATURE = (EntityType<CreatureEntity>)Registry.register(
      BuiltInRegistries.ENTITY_TYPE,
      CREATURE_KEY,
      Builder.of(CreatureEntity::new, MobCategory.CREATURE).sized(0.7F, 0.8F).clientTrackingRange(10).build(CREATURE_KEY)
   );
   public static final ResourceKey<Item> VESSEL_KEY = ResourceKey.create(Registries.ITEM, id("vessel"));
   public static final Item VESSEL = (Item)Registry.register(
      BuiltInRegistries.ITEM, VESSEL_KEY, new VesselItem(new Properties().setId(VESSEL_KEY).stacksTo(64))
   );

   public void onInitialize() {
      Present.register();
      Party.init();
      Mounts.init();
      Variant.init();
      PartySync.init();
      Starters.init();
      Dex.init();
      Villagers.init();
      ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(id("villager_professions"), Profession::reload);
      DropItems.init();
      Bases.init();
      VanillaMobs.init();
      PlayerCharacter.init();
      Battles.init();
      ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(id("creatures"), Species::reload);
      ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(id("influence_rules"), InfluenceRules::reload);
      FabricDefaultAttributeRegistry.register(CREATURE, Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.25));
      WildSpawner.SKIP.add(Arena.DIMENSION);
      WildSpawner.init();
      CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register((ModifyOutput)output -> output.accept(VESSEL));
      this.registerCommands();
      LOGGER.info("Ahrivaea loaded");
   }

   private void registerCommands() {
      CommandRegistrationCallback.EVENT
         .register(
            (CommandRegistrationCallback)(dispatcher, registryAccess, environment) -> {
               PlayerCharacter.registerCommands(dispatcher);
               Battles.registerCommands(dispatcher);
               Starters.registerCommands(dispatcher);
               Villagers.registerCommands(dispatcher);
               dispatcher.register(
                  (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                                          "party"
                                       )
                                       .executes(
                                          ctx -> listParty(((CommandSourceStack)ctx.getSource()).getPlayerOrException(), (CommandSourceStack)ctx.getSource())
                                       ))
                                    .then(
                                       Commands.literal("send")
                                          .then(
                                             Commands.argument("slot", IntegerArgumentType.integer(1, 6))
                                                .executes(
                                                   ctx -> {
                                                      ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
                                                      int slot = IntegerArgumentType.getInteger(ctx, "slot") - 1;
                                                      if (slot >= Party.get(player).size()) {
                                                         ((CommandSourceStack)ctx.getSource())
                                                            .sendFailure(Component.literal("There's no creature in that slot."));
                                                         return 0;
                                                      } else {
                                                         CaughtCreature c = Party.get(player).get(slot);
                                                         Species species = Species.get(c.species()).orElse(null);
                                                         if (species == null) {
                                                            ((CommandSourceStack)ctx.getSource())
                                                               .sendFailure(Component.literal("That creature's species no longer exists."));
                                                            return 0;
                                                         } else {
                                                            CreatureEntity e = spawn(
                                                               player.level(),
                                                               species,
                                                               c.influence(),
                                                               c.level(),
                                                               c.health(),
                                                               player.getX(),
                                                               player.getY(),
                                                               player.getZ()
                                                            );
                                                            if (e == null) {
                                                               return 0;
                                                            } else {
                                                               Party.remove(player, slot);
                                                               e.tame(player);
                                                               e.applyCaught(c);
                                                               ((CommandSourceStack)ctx.getSource())
                                                                  .sendSuccess(
                                                                     () -> Component.literal(
                                                                           "Go, "
                                                                              + e.displayName()
                                                                              + "! (sneak + right-click it to call it back"
                                                                              + (
                                                                                 e.isRideable()
                                                                                    ? (
                                                                                       e.hasSaddle()
                                                                                          ? "; right-click to ride)"
                                                                                          : "; put a saddle on it to ride it)"
                                                                                    )
                                                                                    : ")"
                                                                              )
                                                                        ),
                                                                     false
                                                                  );
                                                               return 1;
                                                            }
                                                         }
                                                      }
                                                   }
                                                )
                                          )
                                    ))
                                 .then(
                                    ((LiteralArgumentBuilder)Commands.literal("feed")
                                          .then(
                                             Commands.literal("out")
                                                .then(
                                                   Commands.argument("entity", IntegerArgumentType.integer(0))
                                                      .executes(
                                                         ctx -> {
                                                            ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
                                                            if (!(
                                                                  player.level().getEntity(IntegerArgumentType.getInteger(ctx, "entity")) instanceof CreatureEntity e
                                                               )
                                                               || !e.isTame()
                                                               || !e.isOwnedBy(player)
                                                               || (double)e.distanceTo(player) > 72.0) {
                                                               ((CommandSourceStack)ctx.getSource())
                                                                  .sendFailure(Component.literal("That creature isn't out with you."));
                                                               return 0;
                                                            }

                                                            if (Healing.foodPercent(player.getMainHandItem()) <= 0) {
                                                               ((CommandSourceStack)ctx.getSource())
                                                                  .sendFailure(Component.literal("Hold some food in your main hand to feed it."));
                                                               return 0;
                                                            } else {
                                                               e.eat(player, player.getMainHandItem());
                                                               return 1;
                                                            }
                                                         }
                                                      )
                                                )
                                          ))
                                       .then(Commands.argument("slot", IntegerArgumentType.integer(1, 6)).executes(ctx -> {
                                          ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
                                          String msg = Healing.feedParty(player, IntegerArgumentType.getInteger(ctx, "slot") - 1);
                                          player.sendSystemMessage(Component.literal(msg));
                                          return 1;
                                       }))
                                 ))
                              .then(
                                 ((LiteralArgumentBuilder)Commands.literal("recall")
                                       .executes(
                                          ctx -> {
                                             ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
                                             int n = 0;

                                             for (CreatureEntity e : player.level()
                                                .getEntitiesOfClass(
                                                   CreatureEntity.class,
                                                   player.getBoundingBox().inflate(64.0),
                                                   ex -> ex.isTame() && ex.isOwnedBy(player) && !ex.hasAttached(Bases.WORKER_OF) && !Battles.inBattle(ex)
                                                )) {
                                                e.recallTo(player);
                                                n++;
                                             }

                                             if (n == 0) {
                                                ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("None of your creatures are out nearby."));
                                             }

                                             return n;
                                          }
                                       ))
                                    .then(
                                       Commands.argument("entity", IntegerArgumentType.integer(0))
                                          .executes(
                                             ctx -> {
                                                ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
                                                if (player.level().getEntity(IntegerArgumentType.getInteger(ctx, "entity")) instanceof CreatureEntity e
                                                   && e.isTame()
                                                   && e.isOwnedBy(player)
                                                   && !e.hasAttached(Bases.WORKER_OF)
                                                   && !Battles.inBattle(e)
                                                   && !((double)e.distanceTo(player) > 72.0)) {
                                                   e.recallTo(player);
                                                   return 1;
                                                }

                                                ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("That creature isn't out with you."));
                                                return 0;
                                             }
                                          )
                                    )
                              ))
                           .then(Commands.literal("release").then(Commands.argument("slot", IntegerArgumentType.integer(1, 6)).executes(ctx -> {
                              ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
                              int slot = IntegerArgumentType.getInteger(ctx, "slot") - 1;
                              if (slot >= Party.get(player).size()) {
                                 ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("There's no creature in that slot."));
                                 return 0;
                              } else {
                                 CaughtCreature c = Party.remove(player, slot);
                                 ((CommandSourceStack)ctx.getSource())
                                    .sendSuccess(() -> Component.literal("Released " + formName(c) + " into the wild for good."), false);
                                 return 1;
                              }
                           }))))
                        .then(((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("box").executes(ctx -> {
                           ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
                           List<CaughtCreature> box = Party.box(player);
                           StringBuilder sb = new StringBuilder("Box (" + box.size() + "):");

                           for (int i = 0; i < box.size(); i++) {
                              sb.append("\n ").append(i + 1).append(". ").append(formName(box.get(i))).append(" Lv.").append(box.get(i).level());
                           }

                           if (box.isEmpty()) {
                              sb.append(" empty. Recruits go here when your party is full.");
                           }

                           ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal(sb.toString()), false);
                           return box.size();
                        })).then(Commands.literal("take").then(Commands.argument("number", IntegerArgumentType.integer(1)).executes(ctx -> {
                           ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
                           int i = IntegerArgumentType.getInteger(ctx, "number") - 1;
                           if (i >= Party.box(player).size()) {
                              ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("There's no creature with that number in your box."));
                              return 0;
                           } else if (Party.get(player).size() >= 6) {
                              ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("Your party is full; /party box store <slot> first."));
                              return 0;
                           } else {
                              CaughtCreature c = Party.removeFromBox(player, i);
                              Party.add(player, c);
                              ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal(formName(c) + " joined your party."), false);
                              return 1;
                           }
                        })))).then(Commands.literal("store").then(Commands.argument("slot", IntegerArgumentType.integer(1, 6)).executes(ctx -> {
                           ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
                           int slot = IntegerArgumentType.getInteger(ctx, "slot") - 1;
                           if (slot >= Party.get(player).size()) {
                              ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("There's no creature in that slot."));
                              return 0;
                           } else {
                              CaughtCreature c = Party.remove(player, slot);
                              Party.addToBox(player, c);
                              ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal(formName(c) + " went to your box."), false);
                              return 1;
                           }
                        })))))
                     .then(
                        ((LiteralArgumentBuilder)Commands.literal("xp")
                              .executes(
                                 ctx -> {
                                    int xp = Party.xp(((CommandSourceStack)ctx.getSource()).getPlayerOrException());
                                    ((CommandSourceStack)ctx.getSource())
                                       .sendSuccess(() -> Component.literal("You have " + xp + " XP to give out. Use /party xp <slot> <amount>."), false);
                                    return xp;
                                 }
                              ))
                           .then(
                              Commands.argument("slot", IntegerArgumentType.integer(1, 6))
                                 .then(
                                    Commands.argument("amount", IntegerArgumentType.integer(1))
                                       .executes(
                                          ctx -> {
                                             ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
                                             int slot = IntegerArgumentType.getInteger(ctx, "slot") - 1;
                                             if (slot >= Party.get(player).size()) {
                                                ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("There's no creature in that slot."));
                                                return 0;
                                             } else {
                                                int before = Party.get(player).get(slot).level();
                                                int spent = Party.feed(player, slot, IntegerArgumentType.getInteger(ctx, "amount"));
                                                CaughtCreature c = Party.get(player).get(slot);
                                                String grew = c.level() > before ? " It grew to level " + c.level() + "!" : "";
                                                ((CommandSourceStack)ctx.getSource())
                                                   .sendSuccess(
                                                      () -> Component.literal(
                                                            "Gave "
                                                               + spent
                                                               + " XP to "
                                                               + formName(c)
                                                               + " ("
                                                               + c.xp()
                                                               + "/"
                                                               + BattleSettings.xpToNext(c.level())
                                                               + ")."
                                                               + grew
                                                         ),
                                                      false
                                                   );
                                                return spent;
                                             }
                                          }
                                       )
                                 )
                           )
                     )
               );
               dispatcher.register(
                  (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("ahrivaea")
                              .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)))
                           .then(
                              Commands.literal("spawn")
                                 .then(
                                    Commands.argument("species", StringArgumentType.word())
                                       .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Species.all().stream().map(Species::id), b))
                                       .then(
                                          Commands.argument("influence", StringArgumentType.word())
                                             .suggests(
                                                (ctx, b) -> SharedSuggestionProvider.suggest(
                                                      Arrays.stream(Influence.values()).map(Influence::getSerializedName), b
                                                   )
                                             )
                                             .then(
                                                ((RequiredArgumentBuilder)Commands.argument("level", IntegerArgumentType.integer(0, 50))
                                                      .executes(ctx -> spawnCommand(ctx, Variant.NONE)))
                                                   .then(
                                                      Commands.argument("variant", StringArgumentType.word())
                                                         .suggests((ctx, b) -> SharedSuggestionProvider.suggest(List.of("none", "enraged", "mutated"), b))
                                                         .executes(ctx -> spawnCommand(ctx, Variant.byName(StringArgumentType.getString(ctx, "variant"))))
                                                   )
                                             )
                                       )
                                 )
                           ))
                        .then(Commands.literal("givexp").then(Commands.argument("amount", IntegerArgumentType.integer(1)).executes(ctx -> {
                           ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
                           Party.addXp(player, IntegerArgumentType.getInteger(ctx, "amount"));
                           ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal("You now have " + Party.xp(player) + " XP."), false);
                           return 1;
                        }))))
                     .then(Commands.literal("selftest").executes(ctx -> {
                        SelfTest.run(msg -> ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal(msg), false));
                        return 1;
                     }))
               );
            }
         );
   }

   private static int spawnCommand(CommandContext<CommandSourceStack> ctx, Variant variant) {
      Optional<Species> species = Species.get(StringArgumentType.getString(ctx, "species"));
      if (species.isEmpty()) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("No such creature."));
         return 0;
      } else {
         Vec3 pos = ((CommandSourceStack)ctx.getSource()).getPosition();
         CreatureEntity e = spawn(
            ((CommandSourceStack)ctx.getSource()).getLevel(),
            species.get(),
            Influence.byName(StringArgumentType.getString(ctx, "influence")),
            IntegerArgumentType.getInteger(ctx, "level"),
            -1.0F,
            pos.x,
            pos.y,
            pos.z
         );
         if (e == null) {
            return 0;
         } else {
            e.setVariant(variant);
            ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal("Spawned " + e.displayName() + " Lv." + e.getCreatureLevel()), true);
            return 1;
         }
      }
   }

   private static String formName(CaughtCreature c) {
      return Species.get(c.species()).map(s -> s.nameFor(c.influence())).orElse(c.species());
   }

   private static int listParty(ServerPlayer player, CommandSourceStack source) {
      List<CaughtCreature> party = Party.get(player);
      if (party.isEmpty()) {
         source.sendSuccess(() -> Component.literal("Your party is empty. Bind a creature with a Vessel."), false);
         return 0;
      } else {
         StringBuilder sb = new StringBuilder("Your party (" + Party.xp(player) + " XP to give out):");

         for (int i = 0; i < party.size(); i++) {
            CaughtCreature c = party.get(i);
            Optional<Species> sp = Species.get(c.species());
            sb.append("\n ")
               .append(i + 1)
               .append(". ")
               .append(formName(c))
               .append(" Lv.")
               .append(c.level())
               .append(" (")
               .append(c.influence().displayName())
               .append(")  ")
               .append(Math.round(c.health()))
               .append(" HP  ")
               .append(c.xp())
               .append("/")
               .append(BattleSettings.xpToNext(c.level()))
               .append(" XP");
            sp.ifPresent(
               s -> {
                  Stats st = s.statsFor(c.influence());
                  sb.append("\n    HP ")
                     .append(st.hp())
                     .append(" Atk ")
                     .append(st.attack())
                     .append(" Def ")
                     .append(st.defense())
                     .append(" Spd ")
                     .append(st.speed())
                     .append(" Mag ")
                     .append(st.magic())
                     .append(" Lck ")
                     .append(st.luck())
                     .append(" Cha ")
                     .append(st.charisma())
                     .append("  (moves ")
                     .append(st.squaresPerTurn())
                     .append(" squares)");
               }
            );
         }

         String text = sb.toString();
         source.sendSuccess(() -> Component.literal(text), false);
         return party.size();
      }
   }

   public static CreatureEntity spawn(ServerLevel level, Species species, Influence influence, int lvl, float health, double x, double y, double z) {
      CreatureEntity e = (CreatureEntity)CREATURE.create(level, EntitySpawnReason.MOB_SUMMONED);
      if (e == null) {
         return null;
      } else {
         e.snapTo(x, y, z);
         e.setup(species, influence, lvl, health);
         level.addFreshEntity(e);
         return e;
      }
   }

   public static Identifier id(String path) {
      return Identifier.fromNamespaceAndPath("ahrivaea", path);
   }
}
