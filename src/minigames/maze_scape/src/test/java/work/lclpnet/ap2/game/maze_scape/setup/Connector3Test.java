package work.lclpnet.ap2.game.maze_scape.setup;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import work.lclpnet.kibu.util.math.Matrix3i;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class Connector3Test {

    @Test
    void rotateToFace() {
        for (int i = 0; i < 4; i++) {
            Direction face = Direction.from2DDataValue(i);

            for (int j = 0; j < 4; j++) {
                Direction other = Direction.from2DDataValue(j);

                int rotation = Connector3.rotateToFace(face, other);

                var mat = Matrix3i.makeRotationY(rotation);
                var vec = mat.transform(other.getUnitVec3i());

                Direction dir = Direction.getNearest(vec.getX(), vec.getY(), vec.getZ(), null);

                assertNotNull(dir);
                assertEquals(face, dir.getOpposite());
            }
        }
    }
}