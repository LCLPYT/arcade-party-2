package work.lclpnet.ap2.game.aim_master;

import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import work.lclpnet.ap2.impl.util.BlockBox;

import java.util.ArrayList;
import java.util.Random;

import static java.lang.Math.*;

public class PositionGenerator {

    private final int radius;
    private final int offset;
    private final int fov;
    private final int targetNumber;
    private final BlockPos center;
    private final ServerWorld world;

    public PositionGenerator(int radius, int offset, BlockPos center, ServerWorld world, int fov, int targetNumber) {
        this.radius = radius;
        this.offset = offset;
        this.center = center;
        this.world = world;
        this.fov = fov;
        this.targetNumber = targetNumber;

        ArrayList<BlockPos> blockPositions = pickPositions();
        for (BlockPos blockPos : blockPositions) world.setBlockState(blockPos, Blocks.TARGET.getDefaultState());
    }

    private BlockBox generateBlockBox() {
        BlockPos pos1 = new BlockPos(center.getX() + radius, center.getY() + radius, (center.getZ()+offset) + radius);
        BlockPos pos2 = new BlockPos(center.getX() - radius, center.getY() - radius, (center.getZ()+offset) - radius);
        return new BlockBox(pos1, pos2);
    }

    private ArrayList<BlockPos> generateCone() {
        ArrayList<BlockPos> validBlockPositions = new ArrayList<>();

        for (BlockPos pos : generateBlockBox()) {

            int x = pos.getX() - center.getX();
            int y = pos.getY() - center.getY();
            int z = pos.getZ() - (center.getZ()+offset);

            int squaredDistance = x * x + y * y + z * z;
            int radiusSquared = radius * radius;

            int e = 13;

            if ((abs(squaredDistance - radiusSquared) <= e)) {
                double a = radius;
                double b = radius * 0.42;

                double distanceToEllipse = (x * x) / (a * a) + (y * y) / (b * b);

                if (distanceToEllipse <= 1) {
                    double angle = getAngle(x, y, z);

                    if (angle <= fov) {
                        world.setBlockState(pos, Blocks.GLASS.getDefaultState());
                        validBlockPositions.add(pos.toImmutable());
                    }
                }
            }
        }
        return validBlockPositions;
    }

    private ArrayList<BlockPos> pickPositions() {
        ArrayList<BlockPos> cone = generateCone();
        ArrayList<BlockPos> BlockPositions = new ArrayList<>();
        final Random random = new Random();

        while (BlockPositions.size() < targetNumber && !cone.isEmpty()) {

            int randIndex = random.nextInt(cone.size());
            BlockPos selectedPos = cone.remove(randIndex);
            BlockPositions.add(selectedPos);

            for (BlockPos pos : new ArrayList<>(cone)) {

                double euclideanDistance = sqrt(pow(pos.getX() - selectedPos.getX(), 2) + pow(pos.getY() - selectedPos.getY(), 2) + pow(pos.getZ() - selectedPos.getZ(), 2));
                if (euclideanDistance <= 1.8) {cone.remove(pos);}
            }
        }
        return BlockPositions;
    }

    private static double getAngle(int x, int y, int z) {
        double[] vec = {x, y, z};
        double[] viewDir = {0, 0.5, 1};

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
        return sqrt(vec[0]*vec[0] + vec[1]*vec[1] + vec[2]*vec[2]);
    }
}
