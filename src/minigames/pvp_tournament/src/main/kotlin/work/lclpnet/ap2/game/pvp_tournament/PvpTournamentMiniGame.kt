package work.lclpnet.ap2.game.pvp_tournament

import net.minecraft.core.RegistryAccess
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.game.GameStartContext
import work.lclpnet.ap2.api.game.GameType
import work.lclpnet.ap2.api.game.MiniGame
import work.lclpnet.ap2.api.game.MiniGameHandle

class PvpTournamentMiniGame : MiniGame {
    override fun canBeFinale(context: GameStartContext) = true
    override fun canBePlayed(context: GameStartContext) = context.participantCount >= 2
    override fun getId() = ApConstants.identifier("pvp_tournament")
    override fun getType() = GameType.FFA
    override fun getAuthor() = ApConstants.PERSON_LCLP
    override fun getIcon(manager: RegistryAccess) = ItemStack(Items.STONE_SWORD)
    override fun createInstance(gameHandle: MiniGameHandle) = PvpTournamentInstance(gameHandle)
}
