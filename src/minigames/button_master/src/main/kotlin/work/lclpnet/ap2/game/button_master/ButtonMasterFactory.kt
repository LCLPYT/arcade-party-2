package work.lclpnet.ap2.game.button_master

import work.lclpnet.ap2.game.MiniGameFactory
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.util.assetPath
import work.lclpnet.ap2.game.util.openRandomMap
import work.lclpnet.ap2.game.util.schematic

class ButtonMasterFactory : MiniGameFactory {

    override suspend fun createInstance(handle: MiniGameHandle): MiniGameInstance {
        val (level, map) = handle.openRandomMap()

        val capsuleSchematic = handle.schematic(map.assetPath("capsule.schem"))

        return ButtonMasterInstance(handle, level, map, capsuleSchematic)
    }
}