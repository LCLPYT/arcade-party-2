package work.lclpnet.ap2.game.util

import net.minecraft.server.level.ServerLevel
import work.lclpnet.game.map.GameMap

fun interface MapReady {
    fun onReady(world: ServerLevel, map: GameMap)
}
