package work.lclpnet.ap2.game.cozy_campfire

import net.minecraft.core.RegistryAccess
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.game.*

private const val DEBUG_PLAYER_CONSTRAINT = false

class CozyCampfireMiniGame : MiniGame {
    override fun getId(): Identifier = ApConstants.identifier("cozy_campfire")
    override fun getType(): GameType = GameType.TEAM
    override fun getAuthor(): String = ApConstants.PERSON_LCLP
    override fun getIcon(manager: RegistryAccess): ItemStack = ItemStack(Items.CAMPFIRE)
    override fun canBeFinale(context: GameStartContext): Boolean = false

    override fun canBePlayed(context: GameStartContext): Boolean {
        val count = context.participantCount
        if (DEBUG_PLAYER_CONSTRAINT) return count >= 2
        return count == 2 || count >= 4
    }

    override fun createInstance(gameHandle: MiniGameHandle): MiniGameInstance = CozyCampfireInstance(gameHandle)
}
