package work.lclpnet.ap2.game.base

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.api.game.WinManagerAccess
import work.lclpnet.ap2.api.game.WinManagerView
import work.lclpnet.ap2.api.game.data.DataContainer
import work.lclpnet.ap2.api.game.team.Team
import work.lclpnet.ap2.api.game.team.TeamEliminatedListener
import work.lclpnet.ap2.api.game.team.TeamManager
import work.lclpnet.ap2.api.game.team.TeamSpawnAccess
import work.lclpnet.ap2.ext.logger
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.data.type.TeamRef
import work.lclpnet.ap2.game.player.ParticipantListener
import work.lclpnet.ap2.game.util.useTeamWinManager
import work.lclpnet.ap2.impl.game.WinManagerAccessImpl
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
    ParticipantListener,
    TeamEliminatedListener,
    TeamSpawnAccess,
    WinManagerView {

    protected val winManager = useTeamWinManager(teamManager, map) { data }
    @Volatile
    private var teamSpawns: MutableMap<String, PositionRotation>? = null

    init {
        teamManager.bind(this)
    }

    override val participantListener = this

    override fun participantRemoved(player: ServerPlayer) {
        val team = teamManager.getTeam(player).orElse(null)

        if (team == null || !teamManager.isParticipating(team)
            || !team.getParticipatingPlayers(gameHandle.participants).isEmpty()
        ) return

        teamManager.setTeamEliminated(team)
    }

    override fun teamEliminated(team: Team) {
        winManager.checkForLastRemaining()
    }

    protected open fun teleportTeamsToSpawns() {
        for (team in teamManager.getTeams()) {
            val spawn = getSpawn(team)

            if (spawn == null) {
                logger.error("No spawn configured for team {} in map {}", team.key().id(), map.descriptor.identifier)
                continue
            }

            for (player in team.getPlayers()) {
                player.teleportTo(level, spawn.x(), spawn.y(), spawn.z(), emptySet(), spawn.yaw, spawn.pitch, true)
            }
        }
    }

    override fun getSpawn(team: Team): PositionRotation? {
        return this.spawns[team.key().id()]
    }

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
        return TeamRef(team.key(), gameHandle.translations)
    }

    override fun getWinManagerAccess(): WinManagerAccess = WinManagerAccessImpl(
        winManager,
        { player -> teamManager.getTeam(player) },
        this.data
    )

    protected abstract val data: DataContainer<Team, TeamRef>
}