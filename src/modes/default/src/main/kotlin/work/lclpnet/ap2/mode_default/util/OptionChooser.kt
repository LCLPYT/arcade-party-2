package work.lclpnet.ap2.mode_default.util

import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.ItemStack
import work.lclpnet.ap2.ext.mc.playNotifySound
import work.lclpnet.game.impl.menu.PaginatedOptionMenu
import work.lclpnet.kibu.hook.HookRegistrar
import work.lclpnet.kibu.translate.Translations

/**
 * An admin option picker backed by a paginated, searchable and sortable [PaginatedOptionMenu].
 * Only game masters can interact with it; selecting an option runs the configured action, plays a
 * confirmation sound and closes the menu.
 * 
 * @param <T> The option type.
</T> */
class OptionChooser<T>(
    id: Identifier,
    translations: Translations,
    title: (ServerPlayer) -> Component,
    iconFactory: (ServerPlayer, T) -> ItemStack,
    searchText: (ServerPlayer, T) -> String,
    action: (T, ServerPlayer) -> Unit
) {
    private val menu: PaginatedOptionMenu<T> = PaginatedOptionMenu.builder<T>(translations, id)
        .title(title)
        .optionIcon(iconFactory)
        .searchText(searchText)
        .search(true)
        .sort(true)
        .canInteract { player -> isGameMaster(player) }
        .closeOnSelect(true)
        .onSelect { player, option ->
            action(option, player)

            player.playNotifySound(
                SoundEvents.NOTE_BLOCK_PLING.value(),
                SoundSource.PLAYERS,
                0.5f,
                2f
            )
        }
        .build()

    fun init(hooks: HookRegistrar) {
        menu.init(hooks)
    }

    fun open(player: ServerPlayer, options: Collection<T>) {
        menu.setOptions(options)
        menu.open(player)
    }
}

private fun isGameMaster(player: ServerPlayer): Boolean {
    val server = player.level().server

    return Commands.LEVEL_GAMEMASTERS.check(server.getProfilePermissions(player.nameAndId()))
}
