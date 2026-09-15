package work.lclpnet.ap2.game

import net.minecraft.core.RegistryAccess
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import work.lclpnet.kibu.translate.Translations

interface GameInfo {

    /**
     * @return A unique [Identifier] for the game.
     */
    val id: Identifier

    /**
     * @return The type of the game.
     */
    val type: GameType

    val author: String

    fun getIcon(manager: RegistryAccess): ItemStack

    val titleKey: String
        get() {
            val id = this.id

            return "game.${id.namespace}.${id.path}"
        }

    val descriptionKey: String
        get() {
            val id = this.id

            return "game.${id.namespace}.${id.path}.description"
        }

    fun descriptionArguments(translations: Translations): Array<Any> = emptyArray()

    val taskKey: String
        get() {
            val id = this.id

            return "game.${id.namespace}.${id.path}.task"
        }

    val taskArguments: Array<Any>
        get() = emptyArray()

    fun identifier(subPath: String): Identifier {
        val gameId = this.id

        return Identifier.fromNamespaceAndPath(gameId.namespace, "${gameId.path}/$subPath")
    }
}
