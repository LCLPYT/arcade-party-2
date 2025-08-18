package work.lclpnet.ap2.game.knockout.util;

public class SquareInwardsIterator extends AbstractHeightIterator {
    static final int[] DX = new int[]{1, 0, -1, 0};
    static final int[] DZ = new int[]{0, 1, 0, -1};

    boolean first;
    int r;
    int steps;
    int repeat;
    int mode;

    public SquareInwardsIterator(int radius, int centerX, int centerZ, int minY, int maxY) {
        super(radius, centerX, centerZ, minY, maxY);
        first = true;
        r = 2 * radius;
        steps = r;
        repeat = 2;
        mode = 0;
    }

    @Override
    protected boolean advance2d() {
        if (first) {
            first = false;
            return true;
        }

        if (r <= 0) return false;

        if (steps > 0) {
            rx += DX[mode];
            rz += DZ[mode];
            steps--;
            return true;
        }

        mode = (mode + 1) % 4;

        if (repeat > 0) {
            repeat--;
        } else {
            r--;
            repeat = 1;
        }

        steps = r;

        return advance2d();
    }
}
