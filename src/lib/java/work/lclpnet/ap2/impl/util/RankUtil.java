package work.lclpnet.ap2.impl.util;

import java.util.Comparator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;

public class RankUtil {

    private RankUtil() {}

    public static <T> Stream<Set<T>> rank(Supplier<Stream<T>> source, Function<T, Integer> ranker) {
        return source.get()
                .collect(groupingBy(ranker))
                .entrySet().stream()
                .sorted(Comparator.comparingInt(Entry::getKey))
                .map(Entry::getValue)
                .map(Set::copyOf);
    }
}
