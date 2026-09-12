package work.lclpnet.ap2.capture_the_flag

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.entity.projectile.arrow.AbstractArrow
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.scores.Team.CollisionRule
import net.minecraft.world.scores.Team.Visibility
import work.lclpnet.ap2.api.stats.CommonStats.DamageDealt
import work.lclpnet.ap2.api.stats.CommonStats.Deaths
import work.lclpnet.ap2.api.stats.CommonStats.DistanceMoved
import work.lclpnet.ap2.api.stats.CommonStats.IntScore
import work.lclpnet.ap2.api.stats.CommonStats.KillDeathRatio
import work.lclpnet.ap2.api.stats.CommonStats.Kills
import work.lclpnet.ap2.capture_the_flag.flag.Flag
import work.lclpnet.ap2.capture_the_flag.flag.ItemFrameFlag
import work.lclpnet.ap2.core.hook.CobwebEntityInsideCallback
import work.lclpnet.ap2.core.hook.ProjectileShootCallback
import work.lclpnet.ap2.core.hook.SpectatePlayerCallback
import work.lclpnet.ap2.ext.allPlayers
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.ext.mc.setAttribute
import work.lclpnet.ap2.ext.mc.setBlocks
import work.lclpnet.ap2.ext.players
import work.lclpnet.ap2.ext.runEveryTick
import work.lclpnet.ap2.ext.trackDistanceMoved
import work.lclpnet.ap2.ext.translate
import work.lclpnet.ap2.ext.translations
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.base.TeamGameInstance
import work.lclpnet.ap2.game.data.IntScoreDataContainer
import work.lclpnet.ap2.game.team.DyeTeamKey
import work.lclpnet.ap2.game.team.Team
import work.lclpnet.ap2.game.team.TeamManager
import work.lclpnet.ap2.game.util.useOldCombat
import work.lclpnet.ap2.game.util.useSurvivalMode
import work.lclpnet.ap2.game.util.useTaskTimer
import work.lclpnet.ap2.game.util.useTeamStats
import work.lclpnet.ap2.impl.util.handler.VisualCooldown
import work.lclpnet.ap2.util.scoreboard.TranslatedScoreboardObjective
import work.lclpnet.ap2.util.scoreboard.setupTranslatedSidebarObjective
import work.lclpnet.game.impl.prot.ProtectionTypes
import work.lclpnet.game.map.GameMap
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks
import work.lclpnet.kibu.hook.util.PositionRotation
import work.lclpnet.kibu.scheduler.Ticks
import java.util.*
import kotlin.math.abs
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val CAPTURES_TO_WIN = 3
internal val ROUND_DURATION = 3.minutes + 20.seconds
private val RESPAWN_DELAY = Ticks.seconds(10)

private const val CROSSBOW_DAMAGE = 12.0
private const val CROSSBOW_ARROW_VELOCITY = 3.15
private const val COBWEB_DAMAGE = 1f

private const val SCOREBOARD_KEY = "game.ap2.capture_the_flag.captures"

private val FLAG_TYPES = mapOf(
    "item_frame" to ItemFrameFlag.FACTORY
)

class CaptureTheFlagInstance(
    gameHandle: MiniGameHandle,
    level: ServerLevel,
    map: GameMap,
    teamManager: TeamManager,
    val schema: CtfSchema,
) : TeamGameInstance(gameHandle, level, map, teamManager) {

    override val data = IntScoreDataContainer(this::createTeamReference)
    private val stats = CtfStats(useTeamStats(
        winManager, data, IntScore,
        teamStats = listOf(
            FlagsStolen,
            FlagsCaptured,
            FlagsRescued,
            Kills,
            Deaths,
            DamageDealt,
            ArrowsShot,
            ArrowsHit,
            ArrowAccuracy
        ),
        memberStats = listOf(
            FlagsStolen,
            FlagsCaptured,
            FlagsRescued,
            Kills,
            Deaths,
            KillDeathRatio,
            DamageDealt,
            DistanceMoved,
            ArrowsShot,
            ArrowsHit,
            ArrowAccuracy
        )
    ), teamManager, gameHandle.translations)
    private val respawnCooldown = VisualCooldown(gameHandle.scheduler)
    private val random = Random()
    private val kit = CtfKit(teamManager, level)
    private var started = false

    lateinit var teamInfo: List<CtfTeamInfo>
    private lateinit var flagManager: CtfFlagManager
    private lateinit var objective: TranslatedScoreboardObjective

    init {
        useOldCombat()
        useSurvivalMode()

        teamManager.setUseColorCodes(true)
    }

    override fun prepare() {
        teamInfo = setupTeams()

        flagManager = CtfFlagManager(gameHandle, level, teamManager, teamInfo, random, stats, translations, ::onCapture)
        flagManager.setup()

        teleportTeamsToSpawns()

        for (player in players()) {
            kit.equip(player)
        }

        setupScoreboard()
        balanceTeams()
    }

    fun setupTeams(): List<CtfTeamInfo> {
        val team1Key = map.properties.optString("team1Color")?.let { DyeTeamKey.byId(it) } ?: DyeTeamKey.RED
        val team2Key = map.properties.optString("team2Color")?.let { DyeTeamKey.byId(it) } ?: DyeTeamKey.BLUE

        require(team1Key != team2Key) { "Team colors cannot be the same" }

        val team1Info = CtfTeamInfo(
            spawn = schema.team1Spawn!!,
            flag = createFlag(schema.team1FlagPos!!),
            gate = schema.team1SpawnGate!!,
            key = team1Key
        )

        val team2Info = CtfTeamInfo(
            spawn = schema.team2Spawn!!,
            flag = createFlag(schema.team2FlagPos!!),
            gate = schema.team2SpawnGate!!,
            key = team2Key
        )

        teamManager.partitionIntoTeams(players(), setOf(team1Key, team2Key))

        teamManager.minecraftTeams.forEach { team ->
            team.isAllowFriendlyFire = false
            team.setSeeFriendlyInvisibles(true)
            team.collisionRule = CollisionRule.PUSH_OTHER_TEAMS
            team.nameTagVisibility = Visibility.HIDE_FOR_OTHER_TEAMS
        }

        return listOf(team1Info, team2Info)
    }

    private fun createFlag(pos: BlockPos): Flag {
        val type = map.properties.optString("flagType") ?: "item_frame"
        val factory = FLAG_TYPES[type] ?: throw IllegalStateException("Unknown flag type $type")

        return factory.create(level, pos)
    }

    override fun getSpawn(team: Team): PositionRotation? =
        teamInfo.find { teamManager.getTeam(it) === team }?.spawn

    override fun go() {
        for (info in teamInfo) {
            level.setBlocks(info.gate, Blocks.AIR)
        }

        respawnCooldown.init(gameHandle.hooks)
        respawnCooldown.setOnCooldownOver(::respawnPlayer)

        started = true

        configureProtection()
        registerHooks()

        runEveryTick {
            flagManager.tick()
        }

        translate("waypoint.enemy_flag")
            .withStyle(ChatFormatting.YELLOW)
            .sendTo(players())

        useTaskTimer(ROUND_DURATION).whenDone(::endGame)
    }

    private fun setupScoreboard() {
        objective = setupTranslatedSidebarObjective(gameHandle.scoreboardManager, SCOREBOARD_KEY)

        for (info in teamInfo) {
            val holder = info.key.id

            objective.setDisplayName(holder, info.key.getDisplayName(gameHandle.translations))
            objective.setScore(holder, 0)

            // seed the container so that a round without any capture still ranks both teams
            teamManager.getTeam(info)?.let { data.setScore(createTeamReference(it), 0) }
        }

        for (player in allPlayers()) {
            objective.add(player)
        }
    }

    private fun configureProtection() {
        gameHandle.protect { config ->
            ProtectionTypes.ALLOW_DAMAGE.allow(config) { entity, source ->
                when (entity) {
                    is ServerPlayer -> gameHandle.participants.isParticipating(entity) && isAllowedDamage(source)
                    is ItemFrame -> !entity.item.isEmpty && isFlagFrame(entity)
                            && (source.entity as? ServerPlayer).let { it != null && areOnOpposingTeams(entity, it) }
                    else -> false
                }
            }

            listOf(ProtectionTypes.ITEM_FRAME_REMOVE_ITEM, ProtectionTypes.ITEM_FRAME_ROTATE_ITEM).forEach { type ->
                type.allow(config) { player, itemFrame ->
                    isFlagFrame(itemFrame) && areOnOpposingTeams(itemFrame, player)
                }
            }

            // players should be able to recover the arrows they shot
            ProtectionTypes.PICKUP_PROJECTILE.allow(config)
            ProtectionTypes.PICKUP_ITEM.allow(config)
        }
    }

    private fun areOnOpposingTeams(x: Entity, y: Entity): Boolean =
        x.team != null && x.team == y.team

    private fun registerHooks() {
        val hooks = gameHandle.hooks

        SpectatePlayerCallback.HOOK.registerWith(hooks) { spectator, _ ->
            gameHandle.participants.isParticipating(spectator)
        }

        ServerLivingEntityHooks.ALLOW_DAMAGE.registerWith(hooks, ::onDamage)

        ProjectileShootCallback.HOOK.registerWith(hooks) { shooter, projectile ->
            if (shooter is ServerPlayer && projectile is AbstractArrow
                && gameHandle.participants.isParticipating(shooter)) {
                stats.arrowShot(shooter)
                projectile.setBaseDamage((CROSSBOW_DAMAGE - 0.5) / CROSSBOW_ARROW_VELOCITY)
            }
        }

        CobwebEntityInsideCallback.HOOK.registerWith(hooks) { entity, _ ->
            hurtInCobweb(entity)
            false
        }

        trackDistanceMoved(stats.players)
    }

    private fun isFlagFrame(itemFrame: ItemFrame) =
        teamInfo.any { (it.flag as? ItemFrameFlag)?.isFlagFrame(itemFrame) == true }

    private fun isAllowedDamage(source: DamageSource) =
        source.isOf(DamageTypes.ARROW) || source.isOf(DamageTypes.PLAYER_ATTACK)
                || source.isOf(DamageTypes.CACTUS)

    /**
     * Makes cobwebs act like barbed wire.
     */
    private fun hurtInCobweb(entity: Entity) {
        if (entity !is ServerPlayer || !gameHandle.participants.isParticipating(entity)) return

        val movement = if (entity.isClientAuthoritative) entity.knownMovement
            else entity.oldPosition().subtract(entity.position())

        if (abs(movement.x) < 0.003 && abs(movement.z) < 0.003) return

        entity.hurtServer(level, level.damageSources().cactus(), COBWEB_DAMAGE)
    }

    private fun onDamage(entity: LivingEntity, source: DamageSource, amount: Float): Boolean {
        if (winManager.gameOver) return false

        val player = entity as? ServerPlayer ?: return false

        if (!gameHandle.participants.isParticipating(player)) return false

        val attacker = source.entity as? ServerPlayer

        if (attacker != null && attacker !== player && teamManager.areTeamMates(attacker, player)) return false

        if (player.health - amount <= 0) {
            trackDamage(player, source, player.health)
            onLethalDamage(player, source, amount)
            return false
        }

        trackDamage(player, source, amount)

        return true
    }

    private fun trackDamage(victim: ServerPlayer, source: DamageSource, applied: Float) {
        val attacker = source.entity as? ServerPlayer ?: return

        if (attacker === victim || teamManager.areTeamMates(attacker, victim)) return

        if (source.isOf(DamageTypes.ARROW)) {
            stats.arrowHit(attacker)
        }

        if (applied <= 0f) return

        stats.damageDealt(attacker, applied)
    }

    private fun onLethalDamage(player: ServerPlayer, source: DamageSource, amount: Float) {
        player.combatTracker.recordDamage(source, amount)

        val killer = source.entity as? ServerPlayer

        if (killer != null && killer !== player && !teamManager.areTeamMates(killer, player)) {
            stats.onKill(player, killer)
        } else {
            stats.onDeath(player)
        }

        gameHandle.deathMessages.getDeathMessage(player, source)
            .sendTo(PlayerLookup.all(gameHandle.server))

        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_DEATH, SoundSource.PLAYERS, 0.8f, 0.8f)

        flagManager.dropCarriedFlag(player)

        player.setGameMode(GameType.SPECTATOR)
        player.health = player.maxHealth

        respawnCooldown.setCooldown(player, RESPAWN_DELAY)
    }

    private fun respawnPlayer(player: ServerPlayer) {
        teamManager.getTeam(player)?.let { team ->
            getSpawn(team)?.let { spawn ->
                player.teleportTo(level, spawn.x(), spawn.y(), spawn.z(), emptySet(), spawn.yaw, spawn.pitch, true)
            }
        }

        player.health = player.maxHealth
        player.abilities.flyingSpeed = 0f
        player.onUpdateAbilities()

        kit.equip(player)

        gameHandle.scheduler.immediate(Runnable {
            player.abilities.flyingSpeed = 0.05f
            player.onUpdateAbilities()
            player.setGameMode(gameHandle.playerUtil.defaultGameMode)
        })
    }

    private fun onCapture(team: Team, home: CtfTeamInfo) {
        val score = data.addScore(createTeamReference(team), 1)

        objective.setScore(home.key.id, score)

        if (score >= CAPTURES_TO_WIN) {
            endGame()
        }
    }

    override fun participantRemoved(player: ServerPlayer) {
        respawnCooldown.resetCooldown(player)
        flagManager.dropCarriedFlag(player)
        balanceTeams()

        super.participantRemoved(player)
    }

    private fun balanceTeams() {
        val members = teamInfo.mapNotNull { teamManager.getTeam(it) }
            .associateWith { it.getParticipatingPlayers(gameHandle.participants) }

        val maxPlayerCount = members.values.maxOfOrNull { it.size } ?: return

        for ((_, players) in members) {
            if (players.isEmpty()) continue

            val deficit = maxPlayerCount - players.size
            val extraHealthPerPlayer = deficit * 20.0 / players.size

            for (player in players) {
                player.setAttribute(Attributes.MAX_HEALTH, 20.0 + extraHealthPerPlayer)
            }
        }

        if (started) return

        for (player in gameHandle.participants) {
            player.health = player.maxHealth
        }
    }

    private fun endGame() {
        if (winManager.gameOver) return

        respawnCooldown.resetAll()
        flagManager.reset()

        if (isDraw()) {
            winManager.draw()
        } else {
            winManager.complete()
        }
    }

    private fun isDraw(): Boolean {
        val scores = teamInfo.mapNotNull { teamManager.getTeam(it) }
            .map { data.getScore(it) }

        return scores.distinct().size <= 1
    }
}
