package work.lclpnet.ap2.game.data

import net.minecraft.core.RegistryAccess
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

interface SubjectRef {

    /**
     * Translate the name of this subject for a given viewer (a player).
     * @param viewer The viewer (to whom the name is translated for).
     * @return The translated text.
     */
    fun getNameFor(viewer: ServerPlayer): Component

    /**
     * Gets an item stack as icon for the subject.
     * @param registryManager The [RegistryAccess].
     * @param viewer The viewer.
     * @return The icon item stack.
     */
    fun getIconStackFor(registryManager: RegistryAccess, viewer: ServerPlayer): ItemStack

    val identifier: String
}
