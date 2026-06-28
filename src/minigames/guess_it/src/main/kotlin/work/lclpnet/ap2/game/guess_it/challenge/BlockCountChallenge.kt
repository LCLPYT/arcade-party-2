package work.lclpnet.ap2.game.guess_it.challenge

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.guess_it.data.*
import work.lclpnet.ap2.game.guess_it.util.BlockCountShapeManager
import work.lclpnet.ap2.impl.util.debug.DebugController
import work.lclpnet.ap2.impl.util.math.shape.Shape
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape
import work.lclpnet.game.util.WorldModifier
import work.lclpnet.kibu.scheduler.Ticks
import work.lclpnet.kibu.scheduler.api.RunningTask
import work.lclpnet.kibu.scheduler.api.SchedulerAction
import work.lclpnet.kibu.scheduler.api.TaskHandle
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.stream.Collectors
import kotlin.math.floor

private const val DEBUG_SHAPES = false

class BlockCountChallenge<S>(
    private val gameHandle: MiniGameHandle,
    private val random: Random,
    private val stage: S,
    private val modifier: WorldModifier,
    private val debugController: DebugController
) : Challenge, SchedulerAction
    where S : BlockShape,
          S : BlockShape.WithRadius,
          S : BlockShape.WithHeight {

    private val shapeManager = BlockCountShapeManager(random, stage)
    private var amount = 0
    private var distance = 0
    private var blocksByDistance: Map<Int, List<BlockPos>>? = null
    private var maxDistance = -1
    private var state: BlockState? = null
    private var shape: Shape? = null
    private var task: TaskHandle? = null

    override fun id() = "block_count"

    override val preparationKey = PREPARE_ESTIMATE

    override val durationTicks = Ticks.seconds(20)

    override fun init(init: Any?) {
        if (init is String) {
            shape = shapeManager.getShape(init)
        }
    }

    override fun prepare() {
        val shape = this.shape ?: shapeManager.randomShape().also { this.shape = it }

        val blocks = ArrayList<BlockPos>()

        for (pos in shape.bounds()) {
            if (shape.contains(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)) {
                blocks.add(pos.immutable())
            }
        }

        amount = blocks.size

        val byDistance = blocks.stream()
            .parallel()
            .filter { stage.contains(it) }
            .collect(Collectors.groupingByConcurrent { distance(it) })

        blocksByDistance = byDistance

        maxDistance = byDistance.keys.maxOrNull() ?: -1

        state = when (random.nextInt(12)) {
            0 -> Blocks.DIAMOND_BLOCK.defaultBlockState()
            1 -> Blocks.GOLD_BLOCK.defaultBlockState()
            2 -> Blocks.EMERALD_BLOCK.defaultBlockState()
            3 -> Blocks.IRON_BLOCK.defaultBlockState()
            4 -> Blocks.REDSTONE_BLOCK.defaultBlockState()
            5 -> Blocks.LAPIS_BLOCK.defaultBlockState()
            6 -> Blocks.COAL_BLOCK.defaultBlockState()
            7 -> Blocks.AMETHYST_BLOCK.defaultBlockState()
            8 -> Blocks.NETHERITE_BLOCK.defaultBlockState()
            9 -> Blocks.SMOOTH_QUARTZ.defaultBlockState()
            10 -> Blocks.CRYING_OBSIDIAN.defaultBlockState()
            11 -> Blocks.RESIN_BLOCK.defaultBlockState()
            else -> throw IllegalStateException()
        }

        distance = 0

        task = gameHandle.scheduler.interval(this, 5)

        if (DEBUG_SHAPES) {
            debugController.exclusive("shape") { shape.debug(it) }
        }
    }

    override fun destroy() {
        shape = null

        task?.cancel()

        if (DEBUG_SHAPES) {
            debugController.exclusive("shape") { }
        }
    }

    override fun begin(input: InputInterface, messenger: ChallengeMessenger) {
        val translations = gameHandle.translations

        val name = shape!!.javaClass.simpleName.lowercase(Locale.ROOT)
        messenger.task(translations.translateText("shape.$name"))

        input.expectInput().validateInt(translations)
    }

    override fun evaluate(choices: PlayerChoices, result: ChallengeResult) {
        result.correctAnswer = amount
        result.grantClosest3(gameHandle.participants.asSet, amount, choices::getInt)
    }

    override fun run(info: RunningTask) {
        if (distance > maxDistance) {
            info.cancel()
            return
        }

        val d = distance++
        val blocks = blocksByDistance?.get(d) ?: return

        for (pos in blocks) {
            modifier.setBlockState(pos, state!!)
        }
    }

    private fun distance(pos: BlockPos): Int =
        floor(shapeManager.distance(shape!!, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)).toInt()

    override fun provideInitCommand(node: LiteralArgumentBuilder<CommandSourceStack>, init: Challenge.Initializer) {
        node.then(
            argument("shape", StringArgumentType.word())
                .suggests { _, builder -> suggestShapes(builder) }
                .executes { ctx -> setShape(ctx, init) }
        )
    }

    private fun suggestShapes(builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
        for (shape in shapeManager.getShapes()) {
            builder.suggest(shape)
        }

        return builder.buildFuture()
    }

    private fun setShape(ctx: CommandContext<CommandSourceStack>, init: Challenge.Initializer): Int {
        val str = StringArgumentType.getString(ctx, "shape")

        if (!shapeManager.getShapes().contains(str)) {
            ctx.source.sendFailure(Component.literal("Unknown shape \"$str\""))
            return 0
        }

        init.accept(ctx, str)

        return 1
    }
}
