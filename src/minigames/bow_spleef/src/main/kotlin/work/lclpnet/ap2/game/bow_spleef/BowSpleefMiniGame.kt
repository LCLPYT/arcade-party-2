package work.lclpnet.ap2.game.bow_spleef

import net.minecraft.core.RegistryAccess
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.game.*

class BowSpleefMiniGame : MiniGame {
    override fun getId(): Identifier = ApConstants.identifier("bow_spleef")
    override fun getType(): GameType = GameType.FFA
    override fun getAuthor(): String = ApConstants.PERSON_BOPS
    override fun getIcon(manager: RegistryAccess): ItemStack = ItemStack(Items.BOW)
    override fun canBeFinale(context: GameStartContext): Boolean = true
    override fun canBePlayed(context: GameStartContext): Boolean = true
    override fun createInstance(gameHandle: MiniGameHandle): MiniGameInstance = BowSpleefInstance(gameHandle)
}
