package dev.ahrivaea.battle;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.character.PlayerCharacter;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Drops;
import dev.ahrivaea.creature.Healing;
import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Species;
import dev.ahrivaea.creature.Stats;
import dev.ahrivaea.creature.Variant;
import dev.ahrivaea.party.CaughtCreature;
import dev.ahrivaea.party.Party;
import dev.ahrivaea.party.RecruitRules;
import dev.ahrivaea.villager.Villagers;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;

public final class Battle {
   public final UUID playerId;
   public final ServerLevel level;
   public final BattleGrid grid;
   public final List<Unit> units = new ArrayList<>();
   public final List<String> log = new ArrayList<>();
   public final List<Battle.Defeated> defeated = new ArrayList<>();
   public final RandomSource random;
   public Battle.Phase phase = Battle.Phase.PLAYER;
   public int turn = 1;
   public Battle.Outcome outcome;
   public int poolXp;
   public int characterXp;
   final boolean startedWithCreatures;
   private final List<Unit> enemyQueue = new ArrayList<>();
   int enemyDelay;
   private int nextId = 1;
   int arenaSlot = -1;
   Battle.Wild wild;
   final List<ItemStack> loot = new ArrayList<>();
   final List<String> lootNames = new ArrayList<>();
   Unit lead;
   final List<CaughtCreature> outCreatures = new ArrayList<>();

   public String whyNotRecruit(Battle.Defeated d) {
      Optional<Species> s = Species.get(d.species());
      return s.isEmpty() ? "it no longer exists" : null;
   }

   static int outSlot(int index) {
      return -2 - index;
   }

   Battle(ServerPlayer player, ServerLevel level, BattleGrid grid, boolean startedWithCreatures) {
      this.playerId = player.getUUID();
      this.level = level;
      this.grid = grid;
      this.random = level.getRandom();
      this.startedWithCreatures = startedWithCreatures;
   }

   int newId() {
      return this.nextId++;
   }

   public Unit unit(int id) {
      for (Unit u : this.units) {
         if (u.id == id) {
            return u;
         }
      }

      return null;
   }

   public Unit unitAt(int x, int z) {
      for (Unit u : this.units) {
         if (u.alive() && u.x == x && u.z == z) {
            return u;
         }
      }

      return null;
   }

   public Unit playerUnit() {
      for (Unit u : this.units) {
         if (u.isPlayer) {
            return u;
         }
      }

      return null;
   }

   public Pathing.Occupancy occupancy(Unit mover) {
      return (x, z) -> {
         Unit o = this.unitAt(x, z);
         if (o != null && o != mover) {
            return o.side == mover.side ? 1 : 2;
         } else {
            return 0;
         }
      };
   }

   public Map<Integer, Integer> reachable(Unit u) {
      return Pathing.reachable(this.grid, u.x, u.z, u.squares(), u.flies(), u.influence == Influence.FIRE, this.occupancy(u));
   }

   public boolean canStop(Unit u, int x, int z) {
      return Pathing.canStop(this.grid, x, z, u.flies(), u.influence == Influence.FIRE, this.occupancy(u), u.x, u.z);
   }

   private boolean inWater(Unit u) {
      BattleGrid.Terrain t = this.grid.at(u.x, u.z);
      return t == BattleGrid.Terrain.WATER || t == BattleGrid.Terrain.DEEP_WATER;
   }

   public String act(Battle.Action a) {
      if (a.kind().equals("flee")) {
         return this.phase == Battle.Phase.OVER ? "The battle is over." : this.flee();
      } else if (this.phase != Battle.Phase.PLAYER) {
         return "It isn't your turn.";
      } else if (a.kind().equals("end_turn")) {
         this.startEnemyPhase();
         return null;
      } else {
         Unit u = this.unit(a.unit());
         if (u == null || !u.alive() || u.side != Unit.Side.PLAYER) {
            return "That's not one of your units.";
         } else if (u.acted) {
            return u.name + " has already acted this turn.";
         } else if (!this.grid.inside(a.toX(), a.toZ())) {
            return "That's off the battlefield.";
         } else {
            Integer cost = this.reachable(u).get(a.toX() + a.toZ() * this.grid.size);
            if (cost != null && this.canStop(u, a.toX(), a.toZ())) {
               int ox = u.x;
               int oz = u.z;
               u.x = a.toX();
               u.z = a.toZ();
               String var7 = a.kind();

               String problem = switch (var7) {
                  case "attack" -> {
                     Unit t = this.unit(a.target());
                     yield a.move() >= 0 && a.move() < u.moves.size()
                        ? (
                           t != null && t.alive() && t.side != u.side
                              ? (!u.moves.get(a.move()).reaches(Pathing.distance(u.x, u.z, t.x, t.z)) ? "That target is out of range." : null)
                              : "Pick an enemy to attack."
                        )
                        : "No such move.";
                  }
                  case "item" -> u.isPlayer ? this.itemCheck(u, this.unit(a.target())) : "Only you can use items.";
                  case "rally" -> {
                     Unit t = this.unit(a.target());
                     yield !u.isPlayer
                        ? "Only you can rally."
                        : (
                           t != null && t != u && t.side == u.side && t.alive() && Pathing.distance(u.x, u.z, t.x, t.z) == 1
                              ? null
                              : "Rally a creature next to you."
                        );
                  }
                  case "wait" -> null;
                  default -> "Unknown action.";
               };
               if (problem != null) {
                  u.x = ox;
                  u.z = oz;
                  return problem;
               } else {
                  Battles.placeEntity(this, u);
                  var7 = a.kind();
                  switch (var7) {
                     case "attack":
                        this.attack(u, this.unit(a.target()), u.moves.get(a.move()), true);
                        break;
                     case "item":
                        this.useItem(u, this.unit(a.target()));
                        break;
                     case "rally":
                        Unit t = this.unit(a.target());
                        t.rally = 1.0 + u.charisma() / 200.0;
                        this.say(u.name + " rallies " + t.name + " (+" + Math.round((t.rally - 1.0) * 100.0) + "% damage on its next attack)");
                  }

                  u.acted = true;
                  if (!this.checkEnd() && this.units.stream().noneMatch(x -> x.alive() && x.side == Unit.Side.PLAYER && !x.acted)) {
                     this.startEnemyPhase();
                  }

                  return null;
               }
            } else {
               return u.name + " can't move there.";
            }
         }
      }
   }

   private String itemCheck(Unit u, Unit target) {
      ServerPlayer p = this.player();
      if (p != null && !Healing.firstPotion(p).isEmpty()) {
         return target != null && target.side == u.side && target.alive() && Pathing.distance(u.x, u.z, target.x, target.z) <= 1
            ? null
            : "Use it on yourself or a unit next to you.";
      } else {
         return "You have no healing potions. Brew Healing or Regeneration potions (food only heals outside battle).";
      }
   }

   private void useItem(Unit u, Unit target) {
      ServerPlayer p = this.player();
      ItemStack s = Healing.firstPotion(p);
      Healing.PotionHeal heal = Healing.potion(s);
      if (heal != null) {
         boolean drinkable = s.is(Items.POTION);
         if (!p.getAbilities().instabuild) {
            s.shrink(1);
         }

         if (drinkable) {
            Healing.giveBack(p, new ItemStack(Items.GLASS_BOTTLE));
         }

         StringBuilder msg = new StringBuilder(u.name + " uses " + heal.name() + " on " + (target == u ? "themself" : target.name));

         for (Unit t : this.units) {
            boolean hit = t == target || heal.splash() && t.side == target.side && t.alive() && Pathing.distance(t.x, t.z, target.x, target.z) == 1;
            if (hit) {
               int amount = Math.min(t.maxHp - t.hp, (int)Math.round((double)(t.maxHp * heal.percent()) / 100.0));
               t.hp += amount;
               msg.append(t == target ? ": " : ", ").append(t == target ? "" : t.name + " ").append("+").append(amount).append(" HP");
            }
         }

         this.say(msg.toString());
      }
   }

   private String flee() {
      this.say("You got away!");
      this.finish(Battle.Outcome.FLED);
      return null;
   }

   private void attack(Unit attacker, Unit target, BattleMove move, boolean allowCounter) {
      this.strike(attacker, target, move);
      if (allowCounter && target.alive() && attacker.alive()) {
         BattleMove counter = this.bestMove(target, attacker, Pathing.distance(attacker.x, attacker.z, target.x, target.z));
         if (counter != null) {
            this.say(target.name + " counters!");
            this.strike(target, attacker, counter);
         }
      }
   }

   private BattleMove bestMove(Unit from, Unit to, int distance) {
      BattleMove best = null;
      double bestDamage = -1.0;

      for (BattleMove m : from.moves) {
         if (m.reaches(distance)) {
            double d = this.preview(from, to, m).expected();
            if (d > bestDamage) {
               bestDamage = d;
               best = m;
            }
         }
      }

      return best;
   }

   DamageCalc.Result preview(Unit a, Unit d, BattleMove m) {
      double highGround = this.grid.y(a.x, a.z) - this.grid.y(d.x, d.z) >= 2 ? 0.1 : 0.0;
      double allies = 0.0;

      for (Unit o : this.units) {
         if (o != a && o.alive() && o.side == a.side && Pathing.distance(o.x, o.z, a.x, a.z) == 1) {
            allies += o.charisma();
         }
      }

      return DamageCalc.calculate(
         a.fighter(this.inWater(a)), d.fighter(this.inWater(d)), m.move(), false, 1.0, allies, new DamageCalc.Extras(highGround, a.rally)
      );
   }

   private void strike(Unit a, Unit d, BattleMove m) {
      DamageCalc.Result p = this.preview(a, d, m);
      double rally = a.rally;
      a.rally = 1.0;
      if (this.random.nextDouble() >= p.hitChance()) {
         this.say(a.name + " uses " + m.name() + " on " + d.name + " - missed!");
      } else {
         boolean crit = this.random.nextDouble() < p.critChance();
         double highGround = this.grid.y(a.x, a.z) - this.grid.y(d.x, d.z) >= 2 ? 0.1 : 0.0;
         double allies = 0.0;

         for (Unit o : this.units) {
            if (o != a && o.alive() && o.side == a.side && Pathing.distance(o.x, o.z, a.x, a.z) == 1) {
               allies += o.charisma();
            }
         }

         DamageCalc.Result r = DamageCalc.calculate(
            a.fighter(this.inWater(a)),
            d.fighter(this.inWater(d)),
            m.move(),
            crit,
            0.9 + this.random.nextDouble() * 0.2,
            allies,
            new DamageCalc.Extras(highGround, rally)
         );
         d.hp = Math.max(0, d.hp - r.damage());
         String eff = r.multiplier() > 1.0 ? " It's super effective!" : (r.multiplier() < 1.0 ? " It's not very effective." : "");
         this.say(a.name + " uses " + m.name() + " on " + d.name + ": " + r.damage() + " damage" + (crit ? " (critical!)" : "") + "." + eff);
         if (!d.alive()) {
            this.knockOut(d);
         }
      }
   }

   private void knockOut(Unit d) {
      this.say(d.name + (d.isPlayer ? " is knocked out!" : (d.side == Unit.Side.PLAYER ? " fainted!" : " is defeated!")));
      if (d.side == Unit.Side.ENEMY && this.level.getEntity(d.entity) instanceof CreatureEntity c) {
         this.defeated.add(new Battle.Defeated(c.getSpeciesId(), c.getInfluence(), c.getCreatureLevel(), d.name, c.getVariant()));
      }

      Battles.removeEntity(this, d);
   }

   private void startEnemyPhase() {
      this.phase = Battle.Phase.ENEMY;
      this.enemyQueue.clear();

      for (Unit u : this.units) {
         if (u.alive() && u.side == Unit.Side.ENEMY) {
            this.enemyQueue.add(u);
         }
      }

      this.enemyDelay = 10;
      this.say("Enemy phase");
   }

   boolean enemyStep() {
      while (!this.enemyQueue.isEmpty() && !this.enemyQueue.getFirst().alive()) {
         this.enemyQueue.removeFirst();
      }

      if (!this.enemyQueue.isEmpty() && this.phase == Battle.Phase.ENEMY) {
         Unit u = this.enemyQueue.removeFirst();
         Map<Integer, Integer> reach = this.reachable(u);
         int bestCell = -1;
         Unit bestTarget = null;
         BattleMove bestMove = null;
         double bestScore = -1.0;

         for (int cell : reach.keySet()) {
            int cx = cell % this.grid.size;
            int cz = cell / this.grid.size;
            if (this.canStop(u, cx, cz)) {
               int ox = u.x;
               int oz = u.z;
               u.x = cx;
               u.z = cz;

               for (Unit t : this.units) {
                  if (t.alive() && t.side != u.side) {
                     int dist = Pathing.distance(cx, cz, t.x, t.z);

                     for (BattleMove m : u.moves) {
                        if (m.reaches(dist)) {
                           DamageCalc.Result p = this.preview(u, t, m);
                           double score = p.expected()
                              + (double)(p.damage() >= t.hp ? 1000 : 0)
                              + (double)(t.isPlayer ? 15 : 0)
                              - (double)reach.get(cell).intValue() * 0.01;
                           if (score > bestScore) {
                              bestScore = score;
                              bestCell = cell;
                              bestTarget = t;
                              bestMove = m;
                           }
                        }
                     }
                  }
               }

               u.x = ox;
               u.z = oz;
            }
         }

         if (bestTarget != null) {
            u.x = bestCell % this.grid.size;
            u.z = bestCell / this.grid.size;
            Battles.placeEntity(this, u);
            this.attack(u, bestTarget, bestMove, true);
         } else {
            int best = -1;
            int bestDist = Integer.MAX_VALUE;

            for (int cellx : reach.keySet()) {
               int cx = cellx % this.grid.size;
               int cz = cellx / this.grid.size;
               if (this.canStop(u, cx, cz)) {
                  for (Unit tx : this.units) {
                     if (tx.alive() && tx.side != u.side) {
                        int d = Pathing.distance(cx, cz, tx.x, tx.z);
                        if (d < bestDist) {
                           bestDist = d;
                           best = cellx;
                        }
                     }
                  }
               }
            }

            if (best >= 0) {
               u.x = best % this.grid.size;
               u.z = best / this.grid.size;
               Battles.placeEntity(this, u);
               this.say(u.name + " moves closer.");
            }
         }

         this.checkEnd();
         return this.phase == Battle.Phase.ENEMY;
      } else {
         if (this.phase == Battle.Phase.ENEMY) {
            this.phase = Battle.Phase.PLAYER;
            this.turn++;

            for (Unit u : this.units) {
               u.acted = false;
            }

            this.say("Turn " + this.turn + ": your phase");
         }

         return false;
      }
   }

   boolean checkEnd() {
      if (this.phase == Battle.Phase.OVER) {
         return true;
      } else {
         Unit me = this.playerUnit();
         boolean enemiesLeft = this.units.stream().anyMatch(u -> u.alive() && u.side == Unit.Side.ENEMY);
         if (!enemiesLeft) {
            this.finish(Battle.Outcome.WON);
            return true;
         } else if (me != null && me.alive()) {
            return false;
         } else {
            this.finish(Battle.Outcome.LOST);
            return true;
         }
      }
   }

   private void finish(Battle.Outcome o) {
      this.outcome = o;
      this.phase = Battle.Phase.OVER;
      ServerPlayer p = this.player();
      if (o == Battle.Outcome.WON && p != null) {
         Villagers.award(p, Villagers.BATTLES_WON);
         ResourceKey<Level> where = (this.wild != null ? this.wild.level() : p.level()).dimension();
         if (where == Level.NETHER) {
            Villagers.award(p, Villagers.NETHER_BATTLES_WON);
         }

         if (where == Level.END) {
            Villagers.award(p, Villagers.END_BATTLES_WON);
         }
      }

      if (o == Battle.Outcome.WON) {
         int me = p == null ? 1 : PlayerCharacter.get(p).level();
         this.characterXp = 10;

         for (Battle.Defeated d : this.defeated) {
            this.characterXp = this.characterXp + (int)Math.max(2L, Math.round(10.0 * Math.clamp(1.0 + 0.1 * (double)(d.level() - me), 0.2, 2.0)));
         }

         this.poolXp = this.characterXp;
         this.say("Victory! +" + this.poolXp + " XP (spend it on your creatures or yourself on the party screen).");

         for (Battle.Defeated d : this.defeated) {
            Optional<Species> sp = Species.get(d.species());
            if (sp.isPresent()) {
               this.loot.addAll(Drops.roll(sp.get(), d.influence(), d.level(), this.random, this.level.registryAccess()));
            }
         }

         Level home = this.wild != null ? this.wild.level() : (p != null ? p.level() : this.level);
         if (home.isDarkOutside() && this.random.nextDouble() < 0.1) {
            this.loot.add(new ItemStack(Items.ENDER_PEARL));
         }

         if (home.dimension() == Level.NETHER) {
            for (Battle.Defeated dx : this.defeated) {
               if (dx.influence() == Influence.FIRE && this.random.nextDouble() < 0.25) {
                  this.loot.add(new ItemStack(Items.BLAZE_ROD));
               }
            }
         }

         for (ItemStack stack : this.loot) {
            this.lootNames.add(Drops.describe(stack));
         }

         if (p != null) {
            Iterator<ItemStack> it = this.loot.iterator();

            while (it.hasNext()) {
               ItemStack stack = it.next();
               if (p.getInventory().add(stack) && stack.isEmpty()) {
                  it.remove();
               }
            }

            Party.addXp(p, this.poolXp);
         }
      } else if (o == Battle.Outcome.LOST) {
         this.say("You were defeated and retreat.");
         this.defeated.clear();
      } else {
         this.defeated.clear();
      }

      Ahrivaea.LOGGER.info("Battle for {} ended: {} after {} turns", new Object[]{p == null ? this.playerId : p.getPlainTextName(), o, this.turn});
      Battles.cleanUp(this);
   }

   void say(String s) {
      this.log.add(s);
      if (this.log.size() > 40) {
         this.log.removeFirst();
      }
   }

   public ServerPlayer player() {
      return this.level.getServer().getPlayerList().getPlayer(this.playerId);
   }

   Unit addPlayer(ServerPlayer p, int x, int z) {
      PlayerCharacter c = PlayerCharacter.get(p);
      ItemStack hand = p.getMainHandItem();
      boolean ranged = hand.is(Items.BOW) || hand.is(Items.CROSSBOW);
      double dmg = ((ItemAttributeModifiers)hand.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY))
         .compute(Attributes.ATTACK_DAMAGE, 1.0, EquipmentSlot.MAINHAND);
      String weapon = hand.isEmpty() ? "Punch" : hand.getHoverName().getString();
      Unit u = new Unit(
         this.newId(),
         Unit.Side.PLAYER,
         true,
         p.getPlainTextName(),
         c.battleStats(p),
         c.level(),
         Influence.NONE,
         Species.Movement.WALK,
         List.of(BattleMove.forWeapon(weapon, dmg, ranged)),
         c.maxHealth(),
         c.maxHealth(),
         "",
         -1
      );
      u.x = x;
      u.z = z;
      u.entity = p.getUUID();
      this.units.add(u);
      return u;
   }

   Unit addCreature(Unit.Side side, Species species, Influence influence, int lvl, float health, int slot, int x, int z) {
      return this.addCreature(side, species, influence, Variant.NONE, lvl, health, slot, x, z);
   }

   Unit addCreature(Unit.Side side, Species species, Influence influence, Variant variant, int lvl, float health, int slot, int x, int z) {
      Stats stats = species.statsFor(influence);
      int max = stats.maxHealth(lvl);
      int hp = health <= 0.0F ? max : Math.max(1, Math.min(max, Math.round(health)));
      Unit u = new Unit(
         this.newId(),
         side,
         false,
         variant.prefix() + species.nameFor(influence),
         stats,
         lvl,
         influence,
         species.movement(),
         BattleMove.forCreature(influence),
         max,
         hp,
         species.id(),
         slot
      );
      u.x = x;
      u.z = z;
      this.units.add(u);
      return u;
   }

   void saveParty(ServerPlayer p) {
      List<CaughtCreature> party = Party.get(p);

      for (Unit u : this.units) {
         if (u.side == Unit.Side.PLAYER && !u.isPlayer) {
            int out = -2 - u.partySlot;
            if (out >= 0 && out < this.outCreatures.size()) {
               this.outCreatures.set(out, this.outCreatures.get(out).withHealth((float)Math.max(1, u.hp)));
            }

            if (u.partySlot >= 0 && u.partySlot < party.size()) {
               CaughtCreature c = party.get(u.partySlot);
               Party.set(p, u.partySlot, c.withHealth((float)Math.max(1, u.hp)));
            }
         }
      }
   }

   public JsonObject view(UUID viewer) {
      JsonObject o = new JsonObject();
      JsonObject g = new JsonObject();
      g.addProperty("size", this.grid.size);
      g.addProperty("ox", this.grid.originX);
      g.addProperty("oz", this.grid.originZ);
      JsonArray h = new JsonArray();
      StringBuilder t = new StringBuilder();

      for (int i = 0; i < this.grid.heights.length; i++) {
         h.add(this.grid.heights[i]);
         t.append(this.grid.terrain[i].code());
      }

      g.add("h", h);
      g.addProperty("t", t.toString());
      o.add("grid", g);
      JsonArray us = new JsonArray();

      for (Unit u : this.units) {
         JsonObject j = new JsonObject();
         j.addProperty("id", u.id);
         j.addProperty("side", u.side.name());
         j.addProperty("player", u.isPlayer);
         j.addProperty("name", u.name);
         j.addProperty("hp", u.hp);
         j.addProperty("max", u.maxHp);
         j.addProperty("x", u.x);
         j.addProperty("z", u.z);
         j.addProperty("acted", u.acted);
         j.addProperty("flies", u.flies());
         j.addProperty("lava", u.influence == Influence.FIRE);
         j.addProperty("squares", u.squares());
         j.addProperty("level", u.level);
         j.addProperty("influence", u.influence.displayName());
         j.addProperty("color", u.influence.color());
         JsonArray ms = new JsonArray();

         for (BattleMove m : u.moves) {
            JsonObject mj = new JsonObject();
            mj.addProperty("name", m.name());
            mj.addProperty("min", m.minRange());
            mj.addProperty("max", m.maxRange());
            mj.addProperty("power", m.move().power());
            mj.addProperty("kind", m.move().category().name());
            ms.add(mj);
         }

         j.add("moves", ms);
         us.add(j);
      }

      o.add("units", us);
      o.addProperty("phase", this.phase.name());
      o.addProperty("turn", this.turn);
      JsonArray lg = new JsonArray();

      for (int i = Math.max(0, this.log.size() - 8); i < this.log.size(); i++) {
         lg.add(this.log.get(i));
      }

      o.add("log", lg);
      if (this.outcome != null) {
         JsonObject r = new JsonObject();
         r.addProperty("outcome", this.outcome.name());
         r.addProperty("xp", this.poolXp);
         r.addProperty("charXp", this.characterXp);
         JsonArray lootJson = new JsonArray();

         for (String l : this.lootNames) {
            lootJson.add(l);
         }

         r.add("loot", lootJson);
         JsonArray rec = new JsonArray();

         for (Battle.Defeated d : this.defeated) {
            JsonObject dj = new JsonObject();
            dj.addProperty("name", d.name());
            dj.addProperty("level", d.level());
            dj.addProperty("cost", d.cost());
            dj.addProperty("rarity", d.rarity());
            String why = this.whyNotRecruit(d);
            if (why != null) {
               dj.addProperty("why", why);
            }

            rec.add(dj);
         }

         r.add("recruits", rec);
         o.add("result", r);
      }

      return o;
   }

   int[] freeCellNear(int x, int z, Unit.Side side) {
      for (int r = 0; r < this.grid.size; r++) {
         for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
               if (Math.max(Math.abs(dx), Math.abs(dz)) == r) {
                  int cx = x + dx;
                  int cz = z + dz;
                  if (this.grid.inside(cx, cz) && this.unitAt(cx, cz) == null) {
                     BattleGrid.Terrain t = this.grid.at(cx, cz);
                     if (t == BattleGrid.Terrain.GROUND || t == BattleGrid.Terrain.WATER) {
                        return new int[]{cx, cz};
                     }
                  }
               }
            }
         }
      }

      return new int[]{x, z};
   }

   public BlockPos cellPos(Unit u) {
      return this.grid.worldPos(u.x, u.z);
   }

   public static record Action(String kind, int unit, int toX, int toZ, int move, int target) {
   }

   public static record Defeated(String species, Influence influence, int level, String name, Variant variant) {
      public int cost() {
         return Species.get(this.species).map(s -> RecruitRules.cost(s, this.influence, this.level)).orElse(0);
      }

      public String rarity() {
         return Species.get(this.species).map(s -> RecruitRules.rarity(s, this.influence)).orElse("Common");
      }
   }

   public static enum Outcome {
      WON,
      LOST,
      FLED;
   }

   public static enum Phase {
      PLAYER,
      ENEMY,
      OVER;
   }

   static record Wild(ServerLevel level, String species, Influence influence, int creatureLevel, double x, double y, double z, Variant variant) {
   }
}
