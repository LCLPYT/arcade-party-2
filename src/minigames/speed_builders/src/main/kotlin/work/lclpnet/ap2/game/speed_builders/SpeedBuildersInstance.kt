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
import work.lclpnet.ap2.api.base.Participants
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.api.map.MapBootstrap
import work.lclpnet.ap2.ext.mc.playNotifySound
import work.lclpnet.ap2.ext.runAfter
import work.lclpnet.ap2.ext.server
import work.lclpnet.ap2.ext.translate
import work.lclpnet.ap2.game.speed_builders.data.SbIsland
import work.lclpnet.ap2.game.speed_builders.data.SbModule
import work.lclpnet.ap2.game.speed_builders.util.*
import work.lclpnet.ap2.impl.game.Announcer
import work.lclpnet.ap2.impl.game.EliminationGameInstance
import work.lclpnet.ap2.impl.util.ParticleHelper
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager
import work.lclpnet.game.map.GameMap
import work.lclpnet.game.util.BossBarTimer
import work.lclpnet.kibu.access.VelocityModifier
import work.lclpnet.kibu.behaviour.level.ServerLevelBehaviour
import work.lclpnet.kibu.hook.entity.ProjectileHooks
import work.lclpnet.kibu.scheduler.Ticks
import work.lclpnet.kibu.title.Title
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.seconds

private const val LOOK_DURATION_SECONDS = 8
private val JUDGE_DURATION = 5.seconds
private val JUDGE_ANNOUNCEMENT_DELAY = 3.seconds
private val DESTROY_DELAY_TICKS = Ticks.seconds(4)

class SpeedBuildersInstance(gameHandle: MiniGameHandle) : EliminationGameInstance(gameHandle), MapBootstrap {

    private val random = Random()
    private val setup = SbSetup(random, gameHandle.logger)
    private val items = SbItems()
    private lateinit var destruction: SbDestruction
    private lateinit var manager: SbManager
    private var islandToDestroy: SbIsland? = null
    private var playerToEliminate: UUID? = null
    private lateinit var aelosId: UUID
    private var timer: BossBarTimer? = null
    private var timerTransaction = 0

    init {
        useSurvivalMode()
        disableTeleportEliminated()
    }

    override fun createWorldBootstrap(world: ServerLevel, map: GameMap): CompletableFuture<Void> {
        return setup.setup(map, world).thenRun {
            val participants: Participants = gameHandle.participants
            val islands = setup.createIslands(participants, world)

            aelosId = setup.getAelosId()
            manager = SbManager(islands, setup.getModules(), gameHandle, world, random, this::allPlayersCompleted)
            destruction = SbDestruction(world, random, aelosId)

            world.gameRules.set(GameRules.BLOCK_DROPS, true, gameHandle.server)
        }
    }

    override fun prepare() {
        setupGameRules()

        ServerLevelBehaviour.setFluidTicksEnabled(world, false)

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

        commons().announcer().announceSubtitle("game.ap2.speed_builders.look")

        val label = translate("game.ap2.speed_builders.prepare_label")

        timer = commons().createTimer(label, LOOK_DURATION_SECONDS, BossEvent.BossBarColor.YELLOW)

        val transaction = timerTransaction

        timer!!.whenDone {
            if (this.timerTransaction == transaction) {
                startBuilding()
            }
        }
    }

    private fun startBuilding() {
        commons().announcer().announceSubtitle("game.ap2.speed_builders.copy")

        val entities = manager.getPreviewEntities()

        manager.clearIslands()
        manager.buildingPhase = true
        items.giveBuildingMaterials(gameHandle.participants, entities)

        val label = translate("game.ap2.speed_builders.label")

        timer = commons().createTimer(label, manager.getBuildingDurationTicks())

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

        manager.resetSuccessiveCompletion()

        onLeaveBuildingPhase()

        val announcer: Announcer = commons().announcer()

        announcer.withTimes(5, 50, 0)
            .announce("game.ap2.speed_builders.time_up", null)


        runAfter(2.seconds) {
            announcer.silent()
                .withTimes(0, 35, 5)
                .announce("game.ap2.speed_builders.time_up", "game.ap2.speed_builders.grade")
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

    private fun announceJudgementDone() {
        commons().announcer().announceSubtitle("game.ap2.speed_builders.judgement")

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
        val subtitle = translate("game.ap2.speed_builders.will_eliminate")
            .formatted(ChatFormatting.DARK_GREEN)


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
                ParticleHelper.spawnForceParticle(ParticleTypes.FIREWORK, charge.x, charge.y, charge.z, 50, 0.0, 0.0, 0.0, 0.25, world.players())
            }
        }

        gameHandle.scheduler.timeout(Ticks.seconds(10)) { ->
            if (charge.isAlive) charge.discard()

            destroyIsland(null)
        }
    }

    private fun putScoreDetail(player: ServerPlayer, winner: Boolean) {
        val completed = manager.getRoundsCompleted(player, winner)
        val detail = translate("game.ap2.speed_builders.survived", completed)

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
            val entity = getWorld().getEntity(aelosId)

            velocity = if (entity is Breeze) {
                impactPos.subtract(getChargePos(entity)).normalize()
            } else {
                impactPos.normalize()
            }
        }

        destruction.destroyIsland(islandToDestroy!!, impactPos, velocity)

        islandToDestroy = null

        val playerManager = gameHandle.getServer().playerList
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
        if (winManager.isGameOver) return

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

        commons().announcer()
            .withSound(SoundEvents.BREEZE_IDLE_AIR, SoundSource.HOSTILE, 1f, 1.2f)
            .announceSubtitle("game.ap2.speed_builders.impressed")

        runAfter(3.seconds) {
            nextRoundOrGameOver()
        }
    }
}
