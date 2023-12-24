package work.lclpnet.ap2.api.util.world;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Iterator;
import java.util.List;

public interface SpaceFinder {

    List<Vec3d> findSpaces(Iterator<BlockPos> positions);
}
