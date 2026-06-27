package work.lclpnet.ap2.mode_default.util;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import work.lclpnet.game.impl.menu.PaginatedOptionMenu;
import work.lclpnet.kibu.access.entity.ServerPlayerAccess;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.translate.Translations;

import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * An admin option picker backed by a paginated, searchable and sortable {@link PaginatedOptionMenu}.
 * Only game masters can interact with it; selecting an option runs the configured action, plays a
 * confirmation sound and closes the menu.
 *
 * @param <T> The option type.
 */
public class OptionChooser<T> {

    private final PaginatedOptionMenu<T> menu;

    public OptionChooser(Identifier id, Translations translations,
                         Function<ServerPlayer, Component> title,
                         BiFunction<ServerPlayer, T, ItemStack> iconFactory,
                         BiFunction<ServerPlayer, T, String> searchText,
                         BiConsumer<T, ServerPlayer> action) {

        this.menu = PaginatedOptionMenu.<T>builder(translations, id)
                .title(title)
                .optionIcon(iconFactory)
                .searchText(searchText)
                .search(true)
                .sort(true)
                .canInteract(OptionChooser::isGameMaster)
                .closeOnSelect(true)
                .onSelect((player, option) -> {
                    action.accept(option, player);
                    ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.5f, 2f);
                })
                .build();
    }

    public void init(HookRegistrar hooks) {
        menu.init(hooks);
    }

    public void open(ServerPlayer player, Collection<T> options) {
        menu.setOptions(options);
        menu.open(player);
    }

    private static boolean isGameMaster(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        return Commands.LEVEL_GAMEMASTERS.check(server.getProfilePermissions(player.nameAndId()));
    }
}
