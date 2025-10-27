package work.lclpnet.ap2.game.killeporter

import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.DynamicRegistryManager
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.game.*

class KilleporterMiniGame : MiniGame {
    override fun canBeFinale(context: GameStartContext) = false
    override fun canBePlayed(context: GameStartContext) = true
    override fun getId() = ApConstants.identifier("killeporter")
    override fun getType() = GameType.FFA
    override fun getAuthor() = ApConstants.PERSON_BOPS
    override fun getIcon(manager: DynamicRegistryManager) = ItemStack(Items.ENDER_PEARL)
    override fun createInstance(gameHandle: MiniGameHandle) = KilleporterInstance(gameHandle)
}
