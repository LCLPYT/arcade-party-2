package work.lclpnet.ap2.game.eggventure

import net.minecraft.core.RegistryAccess
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.game.*
import work.lclpnet.ap2.impl.util.ApRegistries
import work.lclpnet.ap2.impl.util.heads.PlayerHeads

class EggventureMiniGame : MiniGame {
    override fun getId(): Identifier = ApConstants.identifier("eggventure")
    override fun getType(): GameType = GameType.FFA
    override fun getAuthor(): String = ApConstants.PERSON_LCLP
    override fun canBeFinale(context: GameStartContext): Boolean = true
    override fun canBePlayed(context: GameStartContext): Boolean = true
    override fun createInstance(gameHandle: MiniGameHandle): MiniGameInstance = EggventureInstance(gameHandle)
    override fun getIcon(manager: RegistryAccess): ItemStack = manager.lookupOrThrow(ApRegistries.PLAYER_HEAD)
        .getValueOrThrow(PlayerHeads.EASTER_EGG_PINK_PATTERN)
        .createStack()
}
