package work.lclpnet.ap2.game.red_light_green_light

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Blocks
import org.json.JSONObject
import work.lclpnet.ap2.impl.map.MapUtil
import java.util.*

data class TrafficLight(val red: BlockPos, val yellow: BlockPos, val green: BlockPos) {

    fun set(status: EnumSet<Status>, world: ServerLevel) {
        val off = Blocks.BLACK_CONCRETE.defaultBlockState()

        if (status.contains(Status.RED)) {
            world.setBlockAndUpdate(red, Blocks.RED_CONCRETE.defaultBlockState())
        } else {
            world.setBlockAndUpdate(red, off)
        }

        if (status.contains(Status.YELLOW)) {
            world.setBlockAndUpdate(yellow, Blocks.YELLOW_CONCRETE.defaultBlockState())
        } else {
            world.setBlockAndUpdate(yellow, off)
        }

        if (status.contains(Status.GREEN)) {
            world.setBlockAndUpdate(green, Blocks.LIME_CONCRETE.defaultBlockState())
        } else {
            world.setBlockAndUpdate(green, off)
        }
    }

    enum class Status {
        RED, YELLOW, GREEN
    }
}

fun trafficLightFromJson(json: JSONObject): TrafficLight {
    val red = MapUtil.readBlockPos(json.getJSONArray("red"))
    val yellow = MapUtil.readBlockPos(json.getJSONArray("yellow"))
    val green = MapUtil.readBlockPos(json.getJSONArray("green"))
    return TrafficLight(red, yellow, green)
}
