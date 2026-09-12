package work.lclpnet.ap2.capture_the_flag.flag

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import work.lclpnet.kibu.hook.HookRegistrar
import work.lclpnet.kibu.hook.entity.ItemFrameRemoveItemCallback

/**
 * A flag represented by the item inside an item frame, e.g. a map.
 */
class ItemFrameFlag(level: ServerLevel, pos: BlockPos) : Flag {

    private val itemFrame = findItemFrame(level, pos)

    override val carryStack: ItemStack = itemFrame.item.copy()

    override val homePosition: Vec3 = itemFrame.position()

    init {
        require(!carryStack.isEmpty) { "Item frame at $pos does not contain an item" }
    }

    fun isFlagFrame(frame: ItemFrame) = frame === itemFrame

    override fun takeFromHome() {
        itemFrame.item = ItemStack.EMPTY
    }

    override fun placeAtHome() {
        itemFrame.item = carryStack.copy()
    }

    override fun init(hooks: HookRegistrar, onSteal: (ServerPlayer) -> Unit) {
        ItemFrameRemoveItemCallback.HOOK.registerWith(hooks) { frame, attacker ->
            if (!isFlagFrame(frame) || attacker !is ServerPlayer) return@registerWith false

            onSteal(attacker)

            // never let the frame drop its item, the flag state decides what happens
            true
        }
    }

    companion object {

        val FACTORY = FlagFactory(::ItemFrameFlag)

        private fun findItemFrame(level: ServerLevel, pos: BlockPos): ItemFrame {
            val box = AABB(pos).inflate(0.5)

            return level.getEntitiesOfClass(ItemFrame::class.java, box) { it.blockPosition() == pos }
                .firstOrNull() ?: throw IllegalStateException("No item frame at $pos")
        }
    }
}
