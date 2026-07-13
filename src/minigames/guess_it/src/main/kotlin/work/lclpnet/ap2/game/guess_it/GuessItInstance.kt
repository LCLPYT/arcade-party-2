package work.lclpnet.ap2.game.guess_it

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.numbers.FixedFormat
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.BossEvent
import net.minecraft.world.level.gamerules.GameRules
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.core.hook.CopperGolemTurnIntoStatueCallback
import work.lclpnet.ap2.ext.*
import work.lclpnet.ap2.ext.mc.playNotifySound
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.base.FFAGameInstance
import work.lclpnet.ap2.game.data.IntScoreDataContainer
import work.lclpnet.ap2.game.guess_it.data.*
import work.lclpnet.ap2.game.guess_it.util.DynamicEntityModifier
import work.lclpnet.ap2.game.guess_it.util.SetChallengeCommand
import work.lclpnet.ap2.game.guess_it.util.SkipChallengeCommand
import work.lclpnet.ap2.game.util.useDataContainer
import work.lclpnet.ap2.game.util.useScoreboardStatsSync
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.util.scoreboard.ScoreHandle
import work.lclpnet.ap2.util.scoreboard.ScoreboardLayout
import work.lclpnet.ap2.util.scoreboard.setupTranslatedSidebarObjective
import work.lclpnet.ap2.util.useGameRules
import work.lclpnet.gaco.ds.IndexedSet
import work.lclpnet.gaco.dynamic_entities.DynamicEntityManager
import work.lclpnet.game.map.GameMap
import work.lclpnet.game.util.BossBarTimer
import work.lclpnet.game.util.ResetWorldModifier
import work.lclpnet.kibu.hook.entity.*
import work.lclpnet.kibu.scheduler.api.TaskHandle
import work.lclpnet.kibu.title.Title
import work.lclpnet.kibu.translate.text.FormatWrapper
import work.lclpnet.kibu.translate.text.TranslatedText
import java.util.*
import kotlin.time.Duration.Companion.seconds

private val PREPARATION_DELAY = 3.seconds
private val AFTER_CHALLENGE_DELAY = 5.seconds
private const val ROUNDS = 8
private const val MAX_CONSECUTIVE_ERRORS = 5

class GuessItInstance(
    gameHandle: MiniGameHandle,
    world: ServerLevel,
    map: GameMap,
    soundSubtitles: SoundSubtitles,
    mannequinUuids: IndexedSet<UUID>,
) : FFAGameInstance(gameHandle, world, map) {

    override val data = useDataContainer(::IntScoreDataContainer)
    val random = Random()
    val choices = PlayerChoices(gameHandle.translations)
    val result: ChallengeResult = ChallengeResult()
    val answerId = gameHandle.gameInfo.identifier("answer")
    val messenger = ChallengeMessengerImpl(level, gameHandle.translations, answerId)
    val inputManager = InputManager(choices, gameHandle.translations, players(), messenger, answerId)
    val modifier = ResetWorldModifier(level, hooks)
    val dynamicEntities by lazy {
        val dynamicEntityManager = DynamicEntityManager(level)
        dynamicEntityManager.init(gameHandle.rootScheduler, hooks)
        DynamicEntityModifier(dynamicEntityManager)
    }
    val blockShape = MapUtil.readArea(map)
    var manager = GuessItManager(
        gameHandle,
        level,
        random,
        blockShape,
        modifier,
        soundSubtitles,
        commons().debugController(),
        mannequinUuids,
        dynamicEntities
    )
    private var challenge: Challenge? = null
    private var roundHandle: ScoreHandle? = null
    private var round = 0
    private var consecutiveErrors = 0
    private var currentTask: TaskHandle? = null
    private var timer: BossBarTimer? = null
    private var transaction = 0

    override fun prepare() {
        useGameRules {
            set(GameRules.REDUCED_DEBUG_INFO, true)
        }

        SetChallengeCommand(manager) { skip() }.register(commands)
        SkipChallengeCommand { skip() }.register(commands)

        setupScoreboard()

        setupHooks()

        commons().teleportToRandomSpawns(random)
    }

    private fun setupHooks() {
        // ignore daylight affection for undead mobs
        AffectedByDaylightCallback.HOOK.registerWith(hooks) { _ -> true }

        // prevent entity conversion, e.g. piglin -> zombified piglin
        EntityConvertCallback.HOOK.registerWith(hooks) { _, _ -> true }

        // prevent entity teleportation
        EntityTeleportCallback.HOOK.registerWith(hooks) { _, _, _, _ -> true }

        // prevent entity targeting
        EntityTargetCallback.HOOK.registerWith(hooks) { _, _ -> true }

        // prevent mobs from applying effects to players
        EntityStatusEffectCallback.HOOK.registerWith(hooks) { entity, _, source ->
            entity is ServerPlayer && source != null
        }

        // prevent wither shooting skulls
        WitherShootCallback.HOOK.registerWith(hooks) { _, _, _, _ -> true }

        // prevent boss mobs from creating boss bars for players
        EntityBossBarCallback.HOOK.registerWith(hooks) { _, _, _ -> true }

        // prevent copper golems from turning into statues and leaving blocks
        CopperGolemTurnIntoStatueCallback.HOOK.registerWith(hooks) { _ -> true }
    }

    override fun go() {
        inputManager.init(gameHandle.hooks)

        prepareNextChallenge()
    }

    private fun setupScoreboard() {
        val scoreboardManager = gameHandle.scoreboardManager

        val objective = setupTranslatedSidebarObjective(scoreboardManager, gameHandle.gameInfo.titleKey)

        // round display
        roundHandle = objective.createText(translations.translateText("round").withStyle(ChatFormatting.GREEN))
        updateRoundDisplay()

        objective.createNewline(ScoreboardLayout.TOP)

        // score heading
        objective.createText(
            translations.translateText("ap2.score")
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
        )

        val separator = Component.literal(ApConstants.SCOREBOARD_SEPARATOR_SM)
            .withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.STRIKETHROUGH)

        objective.createText(separator)

        for (player in PlayerLookup.all(gameHandle.server)) {
            objective.add(player)
        }

        useScoreboardStatsSync(data, objective)
    }

    private fun updateRoundDisplay() {
        roundHandle?.setNumberFormat(
            FixedFormat(
                Component.literal("$round/$ROUNDS")
                    .withStyle(ChatFormatting.YELLOW)
            )
        )
    }

    @Synchronized
    private fun prepareNextChallenge() {
        modifier.undo()
        dynamicEntities.reset()

        this.challenge?.let { challenge ->
            try {
                challenge.destroy()
            } catch (t: Throwable) {
                gameHandle.logger.error("Failed to destroy {}, ignoring it", challenge.javaClass.getSimpleName(), t)
            }
        }

        round++
        updateRoundDisplay()

        val challengeInit = manager.nextChallenge()

        val challenge = challengeInit.challenge
        val world = level
        val translations = gameHandle.translations

        val prepareMsg = translations.translateText("prepare." + challenge.preparationKey)
            .withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD)

        challenge.init(challengeInit.init)

        this.challenge = challenge

        // send preparation title
        for (player in PlayerLookup.level(world)) {
            Title.get(player).title(Component.empty(), prepareMsg.translateFor(player))

            player.playNotifySound(
                SoundEvents.END_PORTAL_FRAME_FILL,
                SoundSource.NEUTRAL,
                1f,
                0.5f
            )
        }

        try {
            challenge.prepare()
        } catch (t: Throwable) {
            gameHandle.logger.error("Failed to prepare {}", challenge.javaClass.getSimpleName(), t)
            onChallengeError()
            return
        }

        val expected = ++transaction

        currentTask = runAfter(PREPARATION_DELAY) {
            if (transaction != expected) return@runAfter

            beginChallenge()
        }
    }

    @Synchronized
    private fun beginChallenge() {
        val challenge = requireNotNull(challenge) { "Challenge cannot be null" }

        val players = PlayerLookup.level(level)

        if (challenge.shouldPlayBeginSound()) {
            for (player in players) {
                player.playNotifySound(SoundEvents.BREEZE_SHOOT, SoundSource.NEUTRAL, 1f, 0.5f)
            }
        }

        messenger.reset()

        try {
            challenge.begin(inputManager, messenger)
        } catch (t: Throwable) {
            gameHandle.logger.error("Failed to begin {}", challenge.javaClass.getSimpleName(), t)
            onChallengeError()
            return
        }

        consecutiveErrors = 0

        messenger.send()

        val durationTicks = challenge.durationTicks

        val timer = BossBarTimer.builder(translations, translations.translateText("answer"))
            .withAlertSound(true)
            .withColor(BossEvent.BossBarColor.RED)
            .withDurationTicks(durationTicks)
            .build()

        this.timer = timer

        timer.addPlayers(players)

        val expected = ++transaction

        timer.whenDone {
            if (transaction != expected) return@whenDone
            onTimerOver()
        }

        timer.start(gameHandle.bossBarProvider, scheduler)
    }

    private fun onChallengeError() {
        consecutiveErrors++

        if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
            // to many errors in a row, abort the game
            winManager.complete()
            return
        }

        round--
        prepareNextChallenge()
    }

    @Synchronized
    private fun onTimerOver() {
        val challenge = challenge

        if (challenge is LongerChallenge) {
            inputManager.setLocked(true)
            challenge.evaluateDeferred { evaluateChallenge() }
            return
        }

        evaluateChallenge()
    }

    @Synchronized
    private fun evaluateChallenge() {
        val challenge = requireNotNull(challenge) { "Challenge cannot be null" }

        result.clear()
        challenge.evaluate(choices, result)

        val correctAnswer = result.correctAnswer
        var solutionMsg: TranslatedText? = null

        if (correctAnswer != null) {
            solutionMsg = translations.translateText(
                "solution",
                FormatWrapper.styled(correctAnswer, ChatFormatting.YELLOW)
            )
        }

        for (player in players()) {
            val points = result.getPointsGained(player)

            val msg = translations.translateText(
                player,
                "gain_points",
                FormatWrapper.styled(points, ChatFormatting.YELLOW)
            ).withStyle(ChatFormatting.GREEN)

            player.sendOverlayMessage(msg)

            if (points > 0) {
                data.addScore(player, points)

                player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 1.5f)

                if (solutionMsg != null) {
                    player.sendSystemMessage(solutionMsg.translateFor(player).withStyle(ChatFormatting.GREEN))
                }

                continue
            }

            player.playNotifySound(SoundEvents.WITHER_HURT, SoundSource.PLAYERS, 0.3f, 1.3f)

            if (solutionMsg != null) {
                player.sendSystemMessage(solutionMsg.translateFor(player).withStyle(ChatFormatting.RED))
            }
        }

        inputManager.reset()

        if (round >= ROUNDS) {
            winManager.complete()
            return
        }

        val expected = ++transaction

        currentTask = runAfter(AFTER_CHALLENGE_DELAY) {
            if (transaction != expected) return@runAfter

            prepareNextChallenge()
        }
    }

    @Synchronized
    private fun skip() {
        transaction++

        currentTask?.cancel()
        timer?.stop()

        messenger.reset()
        inputManager.reset()
        result.clear()

        round--
        prepareNextChallenge()
    }
}
