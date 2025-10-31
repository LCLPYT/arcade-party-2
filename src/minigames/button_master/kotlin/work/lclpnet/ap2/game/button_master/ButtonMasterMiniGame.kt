package work.lclpnet.ap2.game.button_master

import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.DynamicRegistryManager
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.game.*

class ButtonMasterMiniGame : MiniGame {
    override fun canBeFinale(context: GameStartContext) = true
    override fun canBePlayed(context: GameStartContext) = true
    override fun getId() = ApConstants.identifier("button_master")
    override fun getType() = GameType.FFA
    override fun getAuthor() = ApConstants.PERSON_LCLP
    override fun getIcon(manager: DynamicRegistryManager) = ItemStack(Items.OAK_BUTTON)
    override fun createInstance(gameHandle: MiniGameHandle) = ButtonMasterInstance(gameHandle)
}
