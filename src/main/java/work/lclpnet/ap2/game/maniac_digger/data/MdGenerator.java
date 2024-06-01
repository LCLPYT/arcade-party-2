package work.lclpnet.ap2.game.maniac_digger.data;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.slf4j.Logger;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.BlockHelper;
import work.lclpnet.ap2.impl.util.StructureUtil;
import work.lclpnet.ap2.impl.util.math.AffineIntMatrix;
import work.lclpnet.ap2.impl.util.math.Vec2i;
import work.lclpnet.kibu.structure.BlockStructure;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class MdGenerator {

    private static final int MIN_TURN_DISTANCE = 3;
    private static final float TURN_CHANCE = 0.15f;
    private final ServerWorld world;
    private final GameMap map;
    private final Logger logger;
    private final Random random;

    public MdGenerator(ServerWorld world, GameMap map, Logger logger, Random random) {
        this.world = world;
        this.map = map;
        this.logger = logger;
        this.random = random;
    }

    public List<MdPipe> generate(final int pipeCount) {
        Vec3i dimensions = MapUtil.readBlockPos(map.requireProperty("area-dimensions"));
        JSONArray areas = map.requireProperty("areas");

        List<BlockPos> offsets = new ArrayList<>(areas.length());

        for (Object obj : areas) {
            if (!(obj instanceof JSONArray tuple)) {
                logger.warn("Unexpected array element in areas array (expected array): {}", obj != null ? obj.getClass().getSimpleName() : null);
                continue;
            }

            BlockPos offset = MapUtil.readBlockPos(tuple);
            offsets.add(offset);
        }

        if (pipeCount > offsets.size()) {
            throw new IllegalStateException("Map only supports up to %s pipes, %s requested".formatted(offsets.size(), pipeCount));
        }

        MdPipePlan plan = generatePipe(dimensions);
        BlockStructure structure = plan.structure();

        List<DyeColor> colors = new ArrayList<>(DyeColor.values().length);
        Collections.addAll(colors, DyeColor.values());

        List<MdPipe> pipes = new ArrayList<>(pipeCount);

        for (int i = 0; i < pipeCount; i++) {
            BlockPos offset = offsets.get(i);

            BlockState wallMaterial;

            if (colors.isEmpty()) {
                wallMaterial = Blocks.GLASS.getDefaultState();
            } else {
                DyeColor color = colors.remove(random.nextInt(colors.size()));
                wallMaterial = BlockHelper.getStainedGlass(color).getDefaultState();
            }

            StructureUtil.placeStructureFast(StructureUtil.replace(structure, MdPipePlan.WALL_MATERIAL, wallMaterial), world, offset);

            int x = offset.getX(), y = offset.getY(), z = offset.getZ();

            Vec3d spawn = plan.getSpawn().add(x, y, z);
            BlockBox bounds = plan.getBounds().transform(AffineIntMatrix.makeTranslation(x, y, z));

            pipes.add(new MdPipe(spawn, bounds));
        }

        return pipes;
    }

    private MdPipePlan generatePipe(Vec3i dimensions) {
        BlockBox box = new BlockBox(0, 0, 0,
                dimensions.getX() - 1, dimensions.getY() - 1, dimensions.getZ() - 1);

        List<Vec2i> chasms = genChasms(box);
        Vec2i chasm = chasms.get(random.nextInt(chasms.size()));

        final int maxY = box.max().getY();

        Vec3d spawn = new Vec3d(chasm.x() + 2, maxY - 2, chasm.z() + 2);
        MdPipePlan plan = new MdPipePlan(dimensions, 4, spawn, box, this::randomFillMaterial);

        var pos = new BlockPos.Mutable();
        var pos2 = new BlockPos.Mutable();

        int distance = 0;

        for (int y = maxY; y >= 0; y--) {
            pos.set(chasm.x(), y, chasm.z());

            if (distance >= MIN_TURN_DISTANCE && random.nextFloat() < TURN_CHANCE) {
                // make turn
                Vec2i adj = selectAdjacentChasm(chasms, chasm);

                if (adj != null) {
                    pos2.set(adj.x(), y, adj.z());
                    plan.placeHorizontal(pos, pos2);

                    chasm = adj;
                    distance = -1;
                    pos.set(pos2);
                }
            }

            plan.placeVertical(pos, maxY - y);
            distance++;
        }

        return plan;
    }

    @Nullable
    private Vec2i selectAdjacentChasm(List<Vec2i> chasms, Vec2i chasm) {
        List<Vec2i> adj = new ArrayList<>(2);

        int x = chasm.x(), z = chasm.z();

        for (Vec2i vec : chasms) {
            int vx = vec.x(), vz = vec.z();

            if ((vx == x && vz != z) || (vx != x && vz == z)) {
                adj.add(vec);
            }
        }

        if (adj.isEmpty()) {
            return null;
        }

        return adj.get(random.nextInt(adj.size()));
    }

    private List<Vec2i> genChasms(BlockBox box) {
        List<Vec2i> chasms = new ArrayList<>(4);

        if (box.width() < 4 || box.length() < 4) {
            throw new IllegalStateException("Area dimensions must at least be 4x4 (is %sx%s)".formatted(box.width(), box.length()));
        }

        BlockPos min = box.min();
        BlockPos max = box.max();

        chasms.add(new Vec2i(min.getX(), min.getZ()));

        boolean xSpace = box.width() > 4;
        boolean zSpace = box.length() > 4;

        if (xSpace) {
            chasms.add(new Vec2i(max.getX() - 3, min.getZ()));
        }

        if (zSpace) {
            chasms.add(new Vec2i(min.getX(), max.getZ() - 3));
        }

        if (xSpace && zSpace) {
            chasms.add(new Vec2i(max.getX() - 3, max.getZ() - 3));
        }

        return chasms;
    }

    private BlockState randomFillMaterial(BlockPos pos) {
        return Blocks.DIRT.getDefaultState();
    }
}
