package work.lclpnet.ap2.game.glowing_bomb

import net.minecraft.core.RegistryAccess
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.game.*

class GlowingBombMiniGame : MiniGame {
    override fun getId(): Identifier = ApConstants.identifier("glowing_bomb")
    override fun getType(): GameType = GameType.FFA
    override fun getAuthor(): String = ApConstants.PERSON_LCLP
    override fun getIcon(manager: RegistryAccess): ItemStack = ItemStack(Items.RESPAWN_ANCHOR)
    override fun canBeFinale(context: GameStartContext): Boolean = true

    override fun canBePlayed(context: GameStartContext): Boolean =
        context.participantCount <= 12  // maps should support respawn anchor positioning of max 12 players

    override fun createInstance(gameHandle: MiniGameHandle): MiniGameInstance = GlowingBombInstance(gameHandle)
}
