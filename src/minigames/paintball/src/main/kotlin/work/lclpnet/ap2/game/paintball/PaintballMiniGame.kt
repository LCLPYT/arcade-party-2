package work.lclpnet.ap2.game.paintball

import net.minecraft.core.RegistryAccess
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.game.*

class PaintballMiniGame : MiniGame {

    override fun getId(): Identifier = ApConstants.identifier("paintball")

    override fun getType(): GameType = GameType.TEAM

    override fun getAuthor(): String = ApConstants.PERSON_LCLP

    override fun getIcon(manager: RegistryAccess): ItemStack = ItemStack(Items.IRON_HORSE_ARMOR)

    override fun canBeFinale(context: GameStartContext): Boolean = false

    override fun canBePlayed(context: GameStartContext): Boolean {
        val count = context.participantCount
        return count == 2 || count >= 4
    }

    override fun createInstance(gameHandle: MiniGameHandle): MiniGameInstance = PaintballInstance(gameHandle)
}
