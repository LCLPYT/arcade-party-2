package work.lclpnet.ap2.game.maze_scape.setup;

import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;
import work.lclpnet.kibu.util.math.Matrix3i;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class Connector3Test {

    @Test
    void rotateToFace() {
        for (int i = 0; i < 4; i++) {
            Direction face = Direction.fromHorizontal(i);

            for (int j = 0; j < 4; j++) {
                Direction other = Direction.fromHorizontal(j);

                int rotation = Connector3.rotateToFace(face, other);

                var mat = Matrix3i.makeRotationY(rotation);
                var vec = mat.transform(other.getVector());

                Direction dir = Direction.fromVector(vec.getX(), vec.getY(), vec.getZ());

                assertNotNull(dir);
                assertEquals(face, dir.getOpposite());
            }
        }
    }
}