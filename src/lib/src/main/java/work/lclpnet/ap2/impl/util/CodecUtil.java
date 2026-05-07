package work.lclpnet.ap2.impl.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.function.Function;
import java.util.function.Predicate;

public class CodecUtil {

    public static final Codec<Double> FINITE_DOUBLE = validate(Codec.DOUBLE, Double::isFinite, v -> "Value must be finite: " + v);
    public static final Codec<Integer> POSITIVE_INT = validate(Codec.INT, i -> i > 0, v -> "Value must be positive: " + v);

    public static <T> Codec<T> validate(Codec<T> codec, Predicate<T> predicate, Function<T, String> errorMessage) {
        return codec.validate(value -> predicate.test(value)
                ? DataResult.success(value)
                : DataResult.error(() -> errorMessage.apply(value)));
    }

    private CodecUtil() {}
}
