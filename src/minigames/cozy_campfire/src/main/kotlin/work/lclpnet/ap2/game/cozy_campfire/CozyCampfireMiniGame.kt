package work.lclpnet.ap2.game.cozy_campfire

import net.minecraft.core.RegistryAccess
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.base.GameStartContext
import work.lclpnet.ap2.game.GameType
import work.lclpnet.ap2.game.MiniGame
import work.lclpnet.ap2.game.MiniGameFactory

private const val DEBUG_PLAYER_CONSTRAINT = false

class CozyCampfireMiniGame : MiniGame {
    override val id = ApConstants.identifier("cozy_campfire")
    override val type = GameType.TEAM
    override val author = ApConstants.PERSON_LCLP
    override fun getIcon(manager: RegistryAccess): ItemStack = ItemStack(Items.CAMPFIRE)
    override fun canBeFinale(context: GameStartContext): Boolean = false

    override fun canBePlayed(context: GameStartContext): Boolean {
        val count = context.participantCount
        if (DEBUG_PLAYER_CONSTRAINT) return count >= 2
        return count == 2 || count >= 4
    }

    override fun createFactory(): MiniGameFactory = CozyCampfireFactory()
}
