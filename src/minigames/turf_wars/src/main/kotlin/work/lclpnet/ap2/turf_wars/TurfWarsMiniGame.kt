package work.lclpnet.ap2.turf_wars

import net.minecraft.core.RegistryAccess
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.game.GameStartContext
import work.lclpnet.ap2.api.game.GameType
import work.lclpnet.ap2.game.MiniGame
import work.lclpnet.ap2.game.MiniGameFactory
import work.lclpnet.ap2.game.util.MapLevelTeamSchemaGameFactory

class TurfWarsMiniGame : MiniGame {
    override val id = ApConstants.identifier("turf_wars")
    override val type = GameType.TEAM
    override val author = ApConstants.PERSON_LCLP
    override fun getIcon(manager: RegistryAccess): ItemStack = ItemStack(Items.DYED_TERRACOTTA.lightBlue())
    override fun canBeFinale(context: GameStartContext): Boolean = false
    override fun canBePlayed(context: GameStartContext): Boolean = context.participantCount == 2 || context.participantCount >= 4
    override fun createFactory(): MiniGameFactory = MapLevelTeamSchemaGameFactory(TurfWarsSchema::class.java, ::TurfWarsInstance)
}