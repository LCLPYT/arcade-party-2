package work.lclpnet.ap2.rapid_runner

import net.minecraft.core.RegistryAccess
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.base.GameStartContext
import work.lclpnet.ap2.game.GameType
import work.lclpnet.ap2.game.MiniGame

class RapidRunnerMinigame : MiniGame {
    override val id = ApConstants.identifier("rapid_runner")
    override val type = GameType.FFA
    override val author = ApConstants.PERSON_LCLP
    override val usesMaps = false
    override fun getIcon(manager: RegistryAccess) = ItemStack(Items.COPPER_BOOTS)
    override fun canBeFinale(context: GameStartContext) = true
    override fun canBePlayed(context: GameStartContext) = true
    override fun createFactory() = RapidRunnerFactory()
}