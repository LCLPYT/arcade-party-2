package work.lclpnet.ap2.game.apocalypse_survival

import net.minecraft.core.RegistryAccess
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.game.GameStartContext
import work.lclpnet.ap2.api.game.GameType
import work.lclpnet.ap2.game.MapLevelGameFactory
import work.lclpnet.ap2.game.MiniGame
import work.lclpnet.ap2.game.MiniGameHandle

class ApocalypseSurvivalMiniGame : MiniGame {
    override val id = ApConstants.identifier("apocalypse_survival")
    override val type = GameType.FFA
    override val author = ApConstants.PERSON_LCLP
    override fun getIcon(manager: RegistryAccess) = ItemStack(Items.ZOMBIE_HEAD)
    override fun canBeFinale(context: GameStartContext) = true
    override fun canBePlayed(context: GameStartContext) = true
    override fun createFactory(gameHandle: MiniGameHandle) = MapLevelGameFactory(::ApocalypseSurvivalInstance)
}
