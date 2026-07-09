package work.lclpnet.ap2.dragons

import net.minecraft.server.level.ServerLevel
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.base.EliminationGameInstance
import work.lclpnet.ap2.game.kit.KitHandler
import work.lclpnet.ap2.game.kit.shared.LeapKit
import work.lclpnet.game.map.GameMap

class DragonsInstance(
    gameHandle: MiniGameHandle,
    level: ServerLevel,
    map: GameMap,
    val schema: DragonEscapeMapSchema
) : EliminationGameInstance(gameHandle, level, map) {

    val kitHandler = KitHandler.create(gameHandle, level) { kitHandle ->
        listOf(
            LeapKit(kitHandle),
        )
    }

    override fun prepare() {
        setupKits()
    }

    private fun setupKits() {
    }

    override fun go() {

    }
}
