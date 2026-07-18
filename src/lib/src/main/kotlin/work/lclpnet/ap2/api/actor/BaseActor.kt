package work.lclpnet.ap2.api.actor

import net.minecraft.world.phys.Vec3

open class BaseActor(init: ActorInit) : Actor {

    override val world = init.world
    override val type = init.actorType

    override var position = Vec3.ZERO
}
