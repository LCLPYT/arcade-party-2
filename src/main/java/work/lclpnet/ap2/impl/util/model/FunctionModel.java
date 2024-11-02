package work.lclpnet.ap2.impl.util.model;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.function.CommandFunction;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.api.util.model.Model;
import work.lclpnet.ap2.impl.util.FunctionExecutor;

public class FunctionModel implements Model {

    private final FunctionExecutor executor;
    private final CommandFunction<ServerCommandSource> function;

    public FunctionModel(FunctionExecutor executor, CommandFunction<ServerCommandSource> function) {
        this.executor = executor;
        this.function = function;
    }

    @Override
    public void spawn(ServerWorld world, Vec3d pos) {
        ServerCommandSource source = executor.commandSource().withWorld(world).withPosition(pos);
        executor.execute(function, source);
    }
}
