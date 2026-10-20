package dev.serantis.battle.moves;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import dev.ahrivaea.party.CaughtCreature;

/** Saves a caught creature's moves next to the creatures mod's own fields, as "serantis_moves". */
public final class MovesCodec implements Codec<CaughtCreature> {
    private static final String KEY = "serantis_moves";
    private final Codec<CaughtCreature> inner;

    private MovesCodec(Codec<CaughtCreature> inner) { this.inner = inner; }

    public static Codec<CaughtCreature> wrap(Codec<CaughtCreature> inner) {
        return inner instanceof MovesCodec ? inner : new MovesCodec(inner);
    }

    @Override
    public <T> DataResult<T> encode(CaughtCreature input, DynamicOps<T> ops, T prefix) {
        DataResult<T> base = inner.encode(input, ops, prefix);
        String moves = ((MovesHolder) (Object) input).serantis$moves();
        if (moves == null || moves.isEmpty()) return base;
        return base.flatMap(t -> ops.mergeToMap(t, ops.createString(KEY), ops.createString(moves)));
    }

    @Override
    public <T> DataResult<Pair<CaughtCreature, T>> decode(DynamicOps<T> ops, T input) {
        return inner.decode(ops, input).map(pair -> {
            ops.get(input, KEY).flatMap(ops::getStringValue).result()
                    .ifPresent(s -> ((MovesHolder) (Object) pair.getFirst()).serantis$setMoves(s));
            return pair;
        });
    }
}
