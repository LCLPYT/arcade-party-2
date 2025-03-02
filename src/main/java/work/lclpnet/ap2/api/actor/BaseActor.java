package work.lclpnet.ap2.api.actor;

import lombok.Getter;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.Objects;

@Getter
public class BaseActor implements Actor {

    protected final ServerWorld world;
    protected final ActorType<?> type;
    private Vec3d position = Vec3d.ZERO;

    public BaseActor(ActorInit init) {
        this.world = init.world();
        this.type = init.actorType();
    }

    @Override
    public void setPosition(Vec3d pos) {
        position = Objects.requireNonNull(pos, "Position is null");
    }
}
