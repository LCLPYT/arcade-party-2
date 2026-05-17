package work.lclpnet.ap2.game.one_in_the_chamber

import net.minecraft.core.BlockPos
import org.json.JSONArray
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.impl.map.MapUtil
import java.util.*

class OneInTheChamberSpawns(
    private val gameHandle: MiniGameHandle,
    private val random: Random
) {
    private val squaredRespawnDistance = RESPAWN_SPACING * RESPAWN_SPACING
    private lateinit var spawnPoints: List<BlockPos>

    fun loadSpawnPoints(array: JSONArray) {
        spawnPoints = buildList {
            for (obj in array) {
                if (obj is JSONArray) add(MapUtil.readBlockPos(obj))
            }
        }
    }

    fun getRandomSpawn(): BlockPos {
        val spawnsByDistance = spawnPoints.map { pos -> pos to minPlayerSquaredDistance(pos) }
        val distantSpawns = spawnPoints.filter { pos -> minPlayerSquaredDistance(pos) >= squaredRespawnDistance }

        if (distantSpawns.isEmpty()) {
            return spawnsByDistance.maxByOrNull { it.second }!!.first
        }

        return distantSpawns[random.nextInt(distantSpawns.size)]
    }

    private fun minPlayerSquaredDistance(pos: BlockPos): Double {
        return gameHandle.participants.stream()
            .filter { !it.isSpectator }
            .mapToDouble { it.position().distanceToSqr(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble()) }
            .min().orElse(Double.MAX_VALUE)
    }
}
