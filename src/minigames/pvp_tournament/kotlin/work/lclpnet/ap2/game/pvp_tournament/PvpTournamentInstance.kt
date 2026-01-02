package work.lclpnet.ap2.game.pvp_tournament

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.future.future
import net.minecraft.server.level.ServerLevel
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.api.map.MapBootstrap
import work.lclpnet.ap2.ext.logger
import work.lclpnet.ap2.ext.players
import work.lclpnet.ap2.impl.game.EliminationGameInstance
import work.lclpnet.lobby.game.map.GameMap
import java.util.concurrent.CompletableFuture

enum class TournamentVariant {
    SINGLE_ELIMINATION,
    SWISS_STYLE,
}

class PvpTournamentInstance(gameHandle: MiniGameHandle) : EliminationGameInstance(gameHandle), MapBootstrap {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun createWorldBootstrap(
        world: ServerLevel,
        map: GameMap
    ): CompletableFuture<Void> {
        val setup = TournamentSetup(logger, map, players()) { path ->
            schematicBlocking(assetPath(path))
        }

        return scope.future {
            setup.setup(TournamentVariant.SINGLE_ELIMINATION)
        }.thenCompose { result ->
            gameHandle.server.submit {
                setup.placeArenas(world, result.arenas.values)
            }
        }
    }

    override fun prepare() {

    }

    override fun go() {

    }
}
