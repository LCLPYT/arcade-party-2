package work.lclpnet.ap2.game.guess_it.challenge

import net.minecraft.ChatFormatting.RED
import net.minecraft.ChatFormatting.YELLOW
import net.minecraft.core.BlockPos
import net.minecraft.core.GlobalPos
import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.Mth
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.LodestoneTracker
import net.minecraft.world.phys.Vec3
import work.lclpnet.ap2.ext.mc.setDayTime
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.guess_it.data.*
import work.lclpnet.ap2.game.guess_it.util.DynamicEntityModifier
import work.lclpnet.ap2.game.guess_it.util.MinecraftDayTime
import work.lclpnet.ap2.impl.util.TextUtil
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape
import work.lclpnet.kibu.scheduler.Ticks
import work.lclpnet.kibu.scheduler.api.RunningTask
import work.lclpnet.kibu.scheduler.api.SchedulerAction
import work.lclpnet.kibu.scheduler.api.TaskHandle
import work.lclpnet.kibu.translate.text.FormatWrapper.styled
import java.util.*
import kotlin.math.max

private val ANIMATION_DURATION_TICKS = Ticks.seconds(2)
private val DURATION_TICKS = Ticks.seconds(20)

class DayTimeChallenge(
    private val gameHandle: MiniGameHandle,
    private val world: ServerLevel,
    private val random: Random,
    private val blockShape: BlockShape,
    private val dynamicEntities: DynamicEntityModifier
) : Challenge, SchedulerAction {

    private var timeStart = 6000
    private var timeEnd = 6000
    private var correctTime = 6000
    private var prevTime = 6000
    private var tick = 0
    private var animation: TaskHandle? = null

    override fun id() = "day_time"

    override val preparationKey = PREPARE_ESTIMATE

    override val durationTicks = max(ANIMATION_DURATION_TICKS, DURATION_TICKS)

    override fun prepare() {
        prevTime = world.overworldClockTime.toInt()
        correctTime = random.nextInt(24000)

        animateTime(prevTime, correctTime)
    }

    override fun begin(input: InputInterface, messenger: ChallengeMessenger) {
        val translations = gameHandle.translations
        messenger.task(translations.translateText("daytime.guess"))

        input.expectInput().validate(
            { str, _ -> MinecraftDayTime.dayTimeValue(str) },
            { str -> translations.translateText("input.daytime", styled(str, YELLOW)).withStyle(RED) }
        )

        // create compass that points north
        val stack = ItemStack(Items.COMPASS)

        stack.set(
            DataComponents.LODESTONE_TRACKER, LodestoneTracker(
                Optional.of(GlobalPos(world.dimension(), BlockPos.ZERO.north(10000))),
                false
            )
        )

        stack.set(
            DataComponents.CUSTOM_NAME, TextUtil.getVanillaName(Items.COMPASS)
                .withStyle { style -> style.withItalic(false) }
        )

        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false)

        for (player in gameHandle.participants) {
            player.inventory.setItem(4, stack.copy())
        }

        val origin = blockShape.origin()

        addHint(
            dynamicEntities, world, gameHandle.translations,
            Vec3(origin.x + 0.5, (origin.y + 1).toDouble(), origin.z + 0.5),
            "daytime.hint"
        )
    }

    override fun evaluate(choices: PlayerChoices, result: ChallengeResult) {
        result.correctAnswer = MinecraftDayTime.stringifyDayTime(correctTime)

        val participants = gameHandle.participants

        result.grantClosest3Diff(participants.asSet) { player ->
            val optChoice = choices.get(player) ?: return@grantClosest3Diff null

            val time = MinecraftDayTime.parseDayTime(optChoice) ?: return@grantClosest3Diff null

            // wrap time at 0 am
            val diff1 = Math.floorMod(correctTime - time, 24000)
            val diff2 = Math.floorMod(time - correctTime, 24000)

            minOf(diff1, diff2)
        }

        animateTime(correctTime, prevTime)

        for (player in gameHandle.participants) {
            player.inventory.setItem(4, ItemStack.EMPTY)
        }
    }

    override fun destroy() {
        world.setDayTime(prevTime)

        animation?.let {
            it.cancel()
            animation = null
        }
    }

    private fun animateTime(start: Int, end: Int) {
        tick = 0
        timeStart = start
        timeEnd = end

        animation = gameHandle.scheduler.interval(this, 1)
    }

    override fun run(info: RunningTask) {
        val t = (tick++).toFloat()
        val progress = t / ANIMATION_DURATION_TICKS

        val time = getInterpolatedTime(progress)

        world.setDayTime(time)

        if (t >= ANIMATION_DURATION_TICKS) {
            info.cancel()
        }
    }

    private fun getInterpolatedTime(progress: Float): Int {
        val forwardDiff = Math.floorMod(timeEnd - timeStart, 24000)
        val backwardDiff = Math.floorMod(timeStart - timeEnd, 24000)

        return if (timeStart > timeEnd && forwardDiff <= backwardDiff) {
            // advance time forwards, wrapping at 0 am
            Math.floorMod(Mth.lerpInt(progress, timeStart, timeEnd + 24000), 24000)
        } else if (timeStart < timeEnd && forwardDiff >= backwardDiff) {
            // advance time backwards, wrapping at 0 am
            Math.floorMod(Mth.lerpInt(progress, timeStart + 24000, timeEnd), 24000)
        } else {
            // advance time without wrapping
            Mth.lerpInt(progress, timeStart, timeEnd)
        }
    }
}
