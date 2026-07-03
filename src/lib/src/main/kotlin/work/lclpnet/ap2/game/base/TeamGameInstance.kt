package work.lclpnet.ap2.game.base

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.ext.logger
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.data.DataContainer
import work.lclpnet.ap2.game.data.type.TeamRef
import work.lclpnet.ap2.game.team.Team
import work.lclpnet.ap2.game.team.TeamManager
import work.lclpnet.ap2.game.team.TeamSpawnAccess
import work.lclpnet.ap2.game.util.useLastRemainingTeamListener
import work.lclpnet.ap2.game.util.useTeamWinManager
import work.lclpnet.game.map.GameMap
import work.lclpnet.game.map.MapUtils
import work.lclpnet.kibu.hook.util.PositionRotation
import java.util.*
import kotlin.concurrent.Volatile

abstract class TeamGameInstance(
    gameHandle: MiniGameHandle,
    world: ServerLevel,
    map: GameMap,
    val teamManager: TeamManager
) : MapGameInstance(gameHandle, world, map),
    TeamSpawnAccess {

    override val winManager = useTeamWinManager(teamManager, map) { data }
    override val participantListener = useLastRemainingTeamListener(
        teamManager,
        winManager,
        ::participantRemoved,
        ::teamEliminated
    )
    @Volatile
    private var teamSpawns: MutableMap<String, PositionRotation>? = null

    protected open fun participantRemoved(player: ServerPlayer) {

    }

    protected open fun teamEliminated(team: Team) {
        winManager.checkForLastRemaining()
    }

    protected open fun teleportTeamsToSpawns() {
        for (team in teamManager.teams) {
            val spawn = getSpawn(team)

            if (spawn == null) {
                logger.error("No spawn configured for team {} in map {}", team.key.id, map.descriptor.identifier)
                continue
            }

            for (player in team.players) {
                player.teleportTo(level, spawn.x(), spawn.y(), spawn.z(), emptySet(), spawn.yaw, spawn.pitch, true)
            }
        }
    }

    override fun getSpawn(team: Team): PositionRotation? =
        this.spawns[team.key.id]

    private val spawns: MutableMap<String, PositionRotation>
        get() {
            if (teamSpawns != null) {
                return teamSpawns!!
            }

            synchronized(this) {
                if (teamSpawns != null) return teamSpawns!!
                val spawns = MapUtils.getNamedSpawnPositionsAndRotation(map)
                teamSpawns = Collections.unmodifiableMap(spawns)
            }

            return teamSpawns!!
        }

    protected fun createReference(team: Team): TeamRef {
        return TeamRef(team.key, gameHandle.translations)
    }

    protected abstract val data: DataContainer<Team, TeamRef>
}