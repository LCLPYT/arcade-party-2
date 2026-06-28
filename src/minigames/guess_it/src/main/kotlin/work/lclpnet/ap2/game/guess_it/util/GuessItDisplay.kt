package work.lclpnet.ap2.game.guess_it.util

import com.mojang.math.Transformation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.item.ItemStack
import org.joml.Matrix4f
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape
import work.lclpnet.game.util.WorldModifier
import work.lclpnet.kibu.access.entity.DisplayEntityAccess

class GuessItDisplay(
    private val world: ServerLevel,
    private val modifier: WorldModifier,
    private val blockShape: BlockShape
) {
    fun displayItem(stack: ItemStack) {
        val display = Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, world)

        DisplayEntityAccess.setItemStack(display, stack)
        DisplayEntityAccess.setBillboardMode(display, Display.BillboardConstraints.CENTER)

        val scale = 8f

        val transformation = Transformation(
            Matrix4f(
                -scale, 0f, 0f, 0f,
                0f, scale, 0f, 0f,
                0f, 0f, -scale, 0f,
                0f, 0f, 0f, 1f
            )
        )

        DisplayEntityAccess.setTransformation(display, transformation)

        val origin = blockShape.origin()

        val x = origin.x + 0.5
        val y = (origin.y + scale).toDouble()
        val z = origin.z + 0.5

        display.setPosRaw(x, y, z)

        modifier.spawnEntity(display)
    }
}
