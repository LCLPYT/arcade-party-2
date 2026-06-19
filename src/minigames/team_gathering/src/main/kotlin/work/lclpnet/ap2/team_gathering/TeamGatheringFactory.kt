package work.lclpnet.ap2.team_gathering

import net.minecraft.core.BlockPos
import net.minecraft.core.GlobalPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.storage.LevelData
import work.lclpnet.ap2.ext.mc.setBlock
import work.lclpnet.ap2.ext.mc.setDayTime
import work.lclpnet.ap2.game.MiniGameFactory
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.util.createTeamManager
import work.lclpnet.ap2.game.util.generateRandomLevel
import work.lclpnet.gaco.ds.BlockBox
import work.lclpnet.game.util.ResetWorldModifier

const val PLATFORM_RADIUS = 4
const val PLATFORM_HEIGHT = 4
const val PLATFORM_GROUND_OFFSET = 50

class TeamGatheringFactory : MiniGameFactory {

    override suspend fun createInstance(handle: MiniGameHandle): MiniGameInstance {
        val level = handle.generateRandomLevel()

        level.setDayTime(1000)

        val walls = ResetWorldModifier(level, handle.hooks)

        placePlatformWalls(level, walls)

        val teamManager = handle.createTeamManager()

        return TeamGatheringInstance(handle, level, teamManager, walls)
    }

    fun placePlatformWalls(level: ServerLevel, walls: ResetWorldModifier) {
        val pos = findPlatformPos(level, level.respawnData.pos())

        val box = BlockBox(
            pos.offset(-PLATFORM_RADIUS, -1, -PLATFORM_RADIUS),
            pos.offset(PLATFORM_RADIUS, PLATFORM_HEIGHT, PLATFORM_RADIUS)
        )

        val flags = Block.UPDATE_SUPPRESS_DROPS or Block.UPDATE_KNOWN_SHAPE

        for (pos in box) {
            if (box.isBorder(pos)) {
                val state = level.getBlockState(pos)

                if (state.isCollisionShapeFullBlock(level, pos)) continue

                walls.setBlockState(pos, Blocks.BARRIER.defaultBlockState(), flags)
            } else {
                level.setBlock(pos, Blocks.AIR)
            }
        }

        level.respawnData = LevelData.RespawnData(
            GlobalPos(level.dimension(), pos),
            0f,
            0f
        )
    }

    private fun findPlatformPos(
        level: ServerLevel,
        spawnPos: BlockPos,
    ): BlockPos {
        val maxY = level.maxY - PLATFORM_HEIGHT
        val highestY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, spawnPos)

        val platformY = (highestY + PLATFORM_GROUND_OFFSET).coerceAtMost(maxY)

        return spawnPos.atY(platformY)
    }
}