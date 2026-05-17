package work.lclpnet.ap2.game.musical_minecart

import net.minecraft.core.RegistryAccess
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.game.*

class MusicalMinecartMiniGame : MiniGame {
    override fun getId(): Identifier = ApConstants.identifier("musical_minecart")
    override fun getType(): GameType = GameType.FFA
    override fun getAuthor(): String = ApConstants.PERSON_LCLP
    override fun getIcon(manager: RegistryAccess): ItemStack = ItemStack(Items.MINECART)
    override fun canBeFinale(context: GameStartContext): Boolean = false
    override fun canBePlayed(context: GameStartContext): Boolean = true
    override fun createInstance(gameHandle: MiniGameHandle): MiniGameInstance = MusicalMinecartInstance(gameHandle)
}
