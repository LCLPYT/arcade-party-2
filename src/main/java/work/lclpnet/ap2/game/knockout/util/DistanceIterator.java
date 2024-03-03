package work.lclpnet.ap2.game.knockout.util;

public class DistanceIterator extends AbstractHeightIterator {
    private final int len;
    private final short[][] distances;
    private final int targetDistance;
    boolean first;

    public DistanceIterator(int radius, int centerX, int centerZ, int minY, int maxY, short[][] distances, int targetDistance) {
        super(radius, centerX, centerZ, minY, maxY);
        this.distances = distances;
        this.len = distances.length;
        this.targetDistance = targetDistance;
        first = true;
    }

    @Override
    protected boolean advance2d() {
        // do not advance initially (decrement will result in taking no step)
        if (first) {
            rx--;
            first = false;
        }

        short distance;

        do {
            rx++;

            if (rx >= len) {
                rx = 0;
                rz++;
            }

            if (rz >= len) {
                return false;
            }

            distance = distances[rx][rz];
        } while (distance != targetDistance);

        return true;
    }
}
