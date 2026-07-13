package work.lclpnet.ap2.game.apocalypse_survival.util

import net.minecraft.server.level.ServerLevel
import org.json.JSONArray
import org.json.JSONObject
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.game.map.GameMap
import java.util.*

class AsSetup(
    private val map: GameMap,
    private val world: ServerLevel,
    private val random: Random,
    private val targetManager: TargetManager
) {

    fun readSpawners(): List<MonsterSpawner> {
        val array = map.requireProperty<JSONArray>("spawners")

        return array.filterIsInstance<JSONObject>().map { createSpawner(it) }
    }

    private fun createSpawner(json: JSONObject): MonsterSpawner {
        val stageJson = json.getJSONObject("stage")
        val blockShape = MapUtil.readShape(stageJson)

        val minSpawnTicks = json.getNumber("min-spawn-ticks").toInt()
        val maxSpawnTicks = json.getNumber("max-spawn-ticks").toInt()

        return MonsterSpawner(world, blockShape, random, targetManager, minSpawnTicks, maxSpawnTicks)
    }
}
