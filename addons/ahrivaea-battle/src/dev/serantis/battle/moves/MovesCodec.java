package dev.serantis.battle.moves;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import dev.ahrivaea.party.CaughtCreature;

/** Saves a caught creature's moves next to the creatures mod's own fields, as "serantis_moves". */
public final class MovesCodec implements Codec<CaughtCreature> {
    private static final String KEY = "serantis_moves";
    private static final String TEMPERAMENT = "serantis_temperament";
    private static final String NAME = "serantis_name";
    private static final String APPLES = "serantis_apples";
    private final Codec<CaughtCreature> inner;

    private MovesCodec(Codec<CaughtCreature> inner) { this.inner = inner; }

    public static Codec<CaughtCreature> wrap(Codec<CaughtCreature> inner) {
        return inner instanceof MovesCodec ? inner : new MovesCodec(inner);
    }

    @Override
    public <T> DataResult<T> encode(CaughtCreature input, DynamicOps<T> ops, T prefix) {
        DataResult<T> base = inner.encode(input, ops, prefix);
        MovesHolder h = (MovesHolder) (Object) input;
        String moves = h.serantis$moves(), temperament = h.serantis$temperament();
        if (moves != null && !moves.isEmpty()) base = base.flatMap(t -> ops.mergeToMap(t, ops.createString(KEY), ops.createString(moves)));
        if (temperament != null) base = base.flatMap(t -> ops.mergeToMap(t, ops.createString(TEMPERAMENT), ops.createString(temperament)));
        String nickname = h.serantis$nickname();
        if (nickname != null) base = base.flatMap(t -> ops.mergeToMap(t, ops.createString(NAME), ops.createString(nickname)));
        String apples = h.serantis$apples();
        if (apples != null) base = base.flatMap(t -> ops.mergeToMap(t, ops.createString(APPLES), ops.createString(apples)));
        return base;
    }

    @Override
    public <T> DataResult<Pair<CaughtCreature, T>> decode(DynamicOps<T> ops, T input) {
        return inner.decode(ops, input).map(pair -> {
            ops.get(input, KEY).flatMap(ops::getStringValue).result()
                    .ifPresent(s -> ((MovesHolder) (Object) pair.getFirst()).serantis$setMoves(s));
            ops.get(input, TEMPERAMENT).flatMap(ops::getStringValue).result()
                    .ifPresent(s -> ((MovesHolder) (Object) pair.getFirst()).serantis$setTemperament(s));
            ops.get(input, NAME).flatMap(ops::getStringValue).result()
                    .ifPresent(s -> ((MovesHolder) (Object) pair.getFirst()).serantis$setNickname(s));
            ops.get(input, APPLES).flatMap(ops::getStringValue).result()
                    .ifPresent(s -> ((MovesHolder) (Object) pair.getFirst()).serantis$setApples(s));
            return pair;
        });
    }
}
