package work.lclpnet.ap2.api.actor

import com.mojang.serialization.Codec
import java.util.*
import java.util.function.Consumer
import java.util.function.Function

fun interface ActorFactory<A : Actor> {

    fun create(init: ActorInit): A?

    fun interface WithData<A : Actor, D> {
        fun create(init: ActorInit, data: D): A
    }

    companion object {
        fun <A : Actor, D> withData(
            codec: Codec<D>,
            errorConsumer: (String) -> Unit,
            factory: WithData<A, D>
        ): ActorFactory<A> = ActorFactory { init ->
            codec.parse(init.dataSource)
                .resultOrPartial(errorConsumer)
                .map { data -> factory.create(init, data) }
                .orElse(null)
        }
    }
}
