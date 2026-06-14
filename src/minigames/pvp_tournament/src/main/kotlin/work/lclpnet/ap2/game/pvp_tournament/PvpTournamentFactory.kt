package work.lclpnet.ap2.game.pvp_tournament

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.future.await
import kotlinx.coroutines.joinAll
import work.lclpnet.ap2.game.MiniGameFactory
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.data.type.PlayerRef
import work.lclpnet.ap2.game.util.assetPath
import work.lclpnet.ap2.game.util.openRandomMap
import work.lclpnet.ap2.game.util.schematic
import java.util.*

class PvpTournamentFactory : MiniGameFactory {

    override suspend fun createInstance(handle: MiniGameHandle): MiniGameInstance {
        val (level, map) = handle.openRandomMap()

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val visualizer = CanvasVisualizer(handle, scope)
        val playerSkins = visualizer.preloadPlayerSkins()

        val playerRefs = buildPlayerRefs(handle)
        val setup = TournamentSetup(handle.logger, map, playerRefs) { path ->
            handle.schematic(map.assetPath(path))
        }

        val result = setup.setup(TournamentVariant.SINGLE_ELIMINATION)

        val arenaPlacement = handle.server.submit {
            setup.placeArenas(level, result.arenas.values)
        }

        playerSkins.joinAll()

        visualizer.updateCanvas(result.tournament)

        arenaPlacement.await()

        return PvpTournamentInstance(handle, level, map, playerRefs, visualizer, scope, result)
    }

    private fun buildPlayerRefs(handle: MiniGameHandle): List<PlayerRef> {
        val refs = handle.participants.map { PlayerRef.create(it) }

        if (!DEBUG_FILL_WITH_NPC) return refs

        val targetPlayerCount = 12
        val extraPlayers = (targetPlayerCount - refs.size).coerceAtLeast(0)

        return refs + List(extraPlayers) { PlayerRef(UUID.randomUUID(), "NPC #${it + 1}") }
    }
}