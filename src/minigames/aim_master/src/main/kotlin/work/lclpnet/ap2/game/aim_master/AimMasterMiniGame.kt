package work.lclpnet.ap2.game.aim_master

import net.minecraft.core.RegistryAccess
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.game.GameStartContext
import work.lclpnet.ap2.api.game.GameType
import work.lclpnet.ap2.api.game.MiniGame
import work.lclpnet.ap2.api.game.MiniGameHandle

class AimMasterMiniGame : MiniGame {
    override fun getId() = ApConstants.identifier("aim_master")
    override fun getType() = GameType.FFA
    override fun getAuthor() = ApConstants.PERSON_BOPS
    override fun getIcon(manager: RegistryAccess) = ItemStack(Items.TARGET)
    override fun canBeFinale(context: GameStartContext) = true
    override fun canBePlayed(context: GameStartContext) = true
    override fun createInstance(gameHandle: MiniGameHandle) = AimMasterInstance(gameHandle)
}
