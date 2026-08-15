package work.lclpnet.ap2.capture_the_flag

import net.minecraft.core.RegistryAccess
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.base.GameStartContext
import work.lclpnet.ap2.game.GameType
import work.lclpnet.ap2.game.MiniGame
import work.lclpnet.ap2.game.MiniGameFactory
import work.lclpnet.ap2.game.util.MapLevelTeamSchemaGameFactory

class CaptureTheFlagMiniGame : MiniGame {
    override val id = ApConstants.identifier("capture_the_flag")
    override val type = GameType.TEAM
    override val author = ApConstants.PERSON_LCLP
    override fun getIcon(manager: RegistryAccess): ItemStack = ItemStack(Items.BANNER.green)
    override fun canBeFinale(context: GameStartContext): Boolean = false
    override fun canBePlayed(context: GameStartContext): Boolean = true
    override fun createFactory(): MiniGameFactory = MapLevelTeamSchemaGameFactory(CtfSchema::class.java, ::CaptureTheFlagInstance)
}
