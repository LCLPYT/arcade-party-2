package work.lclpnet.ap2.api.actor;

import lombok.Getter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

@Getter
public class BaseActor implements Actor {

    protected final ServerLevel world;
    protected final ActorType<?> type;
    private Vec3 position = Vec3.ZERO;

    public BaseActor(ActorInit init) {
        this.world = init.world();
        this.type = init.actorType();
    }

    @Override
    public void setPosition(Vec3 pos) {
        position = Objects.requireNonNull(pos, "Position is null");
    }
}
