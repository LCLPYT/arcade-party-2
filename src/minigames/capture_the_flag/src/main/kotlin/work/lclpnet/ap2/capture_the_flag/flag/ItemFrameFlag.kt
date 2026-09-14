package work.lclpnet.ap2.capture_the_flag.flag

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.slf4j.Logger
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.gaco.core.api.EntityRef
import work.lclpnet.kibu.hook.entity.ItemFrameRemoveItemCallback
import work.lclpnet.kibu.hook.entity.ItemFrameRotateCallback

/**
 * A flag represented by the item inside an item frame, e.g. a map.
 */
class ItemFrameFlag(private val level: ServerLevel, private val pos: BlockPos) : Flag {

    private var frameRef: EntityRef<ItemFrame>? = null

    override var carryStack: ItemStack = ItemStack.EMPTY
        private set

    override val homePosition: Vec3 = Vec3.atCenterOf(pos)

    fun isFlagFrame(frame: ItemFrame) = frame.uuid == frameRef?.uuid()

    override fun takeFromHome() {
        val frame = itemFrame() ?: return

        frame.item = ItemStack.EMPTY
    }

    override fun placeAtHome() {
        val frame = itemFrame() ?: return

        frame.item = carryStack.copy()
    }

    override fun init(gameHandle: MiniGameHandle, onSteal: (ServerPlayer) -> Unit) {
        awaitItemFrame(gameHandle)

        ItemFrameRemoveItemCallback.HOOK.registerWith(gameHandle.hooks) { frame, attacker ->
            if (!isFlagFrame(frame) || attacker !is ServerPlayer) return@registerWith false

            onSteal(attacker)

            // never let the frame drop its item, the flag state decides what happens
            true
        }

        ItemFrameRotateCallback.HOOK.registerWith(gameHandle.hooks) { frame, player, _ ->
            if (!isFlagFrame(frame) || player !is ServerPlayer) return@registerWith false

            onSteal(player)

            true
        }
    }

    private fun itemFrame() = frameRef?.resolve()

    private fun awaitItemFrame(gameHandle: MiniGameHandle) {
        if (resolveItemFrame(gameHandle.logger)) return

        // the chunk containing the flag might not have its entities loaded yet at this point
        gameHandle.rootScheduler.interval(1) { task ->
            if (resolveItemFrame(gameHandle.logger)) {
                task.cancel()
            }
        }
    }

    /** Returns true once the lookup is settled, regardless of whether an item frame was found. */
    private fun resolveItemFrame(logger: Logger): Boolean {
        if (!level.areEntitiesLoaded(ChunkPos.pack(pos))) return false

        val frame = findItemFrame(level, pos)

        if (frame == null) {
            logger.error("There is no item frame at {}", pos)
            return true
        }

        if (frame.item.isEmpty) {
            logger.error("The item frame at {} does not contain an item", pos)
            return true
        }

        frameRef = EntityRef(frame)
        carryStack = frame.item.copy()

        return true
    }

    companion object {

        val FACTORY = FlagFactory(::ItemFrameFlag)

        private fun findItemFrame(level: ServerLevel, pos: BlockPos): ItemFrame? {
            val box = AABB(pos).inflate(0.5)

            return level.getEntitiesOfClass(ItemFrame::class.java, box) { it.blockPosition() == pos }
                .firstOrNull()
        }
    }
}
