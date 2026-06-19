package work.lclpnet.ap2.game.speed_builders

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.ChatFormatting
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.BossEvent
import net.minecraft.world.entity.monster.breeze.Breeze
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.BreezeWindCharge
import net.minecraft.world.level.gamerules.GameRules
import net.minecraft.world.phys.Vec3
import net.minecraft.world.scores.PlayerTeam
import net.minecraft.world.scores.Team
import work.lclpnet.ap2.ext.*
import work.lclpnet.ap2.ext.mc.playNotifySound
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.base.EliminationGameInstance
import work.lclpnet.ap2.game.player.Participants
import work.lclpnet.ap2.game.speed_builders.data.SbIsland
import work.lclpnet.ap2.game.speed_builders.data.SbModule
import work.lclpnet.ap2.game.speed_builders.util.*
import work.lclpnet.ap2.game.util.createTimer
import work.lclpnet.ap2.game.util.useAnnouncer
import work.lclpnet.ap2.game.util.useSurvivalMode
import work.lclpnet.ap2.impl.util.ParticleHelper
import work.lclpnet.ap2.util.scoreboard.CustomScoreboardManager
import work.lclpnet.game.map.GameMap
import work.lclpnet.game.util.BossBarTimer
import work.lclpnet.kibu.access.VelocityModifier
import work.lclpnet.kibu.behaviour.level.ServerLevelBehaviour
import work.lclpnet.kibu.hook.entity.ProjectileHooks
import work.lclpnet.kibu.scheduler.Ticks
import work.lclpnet.kibu.title.Title
import java.util.*
import kotlin.time.Duration.Companion.seconds

private val LOOK_DURATION = 8.seconds
const val FAST_MODE_MIN_PLAYERS = 6
private val JUDGE_DURATION = 5.seconds
private val JUDGE_ANNOUNCEMENT_DELAY = 3.seconds
private val DESTROY_DELAY_TICKS = Ticks.seconds(4)

class SpeedBuildersInstance(
    gameHandle: MiniGameHandle,
    level: ServerLevel,
    map: GameMap,
    val setup: SbSetup,
    val islands: Map<UUID, SbIsland>,
) : EliminationGameInstance(gameHandle, level, map) {

    private val items = SbItems()
    private val announcer = useAnnouncer()
    private lateinit var destruction: SbDestruction
    private lateinit var manager: SbManager
    private lateinit var aelosId: UUID
    private var islandToDestroy: SbIsland? = null
    private var playerToEliminate: UUID? = null
    private var timer: BossBarTimer? = null
    private var timerTransaction = 0

    init {
        useSurvivalMode()
        disableTeleportEliminated()
    }

    override fun prepare() {
        val aelosId = setup.getAelosId()

        val fastMode = gameHandle.participants.count() >= FAST_MODE_MIN_PLAYERS
        val random = Random()

        manager = SbManager(
            islands,
            setup.getModules(),
            gameHandle,
            level,
            random,
            fastMode,
            this::allPlayersCompleted,
            this::onLastPlayerRemaining
        )

        destruction = SbDestruction(level, random, aelosId)
        level.gameRules.set(GameRules.BLOCK_DROPS, true, server)

        setupGameRules()

        ServerLevelBehaviour.setFluidTicksEnabled(level, false)

        manager.eachIsland(SbIsland::teleport)

        val scoreboardManager: CustomScoreboardManager = gameHandle.scoreboardManager
        val team: PlayerTeam = scoreboardManager.createTeam("team")
        team.collisionRule = Team.CollisionRule.NEVER
        manager.team = team

        for (player in gameHandle.participants) {
            player.abilities.mayfly = true
            player.abilities.flying = true
            player.onUpdateAbilities()
            scoreboardManager.joinTeam(player, team)
        }

        if (fastMode) {
            translate("fast_mode")
                .withStyle(ChatFormatting.GOLD)
                .sendTo(allPlayers())
        }

        gameHandle.rootScheduler.interval(1) { ->
            manager.tick()
        }
    }

    override fun go() {
        val config = SbConfiguration(gameHandle, manager, items)
        config.configureProtection()
        config.registerHooks()

        ProjectileHooks.HIT_BLOCK.registerWith(hooks) { projectile, _ ->
            onHitBlock(projectile)
        }

        nextRound()
    }

    override fun onEliminated(player: ServerPlayer) {
        putScoreDetail(player, false)
    }

    override fun participantRemoved(player: ServerPlayer) {
        val participants: Participants = gameHandle.participants

        if (participants.count() == 1) {
            putScoreDetail(player, false)
            participants.stream().findAny().ifPresent { winner ->
                putScoreDetail(winner, true)
            }
        }

        super.participantRemoved(player)
    }

    private fun setupGameRules() {
        commons().gameRuleBuilder()
            .set(GameRules.RANDOM_TICK_SPEED, 0)
    }

    private fun nextRound() {
        val module: SbModule = manager.nextModule()
        manager.reset()
        manager.setModule(module)
        items.setModule(module)

        announcer.announceSubtitle("look")

        val label = translate("prepare_label")

        timer = createTimer(label, LOOK_DURATION, BossEvent.BossBarColor.YELLOW)

        val transaction = timerTransaction

        timer!!.whenDone {
            if (this.timerTransaction == transaction) {
                startBuilding()
            }
        }
    }

    private fun startBuilding() {
        announcer.announceSubtitle("copy")

        val entities = manager.getPreviewEntities()

        manager.clearIslands()
        manager.buildingPhase = true
        items.giveBuildingMaterials(gameHandle.participants, entities)

        val label = translate("label")

        timer = createTimer(label, manager.getBuildingDuration())

        val transaction = timerTransaction

        timer!!.whenDone {
            if (this.timerTransaction == transaction) {
                onRoundOver()
            }
        }
    }

    private fun onRoundOver() {
        if (manager.allIslandsComplete()) {
            allPlayersCompleted()
            return
        }

        beginJudgement("time_up")
    }

    private fun beginJudgement(titleKey: String) {
        manager.resetSuccessiveCompletion()

        onLeaveBuildingPhase()

        announcer.withTimes(5, 50, 0)
            .announce(titleKey, null)


        runAfter(2.seconds) {
            announcer.silent()
                .withTimes(0, 35, 5)
                .announce(titleKey, "grade")
        }

        runAfter(JUDGE_DURATION) {
            announceJudgementDone()
        }
    }

    private fun onLeaveBuildingPhase() {
        for (player in gameHandle.participants) {
            player.inventory.clearContent()
        }

        manager.buildingPhase = false
    }

    private fun onLastPlayerRemaining() {
        timerTransaction++
        timer?.stop()

        beginJudgement("round_over")
    }

    private fun announceJudgementDone() {
        announcer.announceSubtitle("judgement")

        runAfter(JUDGE_ANNOUNCEMENT_DELAY) {
            announceJudgement()
        }
    }

    private fun announceJudgement() {
        val worst = manager.getWorstPlayer() ?: run {
            winManager.complete()
            return
        }

        val title = Component.literal(worst.scoreboardName).withStyle(ChatFormatting.AQUA)
        val subtitle = translate("will_eliminate")
            .withStyle(ChatFormatting.DARK_GREEN)


        for (player in PlayerLookup.all(server)) {
            Title.get(player).title(title, subtitle.translateFor(player), 5, 50, 5)
            player.playNotifySound(SoundEvents.BREEZE_HURT, SoundSource.PLAYERS, 1f, 0.5f)
        }

        manager.getIsland(worst)?.let { destruction.setAelosLookingTowards(it) }

        gameHandle.scheduler.timeout(DESTROY_DELAY_TICKS) { ->
            fireChargeTowardsPlayerIsland(worst.uuid)
        }
    }

    private fun fireChargeTowardsPlayerIsland(worstUuid: UUID) {
        val worst = gameHandle.server.playerList.getPlayer(worstUuid)

        if (worst == null) {
            nextRoundOrGameOver()
            return
        }

        val island = manager.getIsland(worst)

        if (island == null) {
            eliminate(worst)
            nextRoundOrGameOver()
            return
        }

        islandToDestroy = island
        playerToEliminate = worstUuid

        val charge = destruction.fireProjectile(island)

        gameHandle.scheduler.interval(1) { task ->
            if (!charge.isAlive) {
                task.cancel()
            } else {
                ParticleHelper.spawnForceParticle(ParticleTypes.FIREWORK, charge.x, charge.y, charge.z, 50, 0.0, 0.0, 0.0, 0.25, level.players())
            }
        }

        gameHandle.scheduler.timeout(Ticks.seconds(10)) { ->
            if (charge.isAlive) charge.discard()

            destroyIsland(null)
        }
    }

    private fun putScoreDetail(player: ServerPlayer, winner: Boolean) {
        val completed = manager.getRoundsCompleted(player, winner)
        val detail = translate("survived", completed)

        data.add(player, detail)
    }

    private fun onHitBlock(projectile: Projectile) {
        if (projectile !is BreezeWindCharge || islandToDestroy == null) return

        destroyIsland(projectile)
    }

    private fun destroyIsland(projectile: Projectile?) {
        if (islandToDestroy == null) return  // the island was already destroyed

        val impactPos: Vec3
        val velocity: Vec3

        if (projectile != null) {
            impactPos = projectile.position()
            velocity = projectile.deltaMovement.normalize()
        } else {
            impactPos = islandToDestroy!!.getCenter()
            val entity = level.getEntity(aelosId)

            velocity = if (entity is Breeze) {
                impactPos.subtract(getChargePos(entity)).normalize()
            } else {
                impactPos.normalize()
            }
        }

        destruction.destroyIsland(islandToDestroy!!, impactPos, velocity)

        islandToDestroy = null

        val playerManager = gameHandle.server.playerList
        val player = playerManager.getPlayer(playerToEliminate!!)

        if (player == null) {
            nextRoundOrGameOver()
            return
        }

        player.abilities.flying = false
        player.abilities.mayfly = false
        player.onUpdateAbilities()

        VelocityModifier.setVelocity(player, velocity.add(0.0, 1.5, 0.0).normalize().scale(3.0))

        runAfter(2.seconds) {
            val futurePlayer = playerManager.getPlayer(playerToEliminate!!)

            if (futurePlayer != null) {
                eliminate(futurePlayer)
            }

            nextRoundOrGameOver()
        }
    }

    private fun nextRoundOrGameOver() {
        if (winManager.gameOver) return

        islandToDestroy = null
        playerToEliminate = null

        if (gameHandle.participants.count() > 1) {
            manager.incrementRound()
            nextRound()
            return
        }

        val it = gameHandle.participants.iterator()

        if (!it.hasNext()) {
            winManager.cancel()
            return
        }

        val winner = it.next()
        putScoreDetail(winner, true)
        winManager.complete()
    }

    private fun allPlayersCompleted() {
        manager.incrementSuccessiveCompletion()

        timerTransaction++

        timer?.stop()

        onLeaveBuildingPhase()

        announcer
            .withSound(SoundEvents.BREEZE_IDLE_AIR, SoundSource.HOSTILE, 1f, 1.2f)
            .announceSubtitle("impressed")

        runAfter(3.seconds) {
            nextRoundOrGameOver()
        }
    }
}
