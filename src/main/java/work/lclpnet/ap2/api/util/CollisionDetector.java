package work.lclpnet.ap2.api.util;

import net.minecraft.util.math.Position;
import org.jetbrains.annotations.Nullable;

public interface CollisionDetector {

    void add(Collider collider);

    void remove(Collider collider);

    @Nullable
    Collider getCollision(Position pos);
}
