package work.lclpnet.ap2.game.guess_it

import net.minecraft.core.RegistryAccess
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.base.GameStartContext
import work.lclpnet.ap2.game.GameType
import work.lclpnet.ap2.game.MiniGame

class GuessItMiniGame : MiniGame {
    override val id = ApConstants.identifier("guess_it")
    override val type = GameType.FFA
    override val author = ApConstants.PERSON_LCLP
    override fun getIcon(manager: RegistryAccess) = ItemStack(Items.KNOWLEDGE_BOOK)
    override fun canBeFinale(context: GameStartContext) = false  // multiple players can have the same score
    override fun canBePlayed(context: GameStartContext) = true
    override fun createFactory() = GuessItFactory()
}
