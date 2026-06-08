package work.lclpnet.ap2.game.musical_minecart

import net.minecraft.core.RegistryAccess
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.game.GameStartContext
import work.lclpnet.ap2.api.game.GameType
import work.lclpnet.ap2.game.MiniGame
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance

class MusicalMinecartMiniGame : MiniGame {
    override val id = ApConstants.identifier("musical_minecart")
    override val type = GameType.FFA
    override val author = ApConstants.PERSON_LCLP
    override fun getIcon(manager: RegistryAccess): ItemStack = ItemStack(Items.MINECART)
    override fun canBeFinale(context: GameStartContext): Boolean = false
    override fun canBePlayed(context: GameStartContext): Boolean = true
    override fun createInstance(gameHandle: MiniGameHandle): MiniGameInstance = MusicalMinecartInstance(gameHandle)
}
