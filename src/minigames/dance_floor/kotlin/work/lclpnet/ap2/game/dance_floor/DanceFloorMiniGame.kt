package work.lclpnet.ap2.game.dance_floor

import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.DynamicRegistryManager
import net.minecraft.util.Identifier
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.game.*

class DanceFloorMiniGame : MiniGame {
    override fun canBeFinale(context: GameStartContext): Boolean = false
    override fun canBePlayed(context: GameStartContext): Boolean = true
    override fun getId(): Identifier = ApConstants.identifier("dance_floor")
    override fun getType(): GameType = GameType.FFA
    override fun getAuthor(): String = ApConstants.PERSON_LCLP
    override fun getIcon(manager: DynamicRegistryManager): ItemStack = ItemStack(Items.JUKEBOX)
    override fun createInstance(gameHandle: MiniGameHandle): MiniGameInstance = DanceFloorInstance(gameHandle)
}