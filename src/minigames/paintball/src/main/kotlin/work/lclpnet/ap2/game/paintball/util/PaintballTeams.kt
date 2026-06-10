package work.lclpnet.ap2.game.paintball.util

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import org.json.JSONObject
import org.slf4j.Logger
import work.lclpnet.ap2.api.game.team.DyeTeamKey
import work.lclpnet.ap2.api.game.team.TeamKey
import work.lclpnet.ap2.api.game.team.TeamManager
import work.lclpnet.ap2.game.player.Participants
import work.lclpnet.ap2.impl.game.team.ApTeams
import work.lclpnet.ap2.impl.util.StreamUtil
import work.lclpnet.gaco.core.api.Partial
import work.lclpnet.gaco.ds.IndexedSet
import work.lclpnet.game.map.GameMap
import java.util.*
import java.util.stream.Stream

class PaintballTeams(
    val teamManager: TeamManager,
    private val map: GameMap,
    private val participants: Participants,
    private val random: Random,
    private val logger: Logger,
    val openBases: () -> Unit,
) : Iterable<PaintballTeam> {

    private val teamsByKey = HashMap<TeamKey, PaintballTeam>()
    private val teamGroups = Object2IntOpenHashMap<PaintballTeam>()
    private lateinit var teams: List<PaintballTeam>

    fun setup() {
        teams = setupTeams()

        teamGroups.clear()
        var group = 0x2

        for (team in teams) {
            teamsByKey[team.key()] = team
            teamGroups.put(team, group)
            group = group shl 2
        }
    }

    private fun setupTeams(): List<PaintballTeam> {
        val props = map.properties
        val array = props.getJSONArray("teams")
        val partial = ArrayList<Partial<PaintballTeam, DyeTeamKey>>(array.length())

        for (entry in array) {
            if (entry !is JSONObject) {
                logger.error("Invalid team entry: {}", entry)
                continue
            }

            partial.add(paintballTeamFromJson(entry))
        }

        val colorPool = availableTeamColors(props)
        val base = colorPool.get(random.nextInt(colorPool.size))
        val complementary = ApTeams.complementary(base, colorPool, partial.size)

        return StreamUtil.zip(partial.stream(), complementary.stream()) { a, b ->
            a.with(b)
        }.toList()
    }

    private fun availableTeamColors(props: JSONObject): IndexedSet<DyeTeamKey> {
        val array = props.getJSONArray("available-team-colors")
        val teamPool = IndexedSet<DyeTeamKey>()

        for (item in array) {
            if (item !is String) {
                logger.warn("Expected team id of type string, but got: {}", item)
                continue
            }

            val key = DyeTeamKey.byId(item)

            if (key == null) {
                logger.warn("Unknown team color \"{}\"", item)
                continue
            }

            teamPool.add(key)
        }

        return teamPool
    }

    fun isMember(pbt: PaintballTeam, player: ServerPlayer): Boolean =
        teamOf(player).map { it == pbt }.orElse(false) ?: false

    fun teamOf(player: ServerPlayer): Optional<PaintballTeam> =
        teamManager.getTeam(player)
            .map { it.key() }
            .flatMap { Optional.ofNullable(teamsByKey[it]) }

    fun teamBaseAt(pos: BlockPos): Optional<PaintballTeam> {
        for (team in teams) {
            if (team.baseBounds.contains(pos)) {
                return Optional.of(team)
            }
        }

        return Optional.empty()
    }

    fun playerGroup(team: PaintballTeam): Int =
        teamGroups.getOrDefault(team, 0x1)

    fun bulletGroup(player: ServerPlayer): Int {
        val team = teamOf(player).orElse(null) ?: return 0x1
        val group = playerGroup(team)

        return if (group == 0 || group == 1) group else bulletGroup(group)
    }

    fun bulletGroup(group: Int): Int = group shl 1

    fun bulletCollisionFlags(player: ServerPlayer): Int {
        val ownTeam = teamOf(player).orElse(null) ?: return 0x1

        var flags = 0x1

        for (entry in teamGroups.object2IntEntrySet()) {
            val playerGroup = entry.intValue
            flags = flags or bulletGroup(playerGroup)

            if (entry.key != ownTeam) {
                flags = flags or playerGroup
            }
        }

        return flags
    }

    fun playerDeficit(pbt: PaintballTeam): Int {
        val team = teamManager.getTeam(pbt).orElse(null) ?: return 0

        val maxPlayerCount = teamManager.teams.stream()
            .mapToInt { it.getParticipatingPlayers(participants).size }
            .max().orElse(0)

        return maxPlayerCount - team.getParticipatingPlayers(participants).size
    }

    override fun iterator(): Iterator<PaintballTeam> = teams.iterator()

    fun stream(): Stream<PaintballTeam> = teams.stream()
}
