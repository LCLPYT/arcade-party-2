package work.lclpnet.ap2.api.util.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.List;

public interface SpaceFinder {

    List<Vec3> findSpaces(Iterator<BlockPos> positions);

    default List<Vec3> findSpaces(Iterable<BlockPos> positions) {
        return findSpaces(positions.iterator());
    }
}
