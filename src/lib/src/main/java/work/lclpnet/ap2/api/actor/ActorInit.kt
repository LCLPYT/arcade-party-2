package work.lclpnet.ap2.api.actor

import com.mojang.serialization.Dynamic
import net.minecraft.server.level.ServerLevel

data class ActorInit(
    val world: ServerLevel,
    val actorType: ActorType<*>,
    val dataSource: Dynamic<*>
)
