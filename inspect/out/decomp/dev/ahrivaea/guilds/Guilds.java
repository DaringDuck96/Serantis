package dev.ahrivaea.guilds;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AllowDamage;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.ClickEvent.RunCommand;
import net.minecraft.network.chat.HoverEvent.ShowText;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.TeamColor;

public final class Guilds {
   public static final String PREFIX = "guild_";
   public static final int MAX_NAME = 16;
   public static final int INVITE_TICKS = 6000;
   public static final AttachmentType<Map<String, Guilds.Guild>> GUILDS = AttachmentRegistry.create(
      Identifier.fromNamespaceAndPath("ahrivaea", "guilds"), b -> b.persistent(Codec.unboundedMap(Codec.STRING, Guilds.Guild.CODEC)).initializer(Map::of)
   );
   private static final Map<UUID, Map<String, Long>> INVITES = new HashMap<>();
   private static final TeamColor[] COLORS = new TeamColor[]{
      TeamColor.AQUA,
      TeamColor.GOLD,
      TeamColor.GREEN,
      TeamColor.LIGHT_PURPLE,
      TeamColor.YELLOW,
      TeamColor.RED,
      TeamColor.BLUE,
      TeamColor.DARK_AQUA,
      TeamColor.DARK_GREEN,
      TeamColor.DARK_PURPLE
   };

   private Guilds() {
   }

   public static void init() {
      ServerLivingEntityEvents.ALLOW_DAMAGE.register((AllowDamage)(victim, source, amount) -> {
         Entity attacker = source.getEntity();
         if (attacker != null && attacker != victim && victim.level().getServer() != null) {
            String a = controller(attacker);
            String v = controller(victim);
            if (a != null && v != null && !a.equals(v)) {
               Scoreboard sb = victim.level().getServer().getScoreboard();
               PlayerTeam ta = sb.getPlayersTeam(a);
               PlayerTeam tv = sb.getPlayersTeam(v);
               return ta == null || tv == null || !isGuild(ta) || !ta.getName().equals(tv.getName());
            } else {
               return true;
            }
         } else {
            return true;
         }
      });
   }

   private static String controller(Entity e) {
      if (e instanceof Player p) {
         return p.getScoreboardName();
      } else {
         if (e instanceof OwnableEntity o && o.getOwner() instanceof Player p) {
            return p.getScoreboardName();
         }

         return null;
      }
   }

   public static boolean isGuild(PlayerTeam team) {
      return team != null && team.getName().startsWith("guild_");
   }

   static Map<String, Guilds.Guild> guilds(MinecraftServer server) {
      return (Map<String, Guilds.Guild>)server.overworld().getAttachedOrCreate(GUILDS);
   }

   private static void save(MinecraftServer server, String team, Guilds.Guild guild) {
      Map<String, Guilds.Guild> copy = new HashMap<>(guilds(server));
      if (guild == null) {
         copy.remove(team);
      } else {
         copy.put(team, guild);
      }

      server.overworld().setAttached(GUILDS, copy);
   }

   public static PlayerTeam teamOf(MinecraftServer server, String player) {
      PlayerTeam t = server.getScoreboard().getPlayersTeam(player);
      return isGuild(t) ? t : null;
   }

   static Guilds.Guild info(MinecraftServer server, PlayerTeam team) {
      Guilds.Guild g = guilds(server).get(team.getName());
      if (g != null && team.getPlayers().contains(g.leader())) {
         return g;
      } else {
         String name = g != null ? g.name() : team.getDisplayName().getString();
         List<String> members = new ArrayList<>(team.getPlayers());
         members.sort(String.CASE_INSENSITIVE_ORDER);
         Guilds.Guild fixed = new Guilds.Guild(name, members.isEmpty() ? "" : members.getFirst());
         save(server, team.getName(), fixed);
         return fixed;
      }
   }

   private static void style(PlayerTeam team, String name, TeamColor color) {
      team.setDisplayName(Component.literal(name));
      team.setColor(Optional.of(color));
      team.setPlayerPrefix(Component.literal("[" + name + "] ").withStyle(s -> s.withColor(color.textColor())));
      team.setAllowFriendlyFire(false);
      team.setSeeFriendlyInvisibles(true);
   }

   public static String create(ServerPlayer p, String name) {
      MinecraftServer server = p.level().getServer();
      if (!name.matches("[A-Za-z0-9_]{2,16}")) {
         return "A guild name is 2 to 16 letters, digits or _.";
      } else {
         PlayerTeam current = p.getTeam();
         if (current != null) {
            return "You're already in " + current.getDisplayName().getString() + ". Leave it first (/guild leave).";
         } else {
            String id = "guild_" + name.toLowerCase(Locale.ROOT);
            if (server.getScoreboard().getPlayerTeam(id) != null) {
               return "There's already a guild called " + name + ".";
            } else {
               PlayerTeam team = server.getScoreboard().addPlayerTeam(id);
               style(team, name, COLORS[Math.floorMod(name.toLowerCase(Locale.ROOT).hashCode(), COLORS.length)]);
               server.getScoreboard().addPlayerToTeam(p.getScoreboardName(), team);
               save(server, id, new Guilds.Guild(name, p.getScoreboardName()));
               GuildsMod.LOGGER.info("{} founded the guild {}", p.getScoreboardName(), name);
               server.getPlayerList()
                  .broadcastSystemMessage(Component.literal(p.getScoreboardName() + " founded the guild " + name + ".").withStyle(ChatFormatting.AQUA), false);
               return null;
            }
         }
      }
   }

   public static String invite(ServerPlayer leader, ServerPlayer target) {
      MinecraftServer server = leader.level().getServer();
      PlayerTeam team = teamOf(server, leader.getScoreboardName());
      if (team == null) {
         return "You're not in a guild. Make one with /guild create <name>.";
      } else {
         Guilds.Guild g = info(server, team);
         if (!g.leader().equals(leader.getScoreboardName())) {
            return "Only " + g.leader() + ", the guild's leader, can invite players.";
         } else if (target == leader) {
            return "You're already in your guild.";
         } else if (team.getPlayers().contains(target.getScoreboardName())) {
            return target.getScoreboardName() + " is already in " + g.name() + ".";
         } else {
            INVITES.computeIfAbsent(target.getUUID(), k -> new HashMap<>()).put(team.getName(), (long)server.getTickCount() + 6000L);
            String cmd = "/guild join " + g.name();
            target.sendSystemMessage(
               Component.literal(leader.getScoreboardName() + " invited you to the guild " + g.name() + ". ")
                  .withStyle(ChatFormatting.AQUA)
                  .append(
                     Component.literal("[Join]")
                        .withStyle(
                           s -> s.withColor(ChatFormatting.GREEN)
                                 .withBold(true)
                                 .withClickEvent(new RunCommand(cmd))
                                 .withHoverEvent(new ShowText(Component.literal(cmd)))
                        )
                  )
                  .append(Component.literal(" (or type " + cmd + "; the invitation lasts 5 minutes)").withStyle(ChatFormatting.GRAY))
            );
            leader.sendSystemMessage(Component.literal("Invited " + target.getScoreboardName() + " to " + g.name() + ".").withStyle(ChatFormatting.AQUA));
            return null;
         }
      }
   }

   public static String join(ServerPlayer p, String name) {
      MinecraftServer server = p.level().getServer();
      String id = "guild_" + name.toLowerCase(Locale.ROOT);
      PlayerTeam team = server.getScoreboard().getPlayerTeam(id);
      if (team == null) {
         return "There's no guild called " + name + ".";
      } else {
         Long until = INVITES.getOrDefault(p.getUUID(), Map.of()).get(id);
         if (until != null && until >= (long)server.getTickCount()) {
            PlayerTeam current = p.getTeam();
            if (current != null) {
               return "You're already in " + current.getDisplayName().getString() + ". Leave it first (/guild leave).";
            } else {
               INVITES.get(p.getUUID()).remove(id);
               server.getScoreboard().addPlayerToTeam(p.getScoreboardName(), team);
               tellGuild(server, team, Component.literal(p.getScoreboardName() + " joined " + info(server, team).name() + "!").withStyle(ChatFormatting.AQUA));
               return null;
            }
         } else {
            return "You need an invitation from " + info(server, team).leader() + " to join " + info(server, team).name() + ".";
         }
      }
   }

   public static String remove(MinecraftServer server, String player, String why) {
      PlayerTeam team = teamOf(server, player);
      if (team == null) {
         return player + " isn't in a guild.";
      } else {
         Guilds.Guild g = info(server, team);
         server.getScoreboard().removePlayerFromTeam(player, team);
         ServerPlayer online = server.getPlayerList().getPlayer(player);
         if (online != null) {
            online.sendSystemMessage(Component.literal(why.isEmpty() ? "You left " + g.name() + "." : why).withStyle(ChatFormatting.YELLOW));
         }

         if (team.getPlayers().isEmpty()) {
            server.getScoreboard().removePlayerTeam(team);
            save(server, team.getName(), null);
            GuildsMod.LOGGER.info("The guild {} ended", g.name());
            return null;
         } else {
            tellGuild(server, team, Component.literal(player + " left " + g.name() + ".").withStyle(ChatFormatting.YELLOW));
            if (g.leader().equals(player)) {
               List<String> members = new ArrayList<>(team.getPlayers());
               members.sort(String.CASE_INSENSITIVE_ORDER);
               save(server, team.getName(), new Guilds.Guild(g.name(), members.getFirst()));
               tellGuild(server, team, Component.literal(members.getFirst() + " now leads " + g.name() + ".").withStyle(ChatFormatting.GOLD));
            }

            return null;
         }
      }
   }

   private static void tellGuild(MinecraftServer server, PlayerTeam team, Component msg) {
      for (String name : team.getPlayers()) {
         ServerPlayer p = server.getPlayerList().getPlayer(name);
         if (p != null) {
            p.sendSystemMessage(msg);
         }
      }
   }

   private static ServerPlayer self(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
      return ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
   }

   private static int result(CommandContext<CommandSourceStack> ctx, String error) {
      if (error != null) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal(error));
         return 0;
      } else {
         return 1;
      }
   }

   private static PlayerTeam leading(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
      ServerPlayer p = self(ctx);
      MinecraftServer server = ((CommandSourceStack)ctx.getSource()).getServer();
      PlayerTeam team = teamOf(server, p.getScoreboardName());
      if (team == null) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("You're not in a guild."));
         return null;
      } else if (!info(server, team).leader().equals(p.getScoreboardName())) {
         ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("Only " + info(server, team).leader() + ", the guild's leader, can do that."));
         return null;
      } else {
         return team;
      }
   }

   public static void registerCommands(CommandDispatcher<CommandSourceStack> d) {
      SuggestionProvider<CommandSourceStack> members = (ctx, b) -> {
         PlayerTeam t = ((CommandSourceStack)ctx.getSource()).getPlayer() == null
            ? null
            : teamOf(((CommandSourceStack)ctx.getSource()).getServer(), ((CommandSourceStack)ctx.getSource()).getPlayer().getScoreboardName());
         return SharedSuggestionProvider.suggest((Iterable)(t == null ? List.of() : t.getPlayers()), b);
      };
      SuggestionProvider<CommandSourceStack> invitedTo = (ctx, b) -> {
         List<String> names = new ArrayList<>();
         if (((CommandSourceStack)ctx.getSource()).getPlayer() != null) {
            for (String id : INVITES.getOrDefault(((CommandSourceStack)ctx.getSource()).getPlayer().getUUID(), Map.of()).keySet()) {
               PlayerTeam t = ((CommandSourceStack)ctx.getSource()).getServer().getScoreboard().getPlayerTeam(id);
               if (t != null) {
                  names.add(info(((CommandSourceStack)ctx.getSource()).getServer(), t).name());
               }
            }
         }

         return SharedSuggestionProvider.suggest(names, b);
      };
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                                          "guild"
                                       )
                                       .executes(
                                          ctx -> {
                                             ServerPlayer p = self(ctx);
                                             MinecraftServer server = ((CommandSourceStack)ctx.getSource()).getServer();
                                             PlayerTeam team = teamOf(server, p.getScoreboardName());
                                             if (team == null) {
                                                ((CommandSourceStack)ctx.getSource())
                                                   .sendSuccess(
                                                      () -> Component.literal(
                                                               "You're not in a guild. Guild members can't hurt each other and can build at the towers their members own.\n/guild create <name> to found one, or ask a guild's leader to /guild invite you. /guild list shows every guild."
                                                            )
                                                            .withStyle(ChatFormatting.AQUA),
                                                      false
                                                   );
                                                return 0;
                                             } else {
                                                Guilds.Guild g = info(server, team);
                                                List<String> names = new ArrayList<>(team.getPlayers());
                                                names.sort(String.CASE_INSENSITIVE_ORDER);
                                                MutableComponent text = Component.literal(g.name())
                                                   .withStyle(
                                                      s -> s.withColor(team.getColor().<TextColor>map(TeamColor::textColor).orElse(null)).withBold(true)
                                                   )
                                                   .append(
                                                      Component.literal("  " + names.size() + (names.size() == 1 ? " member" : " members"))
                                                         .withStyle(ChatFormatting.GRAY)
                                                   );

                                                for (String n : names) {
                                                   boolean online = server.getPlayerList().getPlayer(n) != null;
                                                   text.append(
                                                      Component.literal("\n " + (online ? "● " : "○ ") + n + (n.equals(g.leader()) ? " (leader)" : ""))
                                                         .withStyle(online ? ChatFormatting.WHITE : ChatFormatting.GRAY)
                                                   );
                                                }

                                                text.append(Component.literal("\n/teammsg <message> talks to your guild.").withStyle(ChatFormatting.DARK_GRAY));
                                                ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> text, false);
                                                return names.size();
                                             }
                                          }
                                       ))
                                    .then(
                                       Commands.literal("create")
                                          .then(
                                             Commands.argument("name", StringArgumentType.word())
                                                .executes(ctx -> result(ctx, create(self(ctx), StringArgumentType.getString(ctx, "name"))))
                                          )
                                    ))
                                 .then(
                                    Commands.literal("invite")
                                       .then(
                                          Commands.argument("player", EntityArgument.player())
                                             .executes(ctx -> result(ctx, invite(self(ctx), EntityArgument.getPlayer(ctx, "player"))))
                                       )
                                 ))
                              .then(
                                 Commands.literal("join")
                                    .then(
                                       Commands.argument("name", StringArgumentType.word())
                                          .suggests(invitedTo)
                                          .executes(ctx -> result(ctx, join(self(ctx), StringArgumentType.getString(ctx, "name"))))
                                    )
                              ))
                           .then(
                              Commands.literal("leave")
                                 .executes(ctx -> result(ctx, remove(((CommandSourceStack)ctx.getSource()).getServer(), self(ctx).getScoreboardName(), "")))
                           ))
                        .then(
                           Commands.literal("kick")
                              .then(
                                 Commands.argument("member", StringArgumentType.word())
                                    .suggests(members)
                                    .executes(
                                       ctx -> {
                                          PlayerTeam team = leading(ctx);
                                          if (team == null) {
                                             return 0;
                                          } else {
                                             String who = StringArgumentType.getString(ctx, "member");
                                             if (who.equals(self(ctx).getScoreboardName())) {
                                                return result(ctx, "Use /guild leave to leave your own guild.");
                                             } else {
                                                return !team.getPlayers().contains(who)
                                                   ? result(ctx, who + " isn't in your guild.")
                                                   : result(
                                                      ctx,
                                                      remove(
                                                         ((CommandSourceStack)ctx.getSource()).getServer(),
                                                         who,
                                                         "You were removed from " + info(((CommandSourceStack)ctx.getSource()).getServer(), team).name() + "."
                                                      )
                                                   );
                                             }
                                          }
                                       }
                                    )
                              )
                        ))
                     .then(Commands.literal("leader").then(Commands.argument("member", StringArgumentType.word()).suggests(members).executes(ctx -> {
                        PlayerTeam team = leading(ctx);
                        if (team == null) {
                           return 0;
                        } else {
                           String who = StringArgumentType.getString(ctx, "member");
                           if (!team.getPlayers().contains(who)) {
                              return result(ctx, who + " isn't in your guild.");
                           } else {
                              MinecraftServer server = ((CommandSourceStack)ctx.getSource()).getServer();
                              Guilds.Guild g = info(server, team);
                              save(server, team.getName(), new Guilds.Guild(g.name(), who));
                              tellGuild(server, team, Component.literal(who + " now leads " + g.name() + ".").withStyle(ChatFormatting.GOLD));
                              return 1;
                           }
                        }
                     }))))
                  .then(
                     Commands.literal("color")
                        .then(
                           Commands.argument("color", StringArgumentType.word())
                              .suggests((ctx, b) -> SharedSuggestionProvider.suggest(TeamColor.VALUES.stream().map(TeamColor::getSerializedName), b))
                              .executes(
                                 ctx -> {
                                    PlayerTeam team = leading(ctx);
                                    if (team == null) {
                                       return 0;
                                    } else {
                                       TeamColor c = TeamColor.byName(StringArgumentType.getString(ctx, "color"));
                                       if (c == null) {
                                          return result(ctx, "That's not a color. Try aqua, gold, green, red, blue, yellow, light_purple ...");
                                       } else {
                                          style(team, info(((CommandSourceStack)ctx.getSource()).getServer(), team).name(), c);
                                          ((CommandSourceStack)ctx.getSource())
                                             .sendSuccess(() -> Component.literal("Guild color changed.").withStyle(s -> s.withColor(c.textColor())), false);
                                          return 1;
                                       }
                                    }
                                 }
                              )
                        )
                  ))
               .then(Commands.literal("disband").executes(ctx -> {
                  PlayerTeam team = leading(ctx);
                  if (team == null) {
                     return 0;
                  } else {
                     MinecraftServer server = ((CommandSourceStack)ctx.getSource()).getServer();
                     Guilds.Guild g = info(server, team);
                     tellGuild(server, team, Component.literal(g.name() + " has been disbanded.").withStyle(ChatFormatting.YELLOW));
                     server.getScoreboard().removePlayerTeam(team);
                     save(server, team.getName(), null);
                     GuildsMod.LOGGER.info("{} disbanded the guild {}", self(ctx).getScoreboardName(), g.name());
                     return 1;
                  }
               })))
            .then(
               Commands.literal("list")
                  .executes(
                     ctx -> {
                        MinecraftServer server = ((CommandSourceStack)ctx.getSource()).getServer();
                        MutableComponent text = Component.literal("Guilds:").withStyle(ChatFormatting.GOLD);
                        int n = 0;

                        for (PlayerTeam t : server.getScoreboard().getPlayerTeams()) {
                           if (isGuild(t)) {
                              Guilds.Guild g = info(server, t);
                              n++;
                              text.append(
                                    Component.literal("\n " + g.name())
                                       .withStyle(s -> s.withColor(t.getColor().<TextColor>map(TeamColor::textColor).orElse(null)))
                                 )
                                 .append(Component.literal(" - " + t.getPlayers().size() + " members, led by " + g.leader()).withStyle(ChatFormatting.GRAY));
                           }
                        }

                        if (n == 0) {
                           text.append(Component.literal("\n none yet: /guild create <name>").withStyle(ChatFormatting.GRAY));
                        }

                        ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> text, false);
                        return n;
                     }
                  )
            )
      );
   }

   public static record Guild(String name, String leader) {
      static final Codec<Guilds.Guild> CODEC = RecordCodecBuilder.create(
         i -> i.group(Codec.STRING.fieldOf("name").forGetter(Guilds.Guild::name), Codec.STRING.fieldOf("leader").forGetter(Guilds.Guild::leader))
               .apply(i, Guilds.Guild::new)
      );
   }
}
