package work.lclpnet.ap2.game.speed_builders

import net.minecraft.core.RegistryAccess
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.game.*

class SpeedBuildersMiniGame : MiniGame {
    override fun getId(): Identifier = ApConstants.identifier("speed_builders")
    override fun getType(): GameType = GameType.FFA
    override fun getAuthor(): String = ApConstants.PERSON_LCLP
    override fun getIcon(manager: RegistryAccess): ItemStack = ItemStack(Items.BRICKS)
    override fun canBeFinale(context: GameStartContext) = true
    override fun canBePlayed(context: GameStartContext) = true
    override fun createInstance(gameHandle: MiniGameHandle): MiniGameInstance = SpeedBuildersInstance(gameHandle)
}
