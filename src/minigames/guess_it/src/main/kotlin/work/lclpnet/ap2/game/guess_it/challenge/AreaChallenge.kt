package work.lclpnet.ap2.game.guess_it.challenge

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.DyeColor
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import work.lclpnet.ap2.api.util.world.AdjacentBlocks
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.guess_it.data.*
import work.lclpnet.ap2.game.guess_it.util.OptionMaker
import work.lclpnet.ap2.impl.util.TextUtil
import work.lclpnet.ap2.impl.util.world.PositionUtil.findGroundPositions
import work.lclpnet.ap2.impl.util.world.SimpleAdjacentBlocks
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape
import work.lclpnet.game.util.WorldModifier
import work.lclpnet.kibu.scheduler.Ticks
import java.util.*

class AreaChallenge(
    private val gameHandle: MiniGameHandle,
    private val world: ServerLevel,
    private val random: Random,
    private val blockShape: BlockShape,
    private val modifier: WorldModifier
) : Challenge {

    private var areas: Areas? = null

    override fun id() = "area"

    override val preparationKey = PREPARE_GUESS

    override val durationTicks = Ticks.seconds(16)

    override fun begin(input: InputInterface, messenger: ChallengeMessenger) {
        val translations = gameHandle.translations
        messenger.task(translations.translateText("area"))

        val opts = OptionMaker.createOptions(DyeColor.entries.toSet(), 4, random)

        val blockFunction: (DyeColor) -> Block = when (random.nextInt(3)) {
            0 -> { color -> Blocks.WOOL.pick(color) }
            1 -> { color -> Blocks.CONCRETE.pick(color) }
            2 -> { color -> Blocks.CONCRETE_POWDER.pick(color) }
            else -> throw IllegalStateException()
        }

        val blockStates = opts.map { blockFunction(it).defaultBlockState() }

        randomizeArea(opts, blockStates)

        val names: List<Component> = blockStates.map { TextUtil.getVanillaName(it) }
        input.expectSelection(*names.toTypedArray())
    }

    override fun evaluate(choices: PlayerChoices, result: ChallengeResult) {
        val areas = this.areas!!

        result.correctAnswer = Component.empty()
            .append(areas.biggestAreaText())
            .append(" ")
            .append(areas.distributionText())

        for (player in gameHandle.participants) {
            val optChoice = choices.getOption(player) ?: continue

            if (areas.isMaxCount(optChoice)) {
                result.grant(player, 3)
            }
        }
    }

    private fun randomizeArea(dyeColors: List<DyeColor>, blockStates: List<BlockState>) {
        val open = HashSet<BlockPos>()

        for (pos in findGroundPositions(blockShape, world)) {
            open.add(pos.immutable())
        }

        val startingPoints = OptionMaker.createOptions(open, 4, random)

        val areas = Areas(open, dyeColors, blockStates, startingPoints)

        while (areas.isBuilding()) {
            areas.stepBuild()
        }

        areas.evaluate()

        this.areas = areas
    }

    private inner class Areas(
        open: MutableSet<BlockPos>,
        private val dyeColors: List<DyeColor>,
        private val blockStates: List<BlockState>,
        startingPoints: List<BlockPos>
    ) {
        private val props: List<Propagation>
        private var maxCount = 0

        init {
            startingPoints.forEach { open.remove(it) }

            val adjacent: AdjacentBlocks = SimpleAdjacentBlocks({ open.contains(it) }, 0)

            props = buildList {
                for (i in 0 until 4) {
                    val start = startingPoints[i]
                    val state = blockStates[i]

                    val propagation = Propagation(
                        adjacent,
                        start,
                        { open.remove(it) },
                        { pos -> modifier.setBlockState(pos, state) }
                    )

                    add(propagation)
                }
            }
        }

        fun isBuilding(): Boolean = props.any { it.hasNext() }

        fun stepBuild() {
            for (prop in props) {
                prop.propagate()
            }
        }

        fun evaluate() {
            maxCount = props.maxOf { it.count }
        }

        fun isMaxCount(index: Int): Boolean {
            if (index < 0 || index >= props.size) {
                return false
            }

            return props[index].count == maxCount
        }

        fun biggestAreaText(): Component {
            return props.indices
                .filter { props[it].count == maxCount }
                .map { blockStates[it] }
                .fold(Component.empty()) { text, state ->
                    if (text.string.isNotEmpty()) {
                        text.append(", ")
                    }

                    text.append(TextUtil.getVanillaName(state))
                }
        }

        fun distributionText(): Component {
            val order = props.indices.sortedByDescending { props[it].count }

            val text = Component.literal("(")

            for ((j, i) in order.withIndex()) {
                if (j > 0) {
                    text.append(" / ")
                }

                val dye = dyeColors[i]
                val count = props[i].count

                text.append(
                    Component.literal("$count ")
                        .append(Component.translatable("color.minecraft." + dye.getName()))
                        .withStyle { style -> style.withColor(dye.textColor) }
                )
            }

            return text.append(")")
        }
    }

    private class Propagation(
        private val adjacent: AdjacentBlocks,
        start: BlockPos,
        private val reserve: (BlockPos) -> Unit,
        private val action: (BlockPos) -> Unit
    ) {
        private var queue = ArrayList<BlockPos>()
        private var next = ArrayList<BlockPos>()
        var count = 0
            private set

        init {
            queue.add(start)
        }

        fun propagate() {
            for (pos in queue) {
                action(pos)

                for (adjPos in adjacent.iterate(pos)) {
                    reserve(adjPos)
                    next.add(adjPos.immutable())
                }
            }

            count += queue.size
            queue.clear()

            // flip lists
            val swp = queue
            queue = next
            next = swp
        }

        fun hasNext(): Boolean = queue.isNotEmpty()
    }
}
