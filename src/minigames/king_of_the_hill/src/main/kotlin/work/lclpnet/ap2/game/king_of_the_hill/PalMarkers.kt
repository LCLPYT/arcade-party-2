package work.lclpnet.ap2.game.king_of_the_hill

import com.mojang.math.Transformation
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.DyeColor
import net.minecraft.world.level.block.Blocks
import org.joml.Matrix4f
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.lobby.game.map.GameMap
import work.lclpnet.pal.PalApi

enum class Contraption { JUMP_PAD, BOOSTER_PLATE, ELEVATOR }

fun createMarkers(world: ServerLevel, map: GameMap) {
    val actorScanShape = MapUtil.readOptShape(map, "scan-for-actors-shape") ?: return
    val contraptionService = PalApi.getInstance().contraptionService
    val mutablePos = BlockPos.MutableBlockPos()

    for (pos in actorScanShape) {
        mutablePos.set(pos)

        val contraption = when {
            contraptionService.isJumpPad(world, mutablePos).also { mutablePos.set(pos) } -> Contraption.JUMP_PAD
            contraptionService.isBoosterPlate(world, mutablePos).also { mutablePos.set(pos) } -> Contraption.BOOSTER_PLATE
            contraptionService.isElevator(world, mutablePos) -> Contraption.ELEVATOR
            else -> continue
        }

        val marker = Display.BlockDisplay(EntityType.BLOCK_DISPLAY, world)
        val margin = 0.015f

        when (contraption) {
            Contraption.JUMP_PAD -> {
                marker.blockState = Blocks.LIME_CONCRETE.defaultBlockState()
                marker.setPosRaw(pos.x - 1.0 + margin, pos.y.toDouble() + margin, pos.z - 1.0 + margin)
                marker.setTransformation(
                    Transformation(Matrix4f()
                    .scale(3f - 2 * margin, 1f - 2 * margin, 3f - 2 * margin)))
                marker.glowColorOverride = DyeColor.LIME.textureDiffuseColor
                marker.setGlowingTag(true)
            }
            Contraption.BOOSTER_PLATE -> {
                marker.blockState = Blocks.ORANGE_TERRACOTTA.defaultBlockState()
                marker.setPosRaw(pos.x.toDouble() + margin, pos.y - 1.0 + margin, pos.z.toDouble() + margin)
                marker.setTransformation(Transformation(Matrix4f().scale(1f - 2 * margin)))
                marker.glowColorOverride = DyeColor.ORANGE.textureDiffuseColor
                marker.setGlowingTag(true)
            }
            Contraption.ELEVATOR -> {
                marker.blockState = Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState()
                marker.setPosRaw(pos.x - 1.0 + margin, pos.y.toDouble() + margin, pos.z - 1.0 + margin)
                marker.setTransformation(
                    Transformation(Matrix4f()
                    .scale(3f - 2 * margin, 1f - 2 * margin, 3f - 2 * margin)))
                marker.glowColorOverride = DyeColor.LIGHT_BLUE.textureDiffuseColor
                marker.setGlowingTag(true)
            }
        }

        marker.viewRange = 0.3f
        world.addFreshEntity(marker)
    }
}