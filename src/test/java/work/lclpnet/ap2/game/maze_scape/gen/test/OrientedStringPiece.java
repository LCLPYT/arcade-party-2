package work.lclpnet.ap2.game.maze_scape.gen.test;

import net.minecraft.util.math.BlockPos;
import work.lclpnet.ap2.game.maze_scape.gen.OrientedPiece;
import work.lclpnet.kibu.util.math.Matrix3i;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static work.lclpnet.ap2.game.maze_scape.gen.test.StringConnector.ARROWS;

public final class OrientedStringPiece implements OrientedPiece<StringConnector, StringPiece> {

    private static final char[] CORNERS = new char[] {'┌', '┐', '┘', '└'};
    private final StringPiece piece;
    private final int x, y;
    private final int rotation;
    private final int width, height;
    private final String string;
    private final List<StringConnector> connectors;

    public OrientedStringPiece(StringPiece piece, int x, int y, int rotation) {
        this(piece, x, y, rotation, -1);
    }

    public OrientedStringPiece(StringPiece piece, int x, int y, int rotation, int parentConnector) {
        this.piece = piece;
        this.x = x;
        this.y = y;
        this.rotation = rotation;

        var mat = Matrix3i.makeRotationZ(rotation);

        int baseWidth = piece.width(), baseHeight = piece.height();
        var dimensions = mat.transform(baseWidth, baseHeight, 0);

        this.width = Math.abs(dimensions.getX());
        this.height = Math.abs(dimensions.getY());

        String[] lines = piece.getString().split("\n");
        char[][] chars = new char[height][width];

        int ox = -Math.min(0, dimensions.getX() + 1);
        int oy = -Math.min(0, dimensions.getY() + 1);

        var pos = new BlockPos.Mutable();

        for (int yi = 0; yi < baseHeight; yi++) {
            for (int xi = 0; xi < baseWidth; xi++) {
                mat.transform(xi, yi, 0, pos);

                chars[oy + pos.getY()][ox + pos.getX()] = rotate(lines[yi].charAt(xi), rotation);
            }
        }

        this.string = Arrays.stream(chars)
                .map(String::new)
                .collect(Collectors.joining("\n"));

        // rotate and translate base connectors
        var base = piece().connectors();
        List<StringConnector> connectors = new ArrayList<>(parentConnector == -1 ? base.size() : base.size() - 1);

        for (int i = 0, baseSize = base.size(); i < baseSize; i++) {
            if (i == parentConnector) continue;

            StringConnector connector = base.get(i);

            mat.transform(connector.x(), connector.y(), 0, pos);

            int direction = (connector.direction() + rotation) % 4;

            var orientedConnector = new StringConnector(pos.getX() + ox + x, pos.getY() + oy + y, direction);
            connectors.add(orientedConnector);
        }

        this.connectors = connectors;
    }

    @Override
    public StringPiece piece() {
        return piece;
    }

    @Override
    public List<StringConnector> connectors() {
        return connectors;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int rotation() {
        return rotation;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public String string() {
        return string;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (OrientedStringPiece) obj;
        return Objects.equals(this.piece, that.piece) &&
               this.x == that.x &&
               this.y == that.y &&
               this.rotation == that.rotation;
    }

    @Override
    public int hashCode() {
        return Objects.hash(piece, x, y, rotation);
    }

    @Override
    public String toString() {
        return "OrientedStringPiece(x=%d, y=%d):%n%s".formatted(x, y, string);
    }

    private static char rotate(char c, int rotation) {
        return switch (c) {
            case '─' -> rotation % 2 == 0 ? c : '│';
            case '│' -> rotation % 2 == 0 ? c : '─';
            case '┌' -> CORNERS[rotation % 4];
            case '┐' -> CORNERS[(rotation + 1) % 4];
            case '┘' -> CORNERS[(rotation + 2) % 4];
            case '└' -> CORNERS[(rotation + 3) % 4];
            case '→' -> ARROWS[rotation % 4];
            case '↓' -> ARROWS[(rotation + 1) % 4];
            case '←' -> ARROWS[(rotation + 2) % 4];
            case '↑' -> ARROWS[(rotation + 3) % 4];
            default -> c;
        };
    }
}
