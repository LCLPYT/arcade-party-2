package work.lclpnet.ap2.game.chicken_shooter

import net.minecraft.core.RegistryAccess
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.base.GameStartContext
import work.lclpnet.ap2.game.GameType
import work.lclpnet.ap2.game.MiniGame
import work.lclpnet.ap2.game.MiniGameFactory
import work.lclpnet.ap2.game.util.MapLevelGameFactory

class ChickenShooterMiniGame : MiniGame {
    override val id = ApConstants.identifier("chicken_shooter")
    override val type = GameType.FFA
    override val author = ApConstants.PERSON_BOPS
    override fun getIcon(manager: RegistryAccess): ItemStack = ItemStack(Items.FEATHER)
    override fun canBeFinale(context: GameStartContext): Boolean = true
    override fun canBePlayed(context: GameStartContext): Boolean = true
    override fun createFactory(): MiniGameFactory = MapLevelGameFactory(::ChickenShooterInstance)
}
