package work.lclpnet.ap2.impl.util.world;

import net.minecraft.block.Blocks;
import net.minecraft.text.Text;
import net.minecraft.world.border.WorldBorder;
import work.lclpnet.ap2.impl.game.GameCommons;
import work.lclpnet.ap2.impl.util.debug.DebugController;
import work.lclpnet.ap2.impl.util.math.MathUtil;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.map.MapUtils;

import java.util.Random;

import static java.lang.Math.abs;
import static java.lang.Math.max;

public class WorldBorderRandomizer {

    private static final boolean DEBUG_RANGES = false;

    private final GameMap map;
    private final DebugController debugController;

    public WorldBorderRandomizer(GameMap map, DebugController debugController) {
        this.map = map;
        this.debugController = debugController;
    }

    public void randomizeCenter(WorldBorder worldBorder, GameCommons.WorldBorderConfig config, Random random) {
        double newRadius = config.minSize() * 0.5;
        double oldRadius = worldBorder.getSize() * 0.5;

        if (newRadius <= 0) throw new IllegalArgumentException("Target size must be positive");
        if (newRadius >= oldRadius) return;

        double range = oldRadius - newRadius;

        // relX, relZ = -range..+range
        double relX = random.nextDouble() * 2 * range - range;
        double relZ = random.nextDouble() * 2 * range - range;

        double oldX = worldBorder.getCenterX();
        double oldZ = worldBorder.getCenterZ();

        double newX = config.align(oldX + relX);
        double newZ = config.align(oldZ + relZ);

        worldBorder.setCenter(newX, newZ);

        // all of previously inside area should still be inside
        double dx = newX - oldX;
        double dz = newZ - oldZ;

        double maxAbsShift = max(abs(dx), abs(dz));

        worldBorder.setSize(2 * (oldRadius + maxAbsShift));

        if (!DEBUG_RANGES) return;

        double y = MapUtils.getSpawnPosition(map).getY();

        debugController.renderer().ifPresent(renderer -> {
            renderer.quadStroke(oldX - range, oldZ - range,
                    oldX + range, oldZ + range,
                    y, 0.1, Blocks.BLUE_CONCRETE.getDefaultState());

            renderer.quadStroke(oldX - range - newRadius, oldZ - range - newRadius,
                    oldX + range + newRadius, oldZ + range + newRadius,
                    y, 0.1, Blocks.GREEN_CONCRETE.getDefaultState());

            renderer.labeledCross(oldX, y, oldZ, Blocks.GRAY_CONCRETE.getDefaultState(), Text.literal("original center"));
            renderer.labeledCross(newX, y, newZ, Blocks.RED_CONCRETE.getDefaultState(), Text.literal("random center"));

            renderer.arrow(oldX, y, oldZ, MathUtil.angleY(dx, dz), 0.5, Blocks.YELLOW_CONCRETE.getDefaultState());
            renderer.text(oldX + dx * 0.5, y + 0.2, oldZ + dz * 0.5, Text.literal("(%.2f, %.2f)".formatted(dx, dz)));
        });
    }
}
