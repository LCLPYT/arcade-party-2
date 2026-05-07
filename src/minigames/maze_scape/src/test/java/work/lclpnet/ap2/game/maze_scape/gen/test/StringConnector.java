package work.lclpnet.ap2.game.maze_scape.gen.test;

public record StringConnector(int x, int y, int direction) {

    static final char[] ARROWS = new char[] {'→', '↓', '←', '↑'};

    /**
     * Calculate the amount of rotation another connector has to be rotated in order to face this connector.
     * @param other The other piece that should face this connector
     * @return The amount of rotation the other connector has to be rotated.
     */
    public int rotateToFace(StringConnector other) {
        return Math.floorMod(this.direction - other.direction + 2, 4);
    }

    public int directionX() {
        if (direction % 4 == 0) return 1;
        if (direction % 4 == 2) return -1;
        return 0;
    }

    public int directionY() {
        if (direction % 4 == 1) return 1;
        if (direction % 4 == 3) return -1;
        return 0;
    }

    @Override
    public String toString() {
        return "StringConnector{x=%d, y=%d, direction=%s}".formatted(x, y, ARROWS[direction % 4]);
    }
}
