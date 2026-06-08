package work.lclpnet.ap2.game.panda_finder

import net.minecraft.ChatFormatting
import net.minecraft.core.RegistryAccess
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.game.GameStartContext
import work.lclpnet.ap2.api.game.GameType
import work.lclpnet.ap2.game.MapLevelGameFactory
import work.lclpnet.ap2.game.MiniGame
import work.lclpnet.kibu.translate.text.FormatWrapper

class PandaFinderMiniGame : MiniGame {
    override val id = ApConstants.identifier("panda_finder")
    override val type = GameType.FFA
    override val author = ApConstants.PERSON_LCLP
    override fun getIcon(manager: RegistryAccess) = ItemStack(Items.BAMBOO)
    override fun canBeFinale(context: GameStartContext) = true
    override fun canBePlayed(context: GameStartContext) = true
    override fun createFactory() = MapLevelGameFactory(::PandaFinderInstance)
    override val descriptionArguments: Array<Any> = arrayOf(
        FormatWrapper.styled(WIN_SCORE, ChatFormatting.YELLOW)
    )
}
