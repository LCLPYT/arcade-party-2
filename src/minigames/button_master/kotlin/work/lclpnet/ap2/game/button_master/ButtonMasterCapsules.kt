package work.lclpnet.ap2.game.button_master

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import work.lclpnet.ap2.ext.asVec3d
import work.lclpnet.ap2.impl.game.GameCommons
import work.lclpnet.ap2.impl.util.math.MathUtil
import work.lclpnet.ap2.ext.setBlocks
import work.lclpnet.ap2.ext.teleport
import work.lclpnet.ap2.ext.toMinecraft
import work.lclpnet.ap2.util.scene.ApSceneRenderer
import work.lclpnet.gaco.ds.BlockBox
import work.lclpnet.gaco.math.BlockFace
import work.lclpnet.kibu.hook.util.PositionRotation
import work.lclpnet.kibu.structure.BlockStructure
import work.lclpnet.kibu.util.math.Matrix3i
import java.util.*

const val DEBUG_CAPSULE_BOUNDS = false
const val DEBUG_CAPSULE_SPAWNS = false

class ButtonMasterCapsules(
    val world: ServerLevel,
    val schema: ButtonMasterSchema,
    val capsuleSchematic: BlockStructure,
    val commons: GameCommons
) {
    val buttons = mutableMapOf<BlockPos, BlockFace>()
    val players = mutableMapOf<BlockFace, UUID>()

    fun setup() {
        for (capsule in schema.capsules) {
            buttons[capsule.pos] = capsule

            if (DEBUG_CAPSULE_BOUNDS) {
                val capsuleBounds = getCapsuleBounds(capsule)

                commons.debugController().renderer().ifPresent {
                    it.box(capsuleBounds, Blocks.YELLOW_STAINED_GLASS.defaultBlockState())
                }
            }

            if (DEBUG_CAPSULE_SPAWNS) {
                val capsuleSpawn = getCapsuleSpawn(capsule)

                commons.debugController().renderer().ifPresent {
                    it.arrow(capsuleSpawn.asVec3d(), MathUtil.yaw2vec(capsuleSpawn.yaw), Blocks.LIME_TERRACOTTA.defaultBlockState())
                }
            }
        }
    }

    fun teleportToCapsules(players: List<ServerPlayer>) {
        removeExcessCapsules(players.size)

        val capsules = schema.capsules

        this.players.clear()

        for ((i, player) in players.shuffled().withIndex()) {
            val spawn = getCapsuleSpawn(capsules[i])
            player.teleport(spawn)

            this.players[capsules[i]] = player.uuid
        }
    }

    fun getCapsuleSpawn(capsule: BlockFace): PositionRotation {
        val referenceSpawn = schema.capsuleSpawn!!.asVec3d()
        val referenceButton = schema.capsuleButton!!
        val schematicOffset = requireNotNull(capsuleSchematic).origin.toMinecraft()
        val buttonToOriginOffset = referenceButton.pos.subtract(schematicOffset)
        val localSpawn = referenceSpawn.subtract(referenceButton.pos.center)

        val rotation = Matrix3i.makeRotationY(
            capsule.face.get2DDataValue() - referenceButton.face.get2DDataValue()
        )

        val localOffset = capsule.pos.subtract(referenceButton.pos)

        val capsuleSpawn = rotation.transform(localSpawn)
            .add(localOffset.center)
            .add(Vec3.atLowerCornerOf(buttonToOriginOffset))
            .add(Vec3.atLowerCornerOf(schematicOffset))

        val yaw = MathUtil.rotateYaw(schema.capsuleSpawn.yaw, rotation, Vector3d())

        return PositionRotation(capsuleSpawn.x, capsuleSpawn.y, capsuleSpawn.z, yaw, 0f)
    }

    fun getCapsuleBounds(capsule: BlockFace): BlockBox {
        val capsuleSchematic = requireNotNull(capsuleSchematic)
        val schematicOffset = capsuleSchematic.origin.toMinecraft()
        val capsuleButton = schema.capsuleButton!!
        val referenceBounds = BlockBox.ofBounds(capsuleSchematic)
        val buttonToOriginOffset = capsuleButton.pos.subtract(schematicOffset)

        val rotation = Matrix3i.makeRotationY(
            capsule.face.get2DDataValue() - capsuleButton.face.get2DDataValue()
        )

        val localOffset = capsule.pos.subtract(capsuleButton.pos)

        return referenceBounds
            .translate(buttonToOriginOffset.multiply(-1))
            .transform(rotation)
            .translate(buttonToOriginOffset)
            .translate(localOffset)
            .translate(schematicOffset)
    }

    fun removeExcessCapsules(requiredCapsules: Int) {
        val capsules = schema.capsules

        require(requiredCapsules <= capsules.size) {
            "Not enough capsules (need $requiredCapsules, got ${capsules.size}"
        }

        for (i in requiredCapsules ..< capsules.size) {
            val capsule = capsules[i]
            val capsuleBounds = getCapsuleBounds(capsule)

            world.setBlocks(capsuleBounds, Blocks.AIR)
        }
    }

    fun displayCapsuleButtons(renderer: ApSceneRenderer) {
        val used = players.size

        for (i in 0..<used) {
            val button = schema.capsules[i]

            renderer.markBlock(button.pos, world.getBlockState(button.pos), 0x00ff00)
        }
    }
}
