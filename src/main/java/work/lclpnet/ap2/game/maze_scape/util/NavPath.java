package work.lclpnet.ap2.game.maze_scape.util;

import net.minecraft.util.math.Position;

import java.util.List;

import static java.lang.Math.sqrt;

/**
 * A navigation path that navigates using {@link Passage}s of a {@link work.lclpnet.ap2.game.maze_scape.gen.Graph}.
 */
public record NavPath(Position from, List<Passage> path, Position to) {

    public double length() {
        if (path.isEmpty()) {
            double dx = to.getX() - from.getX();
            double dy = to.getY() - from.getY();
            double dz = to.getZ() - from.getZ();

            return sqrt(dx * dx + dy * dy + dz * dz);
        }

        double distance = 0;
        var last = path.getFirst();

        // sum estimated distance between passages
        for (int i = 1, len = path.size(); i < len; i++) {
            var next = path.get(i);
            distance += sqrt(last.pos().getSquaredDistance(next.pos()));
            last = next;
        }

        // add estimated distance between exact from / to position and their respective passage
        distance += sqrt(path.getFirst().pos().getSquaredDistance(from));
        distance += sqrt(path.getLast().pos().getSquaredDistance(to));

        return distance;
    }
}
