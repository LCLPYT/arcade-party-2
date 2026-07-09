package work.lclpnet.ap2.dragons

import net.minecraft.core.RegistryAccess
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.game.GameStartContext
import work.lclpnet.ap2.api.game.GameType
import work.lclpnet.ap2.game.MiniGame
import work.lclpnet.ap2.game.MiniGameFactory
import work.lclpnet.ap2.game.util.MapLevelSchemaGameFactory

class DragonsMiniGame : MiniGame {
    override val id = ApConstants.identifier("dragons")
    override val type = GameType.FFA
    override val author = ApConstants.PERSON_LCLP
    override fun getIcon(manager: RegistryAccess): ItemStack = ItemStack(Items.DRAGON_EGG)
    override fun canBeFinale(context: GameStartContext): Boolean = true
    override fun canBePlayed(context: GameStartContext): Boolean = true
    override fun createFactory(): MiniGameFactory =
        MapLevelSchemaGameFactory(DragonEscapeMapSchema::class.java, ::DragonsInstance)
}
