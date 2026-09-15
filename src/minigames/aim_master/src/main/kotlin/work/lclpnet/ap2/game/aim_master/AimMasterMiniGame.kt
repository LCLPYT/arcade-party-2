package work.lclpnet.ap2.game.aim_master

import net.minecraft.core.RegistryAccess
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.base.GameStartContext
import work.lclpnet.ap2.game.GameType
import work.lclpnet.ap2.game.MiniGame

class AimMasterMiniGame : MiniGame {
    override val id = ApConstants.identifier("aim_master")
    override val type = GameType.FFA
    override val author = ApConstants.PERSON_BOPS
    override fun getIcon(manager: RegistryAccess) = ItemStack(Items.TARGET)
    override fun canBeFinale(context: GameStartContext) = true
    override fun canBePlayed(context: GameStartContext) = true
    override fun createFactory() = AimMasterFactory()
}
