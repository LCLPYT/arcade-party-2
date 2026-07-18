package work.lclpnet.ap2.game.one_in_the_chamber

import net.minecraft.ChatFormatting
import net.minecraft.core.RegistryAccess
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.base.GameStartContext
import work.lclpnet.ap2.game.GameType
import work.lclpnet.ap2.game.MiniGame
import work.lclpnet.ap2.game.MiniGameFactory
import work.lclpnet.ap2.game.util.MapLevelGameFactory
import work.lclpnet.kibu.translate.text.FormatWrapper

class OneInTheChamberMiniGame : MiniGame {
    override val id = ApConstants.identifier("one_in_the_chamber")
    override val type = GameType.FFA
    override val author = ApConstants.PERSON_BOPS
    override fun getIcon(manager: RegistryAccess): ItemStack = ItemStack(Items.CROSSBOW)
    override fun canBeFinale(context: GameStartContext): Boolean = true
    override fun canBePlayed(context: GameStartContext): Boolean = true
    override fun createFactory(): MiniGameFactory = MapLevelGameFactory(::OneInTheChamberInstance)
    override val descriptionArguments: Array<Any> = arrayOf(SCORE_LIMIT)
    override val taskArguments: Array<Any> = arrayOf(
        FormatWrapper.styled(SCORE_LIMIT, ChatFormatting.YELLOW)
    )
}
