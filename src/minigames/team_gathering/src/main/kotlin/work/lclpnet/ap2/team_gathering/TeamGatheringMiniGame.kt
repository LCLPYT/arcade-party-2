package work.lclpnet.ap2.team_gathering

import net.minecraft.core.RegistryAccess
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.game.GameStartContext
import work.lclpnet.ap2.api.game.GameType
import work.lclpnet.ap2.game.MiniGame

class TeamGatheringMiniGame : MiniGame {
    override val id = ApConstants.identifier("team_gathering")
    override val type = GameType.TEAM
    override val author = ApConstants.PERSON_LCLP
    override val usesMaps = false
    override fun getIcon(manager: RegistryAccess) = ItemStack(Items.DIAMOND)
    override fun canBeFinale(context: GameStartContext) = false
    override fun canBePlayed(context: GameStartContext) = true
    override fun createFactory() = TeamGatheringFactory()
}