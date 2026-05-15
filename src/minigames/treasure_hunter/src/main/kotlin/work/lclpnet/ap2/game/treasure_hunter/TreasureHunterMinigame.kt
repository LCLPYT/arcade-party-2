package work.lclpnet.ap2.game.treasure_hunter

import net.minecraft.core.RegistryAccess
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.game.GameStartContext
import work.lclpnet.ap2.api.game.GameType
import work.lclpnet.ap2.api.game.MiniGame
import work.lclpnet.ap2.api.game.MiniGameHandle

class TreasureHunterMinigame : MiniGame {
    override fun getId() = ApConstants.identifier("treasure_hunter")
    override fun getType() = GameType.FFA
    override fun getAuthor() = ApConstants.PERSON_BOPS
    override fun getIcon(manager: RegistryAccess) = ItemStack(Items.CHEST)
    override fun canBeFinale(context: GameStartContext) = true
    override fun canBePlayed(context: GameStartContext) = true
    override fun createInstance(gameHandle: MiniGameHandle) = TreasureHunterInstance(gameHandle)
}
