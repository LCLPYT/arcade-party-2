package work.lclpnet.ap2.impl.map

import net.minecraft.resources.Identifier
import work.lclpnet.game.map.GameMap

interface MapRandomizer {
    suspend fun nextMap(gameId: Identifier): GameMap

    fun forceMap(mapId: Identifier?)
}
