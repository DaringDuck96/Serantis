package dev.ahrivaea.party;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.serialization.Codec;
import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Job;
import dev.ahrivaea.creature.Mounts;
import dev.ahrivaea.creature.Species;
import dev.ahrivaea.creature.Stats;
import dev.ahrivaea.villager.Villagers;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Join;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.server.level.ServerPlayer;

public final class Starters {
   public static final AttachmentType<Boolean> CHOSEN = AttachmentRegistry.create(
      Ahrivaea.id("starter_chosen"), b -> b.persistent(Codec.BOOL).copyOnDeath().initializer(() -> false)
   );
   public static final int LEVEL = 1;

   private Starters() {
   }

   public static void init() {
      PayloadTypeRegistry.clientboundPlay().register(Starters.OfferPayload.TYPE, Starters.OfferPayload.CODEC);
      PayloadTypeRegistry.serverboundPlay().register(Starters.PickPayload.TYPE, Starters.PickPayload.CODEC);
      ServerPlayNetworking.registerGlobalReceiver(Starters.PickPayload.TYPE, (payload, ctx) -> pick(ctx.player(), payload.species()));
      ServerPlayConnectionEvents.JOIN.register((Join)(handler, sender, server) -> {
         ServerPlayer p = handler.getPlayer();
         if (!chosen(p)) {
            if (Party.get(p).isEmpty() && Party.box(p).isEmpty()) {
               if (!FabricLoader.getInstance().isModLoaded("ahrivaea-test")) {
                  offer(p);
               }
            } else {
               p.setAttached(CHOSEN, true);
            }
         }
      });
   }

   public static boolean chosen(ServerPlayer p) {
      return (Boolean)p.getAttachedOrElse(CHOSEN, false);
   }

   public static List<Species> options() {
      List<Species> out = new ArrayList<>();

      for (Species s : Species.all()) {
         if (s.forms().containsKey(Influence.NONE) && RecruitRules.rank(RecruitRules.rarity(s, Influence.NONE)) == 0 && !Villagers.isVillager(s.id())) {
            out.add(s);
         }
      }

      out.sort(Comparator.comparing(sx -> sx.nameFor(Influence.NONE)));
      return out;
   }

   public static void offer(ServerPlayer p) {
      if (ServerPlayNetworking.canSend(p, Starters.OfferPayload.TYPE)) {
         JsonObject o = new JsonObject();
         JsonArray list = new JsonArray();

         for (Species s : options()) {
            JsonObject j = new JsonObject();
            j.addProperty("id", s.id());
            j.addProperty("name", s.nameFor(Influence.NONE));
            Stats st = s.statsFor(Influence.NONE);
            JsonArray stats = new JsonArray();

            for (int v : new int[]{st.hp(), st.attack(), st.defense(), st.speed(), st.magic(), st.luck(), st.charisma()}) {
               stats.add(v);
            }

            j.add("stats", stats);
            j.addProperty("total", st.total());
            j.addProperty("squares", st.squaresPerTurn());
            j.addProperty("movement", s.movement().getSerializedName());
            j.addProperty("rideable", Mounts.rideable(s.sizeFor(Influence.NONE)));
            StringBuilder jobs = new StringBuilder();

            for (Job job : s.jobsFor(Influence.NONE)) {
               jobs.append(jobs.isEmpty() ? "" : ", ").append(job.displayName());
            }

            j.addProperty("jobs", jobs.isEmpty() ? "none" : jobs.toString());
            list.add(j);
         }

         o.add("options", list);
         o.addProperty("level", 1);
         ServerPlayNetworking.send(p, new Starters.OfferPayload(o.toString()));
      }
   }

   static void pick(ServerPlayer p, String speciesId) {
      if (chosen(p)) {
         p.sendOverlayMessage(Component.literal("You've already chosen your starter."));
      } else {
         Optional<Species> s = options().stream().filter(o -> o.id().equals(speciesId)).findFirst();
         if (s.isEmpty()) {
            p.sendOverlayMessage(Component.literal("That creature can't be a starter."));
         } else {
            Party.addAnywhere(p, new CaughtCreature(s.get().id(), Influence.NONE, 1, 0, (float)s.get().statsFor(Influence.NONE).maxHealth(1)));
            p.setAttached(CHOSEN, true);
            p.sendSystemMessage(
               Component.literal(s.get().nameFor(Influence.NONE) + " is your starter! It's in your party (press K to see it; /party send 1 to bring it out).")
                  .withStyle(ChatFormatting.GOLD)
            );
         }
      }
   }

   public static void registerCommands(CommandDispatcher<CommandSourceStack> d) {
      d.register((LiteralArgumentBuilder)Commands.literal("starter").executes(ctx -> {
         ServerPlayer p = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
         if (chosen(p)) {
            ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("You've already chosen your starter."));
            return 0;
         } else {
            offer(p);
            return 1;
         }
      }));
   }

   public static record OfferPayload(String json) implements CustomPacketPayload {
      public static final Type<Starters.OfferPayload> TYPE = new Type(Ahrivaea.id("starter_offer"));
      public static final StreamCodec<RegistryFriendlyByteBuf, Starters.OfferPayload> CODEC = StreamCodec.composite(
         ByteBufCodecs.stringUtf8(1048576), Starters.OfferPayload::json, Starters.OfferPayload::new
      );

      public Type<Starters.OfferPayload> type() {
         return TYPE;
      }
   }

   public static record PickPayload(String species) implements CustomPacketPayload {
      public static final Type<Starters.PickPayload> TYPE = new Type(Ahrivaea.id("starter_pick"));
      public static final StreamCodec<RegistryFriendlyByteBuf, Starters.PickPayload> CODEC = StreamCodec.composite(
         ByteBufCodecs.stringUtf8(256), Starters.PickPayload::species, Starters.PickPayload::new
      );

      public Type<Starters.PickPayload> type() {
         return TYPE;
      }
   }
}
