package work.lclpnet.ap2.game.pvp_tournament

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import org.json.JSONObject
import org.slf4j.Logger
import work.lclpnet.ap2.api.base.Participants
import work.lclpnet.ap2.game.pvp_tournament.tournament.ByeTracker
import work.lclpnet.ap2.game.pvp_tournament.tournament.Match
import work.lclpnet.ap2.game.pvp_tournament.tournament.SingleEliminationTournamentBuilder
import work.lclpnet.ap2.game.pvp_tournament.tournament.SwissTournamentBuilder
import work.lclpnet.ap2.game.pvp_tournament.tournament.Tournament
import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import work.lclpnet.ap2.impl.util.structure.StructureUtil
import work.lclpnet.kibu.structure.BlockStructure
import work.lclpnet.lobby.game.map.GameMap
import kotlin.math.max

data class TournamentResult(
    val tournament: Tournament,
    val arenas: Map<Match, ArenaInstance>
)

const val ARENA_Y = 64
const val ARENA_PADDING_X = 30
const val ARENA_PADDING_Z = 30

class TournamentSetup(
    val logger: Logger,
    val map: GameMap,
    val players: Participants,
    val schematicLoader: (String) -> BlockStructure,
) {
    suspend fun setup(variant: TournamentVariant): TournamentResult {
        val arenaData = getArenaData()
        val arenas = readArenas(arenaData)

        if (arenas.isEmpty()) {
            error("No valid arenas found")
        }

        val builder = when (variant) {
            TournamentVariant.SINGLE_ELIMINATION -> SingleEliminationTournamentBuilder(ByeTracker())
            TournamentVariant.SWISS_STYLE -> SwissTournamentBuilder(4)
        }

        val tournament = builder.build(players.map { PlayerRef.create(it) })

        val arenaInstances = generateArenasByRound(tournament, arenas, variant)

        return TournamentResult(tournament, arenaInstances)
    }

    private fun getArenaData(): List<ArenaData> {
        return map.properties.getJSONArray("arenas").mapNotNull {
            if (it is JSONObject) it
            else {
                logger.error("Invalid entry in 'arenas' array: $it, expected JSONObject")
                null
            }
        }.mapNotNull {
            Json.decodeFromString<ArenaData>(it.toString())
//            arenaDataFromJson(it, logger)
        }
    }

    private suspend fun readArenas(arenaData: List<ArenaData>): List<Arena> {
        val structures = withContext(Dispatchers.IO) {
            arenaData.map {
                it to schematicLoader("arenas/${it.id}.schem")
            }
        }

        return structures.map { (data, structure) -> Arena(data, structure) }
    }

    private fun generateArenasByRound(tournament: Tournament, arenas: List<Arena>, variant: TournamentVariant): Map<Match, ArenaInstance> {
        val matchesByRound = tournament.matches
            .groupBy { it.round }.entries
            .sortedBy { (round, _) -> round }

        val instances = mutableMapOf<Match, ArenaInstance>()
        var offsetZ = 0

        for ((_, matches) in matchesByRound) {
            var offsetX = 0
            var maxLength = 0

            for (match in matches) {
                val arena = if (variant != TournamentVariant.SWISS_STYLE && match.isFinale()) {
                    arenas.filter { it.data.finale }.ifEmpty { arenas }
                } else {
                    arenas
                }.random()

                val width = arena.structure.width
                val length = arena.structure.length

                instances[match] = ArenaInstance(arena, BlockPos(offsetX, ARENA_Y, offsetZ))

                offsetX += width + ARENA_PADDING_X
                maxLength = max(maxLength, length)
            }

            offsetZ += maxLength + ARENA_PADDING_Z
        }

        return instances
    }

    fun placeArenas(world: ServerLevel, instances: Collection<ArenaInstance>) {
        for (instance in instances) {
            StructureUtil.placeStructureFast(instance.arena.structure, world, instance.origin)
        }
    }
}