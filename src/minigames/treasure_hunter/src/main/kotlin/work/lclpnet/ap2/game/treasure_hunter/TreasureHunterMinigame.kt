package work.lclpnet.ap2.game.treasure_hunter

import net.minecraft.core.RegistryAccess
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.base.GameStartContext
import work.lclpnet.ap2.game.GameType
import work.lclpnet.ap2.game.MiniGame
import work.lclpnet.ap2.game.util.MapLevelGameFactory

class TreasureHunterMinigame : MiniGame {
    override val id = ApConstants.identifier("treasure_hunter")
    override val type = GameType.FFA
    override val author = ApConstants.PERSON_BOPS
    override fun getIcon(manager: RegistryAccess) = ItemStack(Items.CHEST)
    override fun canBeFinale(context: GameStartContext) = true
    override fun canBePlayed(context: GameStartContext) = true
    override fun createFactory() = MapLevelGameFactory(::TreasureHunterInstance)
}
