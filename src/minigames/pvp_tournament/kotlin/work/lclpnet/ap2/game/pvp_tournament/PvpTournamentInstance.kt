package work.lclpnet.ap2.game.pvp_tournament

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import org.json.JSONArray
import org.json.JSONObject
import org.slf4j.Logger
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.api.map.MapBootstrap
import work.lclpnet.ap2.impl.game.EliminationGameInstance
import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.impl.util.structure.StructureUtil
import work.lclpnet.ap2.logger
import work.lclpnet.ap2.players
import work.lclpnet.ap2.transform
import work.lclpnet.gaco.math.AffineIntMatrix
import work.lclpnet.kibu.hook.util.PositionRotation
import work.lclpnet.kibu.structure.BlockStructure
import work.lclpnet.lobby.game.map.GameMap
import java.util.concurrent.CompletableFuture
import kotlin.math.max

data class ArenaData(
    val id: String,
    val finale: Boolean,
    val spawns: List<PositionRotation>,
)

data class Arena(
    val data: ArenaData,
    val structure: BlockStructure,
)

data class ArenaInstance(
    val arena: Arena,
    val origin: BlockPos,
) {
    val spawns: List<PositionRotation> get() {
        val mat = AffineIntMatrix.makeTranslation(origin.multiply(-1))

        return arena.data.spawns.map { it.transform(mat) }
    }
}

fun arenaDataFromJson(json: JSONObject, logger: Logger): ArenaData? {
    val id = json.getString("id")
    val finale = json.optBoolean("finale", false)

    val spawns = (json.optJSONArray("spawns") ?: JSONArray()).mapNotNull {
        if (it is JSONObject) it
        else {
            logger.error("Invalid entry in 'spawns' array of arena '$id': $it")
            null
        }
    }.map {
        val pos = MapUtil.readCenteredVec3d(it.getJSONArray("pos"))
        val yaw = MapUtil.readAngle(it.optNumber("yaw", 0))
        val pitch = MapUtil.readAngle(it.optNumber("pitch", 0))

        PositionRotation(pos.x, pos.y, pos.z, yaw, pitch)
    }

    if (spawns.size < 2) {
        logger.error("Arena '$id' must have at least two spawns")
        return null
    }

    return ArenaData(id, finale, spawns)
}

class PvpTournamentInstance(gameHandle: MiniGameHandle) : EliminationGameInstance(gameHandle), MapBootstrap {

    override fun createWorldBootstrap(
        world: ServerLevel,
        map: GameMap
    ): CompletableFuture<Void> {
        val arenaData = getArenaData(map)

        return readArenas(arenaData).thenCompose { arenas ->
            if (arenas.isEmpty()) {
                CompletableFuture.failedFuture(IllegalStateException("No valid arenas found"))
            } else {
                val leveledInstances = generateLeveledArenas(arenas)
                generateMatchupGraph(leveledInstances)

                gameHandle.server.submit {
                    placeArenas(leveledInstances.flatten())
                }
            }
        }
    }

    private fun getArenaData(map: GameMap): List<ArenaData> {
        return map.properties.getJSONArray("arenas").mapNotNull {
            if (it is JSONObject) it
            else {
                logger.error("Invalid entry in 'arenas' array: $it, expected JSONObject")
                null
            }
        }.mapNotNull {
            arenaDataFromJson(it, logger)
        }
    }

    private fun readArenas(arenaData: List<ArenaData>): CompletableFuture<List<Arena>> {
        return CompletableFuture.supplyAsync {
            arenaData.map { data ->
                val structure = schematicBlocking(assetPath("arenas/${data.id}.schem"))

                Arena(data, structure)
            }
        }
    }

    private fun generateLeveledArenas(arenas: List<Arena>): List<List<ArenaInstance>> {
        var playerCount = players().count()
        val leveledInstanced = mutableListOf<List<ArenaInstance>>()
        val origin = BlockPos.MutableBlockPos()
        var maxLength = 0

        while (playerCount > 0) {
            // stack arenas in same level in x direction and stack levels in z direction
            origin.x = 0
            origin.z += maxLength
            maxLength = 0

            // for the finale, choose an arena marked as finale if one exists
            val arenaPool = if (playerCount <= 2) {
                arenas.filter { it.data.finale }.ifEmpty { arenas }
            } else {
                arenas
            }

            val arenasInRound = mutableListOf<ArenaInstance>()
            val matchups = max(1, playerCount / 2)

            repeat(matchups) {
                val arena = arenaPool.random()
                val pos = origin.immutable()

                arenasInRound.add(ArenaInstance(arena, pos))

                maxLength = max(maxLength, arena.structure.length)
                origin.x += arena.structure.width
            }

            leveledInstanced.add(arenasInRound)

            if (playerCount <= 2) break

            playerCount = matchups + playerCount % 2
        }

        return leveledInstanced
    }

    private fun placeArenas(instances: List<ArenaInstance>) {
        for (instance in instances) {
            StructureUtil.placeStructureFast(instance.arena.structure, world, instance.origin)
        }
    }

    class Matchup(
        val arenaInstance: ArenaInstance,
    ) {
        var a: PlayerRef? = null
        var b: PlayerRef? = null
    }

    private fun generateMatchupGraph(leveledInstances: List<List<ArenaInstance>>) {
        val matchups = leveledInstances.map { instances -> instances.map { Matchup(it) } }

        val firstLevel = matchups.first()
        val playerPool = players().map { PlayerRef.create(it) }.shuffled().iterator()

        for (matchup in firstLevel) {
            matchup.a = playerPool.next()
            matchup.b = playerPool.next()
        }

        // in case of an odd number of players, assign the last remaining to the next level
        if (playerPool.hasNext()) {
            matchups[1][0].a = playerPool.next()
        }

        // TODO join matchups in graph
    }

    override fun prepare() {

    }

    override fun go() {

    }
}
