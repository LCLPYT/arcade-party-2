package work.lclpnet.ap2.game.data.type

import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.players.PlayerList
import work.lclpnet.ap2.api.game.data.SubjectRefResolver

class PlayerRefResolver(private val playerManager: PlayerList) : SubjectRefResolver<ServerPlayer, PlayerRef> {

    override fun resolve(ref: PlayerRef): ServerPlayer? {
        return playerManager.getPlayer(ref.uuid)
    }
}
