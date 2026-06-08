package work.lclpnet.ap2.game.pillar_battle

import net.minecraft.core.RegistryAccess
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.game.GameStartContext
import work.lclpnet.ap2.api.game.GameType
import work.lclpnet.ap2.game.MiniGame
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.kibu.translate.text.LocalizedFormat

class PillarBattleMiniGame : MiniGame {
    override val id = ApConstants.identifier("pillar_battle")
    override val type = GameType.FFA
    override val author = ApConstants.PERSON_LCLP
    override fun getIcon(manager: RegistryAccess): ItemStack = ItemStack(Items.PURPUR_PILLAR)
    override fun canBeFinale(context: GameStartContext): Boolean = true
    override fun canBePlayed(context: GameStartContext): Boolean = true
    override val descriptionArguments: Array<Any> = arrayOf(LocalizedFormat.format("%.1f", RANDOM_ITEM_DELAY_TICKS / 20f))
    override fun createInstance(gameHandle: MiniGameHandle): MiniGameInstance = PillarBattleInstance(gameHandle)
}
