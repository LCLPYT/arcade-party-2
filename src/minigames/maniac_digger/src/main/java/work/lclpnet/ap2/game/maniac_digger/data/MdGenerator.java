package work.lclpnet.ap2.game.maniac_digger.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.slf4j.Logger;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.BlockHelper;
import work.lclpnet.ap2.impl.util.structure.StructureUtil;
import work.lclpnet.gaco.ds.BlockBox;
import work.lclpnet.gaco.ds.WeightedList;
import work.lclpnet.gaco.math.AffineIntMatrix;
import work.lclpnet.gaco.math.Vec2i;
import work.lclpnet.game.map.GameMap;
import work.lclpnet.kibu.structure.BlockStructure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class MdGenerator {

    private static final int MIN_TURN_DISTANCE = 3;
    private static final float TURN_CHANCE = 0.15f;
    private final ServerLevel world;
    private final GameMap map;
    private final Logger logger;
    private final Random random;
    private final WeightedList<BlockState> fillMaterial;
    private final WeightedList<BlockState> nonFallingMaterial;

    public MdGenerator(ServerLevel world, GameMap map, Logger logger, Random random) {
        this.world = world;
        this.map = map;
        this.logger = logger;
        this.random = random;
        this.fillMaterial = genFillMaterial();
        this.nonFallingMaterial = fillMaterial.filter(state -> !(state.getBlock() instanceof FallingBlock));
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
                wallMaterial = Blocks.GLASS.defaultBlockState();
            } else {
                DyeColor color = colors.remove(random.nextInt(colors.size()));
                wallMaterial = BlockHelper.getStainedGlass(color).defaultBlockState();
            }

            StructureUtil.placeStructureFast(StructureUtil.replace(structure, MdPipePlan.WALL_MATERIAL, wallMaterial), world, offset);

            int x = offset.getX(), y = offset.getY(), z = offset.getZ();

            Vec3 spawn = plan.getSpawn().add(x, y, z);
            BlockBox bounds = plan.getBounds().transform(AffineIntMatrix.makeTranslation(x, y, z));

            pipes.add(new MdPipe(spawn, bounds));
        }

        return pipes;
    }

    private MdPipePlan generatePipe(Vec3i dimensions) {
        BlockBox box = new BlockBox(0, 0, 0,
                dimensions.getX() - 1, dimensions.getY() - 1, dimensions.getZ() - 1);

        List<Vec2i> chasms = genChasms(box);
        Vec2i initialChasm = chasms.get(random.nextInt(chasms.size()));
        Vec2i chasm = initialChasm;

        final int maxY = box.max().getY();

        Vec3 spawn = new Vec3(chasm.x() + 2, maxY - 2, chasm.z() + 2);
        MdPipePlan plan = new MdPipePlan(dimensions, 4, spawn, box, this::randomFillMaterial);

        var pos = new BlockPos.MutableBlockPos();
        var pos2 = new BlockPos.MutableBlockPos();

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

        clearSpawn(plan, initialChasm, maxY);

        return plan;
    }

    private void clearSpawn(MdPipePlan plan, Vec2i initialChasm, int maxY) {
        var pos = new BlockPos.MutableBlockPos();
        int x = initialChasm.x(), z = initialChasm.z();
        BlockState air = Blocks.AIR.defaultBlockState();

        pos.set(x + 1, maxY - 1, z + 1);
        plan.setBlockState(pos, air);

        pos.set(x + 2, maxY - 1, z + 1);
        plan.setBlockState(pos, air);

        pos.set(x + 1, maxY - 1, z + 2);
        plan.setBlockState(pos, air);

        pos.set(x + 2, maxY - 1, z + 2);
        plan.setBlockState(pos, air);

        pos.set(x + 1, maxY - 2, z + 1);
        plan.setBlockState(pos, air);

        pos.set(x + 2, maxY - 2, z + 1);
        plan.setBlockState(pos, air);

        pos.set(x + 1, maxY - 2, z + 2);
        plan.setBlockState(pos, air);

        pos.set(x + 2, maxY - 2, z + 2);
        plan.setBlockState(pos, air);
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
        WeightedList<BlockState> pool;

        if (pos.getY() == 0) {
            pool = nonFallingMaterial;
        } else {
            pool = fillMaterial;
        }


        BlockState material = pool.getRandomElement(random);

        if (material != null) {
            return material;
        }

        return Blocks.DIRT.defaultBlockState();
    }

    private WeightedList<BlockState> genFillMaterial() {
        WeightedList<BlockState> material = new WeightedList<>();

        float common = 0.085f;
        float uncommon = 0.035f;
        float rare = 0.01f;

        material.add(Blocks.DIRT.defaultBlockState(), common);
        material.add(Blocks.COBBLESTONE.defaultBlockState(), common);
        material.add(Blocks.ROOTED_DIRT.defaultBlockState(), common);
        material.add(Blocks.TUFF.defaultBlockState(), common);
        material.add(Blocks.ACACIA_WOOD.defaultBlockState(), common);
        material.add(Blocks.SPRUCE_PLANKS.defaultBlockState(), common);
        material.add(Blocks.ANDESITE.defaultBlockState(), common);
        material.add(Blocks.DIORITE.defaultBlockState(), common);
        material.add(Blocks.MUD.defaultBlockState(), common);
        material.add(Blocks.MUD_BRICKS.defaultBlockState(), common);
        material.add(Blocks.MYCELIUM.defaultBlockState(), common);
        material.add(Blocks.CRIMSON_STEM.defaultBlockState(), common);
        material.add(Blocks.BAMBOO_BLOCK.defaultBlockState(), common);
        material.add(Blocks.SMOOTH_STONE.defaultBlockState(), common);
        material.add(Blocks.DEEPSLATE.defaultBlockState(), common);
        material.add(Blocks.SAND.defaultBlockState(), common);
        material.add(Blocks.RED_SAND.defaultBlockState(), common);
        material.add(Blocks.PRISMARINE.defaultBlockState(), common);
        material.add(Blocks.SMOOTH_BASALT.defaultBlockState(), common);
        material.add(Blocks.MAGENTA_CONCRETE_POWDER.defaultBlockState(), common);
        material.add(Blocks.WHITE_CONCRETE_POWDER.defaultBlockState(), common);
        material.add(Blocks.LIME_CONCRETE_POWDER.defaultBlockState(), common);
        material.add(Blocks.COARSE_DIRT.defaultBlockState(), common);
        material.add(Blocks.CLAY.defaultBlockState(), common);
        material.add(Blocks.GRAVEL.defaultBlockState(), common);
        material.add(Blocks.SNOW_BLOCK.defaultBlockState(), common);
        material.add(Blocks.CALCITE.defaultBlockState(), common);
        material.add(Blocks.MOSS_BLOCK.defaultBlockState(), common);
        material.add(Blocks.SOUL_SAND.defaultBlockState(), common);
        material.add(Blocks.BOOKSHELF.defaultBlockState(), common);
        material.add(Blocks.SHROOMLIGHT.defaultBlockState(), common);
        material.add(Blocks.WARPED_WART_BLOCK.defaultBlockState(), common);
        material.add(Blocks.NETHER_WART_BLOCK.defaultBlockState(), common);
        material.add(Blocks.RED_MUSHROOM_BLOCK.defaultBlockState(), common);
        material.add(Blocks.BROWN_MUSHROOM_BLOCK.defaultBlockState(), common);
        material.add(Blocks.DRIPSTONE_BLOCK.defaultBlockState(), common);
        material.add(Blocks.BLUE_ICE.defaultBlockState(), common);
        material.add(Blocks.END_STONE.defaultBlockState(), common);

        material.add(Blocks.SPONGE.defaultBlockState(), uncommon);
        material.add(Blocks.TUFF_BRICKS.defaultBlockState(), uncommon);
        material.add(Blocks.STONE_BRICKS.defaultBlockState(), uncommon);
        material.add(Blocks.BAMBOO_MOSAIC.defaultBlockState(), uncommon);
        material.add(Blocks.CHISELED_RED_SANDSTONE.defaultBlockState(), uncommon);
        material.add(Blocks.PRISMARINE_BRICKS.defaultBlockState(), uncommon);
        material.add(Blocks.WAXED_COPPER_GRATE.defaultBlockState(), uncommon);
        material.add(Blocks.WEATHERED_CUT_COPPER.defaultBlockState(), uncommon);
        material.add(Blocks.PURPUR_BLOCK.defaultBlockState(), uncommon);
        material.add(Blocks.CHISELED_BOOKSHELF.defaultBlockState(), uncommon);
        material.add(Blocks.SLIME_BLOCK.defaultBlockState(), uncommon);
        material.add(Blocks.HONEY_BLOCK.defaultBlockState(), uncommon);
        material.add(Blocks.MAGMA_BLOCK.defaultBlockState(), uncommon);
        material.add(Blocks.OCHRE_FROGLIGHT.defaultBlockState(), uncommon);
        material.add(Blocks.LODESTONE.defaultBlockState(), uncommon);

        material.add(Blocks.OBSIDIAN.defaultBlockState(), rare);
        material.add(Blocks.CRYING_OBSIDIAN.defaultBlockState(), rare);
        material.add(Blocks.GLOWSTONE.defaultBlockState(), rare);
        material.add(Blocks.ANCIENT_DEBRIS.defaultBlockState(), rare);

        return material;
    }
}
