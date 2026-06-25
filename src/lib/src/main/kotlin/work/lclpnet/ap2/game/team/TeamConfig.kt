package work.lclpnet.ap2.game.team

import net.minecraft.server.level.ServerPlayer
import java.util.*

interface TeamConfig {

    val partitioner: TeamPartitioner

    val mapping: Map<ServerPlayer, TeamKey>

    companion object {
        val DEFAULT_CONFIG = object : TeamConfig {
            override val partitioner = UniformTeamPartitioner(Random())
            override val mapping = emptyMap<ServerPlayer, TeamKey>()
        }
    }
}
