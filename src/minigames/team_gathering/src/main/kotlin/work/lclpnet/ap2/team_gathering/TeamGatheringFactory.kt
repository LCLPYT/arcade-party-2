package work.lclpnet.ap2.team_gathering

import net.minecraft.server.level.ServerLevel
import work.lclpnet.ap2.ext.mc.setDayTime
import work.lclpnet.ap2.game.MiniGameFactory
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.util.createTeamManager
import work.lclpnet.ap2.game.util.generateRandomLevel
import work.lclpnet.game.util.ResetWorldModifier

class TeamGatheringFactory : MiniGameFactory {

    override suspend fun createInstance(handle: MiniGameHandle): MiniGameInstance {
        val level = handle.generateRandomLevel()

        level.setDayTime(1000)

        val walls = ResetWorldModifier(level, handle.hooks)

        placeWalls(level, walls)

        val teamManager = handle.createTeamManager()

        return TeamGatheringInstance(handle, level, teamManager, walls)
    }

    fun placeWalls(level: ServerLevel, walls: ResetWorldModifier) {

    }
}