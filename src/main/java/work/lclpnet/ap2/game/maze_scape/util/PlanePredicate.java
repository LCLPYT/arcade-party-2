package work.lclpnet.ap2.game.maze_scape.util;

import net.minecraft.util.math.Vec3i;

public class PlanePredicate implements Int3Predicate {
    final int ox, oy, oz;
    final int nx, ny, nz;

    public PlanePredicate(Vec3i origin, Vec3i normal) {
        ox = origin.getX();
        oy = origin.getY();
        oz = origin.getZ();
        nx = normal.getX();
        ny = normal.getY();
        nz = normal.getZ();
    }

    @Override
    public boolean test(int x, int y, int z) {
        return (x - ox) * nx + (y - oy) * ny + (z - oz) * nz == 0;
    }
}
