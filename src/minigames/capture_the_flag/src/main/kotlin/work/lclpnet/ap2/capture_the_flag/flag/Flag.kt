package work.lclpnet.ap2.capture_the_flag.flag

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import work.lclpnet.kibu.hook.HookRegistrar

/**
 * The flag of a team, resting at a fixed home position until it is stolen.
 */
interface Flag {

    /** The item representing the flag while it is carried or dropped. */
    val carryStack: ItemStack

    /** The position players have to reach to capture this flag. */
    val homePosition: Vec3

    fun takeFromHome()

    fun placeAtHome()

    /**
     * Registers the hooks that detect a player taking this flag from its home position.
     * The callback is only invoked for attempts, it is up to the caller to accept or ignore them.
     */
    fun init(hooks: HookRegistrar, onSteal: (ServerPlayer) -> Unit)
}

fun interface FlagFactory {

    fun create(level: ServerLevel, pos: BlockPos): Flag
}
