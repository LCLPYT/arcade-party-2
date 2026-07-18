package work.lclpnet.ap2.api.actor

import net.minecraft.resources.Identifier

data class ActorType<A : Actor>(
    val id: Identifier,
    val factory: ActorFactory<A>,
)
