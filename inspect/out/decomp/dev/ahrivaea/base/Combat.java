package dev.ahrivaea.base;

import dev.ahrivaea.battle.DamageCalc;
import dev.ahrivaea.battle.DamageCalc.Category;
import dev.ahrivaea.battle.DamageCalc.Fighter;
import dev.ahrivaea.battle.DamageCalc.Move;
import dev.ahrivaea.battle.DamageCalc.Result;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Influence;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public final class Combat {
   public static final double MELEE = 2.8;
   public static final double BOLT = 6.0;
   private static final Move STRIKE = new Move(40, Category.PHYSICAL, Influence.NONE, 0.95);

   private Combat() {
   }

   private static Move bolt(CreatureEntity c) {
      return new Move(35, Category.MAGIC, c.getInfluence(), 0.9);
   }

   private static Fighter fighter(CreatureEntity c) {
      return new Fighter(c.getStats(), c.getCreatureLevel(), c.getInfluence(), c.isInWater(), false);
   }

   public static double reach(CreatureEntity a, CreatureEntity target) {
      Result strike = DamageCalc.calculate(fighter(a), fighter(target), STRIKE, false, 1.0, 0.0);
      Result magic = DamageCalc.calculate(fighter(a), fighter(target), bolt(a), false, 1.0, 0.0);
      return magic.expected() > strike.expected() ? 6.0 : 2.8;
   }

   public static int hit(ServerLevel level, CreatureEntity a, CreatureEntity target) {
      Result strike = DamageCalc.calculate(fighter(a), fighter(target), STRIKE, false, 1.0, 0.0);
      Move magicMove = bolt(a);
      Result magic = DamageCalc.calculate(fighter(a), fighter(target), magicMove, false, 1.0, 0.0);
      boolean useMagic = magic.expected() > strike.expected() && (double)a.distanceTo(target) <= 6.0;
      Move move = useMagic ? magicMove : STRIKE;
      Result plan = useMagic ? magic : strike;
      if (level.getRandom().nextDouble() >= plan.hitChance()) {
         level.sendParticles(ParticleTypes.CLOUD, target.getX(), target.getY() + 1.0, target.getZ(), 4, 0.3, 0.3, 0.3, 0.02);
         return 0;
      } else {
         boolean crit = level.getRandom().nextDouble() < plan.critChance();
         int dmg = DamageCalc.calculate(fighter(a), fighter(target), move, crit, 0.9 + level.getRandom().nextDouble() * 0.2, 0.0).damage();
         target.hurtServer(level, level.damageSources().mobAttack(a), (float)dmg);
         level.sendParticles(
            useMagic ? ParticleTypes.ENCHANTED_HIT : ParticleTypes.CRIT, target.getX(), target.getY() + 1.0, target.getZ(), 8, 0.3, 0.4, 0.3, 0.1
         );
         level.playSound(
            null, target.blockPosition(), useMagic ? SoundEvents.EVOKER_CAST_SPELL : SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 0.8F, 1.1F
         );
         return dmg;
      }
   }
}
