package work.lclpnet.ap2.game.pillar_battle

import net.minecraft.core.RegistryAccess
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.game.*
import work.lclpnet.kibu.translate.text.LocalizedFormat

class PillarBattleMiniGame : MiniGame {
    override fun getId(): Identifier = ApConstants.identifier("pillar_battle")
    override fun getType(): GameType = GameType.FFA
    override fun getAuthor(): String = ApConstants.PERSON_LCLP
    override fun getIcon(manager: RegistryAccess): ItemStack = ItemStack(Items.PURPUR_PILLAR)
    override fun canBeFinale(context: GameStartContext): Boolean = true
    override fun canBePlayed(context: GameStartContext): Boolean = true
    override fun getDescriptionArguments(): Array<Any> = arrayOf(LocalizedFormat.format("%.1f", RANDOM_ITEM_DELAY_TICKS / 20f))
    override fun createInstance(gameHandle: MiniGameHandle): MiniGameInstance = PillarBattleInstance(gameHandle)
}
