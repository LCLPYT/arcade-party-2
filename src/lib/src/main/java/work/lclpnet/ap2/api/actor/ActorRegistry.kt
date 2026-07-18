package work.lclpnet.ap2.api.actor

import net.minecraft.resources.Identifier
import java.util.*

class ActorRegistry {
    private val types = HashMap<Identifier, ActorType<*>>()

    fun register(type: ActorType<*>) {
        types[type.id] = type
    }

    fun getType(id: Identifier): ActorType<*>? =
        types.getOrDefault(id, null)
}
