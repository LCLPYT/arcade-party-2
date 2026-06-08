package work.lclpnet.ap2.game.red_light_green_light

import net.minecraft.core.RegistryAccess
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.game.GameStartContext
import work.lclpnet.ap2.api.game.GameType
import work.lclpnet.ap2.game.MiniGame
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance

class RedLightGreenLightMiniGame : MiniGame {
    override val id = ApConstants.identifier("red_light_green_light")
    override val type = GameType.FFA
    override val author = ApConstants.PERSON_LCLP
    override fun getIcon(manager: RegistryAccess): ItemStack = ItemStack(Items.LIME_WOOL)
    override fun canBeFinale(context: GameStartContext): Boolean = true
    override fun canBePlayed(context: GameStartContext): Boolean = true
    override fun createInstance(gameHandle: MiniGameHandle): MiniGameInstance = RedLightGreenLightInstance(gameHandle)
}
