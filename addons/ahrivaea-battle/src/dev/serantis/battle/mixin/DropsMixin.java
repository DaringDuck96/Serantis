package dev.serantis.battle.mixin;

import dev.ahrivaea.creature.Drops;
import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Species;
import dev.serantis.battle.skills.SkillEffects;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Mixin(Drops.class)
public abstract class DropsMixin {
    @Redirect(method = "roll", at = @At(value = "INVOKE", target = "Ldev/ahrivaea/creature/Species$Drop;chance()F"))
    private static float serantis$treasureSense(Species.Drop drop) {
        return SkillEffects.dropChance(drop.chance());
    }

    /** Farm animals, crabs and fish drop three times the meat and fish (food shouldn't be a grind). */
    @Unique private static final Set<String> SERANTIS_FOOD_ANIMALS = Set.of("cow", "pig", "chicken", "sheep", "rabbit", "goat", "duck", "crab", "fish");
    @Unique private static final Set<String> SERANTIS_MEAT = Set.of("beef", "porkchop", "chicken", "mutton", "rabbit", "cod", "salmon", "tropical_fish",
            "cooked_beef", "cooked_porkchop", "cooked_chicken", "cooked_mutton", "cooked_rabbit", "cooked_cod", "cooked_salmon");

    @Inject(method = "roll", at = @At("RETURN"), cancellable = true)
    private static void serantis$moreMeat(Species species, Influence influence, int level, RandomSource random, RegistryAccess registries,
                                          CallbackInfoReturnable<List<ItemStack>> cir) {
        if (species == null || !SERANTIS_FOOD_ANIMALS.contains(species.id()) || cir.getReturnValue() == null) return;
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : cir.getReturnValue()) {
            var key = BuiltInRegistries.ITEM.getKey(s.getItem());
            if (!s.isEmpty() && "minecraft".equals(key.getNamespace()) && SERANTIS_MEAT.contains(key.getPath())) {
                int total = s.getCount() * 3;
                while (total > 0) {
                    int n = Math.min(total, s.getMaxStackSize());
                    out.add(s.copyWithCount(n));
                    total -= n;
                }
            } else out.add(s);
        }
        cir.setReturnValue(out);
    }
}
