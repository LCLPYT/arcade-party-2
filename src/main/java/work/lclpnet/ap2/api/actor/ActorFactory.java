package work.lclpnet.ap2.api.actor;

import com.mojang.serialization.Codec;

import java.util.Optional;
import java.util.function.Consumer;

public interface ActorFactory<A extends Actor> {

    Optional<A> create(ActorInit init);

    static <A extends Actor, D> ActorFactory<A> withData(Codec<D> codec, Consumer<String> errorConsumer, WithData<A, D> factory) {
        return init -> codec.parse(init.dataSource())
                .resultOrPartial(errorConsumer)
                .map(data -> factory.create(init, data));
    }

    interface WithData<A extends Actor, D> {
        A create(ActorInit init, D data);
    }
}
