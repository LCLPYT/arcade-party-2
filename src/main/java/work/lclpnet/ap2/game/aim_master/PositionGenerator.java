package work.lclpnet.ap2.game.aim_master;

import net.minecraft.util.math.BlockPos;
import work.lclpnet.ap2.impl.util.BlockBox;

import java.util.ArrayList;
import java.util.Random;

import static java.lang.Math.*;

public class PositionGenerator {

    private final int radius;
    private final int offset;
    private final double upwardTilt;
    private final double ellipseFactor;
    private final int fov;
    private final int targetNumber;
    private final int targetMinDistance;
    private final BlockPos center;

    public PositionGenerator(int radius, int offset, double upwardTilt, double ellipseFactor, BlockPos center, int fov, int targetNumber, int targetMinDistance) {
        this.radius = radius;
        this.offset = offset;
        this.upwardTilt = upwardTilt;
        this.ellipseFactor = ellipseFactor;
        this.center = center;
        this.fov = fov;
        this.targetNumber = targetNumber;
        this.targetMinDistance = targetMinDistance;
    }

    private BlockBox generateBlockBox() {
        BlockPos pos1 = new BlockPos(center.getX() + radius, center.getY() + radius, (center.getZ() + offset) + radius);
        BlockPos pos2 = new BlockPos(center.getX() - radius, center.getY() - radius, (center.getZ() + offset) - radius);
        return new BlockBox(pos1, pos2);
    }

    private ArrayList<BlockPos> generateCone() {
        ArrayList<BlockPos> validBlockPositions = new ArrayList<>();

        for (BlockPos pos : generateBlockBox()) {

            int x = pos.getX() - center.getX();
            int y = pos.getY() - center.getY();
            int z = pos.getZ() - (center.getZ() + offset);

            int squaredDistance = x * x + y * y + z * z;
            int radiusSquared = radius * radius;

            double e = radius * (radius * 0.004 + 0.84);

            if ((abs(squaredDistance - radiusSquared) <= e)) {
                double a = radius;
                double b = radius * ellipseFactor;

                double distanceToEllipse = (x * x) / (a * a) + (y * y) / (b * b);

                if (distanceToEllipse <= 1) {
                    double angle = getAngle(x, y, z);

                    if (angle <= fov) {
                        validBlockPositions.add(pos.toImmutable());
                    }
                }
            }
        }
        return validBlockPositions;
    }

    public ArrayList<BlockPos> pickPositions() {
        ArrayList<BlockPos> cone = generateCone();
        ArrayList<BlockPos> BlockPositions = new ArrayList<>();
        final Random random = new Random();

        while (BlockPositions.size() < targetNumber && !cone.isEmpty()) {

            int randIndex = random.nextInt(cone.size());
            BlockPos selectedPos = cone.remove(randIndex);
            BlockPositions.add(selectedPos);

            for (BlockPos pos : new ArrayList<>(cone)) {

                double euclideanDistance = sqrt(pow(pos.getX() - selectedPos.getX(), 2) + pow(pos.getY() - selectedPos.getY(), 2) + pow(pos.getZ() - selectedPos.getZ(), 2));
                if (euclideanDistance <= targetMinDistance) {
                    cone.remove(pos);
                }
            }
        }
        return BlockPositions;
    }

    private double getAngle(int x, int y, int z) {
        double[] vec = {x, y, z};
        double[] viewDir = {0, 0 + upwardTilt, 1};

        return toDegrees(acos(calculateDotProduct(vec, viewDir) / (vectorLen(vec) * vectorLen(viewDir))));
    }

    private static double calculateDotProduct(double[] vec1, double[] vec2) {
        double dotProduct = 0.0;
        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
        }
        return dotProduct;
    }

    private static double vectorLen(double[] vec) {
        return sqrt(vec[0] * vec[0] + vec[1] * vec[1] + vec[2] * vec[2]);
    }
}
