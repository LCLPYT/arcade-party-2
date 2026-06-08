package work.lclpnet.ap2.game.mimicry

import work.lclpnet.ap2.game.MiniGameFactory
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.mimicry.data.MimicryRoom
import work.lclpnet.ap2.game.util.openRandomMap
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.impl.util.world.StackedRoomGenerator
import work.lclpnet.gaco.math.AffineIntMatrix
import work.lclpnet.kibu.mc.KibuBlockPos

class MimicryFactory : MiniGameFactory {

    override suspend fun createInstance(handle: MiniGameHandle): MiniGameInstance {
        val (level, map) = handle.openRandomMap()

        val buttons = MapUtil.readBox(map.requireProperty("button-box"))

        val generator = StackedRoomGenerator(level, map, StackedRoomGenerator.Coordinates.ABSOLUTE) { pos, spawn, yaw, structure ->
            val origin: KibuBlockPos = structure.origin

            val roomButtons = buttons.transform(AffineIntMatrix.makeTranslation(
                pos.x - origin.x,
                pos.y - origin.y,
                pos.z - origin.z))

            MimicryRoom(pos, spawn, yaw, roomButtons)
        }

        val result = generator.generate(handle.participants)

        return MimicryInstance(handle, level, map, result, buttons)
    }
}