package dev.ahrivaea.party;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.battle.Battle;
import dev.ahrivaea.battle.Battles;
import dev.ahrivaea.battle.Unit;
import dev.ahrivaea.battle.Unit.Side;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Job;
import dev.ahrivaea.creature.Mounts;
import dev.ahrivaea.creature.Species;
import dev.ahrivaea.creature.Stats;
import dev.ahrivaea.creature.Species.Drop;
import dev.ahrivaea.creature.Species.Spawn;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

public final class Dex {
   public static final int SEEN = 1;
   public static final int CAUGHT = 2;
   public static final int SEE_RANGE = 20;
   public static final AttachmentType<Map<String, Integer>> ATTACHMENT = AttachmentRegistry.create(
      Ahrivaea.id("dex"), b -> b.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath().initializer(Map::of)
   );

   private Dex() {
   }

   public static void init() {
      PayloadTypeRegistry.serverboundPlay().register(Dex.RequestPayload.TYPE, Dex.RequestPayload.CODEC);
      PayloadTypeRegistry.clientboundPlay().register(Dex.DataPayload.TYPE, Dex.DataPayload.CODEC);
      ServerPlayNetworking.registerGlobalReceiver(Dex.RequestPayload.TYPE, (payload, ctx) -> send(ctx.player()));
      ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
         if (server.getTickCount() % 20 == 7) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
               update(p);
            }
         }
      });
   }

   public static String key(String species, Influence influence) {
      return species + "/" + influence.getSerializedName();
   }

   public static int count(ServerPlayer p, int atLeast) {
      int n = 0;

      for (int s : ((Map)p.getAttachedOrElse(ATTACHMENT, Map.of())).values()) {
         if (s >= atLeast) {
            n++;
         }
      }

      return n;
   }

   public static int status(ServerPlayer p, String key) {
      return ((Map)p.getAttachedOrElse(ATTACHMENT, Map.of())).getOrDefault(key, 0);
   }

   public static void mark(ServerPlayer p, String species, Influence influence, int status) {
      Optional<Species> sp = Species.get(species);
      if (!sp.isEmpty()) {
         influence = sp.get().formOrDefault(influence);
         String key = key(species, influence);
         Map<String, Integer> dex = (Map<String, Integer>)p.getAttachedOrElse(ATTACHMENT, Map.of());
         if (dex.getOrDefault(key, 0) < status) {
            Map<String, Integer> copy = new HashMap<>(dex);
            copy.put(key, status);
            p.setAttached(ATTACHMENT, copy);
            String name = sp.get().nameFor(influence);
            p.sendOverlayMessage(
               Component.literal(status == 2 ? "Dex: " + name + " caught! Its full entry is in your dex." : "Dex: new creature seen, " + name + "!")
                  .withStyle(status == 2 ? ChatFormatting.GOLD : ChatFormatting.AQUA)
            );
         }
      }
   }

   static void update(ServerPlayer p) {
      for (CaughtCreature c : Party.get(p)) {
         mark(p, c.species(), c.influence(), 2);
      }

      for (CaughtCreature c : Party.box(p)) {
         mark(p, c.species(), c.influence(), 2);
      }

      Battle battle = Battles.of(p);
      if (battle != null) {
         for (Unit u : battle.units) {
            if (!u.isPlayer && u.speciesId != null) {
               mark(p, u.speciesId, u.influence, u.side == Side.PLAYER ? 2 : 1);
            }
         }
      } else {
         for (CreatureEntity e : p.level()
            .getEntitiesOfClass(CreatureEntity.class, p.getBoundingBox().inflate(20.0), ex -> !ex.getSpeciesId().isEmpty() && p.hasLineOfSight(ex))) {
            mark(p, e.getSpeciesId(), e.getInfluence(), e.isTame() && e.isOwnedBy(p) ? 2 : 1);
         }
      }
   }

   public static void send(ServerPlayer p) {
      update(p);
      Map<String, Integer> dex = (Map<String, Integer>)p.getAttachedOrElse(ATTACHMENT, Map.of());
      JsonObject o = new JsonObject();
      JsonArray list = new JsonArray();
      List<Species> all = new ArrayList<>(Species.all());
      all.sort(Comparator.comparing(sx -> sx.nameFor(sx.formOrDefault(Influence.NONE))));
      int number = 0;
      int seen = 0;
      int caught = 0;

      for (Species s : all) {
         List<Influence> forms = new ArrayList<>(s.forms().keySet());
         forms.sort(Comparator.comparingInt(Enum::ordinal));

         for (Influence inf : forms) {
            number++;
            int st = dex.getOrDefault(key(s.id(), inf), 0);
            if (st >= 1) {
               seen++;
            }

            if (st >= 2) {
               caught++;
            }

            list.add(entry(s, inf, number, st));
         }
      }

      o.add("entries", list);
      o.addProperty("seen", seen);
      o.addProperty("caught", caught);
      ServerPlayNetworking.send(p, new Dex.DataPayload(o.toString()));
   }

   private static JsonObject entry(Species s, Influence inf, int number, int status) {
      JsonObject j = new JsonObject();
      j.addProperty("n", number);
      j.addProperty("species", s.id());
      j.addProperty("influence", inf.getSerializedName());
      j.addProperty("influenceName", inf.displayName());
      j.addProperty("color", inf.color());
      j.addProperty("status", status);
      Optional<Spawn> spawn = s.form(inf).spawn().or(s::spawn);
      List<String> biomes = spawn.<List<String>>map(Spawn::biomes).orElse(List.of());
      j.addProperty(
         "hint",
         s.id().equals("villager")
            ? "Lives in villages"
            : (biomes.isEmpty() ? "Not found in the wild" : "Lives in " + biomeName(biomes.getFirst()) + (biomes.size() > 1 ? " and elsewhere" : ""))
      );
      if (status < 1) {
         return j;
      } else {
         j.addProperty("name", s.nameFor(inf));
         j.addProperty("rarity", RecruitRules.rarity(s, inf));
         j.addProperty("movement", s.movement().getSerializedName());
         JsonArray where = new JsonArray();

         for (String b : biomes) {
            where.add(biomeName(b));
         }

         j.add("biomes", where);
         int[] levels = s.levelRange(inf);
         j.addProperty("levels", levels[0] + "-" + levels[1]);
         if (status < 2) {
            return j;
         } else {
            Stats st = s.statsFor(inf);
            JsonArray stats = new JsonArray();

            for (int v : new int[]{st.hp(), st.attack(), st.defense(), st.speed(), st.magic(), st.luck(), st.charisma()}) {
               stats.add(v);
            }

            j.add("stats", stats);
            j.addProperty("total", st.total());
            j.addProperty("squares", st.squaresPerTurn());
            StringBuilder jobs = new StringBuilder();

            for (Job job : s.jobsFor(inf)) {
               jobs.append(jobs.isEmpty() ? "" : ", ").append(job.displayName());
            }

            j.addProperty("jobs", jobs.isEmpty() ? "none" : jobs.toString());
            JsonArray drops = new JsonArray();

            for (Drop d : s.form(inf).drops()) {
               Identifier id = Identifier.tryParse(d.item());
               Item item = id == null ? null : (Item)BuiltInRegistries.ITEM.getOptional(id).orElse(null);
               String name = item == null ? d.item() : item.getDefaultInstance().getHoverName().getString();
               drops.add(name + " (" + Math.round(d.chance() * 100.0F) + "%" + (d.max() > 1 ? ", " + d.min() + "-" + d.max() : "") + ")");
            }

            j.add("drops", drops);
            j.addProperty("rideable", Mounts.rideable(s.sizeFor(inf)));
            return j;
         }
      }
   }

   public static String biomeName(String id) {
      boolean tag = id.startsWith("#");
      String path = id.substring(id.indexOf(58) + 1);
      if (tag) {
         if (path.startsWith("is_")) {
            path = path.substring(3);
         }

         if (path.equals("overworld")) {
            return "anywhere in the Overworld";
         } else if (path.equals("nether")) {
            return "anywhere in the Nether";
         } else if (path.equals("end")) {
            return "the End";
         } else {
            String words = path.replace('_', ' ');
            return words.endsWith("s") ? words : words + "s";
         }
      } else {
         StringBuilder out = new StringBuilder();

         for (String w : path.split("_")) {
            if (!w.isEmpty()) {
               out.append(out.isEmpty() ? "" : " ").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase(Locale.ROOT));
            }
         }

         return out.toString();
      }
   }

   public static record DataPayload(String json) implements CustomPacketPayload {
      public static final Type<Dex.DataPayload> TYPE = new Type(Ahrivaea.id("dex_data"));
      public static final StreamCodec<RegistryFriendlyByteBuf, Dex.DataPayload> CODEC = StreamCodec.composite(
         ByteBufCodecs.stringUtf8(2097152), Dex.DataPayload::json, Dex.DataPayload::new
      );

      public Type<Dex.DataPayload> type() {
         return TYPE;
      }
   }

   public static record RequestPayload() implements CustomPacketPayload {
      public static final Type<Dex.RequestPayload> TYPE = new Type(Ahrivaea.id("dex_request"));
      public static final StreamCodec<RegistryFriendlyByteBuf, Dex.RequestPayload> CODEC = StreamCodec.unit(new Dex.RequestPayload());

      public Type<Dex.RequestPayload> type() {
         return TYPE;
      }
   }
}
