package work.lclpnet.ap2.game.king_of_the_hill

import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.DynamicRegistryManager
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.game.GameStartContext
import work.lclpnet.ap2.api.game.GameType
import work.lclpnet.ap2.api.game.MiniGame
import work.lclpnet.ap2.api.game.MiniGameHandle

class KingOfTheHillMiniGame : MiniGame {
    override fun canBeFinale(context: GameStartContext) = false
    override fun canBePlayed(context: GameStartContext) = true
    override fun getId() = ApConstants.identifier("king_of_the_hill")
    override fun getType() = GameType.FFA
    override fun getAuthor() = ApConstants.PERSON_LCLP
    override fun getIcon(manager: DynamicRegistryManager) = ItemStack(Items.GOLD_BLOCK)
    override fun createInstance(gameHandle: MiniGameHandle) = KingOfTheHillInstance(gameHandle)
}
