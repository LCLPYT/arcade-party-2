package work.lclpnet.ap2.game.fine_tuning

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.ChatFormatting.*
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.BlockTags
import net.minecraft.world.BossEvent
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.stats.FFAStatsManager
import work.lclpnet.ap2.api.util.heads.PlayerHead
import work.lclpnet.ap2.ext.mc.isIn
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.ext.mc.playNotifySound
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.data.IntDataContainer
import work.lclpnet.ap2.game.data.type.PlayerRef
import work.lclpnet.ap2.game.fine_tuning.melody.*
import work.lclpnet.ap2.game.util.Announcer
import work.lclpnet.ap2.impl.util.ApRegistries
import work.lclpnet.ap2.impl.util.BookUtil
import work.lclpnet.ap2.impl.util.heads.PlayerHeads
import work.lclpnet.gaco.dynamic_entities.DynamicEntityManager
import work.lclpnet.game.impl.prot.ProtectionTypes
import work.lclpnet.game.util.BossBarTimer
import work.lclpnet.kibu.hook.HookContainer
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks
import work.lclpnet.kibu.hook.util.PlayerUtils
import work.lclpnet.kibu.scheduler.Ticks
import work.lclpnet.kibu.scheduler.api.TaskHandle
import work.lclpnet.kibu.title.Title
import java.util.*
import kotlin.random.Random

private const val TUNING_TIME_SECONDS = 36

class TuningPhase(
    private val gameHandle: MiniGameHandle,
    private val rooms: Map<UUID, FineTuningRoom>,
    private val data: IntDataContainer<ServerPlayer, PlayerRef>,
    private val stats: FFAStatsManager,
    private val onEnd: Runnable,
    private val world: ServerLevel,
    private val announcer: Announcer,
) {
    private val random = Random(System.currentTimeMillis())
    private val melodyProvider: MelodyProvider = SimpleMelodyProvider(random, SimpleNotesProvider(random), 5)
    private val replaying = HashMap<UUID, TaskHandle>()
    private val lastInteracted = LinkedHashSet<UUID>()
    private val completed = HashSet<UUID>()
    private val hooks = HookContainer()
    val records = MelodyRecords()

    private var playersCanInteract = false
    private lateinit var melody: Melody
    private lateinit var dynamicEntityManager: DynamicEntityManager
    private var melodyNumber = 0
    private lateinit var timer: BossBarTimer

    fun init() {
        gameHandle.whenDone(::unload)

        addNoteBlockHooks()
        PlayerInventoryHooks.MODIFY_INVENTORY.registerWith(hooks) { event -> !event.player().canUseGameMasterBlocks() }

        PlayerInteractionHooks.USE_ITEM.registerWith(hooks) { player, _, _ ->
            if (onUseItem(player)) InteractionResult.SUCCESS_SERVER else InteractionResult.PASS
        }

        PlayerInteractionHooks.ATTACK_BLOCK.registerWith(hooks) { player, _, _, _, _ ->
            onUseItem(player)
            InteractionResult.PASS
        }

        gameHandle.protect { config ->
            ProtectionTypes.USE_BLOCK.allow(config) { entity, pos ->
                val state: BlockState = entity.level().getBlockState(pos)
                state.`is`(Blocks.NOTE_BLOCK) || state.isIn(BlockTags.ALL_SIGNS)
            }
        }

        dynamicEntityManager = DynamicEntityManager(world)
        dynamicEntityManager.init(gameHandle.scheduler, gameHandle.hooks)
    }

    private fun addNoteBlockHooks() {
        val participants = gameHandle.participants

        PlayerInteractionHooks.USE_BLOCK.registerWith(hooks) { player, world, _, hitResult ->
            if (player !is ServerPlayer) return@registerWith InteractionResult.FAIL

            if (cannotInteract(player) || !participants.isParticipating(player)) {
                return@registerWith cancelInteraction(player)
            }

            val pos: BlockPos = hitResult.blockPos
            val state: BlockState = world.getBlockState(pos)

            when {
                state.`is`(Blocks.NOTE_BLOCK) -> onUseNoteBlock(player, pos)
                state.`is`(BlockTags.ALL_SIGNS) -> onUseSign(player, pos)
                else -> onUseItem(player)
            }

            cancelInteraction(player)
        }

        PlayerInteractionHooks.ATTACK_BLOCK.registerWith(hooks) { player, world, _, pos, _ ->
            if (cannotInteract(player) || player !is ServerPlayer || !participants.isParticipating(player)) {
                return@registerWith InteractionResult.FAIL
            }

            val state: BlockState = world.getBlockState(pos)

            when {
                state.isOf(Blocks.NOTE_BLOCK) -> {
                    if (rooms[player.uuid]?.playNoteBlock(player, pos) == true) {
                        stats.increment(player, Probes)
                    }
                }
                state.isIn(BlockTags.ALL_SIGNS) -> onUseSign(player, pos)
            }

            InteractionResult.FAIL
        }
    }

    private fun onUseSign(player: ServerPlayer, pos: BlockPos) {
        val room = rooms[player.uuid] ?: return

        if (completed.contains(player.uuid)) return

        val testSignPos = room.testSignPos ?: return

        if (testSignPos != pos) return

        triggerReplay(player)
    }

    private fun onUseNoteBlock(player: ServerPlayer, pos: BlockPos) {
        val room = rooms[player.uuid] ?: return
        if (completed.contains(player.uuid)) return

        if (!room.useNoteBlock(player, pos, dynamicEntityManager)) return

        stats.increment(player, PitchChanges)
        markInteraction(player)

        if (!room.isComplete(melody)) return

        stats.increment(player, MelodiesCompleted)

        completed.add(player.uuid)
        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1f)
        gameHandle.translations.translateText("completed").formatted(GREEN).sendTo(player)

        if (completed.size < gameHandle.participants.count()) return
        timer.stop()
    }

    fun beginListen() {
        announcer.announceSubtitle("listen")
        gameHandle.scheduler.timeout(::playNextMelody, 40)
    }

    private fun playNextMelody() {
        melody = melodyProvider.nextMelody()
        records.recordMelody(melody)
        rooms.values.forEach { it.setMelody(melody) }
        playMelody(::listenAgain)
    }

    private fun playMelody(onDone: Runnable) {
        val participants = gameHandle.participants
        val scheduler = gameHandle.scheduler

        val task = PlayMelodyTask({ note ->
            for (player in participants) {
                rooms[player.uuid]?.playNote(player, note)
            }
        }, melody.notes.size)

        scheduler.interval(task, 1).whenComplete {
            scheduler.timeout(onDone, 20)
        }
    }

    private fun listenAgain() {
        announcer
            .withSound(SoundEvents.CHICKEN_EGG, SoundSource.RECORDS, 0.5f, 0f)
            .announceSubtitle("listen_again")

        gameHandle.scheduler.timeout(40) { ->
            playMelody(::beginTune)
        }
    }

    private fun beginTune() {
        val server = gameHandle.server
        val translations = gameHandle.translations
        val scheduler = gameHandle.scheduler
        val bossBarProvider = gameHandle.bossBarProvider
        val players = PlayerLookup.all(server)

        translations.translateText("repeat").formatted(GREEN)
            .acceptEach(players) { player, text -> Title.get(player).title(Component.empty(), text, 5, 30, 5) }

        val shuffled = baseMelody()
        rooms.values.forEach { it.setMelody(shuffled) }

        playersCanInteract = true
        giveReplayItems()

        timer = BossBarTimer.builder(translations, translations.translateText("tune", melodyNumber + 1, MELODY_COUNT))
            .withAlertSound(false)
            .withColor(BossEvent.BossBarColor.RED)
            .withDurationTicks(Ticks.seconds(TUNING_TIME_SECONDS))
            .build()

        timer.addPlayers(players)
        timer.start(bossBarProvider, scheduler)

        timer.whenDone {
            playersCanInteract = false
            takeReplayItems()
            stopReplay()

            for (room in rooms.values) {
                room.removeDisplays(dynamicEntityManager)
            }

            evaluateScores(server)
            completed.clear()

            if (++melodyNumber == MELODY_COUNT) {
                onEnd.run()
            } else {
                beginListen()
            }
        }
    }

    private fun evaluateScores(server: MinecraftServer) {
        val playerManager = server.playerList
        val baseMelody = baseMelody()
        var bestScore = Int.MIN_VALUE
        var worstScore = Int.MAX_VALUE
        var best: ServerPlayer? = null
        var worst: ServerPlayer? = null

        for (uuid in participantsInteractionOrder()) {
            val room = rooms[uuid] ?: continue
            val player = playerManager.getPlayer(uuid) ?: continue
            val score = room.calculateScore(baseMelody, melody)
            val correct = room.correctNoteCount(melody)

            stats.increment(player, CorrectNotes, correct)
            data.addScore(player, score)

            if (score > bestScore) {
                bestScore = score
                best = player
            }

            if (score <= worstScore && (score > 0 || worst == null || worst == best)) {
                worstScore = score
                worst = player
            }
        }

        lastInteracted.clear()

        val b = best ?: return
        val w = worst ?: return
        val bestRoom = rooms[b.uuid] ?: return
        val worstRoom = rooms[w.uuid] ?: return

        records.record(melody, b, bestRoom.getCurrentMelody(), w, worstRoom.getCurrentMelody())
    }

    private fun stopReplay() {
        replaying.values.forEach { it.cancel() }
        replaying.clear()
    }

    private fun giveReplayItems() {
        val translations = gameHandle.translations
        val participants = gameHandle.participants

        val head: PlayerHead = world.registryAccess()
            .lookupOrThrow(ApRegistries.PLAYER_HEAD)
            .getOptional(PlayerHeads.GEODE_ARROW_FORWARD)
            .orElseThrow()

        for (player in participants) {
            val stack = head.createStack()
            stack.set(DataComponents.CUSTOM_NAME, translations.translateText(player, "replay")
                .styled { it.withItalic(false).applyFormat(YELLOW) })
            player.inventory.setItem(4, stack)
        }
    }

    private fun takeReplayItems() {
        for (player in gameHandle.participants) {
            player.inventory.setItem(4, ItemStack.EMPTY)
        }
    }

    private fun baseMelody(): Melody {
        val notes = Array(8) { Note.FIS3 }
        return Melody(melody.instrument, notes)
    }

    private fun markInteraction(player: Player) {
        val uuid = player.uuid
        lastInteracted.remove(uuid)
        lastInteracted.add(uuid)
    }

    private fun participantsInteractionOrder(): Iterable<UUID> {
        val participants = gameHandle.participants
        val order = LinkedHashSet<UUID>(participants.count())
        order.addAll(lastInteracted)
        for (player in participants) {
            order.add(player.uuid)
        }
        return order
    }

    private fun cannotInteract(player: Player): Boolean =
        !playersCanInteract || replaying.containsKey(player.uuid)

    private fun cancelInteraction(player: ServerPlayer): InteractionResult {
        PlayerUtils.syncPlayerItems(player)
        return InteractionResult.FAIL
    }

    private fun replayMelody(player: ServerPlayer, onDone: Runnable): TaskHandle? {
        val uuid = player.uuid
        val room = rooms[uuid] ?: return null

        room.setTemporaryMelody(melody)
        room.removeDisplays(dynamicEntityManager)

        val scheduler = gameHandle.scheduler
        val task = PlayMelodyTask({ note ->
            if (!player.isAlive) return@PlayMelodyTask
            room.playNote(player, note)
        }, melody.notes.size)

        return scheduler.interval(task, 1).whenComplete {
            room.restoreMelody()
            room.markErrors(baseMelody(), melody, dynamicEntityManager, player)
            onDone.run()
        }
    }

    private fun onUseItem(player: Player): Boolean {
        val participants = gameHandle.participants
        val serverPlayer = player as? ServerPlayer ?: return false
        if (!participants.isParticipating(serverPlayer)) return false

        val stack = player.getItemInHand(InteractionHand.MAIN_HAND)
        if (!stack.`is`(Items.PLAYER_HEAD) || completed.contains(player.uuid)) return false

        return triggerReplay(serverPlayer)
    }

    private fun triggerReplay(player: ServerPlayer): Boolean {
        val uuid = player.uuid
        if (replaying.containsKey(uuid)) return false

        val handle = replayMelody(player) { replaying.remove(uuid) } ?: return false
        replaying[uuid] = handle

        stats.increment(player, Replays)

        return true
    }

    fun unload() {
        hooks.unload()
    }

    fun giveBooks() {
        val translations = gameHandle.translations
        val participants = gameHandle.participants

        for (player in participants) {
            val stack = ItemStack(Items.WRITTEN_BOOK)
            val controls = translations.translate(player, "controls.title")

            BookUtil.builder(controls, ApConstants.PERSON_LCLP)
                .addPage(
                    translations.translateText(player, "controls.note_up")
                        .formatted(DARK_BLUE, BOLD).append(":\n"),
                    Component.keybind("key.use").withStyle(DARK_GREEN).append("\n\n"),
                    translations.translateText(player, "controls.note_down")
                        .formatted(DARK_BLUE, BOLD).append(":\n"),
                    Component.keybind("key.sneak").withStyle(DARK_GREEN).append(" + ")
                        .append(Component.keybind("key.use").append("\n\n")),
                    translations.translateText(player, "controls.test")
                        .formatted(DARK_BLUE, BOLD).append(":\n"),
                    Component.keybind("key.attack").withStyle(DARK_GREEN)
                )
                .applyTo(stack)

            stack.set(DataComponents.CUSTOM_NAME, Component.literal(controls)
                .withStyle { it.withItalic(false).applyFormat(GREEN) })

            player.inventory.setItem(8, stack)
        }
    }
}
