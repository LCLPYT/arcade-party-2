package work.lclpnet.ap2.game.team

import net.minecraft.network.chat.MutableComponent
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.players.PlayerList
import net.minecraft.world.scores.PlayerTeam
import work.lclpnet.ap2.core.hook.PlayerDisplayNameCallback
import work.lclpnet.ap2.game.util.PlayerUtil
import work.lclpnet.ap2.impl.game.team.SimpleTeam
import work.lclpnet.ap2.util.scoreboard.CustomScoreboardManager
import work.lclpnet.kibu.hook.HookRegistrar
import work.lclpnet.kibu.hook.player.PlayerConnectionHooks
import java.util.*

class SimpleTeamManager(
    private val playerManager: PlayerList?,
    private val teamConfig: TeamConfig,
    private val scoreboard: CustomScoreboardManager,
    private val playerUtil: PlayerUtil
) : TeamManager {

    val teamsByKey = mutableMapOf<TeamKey, Team>()
    private val mcTeams: MutableMap<TeamKey?, PlayerTeam?> = HashMap<TeamKey?, PlayerTeam?>()
    private val playerTeams: MutableMap<UUID?, Team?> = HashMap<UUID?, Team?>()
    private val eliminated: MutableSet<TeamKey?> = HashSet<TeamKey?>()
    private var listener: TeamEliminatedListener? = null
    private var useColorCodes = false

    override val teams: Set<Team>
        get() = teamsByKey.values.toSet()

    override fun getMinecraftTeam(key: TeamKey): PlayerTeam? =
        mcTeams[key]

    override fun getTeam(key: TeamKey): Team? =
        teamsByKey[key]

    override fun getTeam(uuid: UUID): Team? =
        playerTeams[uuid]

    override fun partitionIntoTeams(players: Iterable<ServerPlayer>, teams: Iterable<TeamKey>) {
        synchronized(this) {
            reset()

            for (key in teams) {
                registerTeam(key)
            }
        }

        // use pre-configured team constellations
        for ((player, key) in teamConfig.mapping) {
            val team = getTeam(key)

            checkNotNull(team) { "Team '${key.id}' is not registered" }

            joinTeam(player, team)
        }

        // distribute the rest of the players
        val notMapped = players.toMutableSet()
        notMapped.removeAll(teamConfig.mapping.keys)

        val partitioner = teamConfig.partitioner
        val partitions = partitioner.splitIntoTeams(notMapped, this.teams)

        for ((player, team) in partitions) {
            joinTeam(player, team)
        }

        playerUtil.updatePlayerListNames(players.toSet())
    }

    override fun isParticipating(key: TeamKey): Boolean {
        return !eliminated.contains(key)
    }

    override fun setTeamEliminated(team: Team) {
        if (!hasTeam(team.key)) return

        if (eliminated.add(team.key)) {
            listener?.teamEliminated(team)
        }
    }

    override fun bind(listener: TeamEliminatedListener?) {
        this.listener = listener
    }

    override fun setUseColorCodes(useColorCodes: Boolean) {
        this.useColorCodes = useColorCodes
    }

    private fun hasTeam(team: TeamKey): Boolean {
        return teamsByKey.containsKey(team)
    }

    @Synchronized
    override fun registerTeam(key: TeamKey): Team {
        val team = createTeam(key)
        teamsByKey[key] = team
        return team
    }

    private fun createTeam(key: TeamKey): SimpleTeam {
        check(!hasTeamId(key.id)) { "Duplicate team id '${key.id}'" }

        val mcTeam = scoreboard.createTeam(key.id)

        if (useColorCodes) {
            mcTeam.color = Optional.of(key.teamColor)
        }

        mcTeams[key] = mcTeam

        return SimpleTeam(key, playerManager)
    }

    private fun hasTeamId(id: String): Boolean =
        teamsByKey.keys.any { key -> key.id == id }

    override fun joinTeam(player: ServerPlayer, team: Team) {
        synchronized(this) {
            team.addPlayer(player)
            playerTeams[player.getUUID()] = team

            val mcTeam = mcTeams[team.key]

            if (mcTeam != null) {
                scoreboard.joinTeam(player, mcTeam)
            }
        }
    }

    private fun leaveTeam(player: ServerPlayer) {
        synchronized(this) {
            val team = playerTeams.remove(player.getUUID()) ?: return

            team.removePlayer(player)

            val mcTeam = mcTeams[team.key]

            if (mcTeam != null) {
                scoreboard.leaveTeam(player, mcTeam)
            }
        }
    }

    private fun destroyMcTeam(key: TeamKey) {
        scoreboard.removeTeam(key.id)

        synchronized(this) {
            mcTeams.remove(key)
        }
    }

    @Synchronized
    private fun reset() {
        for (key in teamsByKey.keys) {
            destroyMcTeam(key)
        }

        teamsByKey.clear()
        playerTeams.clear()
        mcTeams.clear()
    }

    fun init(hooks: HookRegistrar) {
        // move player back into the minecraft team, as they are automatically removed when quitting by the CustomScoreboardManager
        PlayerConnectionHooks.JOIN.registerWith(hooks) { player ->
            val mcTeam = synchronized(this) {
                val team = playerTeams[player.uuid] ?: return@registerWith

                mcTeams[team.key]
            }

            if (mcTeam != null) {
                scoreboard.joinTeam(player, mcTeam)
            }
        }

        PlayerDisplayNameCallback.HOOK.registerWith(hooks) { player, name ->
            val team = synchronized(this) {
                playerTeams[player.uuid]
            } ?: return@registerWith name

            val text = name as? MutableComponent ?: name.copy()

            text.withColor(team.key.color)
        }
    }
}