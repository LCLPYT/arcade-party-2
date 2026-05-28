package work.lclpnet.ap2.turf_wars

import net.minecraft.core.RegistryAccess
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.game.*

class TurfWarsMiniGame : MiniGame {
    override fun getId(): Identifier = ApConstants.identifier("turf_wars")
    override fun getType(): GameType = GameType.TEAM
    override fun getAuthor(): String = ApConstants.PERSON_LCLP
    override fun getIcon(manager: RegistryAccess): ItemStack = ItemStack(Items.LIGHT_BLUE_CONCRETE)
    override fun canBeFinale(context: GameStartContext): Boolean = false
    override fun canBePlayed(context: GameStartContext): Boolean = context.participantCount == 2 || context.participantCount >= 4
    override fun createInstance(gameHandle: MiniGameHandle): MiniGameInstance = TurfWarsInstance(gameHandle)
}