package work.lclpnet.ap2.impl.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.function.CommandFunction;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;

import java.util.Optional;

public class FunctionExecutor {

    private final MinecraftServer server;
    private final ServerCommandSource commandSource;

    public FunctionExecutor(MinecraftServer server, Logger logger) {
        this("FunctionExecutor", server, logger);
    }

    public FunctionExecutor(String name, MinecraftServer server, Logger logger) {
        this.server = server;

        var output = new LoggerCommandOutput(logger);

        ServerWorld world = server.getOverworld();
        Vec3d pos = world == null ? Vec3d.ZERO : Vec3d.of(world.getSpawnPos());

        this.commandSource = new ServerCommandSource(output, pos, Vec2f.ZERO, world, 2, name, Text.literal(name), server, null).withSilent();
    }

    public ServerCommandSource commandSource() {
        return commandSource;
    }

    public void execute(CommandFunction<ServerCommandSource> function, ServerCommandSource source) {
        server.getCommandFunctionManager().execute(function, source);
    }

    public Optional<CommandFunction<ServerCommandSource>> getFunction(Identifier id) {
        return server.getCommandFunctionManager().getFunction(id);
    }

    private record LoggerCommandOutput(Logger logger) implements CommandOutput {

        @Override
        public void sendMessage(Text message) {
            logger.info(message.getString());
        }

        @Override
        public boolean shouldReceiveFeedback() {
            return true;
        }

        @Override
        public boolean shouldTrackOutput() {
            return true;
        }

        @Override
        public boolean shouldBroadcastConsoleToOps() {
            return false;
        }
    }
}
