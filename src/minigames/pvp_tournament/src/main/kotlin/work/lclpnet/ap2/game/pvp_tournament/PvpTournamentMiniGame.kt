package work.lclpnet.ap2.game.pvp_tournament

import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.core.RegistryAccess
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.game.*

class PvpTournamentMiniGame : MiniGame {
    override fun canBeFinale(context: GameStartContext) = true
    override fun canBePlayed(context: GameStartContext) = true
    override fun getId() = ApConstants.identifier("pvp_tournament")
    override fun getType() = GameType.FFA
    override fun getAuthor() = ApConstants.PERSON_LCLP
    override fun getIcon(manager: RegistryAccess) = ItemStack(Items.STONE_SWORD)
    override fun createInstance(gameHandle: MiniGameHandle) = PvpTournamentInstance(gameHandle)
}
