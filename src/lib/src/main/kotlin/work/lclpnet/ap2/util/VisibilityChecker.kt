package work.lclpnet.ap2.util

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import org.joml.Matrix4d
import org.joml.Quaterniond
import org.joml.Vector4d
import work.lclpnet.ap2.impl.util.math.MathUtil
import java.util.function.BiFunction
import kotlin.math.abs
import kotlin.math.max

class VisibilityChecker(private val blockView: BlockGetter) {
    val viewProjMat = Matrix4d()

    fun isAnyoneLookingAt(mob: Entity, pos: Vec3, players: Iterable<ServerPlayer>): Boolean {
        return getAnyoneLookingAt(mob, pos, players) != null
    }

    fun getAnyoneLookingAt(mob: Entity, pos: Vec3, players: Iterable<ServerPlayer>): ServerPlayer? {
        for (player in players) {
            if (player.isSpectator) continue

            if (isVisibleByAt(mob, player, pos)) {
                return player
            }
        }

        return null
    }

    fun isVisibleByAt(entity: Entity, player: ServerPlayer, pos: Vec3): Boolean {
        viewProjectionMatrix(player, PLAYER_FOV, PLAYER_ASPECT_RATIO, viewProjMat)

        // check if the entity is within the players (estimated) view frustum
        val playerEyePos = player.eyePosition
        val entityEyePos = Vec3(pos.x(), pos.y() + entity.eyeHeight, pos.z())

        val bounds = entity.getDimensions(entity.pose).makeBoundingBox(pos) // add some margin

        return isBoxVisible(playerEyePos, bounds, entityEyePos)
    }

    fun isBoxVisible(cameraPos: Vec3, box: AABB?, quickCheckPos: Vec3): Boolean {
        // check if the box is within an estimated view frustum
        val ndc = Vector4d()

        if (canSee(viewProjMat, cameraPos, quickCheckPos, ndc)) {
            return true
        }

        // need to check bounding box corners
        for (corner in MathUtil.corners(box)) {
            if (canSee(viewProjMat, cameraPos, corner, ndc)) {
                return true
            }
        }

        return false
    }

    fun canSee(viewProjMat: Matrix4d, eyePos: Vec3, pos: Vec3, ndc: Vector4d): Boolean {
        ndc.set(pos.x, pos.y, pos.z, 1.0)
        viewProjMat.transform(ndc)
        ndc.div(ndc.w)

        if (abs(ndc.x) > 1.0 || abs(ndc.y) > 1.0 || abs(ndc.z) > 1.0) return false

        // within view frustum, check for occlusion
        return !occluded(eyePos, pos, blockView)
    }

    companion object {
        val PLAYER_FOV: Double = Math.toRadians(90.0)
        const val PLAYER_ASPECT_RATIO: Double = 1920 / 1080.0

        @JvmStatic
        fun occluded(from: Vec3, to: Vec3, view: BlockGetter): Boolean {
            val hit = BlockGetter.traverseBlocks(
                from,
                to,
                Unit,
                BiFunction { _, pos ->
                    val state = view.getBlockState(pos)

                    // ray should pass through non-opaque blocks
                    if (!state.canOcclude()) {
                        return@BiFunction null
                    }

                    val shape = ClipContext.Block.VISUAL.get(
                        state,
                        view,
                        pos,
                        CollisionContext.empty()
                    )

                    view.clipWithInteractionOverride(from, to, pos, shape, state)
                }
            ) { _ ->
                val dir = from.subtract(to)

                BlockHitResult.miss(
                    to,
                    Direction.getApproximateNearest(dir.x, dir.y, dir.z),
                    BlockPos.containing(to)
                )
            }

            return hit.type != HitResult.Type.MISS
        }

       @JvmStatic
        fun viewProjectionMatrix(
            player: ServerPlayer,
            fovRadians: Double,
            screenAspectRatio: Double,
            mat: Matrix4d
        ): Matrix4d {
            val server = player.level().server

            val viewDistance = Math.clamp(
                player.requestedViewDistance().toLong(),
                2,
                max(2, server.playerList.viewDistance)
            )

            return viewProjectionMatrix(
                player.x,
                player.eyeY,
                player.z,
                player.yRot,
                player.xRot,
                viewDistance,
                fovRadians,
                screenAspectRatio,
                mat
            )
        }

        @JvmStatic
        fun viewProjectionMatrix(
            cameraX: Double,
            cameraY: Double,
            cameraZ: Double,
            yaw: Float,
            pitch: Float,
            viewDistance: Int,
            fovRadians: Double,
            screenAspectRatio: Double,
            mat: Matrix4d
        ): Matrix4d {
            val rotation = Quaterniond()
                .rotationYXZ(Math.PI - yaw * Math.PI / 180.0, -pitch * Math.PI / 180.0, 0.0)
                .conjugate()

            val zFar = viewDistance * 16

            return mat.identity()
                .perspective(fovRadians, screenAspectRatio, 0.05, zFar.toDouble())
                .rotate(rotation)
                .translate(-cameraX, -cameraY, -cameraZ)
        }
    }
}