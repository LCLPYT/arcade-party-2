package work.lclpnet.ap2.task_rush.task

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.GlobalPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.storage.LevelData
import org.slf4j.Logger
import work.lclpnet.ap2.ext.mc.playNotifySound
import work.lclpnet.ap2.ext.mc.teleport
import work.lclpnet.ap2.game.data.DataContainer
import work.lclpnet.ap2.game.data.type.PlayerRef
import work.lclpnet.ap2.game.player.Participants
import work.lclpnet.ap2.impl.util.world.ChunkPersistence
import work.lclpnet.ap2.util.disallowDropItem
import work.lclpnet.ap2.util.scoreboard.CustomScoreboardManager
import work.lclpnet.game.util.BossBarTimer
import work.lclpnet.kibu.access.misc.CustomNbt
import work.lclpnet.kibu.hook.HookContainer
import work.lclpnet.kibu.hook.HookRegistrar
import work.lclpnet.kibu.scheduler.KibuScheduling
import work.lclpnet.kibu.scheduler.api.Scheduler
import work.lclpnet.kibu.scheduler.api.TaskScheduler
import work.lclpnet.kibu.translate.Translations
import work.lclpnet.kibu.translate.text.FormatWrapper
import work.lclpnet.kibu.translate.text.TranslatedText
import kotlin.time.Duration

interface TaskEnv {

    val players: Participants

    val hooks: HookRegistrar

    val scheduler: TaskScheduler

    val translations: Translations

    val level: ServerLevel

    val scoreboardManager: CustomScoreboardManager

    val logger: Logger

    var duplicateDrops: Boolean

    var pvpDisabled: Boolean

    var fallDamageDisabled: Boolean

    /**
     * The world spawn position all players are teleported to at the start of each task.
     * Used as a reference point for tasks that place things relative to spawn.
     */
    val spawnPos: BlockPos

    val chunkPersistence: ChunkPersistence

    fun timer(labelKey: String, duration: Duration, onEnd: () -> Unit): BossBarTimer

    /**
     * Hands the stack to the player through the shared item queue, delivering it later if the inventory is full.
     */
    fun give(player: ServerPlayer, stack: ItemStack)

    fun giveIfMissing(player: ServerPlayer, stack: ItemStack)

    fun setSpawn(pos: BlockPos)

    fun markTemporary(stack: ItemStack)

    fun complete(data: DataContainer<ServerPlayer, PlayerRef>)
}

class TaskEnvImpl(
    override val players: Participants,
    override val level: ServerLevel,
    override val translations: Translations,
    override val scoreboardManager: CustomScoreboardManager,
    override val chunkPersistence: ChunkPersistence,
    override val logger: Logger,
    val itemQueue: ItemQueue,
    val createTimer: (String, Duration) -> BossBarTimer,
    val onComplete: (DataContainer<ServerPlayer, PlayerRef>, TaskEnvImpl) -> Unit,
) : TaskEnv {

    companion object {
        val TEMPORARY_CODEC: MapCodec<Boolean> = Codec.BOOL.fieldOf("reach_coords")
    }

    override val hooks = HookContainer()
    override val scheduler = Scheduler(logger)

    val originalSpawnPos = level.respawnData.pos()

    override val spawnPos: BlockPos
        get() = level.respawnData.pos()

    override var duplicateDrops = true
    override var pvpDisabled = true
    override var fallDamageDisabled = true

    private val timers = ArrayList<BossBarTimer>()
    private var completed = false

    fun init() {
        KibuScheduling.getRootScheduler().addChild(scheduler)

        disallowDropItem(hooks) {
            isTemporary(it)
        }
    }

    override fun timer(labelKey: String, duration: Duration, onEnd: () -> Unit): BossBarTimer {
        val timer = createTimer(labelKey, duration)
        timers.add(timer)

        timer.whenDone {
            onEnd()
        }

        return timer
    }

    override fun give(player: ServerPlayer, stack: ItemStack) {
        itemQueue.give(player, stack)
    }

    override fun giveIfMissing(player: ServerPlayer, stack: ItemStack) {
        if (player.inventory.contains(stack) || itemQueue.contains(player, stack)) return

        give(player, stack)
    }

    override fun setSpawn(pos: BlockPos) {
        if (level.respawnData.pos() == pos) return

        level.respawnData = LevelData.RespawnData(GlobalPos(level.dimension(), pos), 0f, 0f)

        for (player in players) {
            player.teleport(pos)
        }
    }

    override fun markTemporary(stack: ItemStack) {
        CustomNbt.set(stack, TEMPORARY_CODEC, true)
    }

    override fun complete(data: DataContainer<ServerPlayer, PlayerRef>) {
        if (completed) return
        completed = true

        onComplete(data, this)
    }

    fun unload() {
        completed = true

        setSpawn(originalSpawnPos)

        timers.forEach { it.stop() }
        timers.clear()
        hooks.unload()
        KibuScheduling.getRootScheduler().removeChild(scheduler)

        for (player in players) {
            for (i in 0 until player.inventory.containerSize) {
                val stack = player.inventory.getItem(i)

                if (isTemporary(stack)) {
                    player.inventory.setItem(i, ItemStack.EMPTY)
                }
            }
        }
    }

    private fun isTemporary(stack: ItemStack): Boolean =
        CustomNbt.get(stack, TEMPORARY_CODEC).orElse(false) ?: false
}

interface Task {

    val id: String

    /**
     * The chat announcement shown when the task starts. Called once, right before [begin], so tasks may
     * pick random parameters here and merge them into the message.
     */
    fun announcement(env: TaskEnv): TranslatedText =
        env.translations.translateText("task.$id")

    fun begin(env: TaskEnv)

    fun end(env: TaskEnv) {}
}

/**
 * Sends a short overlay feedback message to the player, optionally followed by a confirmation sound.
 * Tasks use this to show live progress such as running totals, personal bests or the current value being tracked.
 */
internal fun TaskEnv.feedback(player: ServerPlayer, key: String, value: Any, sound: Boolean = false) {
    translations.translateText(key, FormatWrapper.styled(value, ChatFormatting.YELLOW))
        .withStyle(ChatFormatting.AQUA)
        .sendTo(player, true)

    if (sound) {
        player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.3f, 1.8f)
    }
}
