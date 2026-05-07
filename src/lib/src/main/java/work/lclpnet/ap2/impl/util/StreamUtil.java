package work.lclpnet.ap2.impl.util;

import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.lang.Math.min;
import static java.util.Spliterators.iterator;
import static java.util.Spliterators.spliterator;
import static java.util.stream.StreamSupport.stream;

public class StreamUtil {

    public static <A, B, C> Stream<C> zip(Stream<? extends A> streamA, Stream<? extends B> streamB, BiFunction<? super A, ? super B, ? extends C> zipper) {
        var spliteratorA = streamA.spliterator();
        var spliteratorB = streamB.spliterator();

        int characteristics = spliteratorA.characteristics() & spliteratorB.characteristics() & ~(Spliterator.SORTED | Spliterator.DISTINCT);

        long size = ((characteristics & Spliterator.SIZED) != 0)
                ? min(spliteratorA.getExactSizeIfKnown(), spliteratorB.getExactSizeIfKnown())
                : -1;

        var iteratorA = iterator(spliteratorA);
        var iteratorB = iterator(spliteratorB);

        var iteratorC = new Iterator<C>() {
            @Override
            public boolean hasNext() {
                return iteratorA.hasNext() && iteratorB.hasNext();
            }

            @Override
            public C next() {
                return zipper.apply(iteratorA.next(), iteratorB.next());
            }
        };

        var spliteratorC = spliterator(iteratorC, size, characteristics);

        return stream(spliteratorC, streamA.isParallel() || streamB.isParallel());
    }

    @SuppressWarnings("unchecked")
    public static <T, U> Function<T, Stream<? extends U>> instanceOf(Class<? extends U> type) {
        return t -> type.isInstance(t) ? Stream.of((U) t) : Stream.empty();
    }
}
