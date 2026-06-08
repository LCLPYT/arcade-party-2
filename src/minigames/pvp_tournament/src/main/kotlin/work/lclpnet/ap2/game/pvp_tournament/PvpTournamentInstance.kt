package work.lclpnet.ap2.game.pvp_tournament

import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import net.minecraft.ChatFormatting
import net.minecraft.core.particles.ItemParticleOption
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentContents
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Avatar
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.Mannequin
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemStackTemplate
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameType
import work.lclpnet.ap2.api.map.MapBootstrap
import work.lclpnet.ap2.core.mixin.entity.MannequinAccessor
import work.lclpnet.ap2.ext.*
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.ext.mc.playNotifySound
import work.lclpnet.ap2.ext.mc.teleportTo
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.pvp_tournament.gen.Match
import work.lclpnet.ap2.game.pvp_tournament.util.*
import work.lclpnet.ap2.impl.game.FFAGameInstance
import work.lclpnet.ap2.impl.game.WinSequence
import work.lclpnet.ap2.impl.game.data.IntScoreDataContainer
import work.lclpnet.ap2.impl.game.data.Ordering
import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import work.lclpnet.ap2.impl.util.SoundHelper
import work.lclpnet.ap2.impl.util.movement.SimpleMovementBlocker
import work.lclpnet.ap2.util.PvpBehavior
import work.lclpnet.ap2.util.SubtitleCountdown
import work.lclpnet.combatctl.api.CombatControl
import work.lclpnet.gaco.core.api.EntityRef
import work.lclpnet.game.map.GameMap
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks
import work.lclpnet.kibu.hook.util.PlayerUtils
import work.lclpnet.kibu.title.Title
import work.lclpnet.kibu.translate.text.TranslatedText
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.seconds

const val DEBUG_FILL_WITH_NPC = false
val SUDDEN_DEATH_DELAY = 40.seconds
val MATCH_DRAW_DELAY = 100.seconds

enum class TournamentVariant {
    SINGLE_ELIMINATION,
    SWISS_STYLE,
}

class PvpTournamentInstance(gameHandle: MiniGameHandle) : FFAGameInstance(gameHandle), MapBootstrap {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val data = IntScoreDataContainer(PlayerRef::create, Ordering.ASCENDING, "")
    val matchInstances = MatchInstanceRegistry()
    val kitManager = MatchKitManager(getKits(gameHandle.server.registryAccess()))
    val visualizer = CanvasVisualizer(gameHandle, scope)

    val movementBlocker = SimpleMovementBlocker(gameHandle.scheduler).also {
        it.setModifySpeedAttribute(false)
        it.init(gameHandle.hooks)
    }

    val playerRefs = buildPlayerRefs()

    lateinit var pvp: PvpBehavior
    lateinit var tournamentResult: TournamentResult

    private fun buildPlayerRefs(): List<PlayerRef> {
        val refs = players().map { PlayerRef.create(it) }

        if (!DEBUG_FILL_WITH_NPC) return refs

        val targetPlayerCount = 12
        val extraPlayers = (targetPlayerCount - refs.size).coerceAtLeast(0)

        return refs + List(extraPlayers) { PlayerRef(UUID.randomUUID(), "NPC #${it + 1}") }
    }

    override fun getData() = data

    init {
        useSurvivalMode()
        gameHandle.whenDone { scope.cancel() }
    }

    override fun createWorldBootstrap(
        world: ServerLevel,
        map: GameMap
    ): CompletableFuture<Void> = scope.future {
        val playerSkins = visualizer.preloadPlayerSkins()

        val setup = TournamentSetup(logger, map, playerRefs) { path ->
            schematicBlocking(assetPath(path))
        }

        val result = setup.setup(TournamentVariant.SINGLE_ELIMINATION)

        tournamentResult = result

        val arenaPlacement = gameHandle.server.submit {
            setup.placeArenas(world, result.arenas.values)
        }

        playerSkins.joinAll()

        visualizer.updateCanvas(result.tournament)

        arenaPlacement.await()
    }

    override fun prepare() {
        visualizer.preventMovingOfFilledMaps()

        registerHooks()

        playerRefs.forEach { setupPlayerForNextMatch(it) }
        players().forEach { movementBlocker.disableMovement(it) }
    }

    override fun go() {
        pvp = PvpBehavior(gameHandle, level).also { it.configure() }

        players().forEach {
            // disallow pvp behavior by default, enable when match is started
            pvp.disallow(it)

            movementBlocker.enableMovement(it)
        }

        if (tournamentResult.tournament.finale.completed) {
            tournamentResult.tournament.finale.winner?.let {
                data.setScore(it, 1)
                winManager.complete()
            }
            return
        }

        val matches = tournamentResult.tournament.matches

        val minRound = matches.minOf { it.round }

        matches.filter { it.round == minRound }.forEach { startMatch(it) }

        onDeathOf<ServerPlayer> { player, source ->
            val data = matchInstanceOf(player)

            if (data != null) {
                val msg = getCustomDeathMessage(player, source)
                    ?: gameHandle.deathMessages.getDeathMessage(player, source)

                msg.sendTo(allPlayers())

                loseMatch(data, player)
            }

            makeSpectator(player)
        }

        onDeathOf<Mannequin> { npc, source ->
            val data = matchInstanceOf(npc)

            if (data != null) {
                getCustomDeathMessage(npc, source)?.sendTo(allPlayers())

                loseMatch(data, npc)
            }

            npc.discard()
        }

        registerDebugCommands()
    }

    private fun getCustomDeathMessage(victim: LivingEntity, source: DamageSource): TranslatedText? {
        val msg = victim.combatTracker.deathMessage
        val content: ComponentContents = msg.contents

        if (content !is TranslatableContents) return null

        val killer = source.entity

        if (killer !is LivingEntity) return null

        val mappedArgs = content.args.map { arg ->
            if (arg !is Component) return@map arg

            val styled = Component.literal(arg.string)
                .withStyle(ChatFormatting.YELLOW)

            if (victim.displayName.string == arg.string) {
                return@map styled
            }

            if (killer.displayName.string != arg.string) return@map arg

            Component.empty()
                .append(styled)
                .append(" (")
                .append(
                    Component.literal("%.1f ♥".format(killer.health / 2))
                        .withStyle(ChatFormatting.RED)
                )
                .append(")")
        }

        return gameHandle.deathMessages.root(
            content.key,
            *mappedArgs.toTypedArray()
        )
    }

    private fun registerHooks() {
        PlayerInteractionHooks.USE_ITEM.registerWith(hooks) { player, _, hand ->
            if (player !is ServerPlayer || !isParticipating(player)) {
                return@registerWith InteractionResult.FAIL
            }

            val instance = matchInstanceOf(player) ?: return@registerWith InteractionResult.FAIL.also {
                PlayerUtils.syncPlayerItems(player)
            }

            val stack = player.getItemInHand(hand)

            if (!instance.started) {
                PlayerUtils.syncPlayerItems(player)
                return@registerWith InteractionResult.FAIL
            }

            if (stack.isOf(Items.MUSHROOM_STEW)) {
                tryUseMushroomStew(player, stack)
            } else {
                InteractionResult.PASS
            }
        }
    }

    private fun tryUseMushroomStew(player: ServerPlayer, stack: ItemStack): InteractionResult {
        if (player.health >= player.maxHealth) return InteractionResult.PASS

        player.heal(4f)
        SoundHelper.playSoundAt(player, SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS, 0.5f, 1f)

        player.level().sendParticles(
            ItemParticleOption(ParticleTypes.ITEM, ItemStackTemplate.fromNonEmptyStack(stack)),
            player.x,
            player.y + 1,
            player.z,
            5,
            0.1,
            0.1,
            0.1,
            0.05
        )

        stack.shrink(1)

        return InteractionResult.SUCCESS_SERVER
    }

    private fun registerDebugCommands() {
        WinMatchCommand(
            win = { ctx, player, winner ->
                val inst = matchInstanceOf(player)

                if (inst == null) {
                    ctx.sendFailure(Component.literal("${player.scoreboardName} is not in a match currently"))
                    return@WinMatchCommand
                }

                if (winner != null && !inst.match.players.contains(winner)) {
                    ctx.sendFailure(Component.literal("Player ${winner.name} is not participating in the current match"))
                    return@WinMatchCommand
                }

                completeMatch(inst.match, winner)
            },
            participants = { player ->
                val inst = matchInstanceOf(player)

                inst?.match?.players?.map { it.name } ?: emptyList()
            },
            resolvePlayer = { matchOf, name ->
                val inst = matchInstanceOf(matchOf)

                inst?.match?.players?.find { it.name == name }
            }
        ).register(gameHandle.commands)
    }

    override fun participantRemoved(player: ServerPlayer) {
        super.participantRemoved(player)

        val data = matchInstanceOf(player)

        if (!winManager.isGameOver && data != null) {
            loseMatch(data, player)
        }
    }

    fun setupPlayerForNextMatch(ref: PlayerRef): Match? {
        val match = nextMatch(ref)

        if (match == null) {
            makeSpectator(ref)
            return null
        }

        val arena = tournamentResult.arenas[match] ?: error("No arena for match $match")
        val kit = kitManager[match]

        val data = initMatchInstance(match, arena, kit)

        val player = players().getParticipant(ref.uuid).orElse(null)

        if (player != null) {
            data.teleport(player)

            gameHandle.playerUtil.resetPlayer(player)

            kit.equip(player)

            CombatControl.get(server).setStyle(player, kit.combatStyle)

            player.setItemInHand(InteractionHand.OFF_HAND, visualizer.getStack())

            visualizer.add(player)

            data.playerUuids += player.uuid
        } else {
            val npc = Mannequin(EntityType.MANNEQUIN, level)
            npc.uuid = ref.uuid
            npc.customName = Component.literal(ref.name)
            npc.isCustomNameVisible = true

            @Suppress("KotlinConstantConditions")
            (npc as MannequinAccessor).invokeSetHideDescription(true)

            data.teleport(npc)

            level.addFreshEntity(npc)

            kit.equip(npc)

            data.npcs += EntityRef(npc)
        }

        return match
    }

    private fun makeSpectator(ref: PlayerRef) {
        val player = players().getParticipant(ref.uuid).orElse(null) ?: return

        makeSpectator(player)
    }

    private fun makeSpectator(player: ServerPlayer) {
        player.setGameMode(GameType.SPECTATOR)

        val nearestPlayer = players()
            .filter { !it.isSpectator && it.level() == player.level() }
            .minByOrNull { it.distanceToSqr(player) } ?: return

        player.teleportTo(nearestPlayer)
    }

    private fun nextMatch(ref: PlayerRef): Match? = tournamentResult.tournament.matches
        .filter { it.hasPlayer(ref) && !it.completed }
        .minByOrNull { it.round }

    private fun initMatchInstance(
        match: Match,
        arena: ArenaInstance,
        kit: Kit
    ): MatchInstance = matchInstances.getOrCreate(match) {
        MatchInstance(it, arena, kit, level, players())
    }

    fun matchInstanceOf(entity: Avatar) = matchInstances.findByParticipant(entity)

    fun startMatchWithCountdown(match: Match) {
        val inst = matchInstances[match]

        if (inst == null) {
            logger.debug("Cannot schedule match start as match instance does not exist: {}", match)
            return
        }

        logger.debug("Scheduler match start: {}", match)


        inst.players.forEach {
            inst.teleport(it)

            movementBlocker.disableMovement(it)
        }

        SubtitleCountdown(server, gameHandle.scheduler) {
            inst.players
        }.schedule(3.seconds) {
            inst.players.forEach {
                sendGo(it)
            }

            startMatch(match)
        }
    }

    fun startMatch(match: Match) {
        val data = matchInstances.markStarted(match)

        if (data == null) {
            logger.debug("Cannot start match (missing instance or already started): {}", match)
            return
        }

        logger.debug("Match between {} has been started", match.players.joinToString { it.name })

        data.participants.forEach {
            // tournament state is displayed on a map in the offhand, replace it with the offhand item of the kit
            it.setItemInHand(InteractionHand.OFF_HAND, data.kit[EquipmentSlot.OFFHAND])

            pvp.allow(it)

            if (it is ServerPlayer) {
                movementBlocker.enableMovement(it)
            }
        }

        scheduleSuddenDeath(match, data)
        scheduleDrawFallback(match, data)
    }

    private fun scheduleSuddenDeath(match: Match, data: MatchInstance) {
        data.tasks.add(runAfter(SUDDEN_DEATH_DELAY) {
            translate("game.ap2.pvp_tournament.sudden_death")
                .formatted(ChatFormatting.RED)
                .acceptEach(data.players) { player, msg ->
                    Title.get(player).title(Component.empty(), msg, 10, 40, 10)
                    player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.5f, 0.5f)
                }

            var damagePerSecond = 2f
            var timer = 0

            data.tasks.add(runEvery(1.seconds) {
                // if both participants would die at the same time though sudden death, end in draw
                if (data.participants.all { it.health <= damagePerSecond }) {
                    completeMatch(match, null)
                    return@runEvery
                }

                data.participants.forEach {
                    it.hurtServer(level, it.damageSources().magic(), damagePerSecond)
                }

                if (++timer % 10 == 0) {
                    damagePerSecond += 2f
                }
            })
        })
    }

    private fun scheduleDrawFallback(match: Match, data: MatchInstance) {
        // fallback, in case the players somehow manage to survive the sudden death
        data.tasks.add(runAfter(MATCH_DRAW_DELAY) {
            completeMatch(match, null)
        })
    }

    fun loseMatch(data: MatchInstance, loser: Avatar) {
        val ref = data.ref(loser) ?: return
        val winner = data.match.other(ref) ?: return

        completeMatch(data.match, winner)
    }

    fun completeMatch(match: Match, winner: PlayerRef?) {
        val inst = matchInstances.remove(match)

        if (inst == null) {
            if (winner == null) {
                // draw propagated from a child; still need to wake up parents
                checkParentMatchStatus(match)
                return
            }

            logger.debug("Cannot complete match as match instance does not exist: {}", match)
            return
        }

        if (winner != null) {
            logger.debug("Match {} completed with winner {}", match, winner)
        } else {
            logger.debug("Match {} completed as a draw", match)
        }

        finalizeMatchInstance(inst, match, winner)

        if (isGameComplete(match)) {
            finishGame(match, winner)
            return
        }

        announceMatchResult(inst, winner)

        if (winner == null) {
            checkParentMatchStatus(match)
        }

        scheduleMatchCleanup(inst, match)
    }

    private fun finalizeMatchInstance(inst: MatchInstance, match: Match, winner: PlayerRef?) {
        inst.tasks.forEach { it.cancel() }
        inst.tasks.clear()

        inst.participants.forEach { pvp.disallow(it) }

        match.complete(winner)

        visualizer.launchUpdate(tournamentResult.tournament)
    }

    private fun finishGame(match: Match, winner: PlayerRef?) {
        logger.debug("Game is now completed")

        if (winner != null) {
            data.setScore(winner, 1)

            match.other(winner)?.let {
                setPlacementLostInMatch(it, match)
            }
        }

        winManager.complete()
    }

    private fun announceMatchResult(inst: MatchInstance, winner: PlayerRef?) {
        inst.players.forEach { player ->
            if (winner?.uuid == player.uuid) {
                WinSequence.playWinSound(player)
            } else {
                WinSequence.playLoseSound(player)
            }

            val title = if (winner != null) {
                winner.getNameFor(player).copy().withStyle(ChatFormatting.AQUA)
            } else {
                translate("ap2.nobody")
                    .formatted(ChatFormatting.AQUA)
                    .translateFor(player)
            }

            Title.get(player).title(
                title,
                translate("game.ap2.pvp_tournament.won_match")
                    .formatted(ChatFormatting.DARK_GREEN)
                    .translateFor(player),
                5,
                100,
                5
            )
        }
    }

    private fun scheduleMatchCleanup(inst: MatchInstance, match: Match) {
        runAfter(5.seconds) {
            inst.npcs.mapNotNull { it.resolve() }.forEach { it.discard() }
            inst.npcs.clear()

            match.players.forEach { ref ->
                val nextMatch = setupPlayerForNextMatch(ref)

                if (nextMatch == null) {
                    setPlacementLostInMatch(ref, match)
                } else {
                    checkMatchStatus(nextMatch)
                }
            }
        }
    }

    private fun checkParentMatchStatus(match: Match) {
        match.winnerNext?.let { checkMatchStatus(it) }
        match.loserNext?.let { checkMatchStatus(it) }
    }

    private tailrec fun isGameComplete(match: Match): Boolean {
        // TODO respect swiss style tournament
        val next = match.winnerNext ?: return match.completed

        return isGameComplete(next)
    }

    private fun checkMatchStatus(match: Match) {
        logger.debug("Checking match status of {}", match)

        if (match.completed) {
            logger.debug("Match {} is already complete in data model, completing instance...", match)

            // the match could have been completed by a draw in one of the child matches
            completeMatch(match, match.winner)
            return
        }

        val inst = matchInstances[match]

        if (inst == null) {
            logger.debug("Nothing to do as match has no instance currently: {}", match)
            return
        }

        if (inst.participants.size >= 2 && !inst.started) {
            // if both players are present, start the match
            logger.debug("Match has sufficient players now: {}; starting it...", match)

            startMatchWithCountdown(match)
        }
    }

    fun setPlacementLostInMatch(ref: PlayerRef, match: Match) {
        val maxPlacement = tournamentResult.tournament.matches.maxOf { it.round } + 2

        data.setScore(ref, maxPlacement - match.round)
    }
}
