package work.lclpnet.ap2.game.aim_master

import net.minecraft.core.BlockPos
import work.lclpnet.ap2.game.MiniGameFactory
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.openRandomMap
import work.lclpnet.ap2.impl.util.world.StackedRoomGenerator

const val TARGET_NUMBER = 6
const val TARGET_MIN_DISTANCE = 2
const val SPHERE_OFFSET = 5
const val UPWARD_TILT = 0.55
const val ELLIPSE_FACTOR = 0.35
const val CONE_FOV = 35
const val SPHERE_RADIUS = 15

class AimMasterFactory : MiniGameFactory {
    override suspend fun createInstance(handle: MiniGameHandle): MiniGameInstance {
        val (level, map) = openRandomMap(handle)

        val generator = StackedRoomGenerator(
            level,
            map,
            StackedRoomGenerator.Coordinates.RELATIVE
        ) { _, spawn, yaw, _ ->
            AimMasterDomain(spawn, yaw, level)
        }

        val positionGenerator = PositionGenerator(
            SPHERE_RADIUS,
            SPHERE_OFFSET,
            UPWARD_TILT,
            ELLIPSE_FACTOR,
            BlockPos(0, 0, 0),
            CONE_FOV,
            TARGET_NUMBER,
            TARGET_MIN_DISTANCE
        )

        val blockOptions = BlockOptions()
        val sequenceGenerator = SequenceGenerator(positionGenerator, blockOptions, SCORE_GOAL)

        val sequence = sequenceGenerator.sequence

        val result = generator.generate(handle.participants)
        val manager = AimMasterManager(result.rooms, sequence)

        return AimMasterInstance(handle, level, map, sequence, manager)
    }
}