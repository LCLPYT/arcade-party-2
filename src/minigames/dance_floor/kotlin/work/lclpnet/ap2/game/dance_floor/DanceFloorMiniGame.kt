package work.lclpnet.ap2.game.dance_floor

import net.minecraft.core.RegistryAccess
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.game.GameStartContext
import work.lclpnet.ap2.api.game.GameType
import work.lclpnet.ap2.api.game.MiniGame
import work.lclpnet.ap2.api.game.MiniGameHandle

class DanceFloorMiniGame : MiniGame {
    override fun canBeFinale(context: GameStartContext) = false
    override fun canBePlayed(context: GameStartContext) = true
    override fun getId() = ApConstants.identifier("dance_floor")
    override fun getType() = GameType.FFA
    override fun getAuthor() = ApConstants.PERSON_LCLP
    override fun getIcon(manager: RegistryAccess) = ItemStack(Items.JUKEBOX)
    override fun createInstance(gameHandle: MiniGameHandle) = DanceFloorInstance(gameHandle)
}