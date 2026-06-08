package work.lclpnet.ap2.game.hot_potato

import net.minecraft.core.RegistryAccess
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.game.GameStartContext
import work.lclpnet.ap2.api.game.GameType
import work.lclpnet.ap2.game.MapLevelGameFactory
import work.lclpnet.ap2.game.MiniGame
import work.lclpnet.ap2.game.MiniGameFactory
import work.lclpnet.ap2.game.MiniGameHandle

class HotPotatoMiniGame : MiniGame {
    override val id = ApConstants.identifier("hot_potato")
    override val type = GameType.FFA
    override val author = ApConstants.PERSON_LCLP
    override fun getIcon(manager: RegistryAccess): ItemStack = ItemStack(Items.BAKED_POTATO)
    override fun canBeFinale(context: GameStartContext): Boolean = true
    override fun canBePlayed(context: GameStartContext): Boolean = true
    override fun createFactory(gameHandle: MiniGameHandle): MiniGameFactory = MapLevelGameFactory(::HotPotatoInstance)
    override val descriptionArguments: Array<Any> = arrayOf(DURATION_SECONDS)
}
