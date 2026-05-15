package work.lclpnet.ap2.game.pvp_tournament

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import org.json.JSONObject
import org.slf4j.Logger
import work.lclpnet.ap2.game.pvp_tournament.gen.*
import work.lclpnet.ap2.game.pvp_tournament.util.Arena
import work.lclpnet.ap2.game.pvp_tournament.util.ArenaData
import work.lclpnet.ap2.game.pvp_tournament.util.ArenaInstance
import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import work.lclpnet.ap2.impl.util.structure.StructureUtil
import work.lclpnet.game.map.GameMap
import work.lclpnet.kibu.structure.BlockStructure
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
    val players: List<PlayerRef>,
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

        val tournament = builder.build(players)

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
            try {
                Json.decodeFromString<ArenaData>(it.toString())
            } catch (err: Throwable) {
                if (err is CancellationException) throw err

                logger.error("Failed to decode arena data from {}", it, err)

                null
            }
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
                    arenas.filter { !it.data.finale }.ifEmpty { arenas }
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