package work.lclpnet.ap2.game.eggventure

import net.minecraft.core.RegistryAccess
import net.minecraft.world.item.ItemStack
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.base.GameStartContext
import work.lclpnet.ap2.game.GameType
import work.lclpnet.ap2.game.MiniGame
import work.lclpnet.ap2.game.MiniGameFactory
import work.lclpnet.ap2.impl.util.ApRegistries
import work.lclpnet.ap2.impl.util.heads.PlayerHeads

class EggventureMiniGame : MiniGame {
    override val id = ApConstants.identifier("eggventure")
    override val type = GameType.FFA
    override val author = ApConstants.PERSON_LCLP
    override fun canBeFinale(context: GameStartContext): Boolean = true
    override fun canBePlayed(context: GameStartContext): Boolean = true
    override fun createFactory(): MiniGameFactory = EggventureFactory()
    override fun getIcon(manager: RegistryAccess): ItemStack = manager.lookupOrThrow(ApRegistries.PLAYER_HEAD)
        .getValueOrThrow(PlayerHeads.EASTER_EGG_PINK_PATTERN)
        .createStack()
}
