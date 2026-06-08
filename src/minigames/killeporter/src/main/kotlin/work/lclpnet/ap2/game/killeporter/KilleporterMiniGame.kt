package work.lclpnet.ap2.game.killeporter

import net.minecraft.core.RegistryAccess
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.game.GameStartContext
import work.lclpnet.ap2.api.game.GameType
import work.lclpnet.ap2.game.MapLevelGameFactory
import work.lclpnet.ap2.game.MiniGame
import work.lclpnet.ap2.game.MiniGameHandle

class KilleporterMiniGame : MiniGame {
    override fun canBeFinale(context: GameStartContext) = false
    override fun canBePlayed(context: GameStartContext) = true
    override val id = ApConstants.identifier("killeporter")
    override val type = GameType.FFA
    override val author = ApConstants.PERSON_BOPS
    override fun getIcon(manager: RegistryAccess) = ItemStack(Items.ENDER_PEARL)
    override fun createFactory(gameHandle: MiniGameHandle) = MapLevelGameFactory(::KilleporterInstance)
}
