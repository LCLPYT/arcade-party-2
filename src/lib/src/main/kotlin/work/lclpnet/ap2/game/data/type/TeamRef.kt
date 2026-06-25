package work.lclpnet.ap2.game.data.type

import net.minecraft.core.RegistryAccess
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import work.lclpnet.ap2.api.game.data.SubjectRef
import work.lclpnet.ap2.game.team.TeamKey
import work.lclpnet.ap2.game.team.TeamKeyable
import work.lclpnet.ap2.impl.util.ColorUtil
import work.lclpnet.kibu.translate.Translations
import java.util.*

class TeamRef(
    override val key: TeamKey,
    private val translations: Translations,
) : SubjectRef, TeamKeyable {

    override fun getNameFor(viewer: ServerPlayer): Component {
        return key.getDisplayName(translations).translateFor(viewer)
    }

    override fun getIconStackFor(registryManager: RegistryAccess, viewer: ServerPlayer): ItemStack =
        ItemStack(Blocks.WOOL.pick(ColorUtil.closestEntityDyeColor(key.color)))

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val teamRef = other as TeamRef
        return key == teamRef.key
    }

    override fun hashCode(): Int {
        return Objects.hash(key)
    }

    override val identifier: String
        get() = key.id
}
