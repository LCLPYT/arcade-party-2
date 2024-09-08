package work.lclpnet.ap2.game.maze_scape.gen.test;

import org.apache.commons.lang3.mutable.MutableInt;
import work.lclpnet.ap2.game.maze_scape.gen.Graph;

import java.util.Arrays;
import java.util.stream.Collectors;

public class GraphStringSerializer {

    public static String genString(Graph<StringConnector, StringPiece, OrientedStringPiece> graph) {
        MutableInt minX = new MutableInt(Integer.MAX_VALUE), maxX = new MutableInt(Integer.MIN_VALUE),
                minY = new MutableInt(Integer.MAX_VALUE), maxY = new MutableInt(Integer.MIN_VALUE);

        // determine dimensions
        graph.root().traverse(node -> {
            OrientedStringPiece oriented = node.oriented();

            if (oriented == null) return false;

            int x = oriented.x(), y = oriented.y();

            if (x < minX.intValue()) {
                minX.setValue(x);
            }

            if (y < minY.intValue()) {
                minY.setValue(y);
            }

            int mx = x + oriented.width(), my = y + oriented.height();

            if (mx > maxX.intValue()) {
                maxX.setValue(mx);
            }

            if (my > maxY.intValue()) {
                maxY.setValue(my);
            }

            return true;
        });

        int width = maxX.intValue() - minX.intValue();
        int height = maxY.intValue() - minY.intValue();

        if (width > 100 || height > 100) {
            throw new IllegalStateException("Dimensions to big: %dx%d".formatted(width, height));
        }

        int ox = -Math.min(0, minX.intValue());
        int oy = -Math.min(0, minY.intValue());

        char[][] chars = new char[height][width];

        for (int y = 0; y < height; y++) {
            Arrays.fill(chars[y], ' ');
        }

        graph.root().traverse(node -> {
            OrientedStringPiece oriented = node.oriented();

            if (oriented == null) return false;

            // place oriented piece
            int sx = oriented.x() + ox, sy = oriented.y() + oy;

            String[] lines = oriented.string().split("\n");

            for (int y = 0; y < lines.length; y++) {
                for (int x = 0; x < oriented.width(); x++) {
                    chars[sy + y][sx + x] = lines[y].charAt(x);
                }
            }

            return true;
        });

        return Arrays.stream(chars)
                .map(String::new)
                .collect(Collectors.joining("\n"));
    }
}
