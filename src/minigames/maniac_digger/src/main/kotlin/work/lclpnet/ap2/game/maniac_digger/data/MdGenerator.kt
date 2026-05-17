package work.lclpnet.ap2.game.maniac_digger.data

import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.DyeColor
import net.minecraft.world.level.block.Blocks.*
import net.minecraft.world.level.block.FallingBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import org.json.JSONArray
import org.slf4j.Logger
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.impl.util.BlockHelper
import work.lclpnet.ap2.impl.util.structure.StructureUtil
import work.lclpnet.gaco.ds.BlockBox
import work.lclpnet.gaco.ds.WeightedList
import work.lclpnet.gaco.math.AffineIntMatrix
import work.lclpnet.gaco.math.Vec2i
import work.lclpnet.game.map.GameMap
import java.util.*

private const val MIN_TURN_DISTANCE = 3
private const val TURN_CHANCE = 0.15f

class MdGenerator(
    private val world: ServerLevel,
    private val map: GameMap,
    private val logger: Logger,
    private val random: Random
) {
    private val fillMaterial: WeightedList<BlockState> = genFillMaterial()
    private val nonFallingMaterial: WeightedList<BlockState> = fillMaterial.filter { state ->
        state.block !is FallingBlock
    }

    fun generate(pipeCount: Int): List<MdPipe> {
        val dimensions: Vec3i = MapUtil.readBlockPos(map.requireProperty("area-dimensions"))
        val areas: JSONArray = map.requireProperty("areas")

        val offsets = ArrayList<BlockPos>(areas.length())

        for (obj in areas) {
            if (obj !is JSONArray) {
                logger.warn("Unexpected array element in areas array (expected array): {}", obj?.javaClass?.simpleName)
                continue
            }

            offsets.add(MapUtil.readBlockPos(obj))
        }

        if (pipeCount > offsets.size) {
            throw IllegalStateException("Map only supports up to %s pipes, %s requested".format(offsets.size, pipeCount))
        }

        val plan = generatePipe(dimensions)
        val structure = plan.structure()

        val colors = ArrayList<DyeColor>(DyeColor.entries.size)
        colors.addAll(DyeColor.entries.toTypedArray())

        val pipes = ArrayList<MdPipe>(pipeCount)

        for (i in 0 until pipeCount) {
            val offset = offsets[i]

            val wallMaterial: BlockState = if (colors.isEmpty()) {
                GLASS.defaultBlockState()
            } else {
                val color = colors.removeAt(random.nextInt(colors.size))
                BlockHelper.getStainedGlass(color).defaultBlockState()
            }

            StructureUtil.placeStructureFast(StructureUtil.replace(structure, MdPipePlan.WALL_MATERIAL, wallMaterial), world, offset)

            val x = offset.x
            val y = offset.y
            val z = offset.z

            val spawn = plan.spawn.add(x.toDouble(), y.toDouble(), z.toDouble())
            val bounds = plan.bounds.transform(AffineIntMatrix.makeTranslation(x, y, z))

            pipes.add(MdPipe(spawn, bounds))
        }

        return pipes
    }

    private fun generatePipe(dimensions: Vec3i): MdPipePlan {
        val box = BlockBox(0, 0, 0, dimensions.x - 1, dimensions.y - 1, dimensions.z - 1)

        val chasms = genChasms(box)
        var chasm = chasms[random.nextInt(chasms.size)]
        val initialChasm = chasm

        val maxY = box.max().y

        val spawn = Vec3(chasm.x() + 2.0, maxY - 2.0, chasm.z() + 2.0)
        val plan = MdPipePlan(dimensions, 4, spawn, box, this::randomFillMaterial)

        val pos = BlockPos.MutableBlockPos()
        val pos2 = BlockPos.MutableBlockPos()

        var distance = 0

        for (y in maxY downTo 0) {
            pos.set(chasm.x(), y, chasm.z())

            if (distance >= MIN_TURN_DISTANCE && random.nextFloat() < TURN_CHANCE) {
                val adj = selectAdjacentChasm(chasms, chasm)

                if (adj != null) {
                    pos2.set(adj.x(), y, adj.z())
                    plan.placeHorizontal(pos, pos2)

                    chasm = adj
                    distance = -1
                    pos.set(pos2)
                }
            }

            plan.placeVertical(pos, maxY - y)
            distance++
        }

        clearSpawn(plan, initialChasm, maxY)

        return plan
    }

    private fun clearSpawn(plan: MdPipePlan, initialChasm: Vec2i, maxY: Int) {
        val pos = BlockPos.MutableBlockPos()
        val x = initialChasm.x()
        val z = initialChasm.z()
        val air = AIR.defaultBlockState()

        pos.set(x + 1, maxY - 1, z + 1)
        plan.setBlockState(pos, air)

        pos.set(x + 2, maxY - 1, z + 1)
        plan.setBlockState(pos, air)

        pos.set(x + 1, maxY - 1, z + 2)
        plan.setBlockState(pos, air)

        pos.set(x + 2, maxY - 1, z + 2)
        plan.setBlockState(pos, air)

        pos.set(x + 1, maxY - 2, z + 1)
        plan.setBlockState(pos, air)

        pos.set(x + 2, maxY - 2, z + 1)
        plan.setBlockState(pos, air)

        pos.set(x + 1, maxY - 2, z + 2)
        plan.setBlockState(pos, air)

        pos.set(x + 2, maxY - 2, z + 2)
        plan.setBlockState(pos, air)
    }

    private fun selectAdjacentChasm(chasms: List<Vec2i>, chasm: Vec2i): Vec2i? {
        val adj = ArrayList<Vec2i>(2)

        val x = chasm.x()
        val z = chasm.z()

        for (vec in chasms) {
            val vx = vec.x()
            val vz = vec.z()

            if ((vx == x && vz != z) || (vx != x && vz == z)) {
                adj.add(vec)
            }
        }

        if (adj.isEmpty()) return null

        return adj[random.nextInt(adj.size)]
    }

    private fun genChasms(box: BlockBox): List<Vec2i> {
        val chasms = ArrayList<Vec2i>(4)

        if (box.width() < 4 || box.length() < 4) {
            throw IllegalStateException("Area dimensions must at least be 4x4 (is %sx%s)".format(box.width(), box.length()))
        }

        val min = box.min()
        val max = box.max()

        chasms.add(Vec2i(min.x, min.z))

        val xSpace = box.width() > 4
        val zSpace = box.length() > 4

        if (xSpace) {
            chasms.add(Vec2i(max.x - 3, min.z))
        }

        if (zSpace) {
            chasms.add(Vec2i(min.x, max.z - 3))
        }

        if (xSpace && zSpace) {
            chasms.add(Vec2i(max.x - 3, max.z - 3))
        }

        return chasms
    }

    private fun randomFillMaterial(pos: BlockPos): BlockState {
        val pool = if (pos.y == 0) nonFallingMaterial else fillMaterial

        return pool.getRandomElement(random) ?: DIRT.defaultBlockState()
    }

    private fun genFillMaterial() = WeightedList<BlockState>().apply {
        val common = 0.085f
        val uncommon = 0.035f
        val rare = 0.01f

        add(DIRT.defaultBlockState(), common)
        add(COBBLESTONE.defaultBlockState(), common)
        add(ROOTED_DIRT.defaultBlockState(), common)
        add(TUFF.defaultBlockState(), common)
        add(ACACIA_WOOD.defaultBlockState(), common)
        add(SPRUCE_PLANKS.defaultBlockState(), common)
        add(ANDESITE.defaultBlockState(), common)
        add(DIORITE.defaultBlockState(), common)
        add(MUD.defaultBlockState(), common)
        add(MUD_BRICKS.defaultBlockState(), common)
        add(MYCELIUM.defaultBlockState(), common)
        add(CRIMSON_STEM.defaultBlockState(), common)
        add(BAMBOO_BLOCK.defaultBlockState(), common)
        add(SMOOTH_STONE.defaultBlockState(), common)
        add(DEEPSLATE.defaultBlockState(), common)
        add(SAND.defaultBlockState(), common)
        add(RED_SAND.defaultBlockState(), common)
        add(PRISMARINE.defaultBlockState(), common)
        add(SMOOTH_BASALT.defaultBlockState(), common)
        add(MAGENTA_CONCRETE_POWDER.defaultBlockState(), common)
        add(WHITE_CONCRETE_POWDER.defaultBlockState(), common)
        add(LIME_CONCRETE_POWDER.defaultBlockState(), common)
        add(COARSE_DIRT.defaultBlockState(), common)
        add(CLAY.defaultBlockState(), common)
        add(GRAVEL.defaultBlockState(), common)
        add(SNOW_BLOCK.defaultBlockState(), common)
        add(CALCITE.defaultBlockState(), common)
        add(MOSS_BLOCK.defaultBlockState(), common)
        add(SOUL_SAND.defaultBlockState(), common)
        add(BOOKSHELF.defaultBlockState(), common)
        add(SHROOMLIGHT.defaultBlockState(), common)
        add(WARPED_WART_BLOCK.defaultBlockState(), common)
        add(NETHER_WART_BLOCK.defaultBlockState(), common)
        add(RED_MUSHROOM_BLOCK.defaultBlockState(), common)
        add(BROWN_MUSHROOM_BLOCK.defaultBlockState(), common)
        add(DRIPSTONE_BLOCK.defaultBlockState(), common)
        add(BLUE_ICE.defaultBlockState(), common)
        add(END_STONE.defaultBlockState(), common)

        add(SPONGE.defaultBlockState(), uncommon)
        add(TUFF_BRICKS.defaultBlockState(), uncommon)
        add(STONE_BRICKS.defaultBlockState(), uncommon)
        add(BAMBOO_MOSAIC.defaultBlockState(), uncommon)
        add(CHISELED_RED_SANDSTONE.defaultBlockState(), uncommon)
        add(PRISMARINE_BRICKS.defaultBlockState(), uncommon)
        add(WAXED_COPPER_GRATE.defaultBlockState(), uncommon)
        add(WEATHERED_CUT_COPPER.defaultBlockState(), uncommon)
        add(PURPUR_BLOCK.defaultBlockState(), uncommon)
        add(CHISELED_BOOKSHELF.defaultBlockState(), uncommon)
        add(SLIME_BLOCK.defaultBlockState(), uncommon)
        add(HONEY_BLOCK.defaultBlockState(), uncommon)
        add(MAGMA_BLOCK.defaultBlockState(), uncommon)
        add(OCHRE_FROGLIGHT.defaultBlockState(), uncommon)
        add(LODESTONE.defaultBlockState(), uncommon)

        add(OBSIDIAN.defaultBlockState(), rare)
        add(CRYING_OBSIDIAN.defaultBlockState(), rare)
        add(GLOWSTONE.defaultBlockState(), rare)
        add(ANCIENT_DEBRIS.defaultBlockState(), rare)
    }
}
