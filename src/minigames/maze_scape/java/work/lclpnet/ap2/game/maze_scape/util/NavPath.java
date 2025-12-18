package work.lclpnet.ap2.game.maze_scape.util;

import net.minecraft.core.Position;

import java.util.List;

import static java.lang.Math.sqrt;

/**
 * A navigation path that navigates using {@link Passage}s of a {@link work.lclpnet.ap2.game.maze_scape.gen.Graph}.
 */
public record NavPath(Position from, List<Passage> path, Position to) {

    public double length() {
        if (path.isEmpty()) {
            double dx = to.x() - from.x();
            double dy = to.y() - from.y();
            double dz = to.z() - from.z();

            return sqrt(dx * dx + dy * dy + dz * dz);
        }

        double distance = 0;
        var last = path.getFirst();

        // sum estimated distance between passages
        for (int i = 1, len = path.size(); i < len; i++) {
            var next = path.get(i);
            distance += sqrt(last.pos().distSqr(next.pos()));
            last = next;
        }

        // add estimated distance between exact from / to position and their respective passage
        distance += sqrt(path.getFirst().pos().distToCenterSqr(from));
        distance += sqrt(path.getLast().pos().distToCenterSqr(to));

        return distance;
    }
}
