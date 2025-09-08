package work.lclpnet.ap2.game.king_of_the_hill

import net.minecraft.block.Blocks
import net.minecraft.entity.EntityType
import net.minecraft.entity.decoration.DisplayEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.DyeColor
import net.minecraft.util.math.AffineTransformation
import net.minecraft.util.math.BlockPos
import org.joml.Matrix4f
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.lobby.game.map.GameMap
import work.lclpnet.pal.PalApi

enum class Contraption { JUMP_PAD, BOOSTER_PLATE, ELEVATOR }

fun createMarkers(world: ServerWorld, map: GameMap) {
    val actorScanShape = MapUtil.readOptShape(map, "scan-for-actors-shape") ?: return
    val contraptionService = PalApi.getInstance().contraptionService
    val mutablePos = BlockPos.Mutable()

    for (pos in actorScanShape) {
        mutablePos.set(pos)

        val contraption = when {
            contraptionService.isJumpPad(world, mutablePos).also { mutablePos.set(pos) } -> Contraption.JUMP_PAD
            contraptionService.isBoosterPlate(world, mutablePos).also { mutablePos.set(pos) } -> Contraption.BOOSTER_PLATE
            contraptionService.isElevator(world, mutablePos) -> Contraption.ELEVATOR
            else -> continue
        }

        val marker = DisplayEntity.BlockDisplayEntity(EntityType.BLOCK_DISPLAY, world)
        val margin = 0.015f

        when (contraption) {
            Contraption.JUMP_PAD -> {
                marker.blockState = Blocks.LIME_CONCRETE.defaultState
                marker.setPos(pos.x - 1.0 + margin, pos.y.toDouble() + margin, pos.z - 1.0 + margin)
                marker.setTransformation(AffineTransformation(Matrix4f()
                    .scale(3f - 2 * margin, 1f - 2 * margin, 3f - 2 * margin)))
                marker.glowColorOverride = DyeColor.LIME.entityColor
                marker.isGlowing = true
            }
            Contraption.BOOSTER_PLATE -> {
                marker.blockState = Blocks.ORANGE_TERRACOTTA.defaultState
                marker.setPos(pos.x.toDouble() + margin, pos.y - 1.0 + margin, pos.z.toDouble() + margin)
                marker.setTransformation(AffineTransformation(Matrix4f().scale(1f - 2 * margin)))
                marker.glowColorOverride = DyeColor.ORANGE.entityColor
                marker.isGlowing = true
            }
            Contraption.ELEVATOR -> {
                marker.blockState = Blocks.LIGHT_BLUE_CONCRETE.defaultState
                marker.setPos(pos.x - 1.0 + margin, pos.y.toDouble() + margin, pos.z - 1.0 + margin)
                marker.setTransformation(AffineTransformation(Matrix4f()
                    .scale(3f - 2 * margin, 1f - 2 * margin, 3f - 2 * margin)))
                marker.glowColorOverride = DyeColor.LIGHT_BLUE.entityColor
                marker.isGlowing = true
            }
        }

        marker.viewRange = 0.3f
        world.spawnEntity(marker)
    }
}