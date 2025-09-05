package work.lclpnet.ap2.game.mine_field

import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.DynamicRegistryManager
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.game.GameStartContext
import work.lclpnet.ap2.api.game.GameType
import work.lclpnet.ap2.api.game.MiniGame
import work.lclpnet.ap2.api.game.MiniGameHandle

class MineFieldMiniGame : MiniGame {
    override fun canBeFinale(context: GameStartContext) = true
    override fun canBePlayed(context: GameStartContext) = true
    override fun getId() = ApConstants.identifier("mine_field")
    override fun getType() = GameType.FFA
    override fun getAuthor() = ApConstants.PERSON_LCLP
    override fun getIcon(manager: DynamicRegistryManager) = ItemStack(Items.STONE_PRESSURE_PLATE)
    override fun createInstance(gameHandle: MiniGameHandle) = MineFieldInstance(gameHandle)
}
