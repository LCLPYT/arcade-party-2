package work.lclpnet.ap2.deadline

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import work.lclpnet.ap2.ext.mc.teleport
import work.lclpnet.ap2.impl.util.debug.DebugController
import work.lclpnet.ap2.impl.util.math.MathUtil
import work.lclpnet.ap2.impl.util.world.SpawnFinder
import work.lclpnet.gaco.ds.BlockBox
import java.util.Random

private const val SPACING = 8.0
private const val CLEARANCE_RADIUS = 1.5
private const val CLEARANCE_HEIGHT = 2.0
private const val RUNWAY = 10.0 // how many blocks should be free in spawn direction

private val FACING_OFFSETS = floatArrayOf(0f, 45f, -45f, 90f, -90f, 135f, -135f, 180f)

class DeadlineSpawns(
    private val level: ServerLevel,
    private val random: Random,
    private val debugController: DebugController,
) {

    private val finder = SpawnFinder(SPACING, debugController)

    fun teleport(players: List<ServerPlayer>, scanBox: BlockBox, scanStarts: List<BlockPos>, center: Vec3) {
        val pool = finder.findSpawns(level, scanBox, scanStarts.toSet()).filter(::hasClearance)

        visualize(pool)

        val spawns = finder.generateSpacedSpawns(pool, players.size, random)

        for ((i, player) in players.withIndex()) {
            val pos = spawns[i]
            player.teleport(pos, spawnYaw(player, pos, center), level = level)
        }
    }

    private fun visualize(pool: List<Vec3>) {
        debugController.visualizeBlockPositions(pool.map(BlockPos::containing), Blocks.STAINED_GLASS.blue.defaultBlockState())
    }

    private fun hasClearance(pos: Vec3): Boolean {
        val box = AABB(
            pos.x - CLEARANCE_RADIUS, pos.y, pos.z - CLEARANCE_RADIUS,
            pos.x + CLEARANCE_RADIUS, pos.y + CLEARANCE_HEIGHT, pos.z + CLEARANCE_RADIUS,
        )

        return level.noCollision(box)
    }

    private fun spawnYaw(player: ServerPlayer, pos: Vec3, center: Vec3): Float {
        val preferred = MathUtil.yaw(center.subtract(pos))

        var bestYaw = preferred
        var bestRunway = 0.0

        for (offset in FACING_OFFSETS) {
            val yaw = preferred + offset
            val runway = runway(player, pos, yaw)

            if (runway >= RUNWAY) return yaw

            if (runway > bestRunway) {
                bestRunway = runway
                bestYaw = yaw
            }
        }

        return bestYaw
    }

    private fun runway(player: ServerPlayer, pos: Vec3, yaw: Float): Double {
        val from = pos.add(0.0, 1.0, 0.0)
        val to = from.add(MathUtil.yaw2vec(yaw).scale(RUNWAY))
        val hit = level.clip(ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))

        return if (hit.type == HitResult.Type.MISS) RUNWAY else hit.location.distanceTo(from)
    }
}
