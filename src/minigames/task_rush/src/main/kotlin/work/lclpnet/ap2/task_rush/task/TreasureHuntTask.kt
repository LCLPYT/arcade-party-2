package work.lclpnet.ap2.task_rush.task

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.MapItem
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes
import net.minecraft.world.level.saveddata.maps.MapItemSavedData
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.ext.mc.setBlock
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration.Companion.seconds

/**
 * Be the first to find the treasure chest.
 * Every player receives a map with an "X" marker pointing at achest placed near spawn,
 * similar to buried treasures in vanilla Minecraft.
 */
object TreasureHuntTask : OrderTask("treasure_hunt", 60.seconds) {

    override fun begin(env: TaskEnv) {
        val progress = start(env)
        val chestPos = placeChest(env.level, env.spawnPos)

        for (player in env.players) {
            giveMap(player, env.level, chestPos)
        }

        PlayerInteractionHooks.USE_BLOCK.registerWith(env.hooks) { player, world, _, hitResult ->
            if (player is ServerPlayer && env.players.isParticipating(player)
                && hitResult.blockPos == chestPos
                && world.getBlockState(hitResult.blockPos).isOf(Blocks.CHEST)
            ) {
                world.setBlock(hitResult.blockPos, Blocks.AIR)
                progress.finish(player)
                InteractionResult.SUCCESS_SERVER
            } else {
                InteractionResult.PASS
            }
        }
    }

    private fun placeChest(level: ServerLevel, spawn: BlockPos): BlockPos {
        val angle = level.random.nextDouble() * 2.0 * Math.PI
        val distance = 20 + level.random.nextInt(41)
        val x = spawn.x + (cos(angle) * distance).toInt()
        val z = spawn.z + (sin(angle) * distance).toInt()
        val y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)
        val pos = BlockPos(x, y, z)

        level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState())

        return pos
    }

    private fun giveMap(player: ServerPlayer, level: ServerLevel, chestPos: BlockPos) {
        val map = MapItem.create(level, chestPos.x, chestPos.z, 0.toByte(), true, true)
        MapItemSavedData.addTargetDecoration(map, chestPos, "+", MapDecorationTypes.RED_X)

        giveOrDrop(player, map)
    }
}
