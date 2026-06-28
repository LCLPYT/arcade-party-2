package work.lclpnet.ap2.team_gathering

import work.lclpnet.ap2.ext.mc.setDayTime
import work.lclpnet.ap2.game.MiniGameFactory
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.util.createBarrierPlatformAboveSpawnGround
import work.lclpnet.ap2.game.util.createTeamManager
import work.lclpnet.ap2.game.util.generateRandomLevel

class TeamGatheringFactory : MiniGameFactory {

    override suspend fun createInstance(handle: MiniGameHandle): MiniGameInstance {
        val level = handle.generateRandomLevel()

        level.setDayTime(1000)

        val walls = handle.createBarrierPlatformAboveSpawnGround(level)

        val teamManager = handle.createTeamManager()

        return TeamGatheringInstance(handle, level, teamManager, walls)
    }
}