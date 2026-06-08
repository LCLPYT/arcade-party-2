package work.lclpnet.ap2.game.weapon_swap

import net.minecraft.core.RegistryAccess
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.game.*
import work.lclpnet.ap2.game.MiniGame
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance

class WeaponSwapMiniGame : MiniGame {
    override val id = ApConstants.identifier("weapon_swap")
    override val type = GameType.FFA
    override val author = ApConstants.PERSON_LCLP
    override fun getIcon(manager: RegistryAccess): ItemStack = ItemStack(Items.WOODEN_SWORD)
    override fun canBeFinale(context: GameStartContext): Boolean = true
    override fun canBePlayed(context: GameStartContext): Boolean = true
    override fun createInstance(gameHandle: MiniGameHandle): MiniGameInstance = WeaponSwapInstance(gameHandle)
}
