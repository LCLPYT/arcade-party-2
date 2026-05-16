package work.lclpnet.ap2.game.hot_potato

import net.minecraft.core.RegistryAccess
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.game.*

class HotPotatoMiniGame : MiniGame {
    override fun getId(): Identifier = ApConstants.identifier("hot_potato")
    override fun getType(): GameType = GameType.FFA
    override fun getAuthor(): String = ApConstants.PERSON_LCLP
    override fun getIcon(manager: RegistryAccess): ItemStack = ItemStack(Items.BAKED_POTATO)
    override fun canBeFinale(context: GameStartContext): Boolean = true
    override fun canBePlayed(context: GameStartContext): Boolean = true
    override fun createInstance(gameHandle: MiniGameHandle): MiniGameInstance = HotPotatoInstance(gameHandle)
    override fun getDescriptionArguments(): Array<Any> = arrayOf(DURATION_SECONDS)
}
