package work.lclpnet.ap2.mode_default.activity

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.ChatFormatting
import net.minecraft.commands.Commands
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.numbers.FixedFormat
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Display
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.joml.Vector3d
import org.json.JSONObject
import org.slf4j.Logger
import work.lclpnet.activity.Activity
import work.lclpnet.activity.ComponentActivity
import work.lclpnet.activity.component.ComponentBundle
import work.lclpnet.activity.component.builtin.BossBarComponent
import work.lclpnet.activity.component.builtin.BuiltinComponents
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.base.GameQueue
import work.lclpnet.ap2.api.data.DataManager
import work.lclpnet.ap2.api.game.GameStartContext
import work.lclpnet.ap2.api.map.MapFacade
import work.lclpnet.ap2.api.music.SongWrapper
import work.lclpnet.ap2.api.music.WeightedSong
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.ext.mc.playNotifySound
import work.lclpnet.ap2.game.MiniGame
import work.lclpnet.ap2.impl.activity.ArcadePartyComponents
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.impl.music.MusicHelper
import work.lclpnet.ap2.impl.util.IconMaker
import work.lclpnet.ap2.impl.util.scoreboard.DynamicScoreboardObjective
import work.lclpnet.ap2.impl.util.scoreboard.ScoreboardLayout
import work.lclpnet.ap2.impl.util.title.AnimatedTitle
import work.lclpnet.ap2.impl.util.title.NextGameTitleAnimation
import work.lclpnet.ap2.mode_default.ApMiniGameArgs
import work.lclpnet.ap2.mode_default.api.Skippable
import work.lclpnet.ap2.mode_default.cmd.ForceMapCommand
import work.lclpnet.ap2.mode_default.cmd.SkipCommand
import work.lclpnet.ap2.mode_default.util.*
import work.lclpnet.ap2.util.scoreboard.setupDynamicSidebarObjective
import work.lclpnet.gaco.dynamic_entities.DynamicEntityManager
import work.lclpnet.gaco.scene.MixedMountContext
import work.lclpnet.gaco.scene.Object3d
import work.lclpnet.gaco.scene.Scene
import work.lclpnet.gaco.scene.`object`.TranslatedTextDisplayObject
import work.lclpnet.game.api.WorldOptions
import work.lclpnet.game.map.GameMap
import work.lclpnet.game.util.BossBarTimer
import work.lclpnet.game.util.ProtectorComponent
import work.lclpnet.kibu.hook.HookRegistrar
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks
import work.lclpnet.kibu.hook.player.PlayerConnectionHooks
import work.lclpnet.kibu.scheduler.Ticks
import work.lclpnet.kibu.scheduler.api.RunningTask
import work.lclpnet.kibu.scheduler.api.TaskHandle
import work.lclpnet.kibu.scheduler.api.TaskScheduler
import work.lclpnet.kibu.translate.Translations
import work.lclpnet.kibu.translate.text.FormatWrapper
import java.util.concurrent.CompletableFuture
import kotlin.math.floor
import kotlin.math.max


private val GAME_ANNOUNCE_DELAY = Ticks.seconds(3)
private val PREPARATION_TIME = Ticks.seconds(18)
private const val GAME_SONG_ID = "ap2_game"

class PreparationActivity(private val args: ApBaseArgs) : ComponentActivity(
    args.miniGameArgs.server,
    args.miniGameArgs.logger
), Skippable, GameStartContext {

    private var gameChooser: OptionChooser<MiniGame>? = null
    private var mapChooser: OptionChooser<GameMap>? = null
    private val activityConfigurator: BaseActivityConfigurator = BaseActivityConfigurator(this, args)
    private var time = 0
    override var isSkip = false
    private var gameForced = false
    private var miniGame: MiniGame? = null
    private var taskHandle: TaskHandle? = null
    private var bossBarTimer: BossBarTimer? = null
    private var animatedTitle: AnimatedTitle? = null
    private var song: SongWrapper? = null
    private var whenTasksDone: CompletableFuture<Void?>? = null
    private var onScoreUpdate: Runnable? = null
    private var world: ServerLevel? = null
    private var map: GameMap? = null
    private var dynamicEntityManager: DynamicEntityManager? = null
    private var gameQueueDisplay: Object3d? = null
    private var nextGameSong: WeightedSong? = null

    override fun registerComponents(componentBundle: ComponentBundle) {
        componentBundle.add(BuiltinComponents.SCHEDULER)
            .add(BuiltinComponents.BOSS_BAR)
            .add(BuiltinComponents.HOOKS)
            .add(BuiltinComponents.COMMANDS)
            .add(ProtectorComponent.KEY)
            .add(ArcadePartyComponents.SCORE_BOARD)
    }

    override fun start() {
        super.start()

        activityConfigurator.configureProtector()

        args.tablistManager.setPreparation()
        args.tablistManager.update()

        CompletableFuture.supplyAsync {
            val setupFuture = setupMap(args.miniGameArgs)
            val assetFuture = loadAssets()

            assetFuture.join()
            setupFuture.join()
        }.whenComplete { res: SetupResult?, err: Throwable? ->
            if (err != null) {
                args.miniGameArgs.logger.error("Failed to setup preparation activity", err)
            } else if (res != null) {
                onReady(res.world, res.map)
            }
        }
    }

    private fun loadAssets(): CompletableFuture<Void> {
        return args.miniGameArgs.songManager
            .getSongAndCache(MusicHelper.ARCADE_PARTY_GAME_TAG, GAME_SONG_ID)
            .thenAccept { song -> nextGameSong = song.orElse(null) }
    }

    override fun stop() {
        args.forceGameCommand.gameEnforcer = { game -> args.gameQueue.setNextGame(game) }

        if (onScoreUpdate != null) {
            args.scoreManager.onChange.unregister(onScoreUpdate)
        }

        removeGameQueue()

        super.stop()
    }

    private fun onReady(world: ServerLevel, map: GameMap) {
        this.world = world
        this.map = map

        val mapFacade: MapFacade = args.miniGameArgs.mapFacade
        mapFacade.forceMap(null)  // reset forced map

        val scoreManager: ScoreManager = args.scoreManager

        if (scoreManager.hasClearWinner()) {
            beginWinSequence()
            return
        }

        if (scoreManager.hasMultipleWinners()) {
            args.playerManager.enterFinale(scoreManager.finalists)

            // remove games from the queue that cannot be played in a finale
            args.gameQueue.setFilter { game -> game.canBeFinale(this) }
        }

        args.playerManager.startPreparation()

        scoreManager.incrementRound()

        scoreManager.onChange.register(Runnable { restartActivity() }.also { onScoreUpdate = it })

        activityConfigurator.resetPlayers()
        activityConfigurator.configureHooks()

        world.waypointManager.breakAllConnections()

        val hooks = component(BuiltinComponents.HOOKS).hooks()
        val scheduler = component(BuiltinComponents.SCHEDULER).scheduler()

        dynamicEntityManager = DynamicEntityManager(world)
        dynamicEntityManager!!.init(scheduler, hooks)

        setupAdminItems(hooks)
        setupSettingsMenu(hooks)

        showLeaderboard()
        displayGameQueue()
        prepareNextMiniGame()

        val commandRegistrar = component(BuiltinComponents.COMMANDS).commands()

        SkipCommand(this).register(commandRegistrar)
        ForceMapCommand(mapFacade) { miniGame }.register(commandRegistrar)

        args.forceGameCommand.gameEnforcer = { miniGame -> forceGame(miniGame) }
    }

    private fun restartActivity() {
        args.scoreManager.decrementRound()

        if (this.miniGame != null) {
            args.gameQueue.shiftGame(this.miniGame)
        }

        switchActivity(PreparationActivity(args))
    }

    private fun prepareNextMiniGame() {
        if (miniGame == null) {
            miniGame = pickNextGame()
        }

        val gameId = miniGame!!.id

        if (miniGame!!.usesMaps) {
            whenTasksDone = args.miniGameArgs.mapFacade.reloadMaps(gameId).exceptionally { err ->
                args.miniGameArgs.logger.error("Failed to reload maps for {}", gameId, err)
                null
            }
        }

        displayGameQueue()
        startTimer().whenDone(::onTimerEnded)

        taskHandle = component(BuiltinComponents.SCHEDULER).scheduler()
            .interval(1, ::tick)

        for (player in PlayerLookup.all(server)) {
            giveAdminItems(player)
        }
    }

    private fun showLeaderboard() {
        val translations = args.miniGameArgs.translations
        val scoreManager = args.scoreManager
        val component = component(ArcadePartyComponents.SCORE_BOARD)
        val scoreboard = component.scoreboardManager(args.miniGameArgs::translations)

        val objective = setupDynamicSidebarObjective(scoreboard, "game.${ApConstants.ID}.title")

        // header
        val round = FixedFormat(
            Component.literal(scoreManager.round.toString()).withStyle(ChatFormatting.YELLOW)
        )

        objective.createText(translations.translateText("ap2.prepare.round")
            .withStyle(ChatFormatting.GREEN)
        ).setNumberFormat(round)

        if (args.playerManager.isFinale) {
            addFinalistsToScoreboard(objective)
        } else {
            addPlayerScoresToScoreboard(objective)
        }

        // footer
        if (args.playerManager.isFinale) {
            objective.createText({ player ->
                val translation = if (args.playerManager.isParticipating(player))
                    "ap2.prepare.win_finale"
                else
                    "ap2.prepare.spectating"

                translations.translateText(player, translation).withStyle(ChatFormatting.AQUA)
            }, ScoreboardLayout.BOTTOM)
        } else {
            val requiredScore = FormatWrapper.styled(scoreManager.targetScore).formatted(ChatFormatting.YELLOW)
            val taskMsg = translations.translateText("ap2.prepare.score_required", requiredScore)
            objective.createText(taskMsg.withStyle(ChatFormatting.AQUA), ScoreboardLayout.BOTTOM)
        }

        objective.createNewline(ScoreboardLayout.BOTTOM)

        // display objective for all players
        for (player in PlayerLookup.all(args.miniGameArgs.server)) {
            objective.add(player)
        }
    }

    private fun addFinalistsToScoreboard(objective: DynamicScoreboardObjective) {
        val finalists = args.scoreManager.finalists
        val translations = args.miniGameArgs.translations

        objective.createNewline(ScoreboardLayout.TOP)

        objective.createText(
            translations.translateText("ap2.finale").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
        )

        val separator = Component.literal(ApConstants.SCOREBOARD_SEPARATOR_SM)
            .withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.STRIKETHROUGH)

        objective.createText(separator)

        for (finalist in finalists) {
            objective.createText(
                Component.literal("• ${finalist.scoreboardName}").withStyle(ChatFormatting.GREEN)
            )
        }
    }

    private fun addPlayerScoresToScoreboard(objective: DynamicScoreboardObjective) {
        val translations: Translations = args.miniGameArgs.translations
        val scoreManager: ScoreManager = args.scoreManager

        if (scoreManager.hasScores()) {
            objective.createNewline(ScoreboardLayout.TOP)

            objective.createText(
                translations.translateText("ap2.score").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
            )

            val separator = Component.literal(ApConstants.SCOREBOARD_SEPARATOR_SM)
                .withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.STRIKETHROUGH)

            objective.createText(separator)
        }

        // top 5 scores
        var i = 0

        for ((ref, rank) in scoreManager.iterateRankedScores()) {
            if (i++ >= 5) continue

            val score = scoreManager.getScore(ref)

            objective.setScore(ref.name, score)

            objective.setDisplayName(
                ref.name,
                Component.literal("#$rank ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(ref.name).withStyle(ChatFormatting.GREEN))
            )
        }
    }

    private fun displayGameQueue() {
        val gameQueue = map!!.getProperty<JSONObject?>("game-queue") ?: return

        val pos = MapUtil.readVec3d(gameQueue.getJSONArray("pos"))
        val height = max(1.0, gameQueue.getDouble("height"))
        val yaw = Math.toRadians(gameQueue.optDouble("yaw", 0.0) + 90f)

        val scene = Scene(MixedMountContext(world, dynamicEntityManager))

        val translations = args.miniGameArgs.translations

        removeGameQueue()

        gameQueueDisplay = Object3d(scene)
        gameQueueDisplay!!.position.set(pos.x(), pos.y(), pos.z())
        gameQueueDisplay!!.rotation.setAngleAxis(yaw, Vector3d(0.0, 1.0, 0.0))

        var offsetY = 0.0
        val textHeight = 0.25

        if (!args.playerManager.isFinale) {
            offsetY = addUpcomingGames(height, translations, offsetY, textHeight)
        }

        if (miniGame != null) {
            val obj = TranslatedTextDisplayObject(scene, translations)
            val currentTitle = translations.translateText(miniGame!!.titleKey).withStyle(ChatFormatting.AQUA)

            obj.controller().configure { controller ->
                controller.text = { lang: String ->
                    Component.literal("→ ")
                        .withStyle(ChatFormatting.YELLOW)
                        .append(currentTitle.translateTo(lang))
                }

                controller.displayFlags = Display.TextDisplay.FLAG_USE_DEFAULT_BACKGROUND
            }

            obj.position.set(0.0, offsetY, 0.0)

            offsetY += textHeight

            gameQueueDisplay!!.addChild(obj)
        }

        val title = TranslatedTextDisplayObject(scene, translations)

        title.controller().configure { controller ->
            controller.text = translations.translateText("ap2.prepare.game_queue")
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.UNDERLINE, ChatFormatting.BOLD)

            controller.displayFlags = Display.TextDisplay.FLAG_USE_DEFAULT_BACKGROUND
        }

        title.position.set(0.0, offsetY, 0.0)

        gameQueueDisplay!!.addChild(title)

        scene.add(gameQueueDisplay)
    }

    private fun removeGameQueue() {
        if (gameQueueDisplay != null) {
            gameQueueDisplay!!.detach()
        }
    }

    private fun addUpcomingGames(
        height: Double,
        translations: Translations,
        offsetY: Double,
        textHeight: Double
    ): Double {
        var offsetY = offsetY
        var preview: MutableList<GameQueue.Entry> = args.gameQueue.preview()

        val reservedSpace = if (miniGame != null) 2 else 1
        val amount = Math.clamp((floor(height / textHeight).toInt() - reservedSpace).toLong(), 0, preview.size)
        preview = preview.subList(0, amount)

        preview.reverse()

        for (entry in preview) {
            val obj = TranslatedTextDisplayObject(gameQueueDisplay!!.getScene(), translations)

            val color = when (entry.type) {
                GameQueue.Type.REGULAR -> ChatFormatting.GREEN
                GameQueue.Type.VOTED -> ChatFormatting.GOLD
                GameQueue.Type.PRIORITY -> ChatFormatting.LIGHT_PURPLE
            }

            val mayPossiblyNotBePlayed = !entry.game.canBePlayed(this)

            obj.controller().configure { controller ->
                val text = translations.translateText(entry.game.titleKey).withStyle(color)
                if (mayPossiblyNotBePlayed) {
                    controller.text = { lang ->
                        Component.literal("⏳ ")
                            .withStyle(ChatFormatting.WHITE)
                            .append(text.translateTo(lang).withStyle(ChatFormatting.ITALIC))
                    }
                } else {
                    controller.text = text
                }

                controller.displayFlags = Display.TextDisplay.FLAG_USE_DEFAULT_BACKGROUND
            }

            obj.position.set(0.0, offsetY, 0.0)

            offsetY += textHeight

            gameQueueDisplay!!.addChild(obj)
        }
        return offsetY
    }

    private fun tick(task: RunningTask) {
        val t = time++

        if (isSkip || timedLogic(t)) {
            task.cancel()
            bossBarTimer!!.stop()
        }
    }

    private fun timedLogic(t: Int): Boolean {
        if (gameConditionsNoLongerMatch()) {
            announceInvalidGame()
            reset()
            return true
        }

        if (gameForced) {
            if (t == 0) {
                announceNextGame()
            }
        } else {
            if (t == GAME_ANNOUNCE_DELAY - 40) {
                // "The next game will be %s"
                args.miniGameArgs.translations.translateText("ap2.prepare.next_game")
                    .withStyle(ChatFormatting.GRAY)
                    .sendTo(PlayerLookup.all(server))
            } else if (t == GAME_ANNOUNCE_DELAY) {
                announceNextGame()
            }
        }

        return t == PREPARATION_TIME
    }

    private fun startTimer(): BossBarTimer {
        val bossBars: BossBarComponent = component(BuiltinComponents.BOSS_BAR)
        val scheduler = component(BuiltinComponents.SCHEDULER).scheduler()

        val translations = args.miniGameArgs.translations

        val label = translations.translateText("ap2.prepare.next_game_title")

        val bossBarTimer = BossBarTimer.builder(translations, label)
            .withIdentifier(ApConstants.identifier("prepare"))
            .withDurationTicks(PREPARATION_TIME)
            .build()

        bossBarTimer.addPlayers(PlayerLookup.all(server))

        bossBarTimer.start(bossBars, scheduler)

        bossBars.showOnJoin(bossBarTimer.bossBar)

        this.bossBarTimer = bossBarTimer

        return bossBarTimer
    }

    private fun onTimerEnded() {
        if (miniGame == null) {
            prepareNextMiniGame()
            return
        }

        if (whenTasksDone == null) {
            startGame()
            return
        }

        whenTasksDone!!.thenRun(::startGame)
    }

    private fun startGame() {
        val miniGame = requireNotNull(miniGame) { "Mini-Game is not set" }

        val activity = MiniGameActivity(miniGame, args)

        switchActivity(activity)
    }

    private fun beginWinSequence() {
        val activity = WinActivity(args)

        switchActivity(activity)
    }

    private fun switchActivity(activity: Activity) {
        if (animatedTitle != null) {
            animatedTitle!!.stop()
        }

        if (song != null) {
            song!!.stop()
        }

        try {
            args.activitySwitcher.switchTo(activity)
        } catch (t: Throwable) {
            args.miniGameArgs.logger.error("Failed to switch activity", t)
        }
    }

    /**
     * Picks the next game.
     */
    private fun pickNextGame(): MiniGame {
        val queue: GameQueue = args.gameQueue

        if (gameForced) {
            return requireNotNull(queue.pollNextGame()) { "Could not determine next game" }
        }

        val maxTries = args.miniGameArgs.miniGames.getGames().size  // cycle through every registered game once

        repeat(maxTries) {
            val game = requireNotNull(queue.pollNextGame()) { "Next game from queue is null" }

            if (args.playerManager.isFinale && !game.canBeFinale(this)) return@repeat

            if (game.canBePlayed(this)) {
                return game
            }
        }

        throw IllegalStateException("No game was found that can be played")
    }

    private fun forceGame(miniGame: MiniGame) {
        if (this.miniGame === miniGame) return

        val queue: GameQueue = args.gameQueue

        if (this.miniGame != null) {
            queue.shiftGame(this.miniGame)
        }

        queue.shiftGame(miniGame)

        reset()

        gameForced = true
    }

    private fun reset() {
        taskHandle?.cancel()
        bossBarTimer?.stop()

        this.time = 0
        this.miniGame = null
        this.gameForced = false
    }

    private fun announceNextGame() {
        val container: ApMiniGameArgs = args.miniGameArgs
        val translations: Translations = container.translations
        val logger: Logger = container.logger
        val scheduler: TaskScheduler? = component(BuiltinComponents.SCHEDULER).scheduler()
        val dataManager: DataManager = container.dataManager

        val nextGameSong = this.nextGameSong

        if (nextGameSong == null) {
            logger.warn("Sound {} wasn't found, fallback to default sound", GAME_SONG_ID)
        }

        if (animatedTitle != null) {
            animatedTitle!!.stop()
        }

        animatedTitle = AnimatedTitle()

        val playedNextMsg = translations.translateText("ap2.prepare.will_be_played_next")
            .withStyle(ChatFormatting.GREEN)

        val separator = Component.literal(ApConstants.SEPARATOR)
            .withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.STRIKETHROUGH, ChatFormatting.BOLD)

        val author = dataManager.string(miniGame!!.author)

        val players = PlayerLookup.all(server)

        for (player in players) {
            player.sendSystemMessage(separator)

            val gameTitle = translations.translateText(player, miniGame!!.titleKey)
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)

            player.sendSystemMessage(gameTitle)

            val descriptionKey = miniGame!!.descriptionKey
            val descArgs = miniGame!!.descriptionArguments

            val description = translations.translateText(player, descriptionKey, *descArgs)
                .withStyle(ChatFormatting.GREEN)

            player.sendSystemMessage(description)

            val createdBy = translations.translateText(
                player, "ap2.prepare.created_by",
                FormatWrapper.styled(author, ChatFormatting.YELLOW)
            ).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)

            player.sendSystemMessage(Component.literal(""))
            player.sendSystemMessage(createdBy)

            player.sendSystemMessage(separator)

            animatedTitle!!.add(NextGameTitleAnimation(player, gameTitle, playedNextMsg.translateFor(player)))

            if (nextGameSong == null) {
                player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 1f)
            }
        }

        animatedTitle!!.start(scheduler, 2)

        if (nextGameSong != null && !players.isEmpty()) {
            this.song = MusicHelper.playSong(nextGameSong, 0.4f, players, server, args.sharedSongCache, logger)
        }
    }

    /**
     * Check if the game conditions no longer match.
     * @return Whether the game conditions still match.
     */
    private fun gameConditionsNoLongerMatch(): Boolean {
        if (miniGame == null) {
            // game wasn't picked yet
            return false
        }

        return !miniGame!!.canBePlayed(this)
    }

    /**
     * Announces that the current game is no longer valid and that a new game will be picked.
     */
    private fun announceInvalidGame() {
        val translations: Translations = args.miniGameArgs.translations

        val gameTitle = translations.translateText(miniGame!!.titleKey).withStyle(ChatFormatting.YELLOW)
        val msg =
            translations.translateText("ap2.prepare.game_cannot_be_played", gameTitle).withStyle(ChatFormatting.RED)

        msg.acceptEach(PlayerLookup.all(server)) { player, text ->
            player.sendSystemMessage(text)
            player.playNotifySound(SoundEvents.WITHER_HURT, SoundSource.PLAYERS, 0.4f, 0.9f)
        }
    }

    private fun setupSettingsMenu(hooks: HookRegistrar) {
        val settingsMenu = SettingsMenu(args.miniGameArgs.translations, args.colorPreferences)

        settingsMenu.init(hooks)
        settingsMenu.giveItems(args.playerManager)
    }

    private fun setupAdminItems(hooks: HookRegistrar) {
        val server = getServer()

        for (player in PlayerLookup.all(server)) {
            giveAdminItems(player)
        }

        PlayerInteractionHooks.USE_ITEM.registerWith(hooks) { player, _, hand ->
            if (player !is ServerPlayer || !Commands.LEVEL_GAMEMASTERS.check(server.getProfilePermissions(player.nameAndId()))) {
                return@registerWith InteractionResult.PASS
            }

            val stack = player.getItemInHand(hand)

            when {
                stack.isOf(Items.TOTEM_OF_UNDYING) -> {
                    openGamePicker(player)

                    InteractionResult.SUCCESS_SERVER
                }

                stack.isOf(Items.EMERALD_BLOCK) -> {
                    isSkip = true
                    player.sendSystemMessage(Component.literal("Skipped the preparation phase"))

                    InteractionResult.SUCCESS_SERVER
                }

                stack.isOf(Items.HEART_OF_THE_SEA) -> {
                    openMapPicker(player)

                    InteractionResult.SUCCESS_SERVER
                }

                else -> {
                    InteractionResult.PASS
                }
            }
        }

        PlayerConnectionHooks.JOIN.registerWith(hooks) { player -> giveAdminItems(player) }

        val container: ApMiniGameArgs = args.miniGameArgs
        val translations: Translations = container.translations
        val dataManager: DataManager = container.dataManager

        gameChooser = OptionChooser<MiniGame>(
            ApConstants.identifier("force_game"), translations,
            { _ -> Component.literal("Force Game") },
            { player, game -> IconMaker.createIcon(game, player, translations) },
            { player, game -> translations.translate(player, game.titleKey) },
            { game, player ->
                forceGame(game)
                player.sendSystemMessage(Component.literal("Forcing mini-game \"${game.id}\""))
            }
        ).also { it.init(hooks) }

        mapChooser = OptionChooser<GameMap>(
            ApConstants.identifier("force_map"), translations,
            { _ -> Component.literal("Force Map") },
            { player, map -> IconMaker.createIcon(map, player, translations, dataManager) },
            { player, map -> map.getName(translations.getLanguage(player)) },
            { gameMap, player ->
                val mapId = gameMap.descriptor.identifier
                args.miniGameArgs.mapFacade.forceMap(mapId)
                player.sendSystemMessage(Component.literal("Next map will be \"$mapId\""))
            }
        ).also { it.init(hooks) }
    }

    private fun giveAdminItems(player: ServerPlayer) {
        val server = getServer()

        if (!Commands.LEVEL_GAMEMASTERS.check(server.getProfilePermissions(player.nameAndId()))) return

        player.inventory.setItem(0, ItemStack(Items.TOTEM_OF_UNDYING).apply {
            val label = Component.literal("Select Game")
                .withStyle { it.withItalic(false).applyFormat(ChatFormatting.YELLOW) }

            set(DataComponents.ITEM_NAME, label)
        })

        player.inventory.setItem(1, ItemStack(Items.EMERALD_BLOCK).apply {
            val label = Component.literal("Skip Preparation")
                .withStyle { it.withItalic(false).applyFormat(ChatFormatting.GREEN) }

            set(DataComponents.CUSTOM_NAME, label)
        })

        if (miniGame != null && miniGame!!.usesMaps) {
            player.inventory.setItem(8, ItemStack(Items.HEART_OF_THE_SEA).apply {
                val label = Component.literal("Select Map")
                    .withStyle { it.withItalic(false).applyFormat(ChatFormatting.YELLOW) }

                set(DataComponents.CUSTOM_NAME, label)
            })
        } else {
            player.inventory.setItem(8, ItemStack.EMPTY)
        }
    }

    private fun openGamePicker(player: ServerPlayer) {
        val games = args.miniGameArgs.miniGames.games.toList()

        gameChooser?.open(player, games)
    }

    private fun openMapPicker(player: ServerPlayer) {
        if (miniGame == null) {
            player.sendSystemMessage(Component.literal("No maps found").withStyle(ChatFormatting.RED))
            return
        }

        args.miniGameArgs.mapFacade.getMaps(miniGame!!.id).thenAccept { maps ->
            mapChooser?.open(player, maps)
        }
    }

    override fun getParticipants(): Set<ServerPlayer> =
        args.playerManager.asSet

    data class SetupResult(val world: ServerLevel, val map: GameMap)

    companion object {

        fun setupMap(miniGameArgs: ApMiniGameArgs): CompletableFuture<SetupResult> {
            val prefix = ApConstants.identifier("preparation")

            return miniGameArgs.mapFacade
                .findMapIdByPrefix(prefix)
                .thenApply { mapId ->
                    checkNotNull(mapId.orElse(null)) {
                        "No map found for prefix $prefix"
                    }
                }
                .thenCompose { mapId ->
                    miniGameArgs.mapFacade.changeMap(mapId, WorldOptions.REUSABLE)
                        .thenCompose { world ->
                            miniGameArgs.mapFacade.getMap(mapId).thenApply { map ->
                                SetupResult(
                                    world,
                                    map.orElseThrow { IllegalStateException("Map $mapId not found") }
                                )
                            }
                        }
                }
        }
    }
}
