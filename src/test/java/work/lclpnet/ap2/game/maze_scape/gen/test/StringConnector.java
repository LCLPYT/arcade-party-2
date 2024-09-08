package work.lclpnet.ap2.game.maze_scape.gen.test;

import static work.lclpnet.ap2.game.maze_scape.gen.test.StringPiece.ARROWS;

public record StringConnector(int x, int y, int direction) {

    /**
     * Calculate the amount of rotation another connector has to be rotated in order to face this connector.
     * @param other The other piece that should face this connector
     * @return The amount of rotation the other connector has to be rotated.
     */
    public int rotateToFace(StringConnector other) {
        return (this.direction - other.direction + 2) % 4;
    }

    @Override
    public String toString() {
        return "StringConnector{x=%d, y=%d, direction=%s}".formatted(x, y, ARROWS[direction % 4]);
    }
}
