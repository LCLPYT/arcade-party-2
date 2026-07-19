package work.lclpnet.ap2.impl.map

import net.minecraft.resources.Identifier
import work.lclpnet.game.map.GameMap
import java.util.concurrent.CompletableFuture

interface MapRandomizer {
    fun nextMap(gameId: Identifier): CompletableFuture<GameMap>

    fun forceMap(mapId: Identifier?)
}
