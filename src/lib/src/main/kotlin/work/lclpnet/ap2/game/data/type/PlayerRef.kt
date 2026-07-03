package work.lclpnet.ap2.game.data.type

import net.minecraft.ChatFormatting
import net.minecraft.core.RegistryAccess
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.objects.PlayerSprite
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ResolvableProfile
import work.lclpnet.ap2.game.data.SubjectRef
import java.util.*

@JvmRecord
data class PlayerRef(val uuid: UUID, val name: String) : SubjectRef {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val playerRef = other as PlayerRef
        return uuid == playerRef.uuid
    }

    override fun hashCode(): Int {
        return Objects.hash(uuid)
    }

    override fun getNameFor(viewer: ServerPlayer): Component {
        return Component.empty()
            .append(
                Component.`object`(PlayerSprite(ResolvableProfile.createUnresolved(uuid), true))
                    .withStyle(ChatFormatting.WHITE)
            )
            .append(" ")
            .append(name)
    }

    override fun getIconStackFor(registryManager: RegistryAccess, viewer: ServerPlayer): ItemStack {
        val stack = ItemStack(Items.PLAYER_HEAD)

        stack.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved(uuid))

        return stack
    }

    override val identifier: String
        get() = uuid.toString()

    companion object {

        @JvmStatic
        fun create(player: ServerPlayer): PlayerRef {
            return PlayerRef(player.getUUID(), player.scoreboardName)
        }

        fun createForUuid(uuid: UUID): PlayerRef {
            return PlayerRef(uuid, "?")
        }
    }
}
