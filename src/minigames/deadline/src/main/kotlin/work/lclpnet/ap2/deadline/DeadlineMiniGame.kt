package work.lclpnet.ap2.deadline

import net.minecraft.core.RegistryAccess
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.base.GameStartContext
import work.lclpnet.ap2.game.GameType
import work.lclpnet.ap2.game.MiniGame
import work.lclpnet.ap2.game.MiniGameFactory
import work.lclpnet.ap2.game.util.MapLevelSchemaGameFactory

class DeadlineMiniGame : MiniGame {
    override val id = ApConstants.identifier("deadline")
    override val type = GameType.FFA
    override val author = ApConstants.PERSON_QUADRUBO
    override fun getIcon(manager: RegistryAccess): ItemStack = ItemStack(Items.STAINED_GLASS_PANE.pick(DyeColor.RED))
    override fun canBeFinale(context: GameStartContext): Boolean = true
    override fun canBePlayed(context: GameStartContext): Boolean = true
    override fun createFactory(): MiniGameFactory = MapLevelSchemaGameFactory(DeadlineMapSchema::class.java, ::DeadlineInstance)
}
