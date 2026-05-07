package work.lclpnet.ap2.impl.util.world;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CircleStructureGeneratorTest {

    @ParameterizedTest
    @CsvSource({"1,10,0", "2,2,1", "2,10.27,6", "3,18,11", "12,90,174"})
    void calculateRadius(int segments, double spacing, int expected) {
        double radius = CircleStructureGenerator.calculateRadius(segments, spacing);
        assertEquals(expected, radius);
    }
}