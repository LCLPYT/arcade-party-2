package work.lclpnet.ap2.api.actor

fun interface ActorRegistrar {
    fun register(type: ActorType<*>)
}
