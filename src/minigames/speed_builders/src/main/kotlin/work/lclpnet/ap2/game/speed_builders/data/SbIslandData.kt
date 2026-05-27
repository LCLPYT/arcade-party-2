package work.lclpnet.ap2.game.speed_builders.data

import net.minecraft.core.BlockPos
import org.json.JSONException
import org.json.JSONObject
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.gaco.ds.BlockBox

data class SbIslandData(val id: String, val spawn: BlockPos, val yaw: Float, val buildArea: BlockBox)

@Throws(JSONException::class)
fun sbIslandDataFromJson(json: JSONObject): SbIslandData {
    val id = json.getString("id")

    val spawn = MapUtil.readBlockPos(json.getJSONArray("spawn"))

    val yaw = if (json.has("yaw")) MapUtil.readAngle(json.getNumber("yaw")) else 0f

    val buildArea = MapUtil.readBox(json.getJSONArray("build-area"))

    return SbIslandData(id, spawn, yaw, buildArea)
}
