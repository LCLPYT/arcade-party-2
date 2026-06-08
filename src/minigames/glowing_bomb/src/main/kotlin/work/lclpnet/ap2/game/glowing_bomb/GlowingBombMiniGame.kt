package work.lclpnet.ap2.game.glowing_bomb

import net.minecraft.core.RegistryAccess
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.game.GameStartContext
import work.lclpnet.ap2.api.game.GameType
import work.lclpnet.ap2.game.MapLevelGameFactory
import work.lclpnet.ap2.game.MiniGame
import work.lclpnet.ap2.game.MiniGameFactory

class GlowingBombMiniGame : MiniGame {
    override val id = ApConstants.identifier("glowing_bomb")
    override val type = GameType.FFA
    override val author = ApConstants.PERSON_LCLP
    override fun getIcon(manager: RegistryAccess): ItemStack = ItemStack(Items.RESPAWN_ANCHOR)
    override fun canBeFinale(context: GameStartContext): Boolean = true

    override fun canBePlayed(context: GameStartContext): Boolean =
        context.participantCount <= 12  // maps should support respawn anchor positioning of max 12 players

    override fun createFactory(): MiniGameFactory = MapLevelGameFactory(::GlowingBombInstance)
}
