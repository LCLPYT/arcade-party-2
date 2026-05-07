package work.lclpnet.ap2.game.maze_scape.gen.test;

import work.lclpnet.ap2.game.maze_scape.gen.Piece;

import java.util.ArrayList;
import java.util.List;

public class StringPiece implements Piece<StringConnector> {

    private final String string;
    private final int width, height;
    private final List<StringConnector> connectors;

    public StringPiece(String str) {
        String[] lines = str.split("\n");

        int maxWidth = 0;

        for (String line : lines) {
            maxWidth = Math.max(maxWidth, line.length());
        }

        width = maxWidth;
        height = lines.length;

        List<StringConnector> connectors = new ArrayList<>(2);

        for (int y = 0; y < lines.length; y++) {
            lines[y] = String.format("%-" + width + "s", lines[y]);

            int dir;

            for (int x = 0; x < width; x++) {
                switch (lines[y].charAt(x)) {
                    case '→' -> dir = 0;
                    case '↓' -> dir = 1;
                    case '←' -> dir = 2;
                    case '↑' -> dir = 3;
                    default -> { continue; }
                }

                connectors.add(new StringConnector(x, y, dir));
            }
        }

        this.connectors = connectors;

        this.string = String.join("\n", lines);
    }

    public String getString() {
        return string;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    @Override
    public List<StringConnector> connectors() {
        return connectors;
    }
}
