package work.lclpnet.ap2.game.team

import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.game.player.PlayerRankView
import java.util.*

interface TeamConfig {

    val partitioner: TeamPartitioner

    val mapping: Map<ServerPlayer, TeamKey>

    companion object {
        fun uniform(): TeamConfig = object : TeamConfig {
            override val partitioner = UniformTeamPartitioner(Random())
            override val mapping = emptyMap<ServerPlayer, TeamKey>()
        }

        /**
         * A [TeamConfig] that balances teams by the overall rank of each player, so that the average
         * rank of each team is roughly equal.
         * @param rankView The view providing the overall rank of each player.
         */
        fun balanced(rankView: PlayerRankView): TeamConfig = object : TeamConfig {
            override val partitioner = BalancedTeamPartitioner(rankView, Random())
            override val mapping = emptyMap<ServerPlayer, TeamKey>()
        }
    }
}
