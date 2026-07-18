package work.lclpnet.ap2.api.actor

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3

interface Actor {

    val type: ActorType<*>

    var position: Vec3

    val world: ServerLevel

    fun onSpawn() {}

    fun onRemove() {}

    fun createData(): ActorData<*>? {
        return null
    }
}
