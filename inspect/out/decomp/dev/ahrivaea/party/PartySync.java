package dev.ahrivaea.party;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.base.Bases;
import dev.ahrivaea.battle.Battles;
import dev.ahrivaea.character.PlayerCharacter;
import dev.ahrivaea.creature.BattleSettings;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Job;
import dev.ahrivaea.creature.Mounts;
import dev.ahrivaea.creature.Species;
import dev.ahrivaea.creature.Stats;
import dev.ahrivaea.creature.Mounts.Step;
import dev.ahrivaea.villager.Profession;
import dev.ahrivaea.villager.Profession.Rank;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Disconnect;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Join;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public final class PartySync {
   public static final double OUT_RANGE = 64.0;
   private static final Map<UUID, String> LAST_SENT = new HashMap<>();

   private PartySync() {
   }

   public static void init() {
      PayloadTypeRegistry.clientboundPlay().register(PartySync.StatePayload.TYPE, PartySync.StatePayload.CODEC);
      ServerPlayConnectionEvents.JOIN.register((Join)(handler, sender, server) -> LAST_SENT.remove(handler.getPlayer().getUUID()));
      ServerPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, server) -> LAST_SENT.remove(handler.getPlayer().getUUID()));
      ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
         if (server.getTickCount() % 10 == 0) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
               sync(p);
            }
         }
      });
   }

   public static void sync(ServerPlayer p) {
      if (ServerPlayNetworking.canSend(p, PartySync.StatePayload.TYPE)) {
         String json = view(p).toString();
         if (!json.equals(LAST_SENT.get(p.getUUID()))) {
            LAST_SENT.put(p.getUUID(), json);
            ServerPlayNetworking.send(p, new PartySync.StatePayload(json));
         }
      }
   }

   public static JsonObject view(ServerPlayer p) {
      JsonObject o = new JsonObject();
      o.addProperty("xp", Party.xp(p));
      PlayerCharacter ch = PlayerCharacter.get(p);
      Stats cs = ch.battleStats(p);
      JsonObject me = new JsonObject();
      me.addProperty("name", p.getPlainTextName());
      me.addProperty("level", ch.level());
      me.addProperty("xp", ch.xp());
      me.addProperty("xpNext", ch.level() >= 50 ? 0 : PlayerCharacter.xpToNext(ch.level()));
      me.addProperty("points", ch.points());
      JsonArray mine = new JsonArray();
      JsonArray base = new JsonArray();

      for (int v : new int[]{cs.hp(), cs.attack(), cs.defense(), cs.speed(), cs.magic(), cs.luck(), cs.charisma()}) {
         mine.add(v);
      }

      Stats raw = ch.stats();

      for (int v : new int[]{raw.hp(), raw.attack(), raw.defense(), raw.speed(), raw.magic(), raw.luck(), raw.charisma()}) {
         base.add(v);
      }

      me.add("stats", mine);
      me.add("base", base);
      me.addProperty("maxHp", ch.maxHealth());
      me.addProperty("squares", cs.squaresPerTurn());
      me.addProperty("speedBonus", (int)Math.round(ch.speedBonus() * 100.0));
      me.addProperty("hearts", ch.extraHealth() / 2);
      o.add("character", me);
      JsonArray party = new JsonArray();

      for (CaughtCreature c : Party.get(p)) {
         party.add(describe(c));
      }

      o.add("party", party);
      JsonArray box = new JsonArray();

      for (CaughtCreature c : Party.box(p)) {
         box.add(describe(c));
      }

      o.add("box", box);
      JsonArray out = new JsonArray();
      if (Battles.of(p) == null) {
         List<CreatureEntity> near = p.level()
            .getEntitiesOfClass(
               CreatureEntity.class,
               p.getBoundingBox().inflate(64.0),
               ex -> ex.isTame() && ex.isOwnedBy(p) && !ex.hasAttached(Bases.WORKER_OF) && !Battles.inBattle(ex)
            );
         near.sort(Comparator.comparingInt(Entity::getId));

         for (CreatureEntity e : near) {
            JsonObject j = describe(e.toCaught());
            j.addProperty("hp", Math.round(e.getHealth()));
            j.addProperty("entity", e.getId());
            j.addProperty("riding", p.getVehicle() == e);
            j.addProperty("sitting", e.isOrderedToSit());
            out.add(j);
         }
      }

      o.add("out", out);
      return o;
   }

   public static JsonObject describe(CaughtCreature c) {
      JsonObject j = new JsonObject();
      Optional<Species> sp = Species.get(c.species());
      j.addProperty("name", c.variant().prefix() + sp.<String>map(sx -> sx.nameFor(c.influence())).orElse(c.species()));
      j.addProperty("species", c.species());
      if (c.job().trained()) {
         Profession pr = Profession.get(c.job().profession());
         if (pr != null) {
            j.addProperty(
               "profession",
               (c.job().rank() == 0 ? "Training as a " : ((Rank)pr.ranks().get(Math.min(c.job().rank(), pr.ranks().size()) - 1)).name() + " ") + pr.name()
            );
         }
      }

      j.addProperty("variant", c.variant().getSerializedName());
      j.addProperty("influence", c.influence().displayName());
      j.addProperty("color", c.influence().color());
      j.addProperty("level", c.level());
      j.addProperty("xp", c.xp());
      j.addProperty("xpNext", BattleSettings.xpToNext(c.level()));
      j.addProperty("hp", Math.round(c.health()));
      if (sp.isEmpty()) {
         return j;
      } else {
         Species s = sp.get();
         Stats st = s.statsFor(c.influence());
         j.addProperty("maxHp", st.maxHealth(c.level()));
         j.addProperty("hp", Math.min(Math.round(c.health()), st.maxHealth(c.level())));
         JsonArray stats = new JsonArray();
         int[] base = new int[]{st.hp(), st.attack(), st.defense(), st.speed(), st.magic(), st.luck(), st.charisma()};

         for (int b : base) {
            JsonArray pair = new JsonArray();
            pair.add((int)Math.round(st.effective(b, c.level())));
            pair.add(b);
            stats.add(pair);
         }

         j.add("stats", stats);
         j.addProperty("squares", st.squaresPerTurn());
         j.addProperty("movement", s.movement().getSerializedName());
         StringBuilder jobs = new StringBuilder();

         for (Job job : s.jobsFor(c.influence())) {
            jobs.append(jobs.isEmpty() ? "" : ", ").append(job.displayName());
         }

         j.addProperty("jobs", jobs.isEmpty() ? "none" : jobs.toString());
         boolean rideable = Mounts.rideable(s.sizeFor(c.influence()));
         j.addProperty("rideable", rideable);
         if (rideable) {
            double speed = st.effective(st.speed(), c.level());
            int tier = Mounts.tier(c.mountTier(), speed);
            j.addProperty("rideTier", tier);
            j.addProperty("topSpeed", (double)Math.round(Mounts.blocksPerSecond(speed, tier) * 10.0) / 10.0);
            j.addProperty("saddled", c.saddled());
            Step step = Mounts.stepFrom(c.mountTier());
            j.addProperty(
               "nextTier",
               step != null
                  ? c.mountFed() + "/" + step.amount() + " " + step.treat().getDefaultInstance().getHoverName().getString()
                  : (tier >= Mounts.MAX_TIER ? "top tier" : "Speed " + (tier < 4 ? 60 : 80) + " (it has " + Math.round(speed) + ")")
            );
         }

         return j;
      }
   }

   public static record StatePayload(String json) implements CustomPacketPayload {
      public static final Type<PartySync.StatePayload> TYPE = new Type(Ahrivaea.id("party_state"));
      public static final StreamCodec<RegistryFriendlyByteBuf, PartySync.StatePayload> CODEC = StreamCodec.composite(
         ByteBufCodecs.stringUtf8(1048576), PartySync.StatePayload::json, PartySync.StatePayload::new
      );

      public Type<PartySync.StatePayload> type() {
         return TYPE;
      }
   }
}
