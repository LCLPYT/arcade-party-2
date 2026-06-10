package work.lclpnet.ap2.game.base

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.api.event.IntScoreEventSource
import work.lclpnet.ap2.api.game.WinManagerAccess
import work.lclpnet.ap2.api.game.WinManagerView
import work.lclpnet.ap2.api.game.data.DataContainer
import work.lclpnet.ap2.api.game.team.Team
import work.lclpnet.ap2.api.game.team.TeamEliminatedListener
import work.lclpnet.ap2.api.game.team.TeamManager
import work.lclpnet.ap2.api.game.team.TeamSpawnAccess
import work.lclpnet.ap2.api.stats.CommonStats
import work.lclpnet.ap2.api.stats.Stat
import work.lclpnet.ap2.api.stats.TeamStatsManager
import work.lclpnet.ap2.ext.logger
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.player.ParticipantListener
import work.lclpnet.ap2.impl.game.WinManager
import work.lclpnet.ap2.impl.game.WinManagerAccessImpl
import work.lclpnet.ap2.impl.game.data.type.TeamGameResult
import work.lclpnet.ap2.impl.game.data.type.TeamRef
import work.lclpnet.ap2.impl.game.data.type.TeamRefResolver
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

    protected val resolver = TeamRefResolver(teamManager)
    protected val winManager: WinManager<Team, TeamRef>
    @Volatile
    private var teamSpawns: MutableMap<String, PositionRotation>? = null

    init {
        val data: WinManager.Data<Team, TeamRef> = WinManager.Data(
            { data },
            { player -> teamManager.getTeam(player) },
            { team -> createReference(team) },
            { player -> createReferenceFor(player) },
            { dataContainer ->
                TeamGameResult(dataContainer, resolver)
            }
        )

        this.winManager = WinManager(gameHandle, this::map, data)

        teamManager.bind(this)
    }

    override val participantListener: ParticipantListener
        get() = this

    override fun start() {
        for (team in teamManager.getTeams()) {
            data.identityIfAbsent(team)
        }

        super.start()
    }

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

    protected fun createReferenceFor(player: ServerPlayer): TeamRef? {
        val team = teamManager.getTeam(player)

        return team.map { team ->
            createReference(team)
        }.orElse(null)
    }

    override fun getWinManagerAccess(): WinManagerAccess = WinManagerAccessImpl(
        winManager,
        { player -> teamManager.getTeam(player) },
        this.data
    )

    protected abstract val data: DataContainer<Team, TeamRef>

    fun createStats(
        teamStats: Iterable<Stat<out Any>>,
        playerStats: Iterable<Stat<out Any>>
    ): TeamStatsManager {
        val manager = TeamStatsManager(teamStats.toSet(), playerStats.toSet()) { team ->
            this.createReference(team)
        }

        winManager.setStatsManager(manager)

        return manager
    }

    fun createStats(
        teamScore: IntScoreEventSource<Team>,
        teamStats: Iterable<Stat<out Any>>,
        memberStats: Iterable<Stat<out Any>>
    ): TeamStatsManager {
        val manager = TeamStatsManager(
            buildSet {
                add(CommonStats.Score)
                addAll(teamStats)
            },
            memberStats.toSet()
        ) { team ->
            createReference(team)
        }

        teamScore.register { team, score ->
            manager.teams.set(team, CommonStats.Score, score)
        }

        winManager.setStatsManager(manager)

        return manager
    }
}