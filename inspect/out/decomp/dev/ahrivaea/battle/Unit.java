package dev.ahrivaea.battle;

import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Stats;
import dev.ahrivaea.creature.Species.Movement;
import java.util.List;
import java.util.UUID;

public final class Unit {
   public final int id;
   public final Unit.Side side;
   public final boolean isPlayer;
   public final String name;
   public final Stats stats;
   public final int level;
   public final Influence influence;
   public final Movement movement;
   public final List<BattleMove> moves;
   public final int maxHp;
   public int hp;
   public int x;
   public int z;
   public boolean acted;
   public double rally = 1.0;
   public UUID entity;
   public final String speciesId;
   public final int partySlot;

   public Unit(
      int id,
      Unit.Side side,
      boolean isPlayer,
      String name,
      Stats stats,
      int level,
      Influence influence,
      Movement movement,
      List<BattleMove> moves,
      int maxHp,
      int hp,
      String speciesId,
      int partySlot
   ) {
      this.id = id;
      this.side = side;
      this.isPlayer = isPlayer;
      this.name = name;
      this.stats = stats;
      this.level = level;
      this.influence = influence;
      this.movement = movement;
      this.moves = moves;
      this.maxHp = maxHp;
      this.hp = hp;
      this.speciesId = speciesId;
      this.partySlot = partySlot;
   }

   public boolean alive() {
      return this.hp > 0;
   }

   public int squares() {
      return this.stats.squaresPerTurn();
   }

   public boolean flies() {
      return this.movement == Movement.FLY;
   }

   public DamageCalc.Fighter fighter(boolean inWater) {
      return new DamageCalc.Fighter(this.stats, this.level, this.influence, inWater, this.isPlayer);
   }

   public double charisma() {
      return this.isPlayer ? (double)this.stats.charisma() : this.stats.effective(this.stats.charisma(), this.level);
   }

   public static enum Side {
      PLAYER,
      ENEMY;
   }
}
