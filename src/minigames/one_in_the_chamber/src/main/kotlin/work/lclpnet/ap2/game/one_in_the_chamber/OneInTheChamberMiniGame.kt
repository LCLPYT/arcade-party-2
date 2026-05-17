package work.lclpnet.ap2.game.one_in_the_chamber

import net.minecraft.ChatFormatting
import net.minecraft.core.RegistryAccess
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.game.*
import work.lclpnet.kibu.translate.text.FormatWrapper

class OneInTheChamberMiniGame : MiniGame {
    override fun getId(): Identifier = ApConstants.identifier("one_in_the_chamber")
    override fun getType(): GameType = GameType.FFA
    override fun getAuthor(): String = ApConstants.PERSON_BOPS
    override fun getIcon(manager: RegistryAccess): ItemStack = ItemStack(Items.CROSSBOW)
    override fun canBeFinale(context: GameStartContext): Boolean = true
    override fun canBePlayed(context: GameStartContext): Boolean = true
    override fun createInstance(gameHandle: MiniGameHandle): MiniGameInstance = OneInTheChamberInstance(gameHandle)
    override fun getDescriptionArguments(): Array<out Any> = arrayOf(SCORE_LIMIT)
    override fun getTaskArguments(): Array<out Any> = arrayOf(FormatWrapper.styled(SCORE_LIMIT, ChatFormatting.YELLOW))
}
