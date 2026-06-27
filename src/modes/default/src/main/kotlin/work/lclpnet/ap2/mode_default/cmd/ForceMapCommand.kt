package work.lclpnet.ap2.mode_default.cmd;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import work.lclpnet.ap2.api.map.MapFacade;
import work.lclpnet.ap2.game.MiniGame;
import work.lclpnet.ap2.mode_default.cmd.arg.MapSuggestionProvider;
import work.lclpnet.kibu.cmd.type.CommandRegistrar;
import work.lclpnet.kibu.cmd.type.KibuCommand;

import java.util.Optional;
import java.util.function.Supplier;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class ForceMapCommand implements KibuCommand {

    private final MapFacade mapFacade;
    private final Supplier<Optional<MiniGame>> gameSupplier;

    public ForceMapCommand(MapFacade mapFacade, Supplier<Optional<MiniGame>> gameSupplier) {
        this.mapFacade = mapFacade;
        this.gameSupplier = gameSupplier;
    }

    @Override
    public void register(CommandRegistrar registrar) {
        registrar.registerCommand(command());
    }

    private LiteralArgumentBuilder<CommandSourceStack> command() {
        return literal("forcemap")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(argument("mapId", IdentifierArgument.id())
                        .suggests(new MapSuggestionProvider(mapFacade, gameSupplier))
                        .executes(this::forceMap));
    }

    private int forceMap(CommandContext<CommandSourceStack> ctx) {
        Identifier mapId = IdentifierArgument.getId(ctx, "mapId");

        mapFacade.forceMap(mapId);

        ctx.getSource().sendSystemMessage(Component.literal("Next map will be \"%s\"".formatted(mapId)));

        return 1;
    }
}
