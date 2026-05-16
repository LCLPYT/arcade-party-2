package work.lclpnet.ap2.game.glowing_bomb.data

import net.minecraft.world.entity.Display
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.RespawnAnchorBlock
import net.minecraft.world.phys.Vec3
import work.lclpnet.gaco.core.api.EntityRef
import work.lclpnet.kibu.access.entity.DisplayEntityAccess
import java.util.*

class GbAnchor(val owner: UUID, val pos: Vec3, display: Display.BlockDisplay) {

    private val displayRef = EntityRef(display)
    var charges: Int = 0
        private set

    private fun display(): Display.BlockDisplay? = displayRef.resolve()

    fun setCharges(charges: Int) {
        this.charges = charges.coerceIn(0, 4)
        display()?.let {
            DisplayEntityAccess.setBlockState(
                it,
                Blocks.RESPAWN_ANCHOR.defaultBlockState()
                    .setValue(RespawnAnchorBlock.CHARGE, this.charges)
            )
        }
    }

    fun discard() {
        display()?.discard()
    }
}
