package dev.ahrivaea.character;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.creature.BattleSettings;
import dev.ahrivaea.creature.Stats;
import dev.ahrivaea.party.Party;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.player.Player;

public record PlayerCharacter(int level, int xp, int points, Stats stats) {
   public static final int MAX_LEVEL = 50;
   public static final int START_STAT = 30;
   public static final int POINTS_PER_LEVEL = 6;
   public static final List<String> STAT_NAMES = List.of("hp", "attack", "defense", "speed", "magic", "luck", "charisma");
   public static final Codec<PlayerCharacter> CODEC = RecordCodecBuilder.create(
      i -> i.group(
               Codec.INT.fieldOf("level").forGetter(PlayerCharacter::level),
               Codec.INT.fieldOf("xp").forGetter(PlayerCharacter::xp),
               Codec.INT.fieldOf("points").forGetter(PlayerCharacter::points),
               Stats.CODEC.fieldOf("stats").forGetter(PlayerCharacter::stats)
            )
            .apply(i, PlayerCharacter::new)
   );
   public static final PlayerCharacter NEW = new PlayerCharacter(1, 0, 0, new Stats(30, 30, 30, 30, 30, 30, 30));
   public static final AttachmentType<PlayerCharacter> ATTACHMENT = AttachmentRegistry.create(
      Ahrivaea.id("character"), b -> b.persistent(CODEC).copyOnDeath().initializer(() -> NEW)
   );
   public static final double SPEED_PER_POINT = 0.02;
   private static final Identifier SPEED_ID = Ahrivaea.id("character_speed");
   private static final Identifier HEALTH_ID = Ahrivaea.id("character_health");

   public static void init() {
      ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
         if (server.getTickCount() % 20 == 11) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
               applySpeed(p);
               applyHealth(p);
            }
         }
      });
   }

   public double speedBonus() {
      return 0.02 * (double)Math.max(0, this.stats.speed() - 30);
   }

   public int extraHealth() {
      return 2 * (Math.max(0, this.stats.hp() - 30) / 5);
   }

   public static void applyHealth(ServerPlayer p) {
      AttributeInstance attr = p.getAttribute(Attributes.MAX_HEALTH);
      if (attr != null) {
         double extra = (double)get(p).extraHealth();
         AttributeModifier now = attr.getModifier(HEALTH_ID);
         if (now == null || now.amount() != extra) {
            attr.removeModifier(HEALTH_ID);
            if (extra > 0.0) {
               attr.addPermanentModifier(new AttributeModifier(HEALTH_ID, extra, Operation.ADD_VALUE));
            }

            if (p.getHealth() > p.getMaxHealth()) {
               p.setHealth(p.getMaxHealth());
            }
         }
      }
   }

   public static void applySpeed(ServerPlayer p) {
      AttributeInstance attr = p.getAttribute(Attributes.MOVEMENT_SPEED);
      if (attr != null) {
         double bonus = get(p).speedBonus();
         AttributeModifier now = attr.getModifier(SPEED_ID);
         if (now == null || now.amount() != bonus) {
            attr.removeModifier(SPEED_ID);
            if (bonus > 0.0) {
               attr.addTransientModifier(new AttributeModifier(SPEED_ID, bonus, Operation.ADD_MULTIPLIED_BASE));
            }
         }
      }
   }

   public int toNext() {
      return this.level >= 50 ? 0 : xpToNext(this.level) - this.xp;
   }

   public static String levelUp(ServerPlayer p) {
      PlayerCharacter c = get(p);
      if (c.level >= 50) {
         return "You're already level 50.";
      } else {
         int need = c.toNext();
         if (Party.xp(p) < need) {
            return "Level " + (c.level + 1) + " takes " + need + " XP; you have " + Party.xp(p) + ".";
         } else {
            Party.addXp(p, -need);
            p.setAttached(ATTACHMENT, new PlayerCharacter(c.level + 1, 0, c.points + 6, c.stats));
            p.sendSystemMessage(
               Component.literal("You reached level " + (c.level + 1) + "! " + (c.points + 6) + " stat points to spend (party screen, You).")
                  .withStyle(ChatFormatting.GOLD)
            );
            return null;
         }
      }
   }

   public static String spend(ServerPlayer p, String stat, int want) {
      PlayerCharacter c = get(p);
      if (!STAT_NAMES.contains(stat)) {
         return "Stats: " + String.join(", ", STAT_NAMES);
      } else {
         int now = stat(c.stats, stat);
         int spend = Math.min(Math.min(want, c.points), 100 - now);
         if (spend <= 0) {
            return c.points == 0 ? "You have no points to spend." : stat + " is already 100.";
         } else {
            p.setAttached(ATTACHMENT, new PlayerCharacter(c.level, c.xp, c.points - spend, with(c.stats, stat, now + spend)));
            if (stat.equals("speed")) {
               applySpeed(p);
            }

            if (stat.equals("hp")) {
               applyHealth(p);
            }

            return null;
         }
      }
   }

   public static PlayerCharacter get(Player p) {
      return (PlayerCharacter)p.getAttachedOrCreate(ATTACHMENT);
   }

   public static int xpToNext(int level) {
      return BattleSettings.xpToNext(level);
   }

   public Stats battleStats(Player p) {
      int def = (int)Math.min(100L, (long)this.stats.defense() + Math.round((double)p.getArmorValue() * 1.5));
      return new Stats(this.stats.hp(), this.stats.attack(), def, this.stats.speed(), this.stats.magic(), this.stats.luck(), this.stats.charisma());
   }

   public int maxHealth() {
      return (int)Math.round(20.0 + 2.0 * (double)this.stats.hp());
   }

   public static int addXp(Player p, int amount) {
      PlayerCharacter c = get(p);
      int level = c.level;
      int xp = c.xp + amount;
      int points = c.points;

      int gained;
      for (gained = 0; level < 50 && xp >= xpToNext(level); gained++) {
         xp -= xpToNext(level);
         level++;
         points += 6;
      }

      if (level >= 50) {
         xp = 0;
      }

      p.setAttached(ATTACHMENT, new PlayerCharacter(level, xp, points, c.stats));
      if (gained > 0 && p instanceof ServerPlayer sp) {
         sp.sendSystemMessage(
            Component.literal("You reached level " + level + "! You have " + points + " stat points to spend: /character spend <stat> <points>")
               .withStyle(ChatFormatting.GOLD)
         );
      }

      return gained;
   }

   private static int stat(Stats s, String name) {
      return switch (name) {
         case "hp" -> s.hp();
         case "attack" -> s.attack();
         case "defense" -> s.defense();
         case "speed" -> s.speed();
         case "magic" -> s.magic();
         case "luck" -> s.luck();
         default -> s.charisma();
      };
   }

   private static Stats with(Stats s, String name, int v) {
      return new Stats(
         name.equals("hp") ? v : s.hp(),
         name.equals("attack") ? v : s.attack(),
         name.equals("defense") ? v : s.defense(),
         name.equals("speed") ? v : s.speed(),
         name.equals("magic") ? v : s.magic(),
         name.equals("luck") ? v : s.luck(),
         name.equals("charisma") ? v : s.charisma()
      );
   }

   public static void registerCommands(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("character")
                     .executes(
                        ctx -> {
                           ServerPlayer p = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
                           PlayerCharacter c = get(p);
                           Stats b = c.battleStats(p);
                           String text = "Your character: level "
                              + c.level
                              + (c.level < 50 ? " (" + c.xp + "/" + xpToNext(c.level) + " XP)" : "")
                              + ", "
                              + c.points
                              + " points to spend\n HP "
                              + b.hp()
                              + "  Attack "
                              + b.attack()
                              + "  Defense "
                              + b.defense()
                              + (b.defense() != c.stats.defense() ? " (with armor)" : "")
                              + "  Speed "
                              + b.speed()
                              + "  Magic "
                              + b.magic()
                              + "  Luck "
                              + b.luck()
                              + "  Charisma "
                              + b.charisma()
                              + "\n Moves "
                              + b.squaresPerTurn()
                              + " squares per turn in battle, "
                              + c.maxHealth()
                              + " battle HP, "
                              + Math.round(c.speedBonus() * 100.0)
                              + "% faster and "
                              + c.extraHealth() / 2
                              + " extra hearts outside battles\n Level up with XP from your pool: /character levelup ("
                              + c.toNext()
                              + " XP), or on the party screen";
                           ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal(text), false);
                           return c.level;
                        }
                     ))
                  .then(Commands.literal("levelup").executes(ctx -> {
                     String why = levelUp(((CommandSourceStack)ctx.getSource()).getPlayerOrException());
                     if (why != null) {
                        ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal(why));
                     }

                     return why == null ? 1 : 0;
                  })))
               .then(
                  Commands.literal("spend")
                     .then(
                        Commands.argument("stat", StringArgumentType.word())
                           .suggests((ctx, b) -> SharedSuggestionProvider.suggest(STAT_NAMES, b))
                           .then(
                              Commands.argument("points", IntegerArgumentType.integer(1))
                                 .executes(
                                    ctx -> {
                                       ServerPlayer p = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
                                       String stat = StringArgumentType.getString(ctx, "stat").toLowerCase(Locale.ROOT);
                                       String why = spend(p, stat, IntegerArgumentType.getInteger(ctx, "points"));
                                       if (why != null) {
                                          ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal(why));
                                          return 0;
                                       } else {
                                          PlayerCharacter c = get(p);
                                          ((CommandSourceStack)ctx.getSource())
                                             .sendSuccess(
                                                () -> Component.literal(stat + " is now " + stat(c.stats, stat) + " (" + c.points + " points left)."), false
                                             );
                                          return 1;
                                       }
                                    }
                                 )
                           )
                     )
               ))
            .then(
               ((LiteralArgumentBuilder)Commands.literal("givexp").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)))
                  .then(Commands.argument("amount", IntegerArgumentType.integer(1)).executes(ctx -> {
                     addXp(((CommandSourceStack)ctx.getSource()).getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "amount"));
                     return 1;
                  }))
            )
      );
   }
}
