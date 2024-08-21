package work.lclpnet.ap2.base.cmd;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import work.lclpnet.ap2.api.game.MiniGame;
import work.lclpnet.ap2.api.map.MapFacade;
import work.lclpnet.ap2.base.cmd.arg.MapSuggestionProvider;
import work.lclpnet.kibu.cmd.type.CommandRegistrar;
import work.lclpnet.kibu.cmd.type.KibuCommand;

import java.util.Optional;
import java.util.function.Supplier;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

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

    private LiteralArgumentBuilder<ServerCommandSource> command() {
        return literal("forcemap")
                .requires(s -> s.hasPermissionLevel(2))
                .then(argument("mapId", IdentifierArgumentType.identifier())
                        .suggests(new MapSuggestionProvider(mapFacade, gameSupplier))
                        .executes(this::forceMap));
    }

    private int forceMap(CommandContext<ServerCommandSource> ctx) {
        Identifier mapId = IdentifierArgumentType.getIdentifier(ctx, "mapId");

        mapFacade.forceMap(mapId);

        ctx.getSource().sendMessage(Text.literal("Next map will be \"%s\"".formatted(mapId)));

        return 1;
    }
}
