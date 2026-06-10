package work.lclpnet.ap2.game.cozy_campfire

import kotlinx.coroutines.future.await
import work.lclpnet.ap2.game.MiniGameFactory
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.cozy_campfire.setup.CCBaseManager
import work.lclpnet.ap2.game.cozy_campfire.setup.CCReader
import work.lclpnet.ap2.game.util.createTeamManager
import work.lclpnet.ap2.game.util.openRandomMap

class CozyCampfireFactory : MiniGameFactory {

    override suspend fun createInstance(handle: MiniGameHandle): MiniGameInstance {
        val (level, map) = handle.openRandomMap()
        val teamManager = handle.createTeamManager()

        teamManager.partitionIntoTeams(handle.participants, setOf(TEAM_RED, TEAM_BLUE))

        val setup = CCReader(map, level, handle.logger)
        val bases = setup.readBases(teamManager.teams).await()
        val baseManager = CCBaseManager(bases, teamManager)

        return CozyCampfireInstance(handle, level, map, teamManager, baseManager)
    }
}