package work.lclpnet.ap2.game.cozy_campfire

import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.Mth
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.gamerules.GameRules
import net.minecraft.world.scores.Team.CollisionRule
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.api.game.team.DyeTeamKey
import work.lclpnet.ap2.api.game.team.Team
import work.lclpnet.ap2.api.game.team.TeamKey
import work.lclpnet.ap2.api.map.MapBootstrap
import work.lclpnet.ap2.api.stats.CommonStats.DamageDealt
import work.lclpnet.ap2.api.stats.CommonStats.Deaths
import work.lclpnet.ap2.api.stats.CommonStats.KillDeathRatio
import work.lclpnet.ap2.api.stats.CommonStats.Kills
import work.lclpnet.ap2.ext.mc.setDayTime
import work.lclpnet.ap2.ext.mc.setWeatherParameters
import work.lclpnet.ap2.ext.runEveryTick
import work.lclpnet.ap2.game.cozy_campfire.setup.*
import work.lclpnet.ap2.game.player.Participants
import work.lclpnet.ap2.impl.game.TeamEliminationGameInstance
import work.lclpnet.ap2.impl.util.TeamStorage
import work.lclpnet.ap2.impl.util.TimeHelper
import work.lclpnet.ap2.impl.util.bossbar.DynamicTranslatedTeamBossBar
import work.lclpnet.gaco.collisions.ChunkedCollisionDetector
import work.lclpnet.gaco.collisions.CollisionDetector
import work.lclpnet.gaco.collisions.movement.PlayerMovementObserver
import work.lclpnet.game.map.GameMap
import work.lclpnet.game.util.PlayerReset
import work.lclpnet.kibu.access.entity.ServerPlayerAccess
import work.lclpnet.kibu.hook.HookRegistrar
import work.lclpnet.kibu.translate.text.FormatWrapper.styled
import work.lclpnet.kibu.translate.text.LocalizedFormat
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.math.roundToInt

private const val DAY_TIME_CHANCE = 0.55f
private const val RAIN_CHANCE = 0.6f
private const val THUNDER_CHANCE = 0.15f

val TEAM_RED: TeamKey = DyeTeamKey.RED
val TEAM_BLUE: TeamKey = DyeTeamKey.BLUE
const val MOVEMENT_SPEED = 0.15f

class CozyCampfireInstance(gameHandle: MiniGameHandle) : TeamEliminationGameInstance(gameHandle), MapBootstrap {

    private val random = Random()
    private val collisionDetector: CollisionDetector = ChunkedCollisionDetector()
    private val movementObserver = PlayerMovementObserver(collisionDetector, gameHandle.participants::isParticipating)
    private val campfireFuel = TeamStorage.create(::createCampfireFuel)
    private val toEliminate = mutableSetOf<Team>()
    private val stats = CCStats(createStats(
        /* teamStats = */ listOf(FuelAdded, Kills, Deaths, DamageDealt),
        /* playerStats = */ listOf(FuelAdded, Kills, Deaths, KillDeathRatio, DamageDealt)
    ), teamManager)
    private lateinit var hookSetup: CCHooks
    private lateinit var fuel: CCFuel
    private lateinit var bossBar: DynamicTranslatedTeamBossBar
    private lateinit var baseManager: CCBaseManager
    private var teamBias = 0.8f
    private var fuelPerSecond = 100
    private var startingFuelSeconds = 30
    private var fuelPerMinute = 0
    private var startingFuel = 0
    private var time = 0

    init {
        useOldCombat()
        useSurvivalMode()
        teamManager.setUseColorCodes(true)
    }

    override fun createWorldBootstrap(world: ServerLevel, map: GameMap): CompletableFuture<Void> {
        teamManager.partitionIntoTeams(gameHandle.participants, setOf(TEAM_RED, TEAM_BLUE))

        val setup = CCReader(map, world, gameHandle.logger)

        return setup.readBases(teamManager.teams)
            .thenAccept { bases -> baseManager = CCBaseManager(bases, teamManager) }
            .thenCompose { world.server.submit {
                setupGameRules(map, world)
                randomizeWorldConditions(world)
            } }
    }

    override fun prepare() {
        teamManager.minecraftTeams.forEach { team ->
            team.isAllowFriendlyFire = false
            team.setSeeFriendlyInvisibles(true)
            team.collisionRule = CollisionRule.PUSH_OTHER_TEAMS
        }

        readMapFuelInfo()

        fuel = CCFuel(level, baseManager)
        fuel.registerFuel(fuelPerSecond)

        teleportTeamsToSpawns()

        val kitManager = CCKitManager(teamManager, level, random)
        val participants: Participants = gameHandle.participants

        for (player in participants) {
            kitManager.giveItems(player)
            PlayerReset.modifyWalkSpeed(player, MOVEMENT_SPEED)
        }

        val translations = gameHandle.translations
        val args = CCHooks.Args(fuel, baseManager, kitManager, ::onAddFuel, stats)

        hookSetup = CCHooks(participants, teamManager, this, translations, args)
        hookSetup.register(gameHandle.hooks)

        setupMovementObserver(hookSetup)

        val timeArg = getTimeArgument(startingFuel, 1)
        val playerBossBar = usePlayerDynamicTaskDisplay(timeArg)
        bossBar = DynamicTranslatedTeamBossBar(playerBossBar, teamManager)
    }

    override fun go() {
        gameHandle.protect(hookSetup::configure)
        runEveryTick { tick() }
        baseManager.openDoors(level)
    }

    override fun teamEliminated(team: Team) {
        val participatingTeams = teamManager.participatingTeams

        if (participatingTeams.size == 1) {
            val lastTeam = participatingTeams.iterator().next()
            val translations = gameHandle.translations
            val fuel = campfireFuel.get(lastTeam)
            val remainingSeconds = getRemainingTime(fuel.count, lastTeam.playerCount)

            val duration = TimeHelper.formatTime(translations, remainingSeconds)
            val detail = translations.translateText("game.ap2.cozy_campfire.remaining", duration)

            data.add(lastTeam, detail)
        }

        super.teamEliminated(team)
    }

    private fun setupMovementObserver(hookSetup: CCHooks) {
        val hooks: HookRegistrar = gameHandle.hooks
        val server: MinecraftServer = gameHandle.server

        movementObserver.init(hooks, server)
        hookSetup.configureBaseRegionEvents(collisionDetector, movementObserver)
    }

    private fun readMapFuelInfo() {
        val perSecond = map.getProperty("fuel-per-second") as? Number
        val startSeconds = map.getProperty("start-fuel-seconds") as? Number
        val teamBiasProp = map.getProperty("team-bias") as? Number

        if (perSecond != null) fuelPerSecond = perSecond.toInt()
        if (startSeconds != null) startingFuelSeconds = startSeconds.toInt()
        if (teamBiasProp != null) teamBias = teamBiasProp.toFloat().coerceAtLeast(0f)

        fuelPerMinute = fuelPerSecond * 60
        startingFuel = fuelPerSecond * startingFuelSeconds
    }

    private fun setupGameRules(map: GameMap, world: ServerLevel) {
        commons(map, world).gameRuleBuilder()
            .set(GameRules.MAX_SNOW_ACCUMULATION_HEIGHT, 0)
            .set(GameRules.ADVANCE_WEATHER, false)
            .set(GameRules.ADVANCE_TIME, false)
    }

    private fun randomizeWorldConditions(world: ServerLevel) {
        if (random.nextFloat() <= DAY_TIME_CHANCE) {
            world.setDayTime(6000)
        } else {
            world.setDayTime(18000)
        }

        if (random.nextFloat() <= RAIN_CHANCE) {
            val thunder = random.nextFloat() <= (THUNDER_CHANCE / RAIN_CHANCE)
            world.setWeatherParameters(0, 1000, true, thunder)
        } else {
            world.setWeatherParameters(1000, 0, raining = false, thundering = false)
            world.setRainLevel(0f)
        }
    }

    private fun playersFactor(team: Team) = playersFactor(team.playerCount)

    private fun playersFactor(players: Int) = maxOf(1f, players * teamBias)

    private fun getTimeArgument(fuel: Int, playerCount: Int): Any {
        val totalSeconds = getRemainingTime(fuel, playerCount)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60

        return gameHandle.translations.translateText("game.ap2.cozy_campfire.time", minutes, seconds)
            .formatted(ChatFormatting.YELLOW)
    }

    private fun getRemainingTime(fuel: Int, playerCount: Int): Int {
        val normalizedFuel = (fuel / playersFactor(playerCount)).roundToInt()
        val minutes = normalizedFuel / fuelPerMinute
        val seconds = normalizedFuel % fuelPerMinute / fuelPerSecond
        return minutes * 60 + seconds
    }

    private fun tick() {
        if (++time % 20 == 0) everySecond()
    }

    private fun everySecond() {
        toEliminate.clear()

        for (team in teamManager.teams) {
            val fuel = campfireFuel.get(team)
            fuel.set(fuel.count - getFuelConsumption(team))
            updateBossBar(team, fuel)
            if (fuel.count <= 0) toEliminate.add(team)
        }

        if (toEliminate.isEmpty()) return

        val translations = gameHandle.translations
        val timeSurvived = time / 20
        val duration = TimeHelper.formatTime(translations, timeSurvived)
        val detail = translations.translateText("game.ap2.cozy_campfire.survived", duration)

        eliminateAll(toEliminate, detail)
    }

    private fun getFuelConsumption(team: Team) = (fuelPerSecond * playersFactor(team)).roundToInt()

    private fun updateBossBar(team: Team, fuel: CampfireFuel) {
        val timeArg = getTimeArgument(fuel.count, team.playerCount)
        bossBar.setArgument(team, 0, timeArg)
        bossBar.setPercent(team, fuel.percent())
    }

    fun onAddFuel(player: ServerPlayer, pos: BlockPos, team: Team, stack: ItemStack) {
        val value = fuel.getValue(stack)
        stack.count = 0

        val world = player.level()
        val x = pos.x + 0.5
        val y = pos.y + 0.5
        val z = pos.z.toDouble()

        world.playSound(null, x, y, z, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.2f, 1f)
        world.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 7, 0.1, 0.1, 0.1, 0.0)

        if (value <= 0) return

        stats.addFuel(player, team, value.toFloat() / fuelPerSecond)

        val campfire = campfireFuel.get(team)
        campfire.set(campfire.count + value)

        updateBossBar(team, campfire)
        notifyTeamMembers(team, value)
    }

    private fun notifyTeamMembers(team: Team, value: Int) {
        val translations = gameHandle.translations

        val added = LocalizedFormat.format("%.2f", value.toFloat() / (fuelPerSecond * playersFactor(team)))

        val msg = translations.translateText(
            "game.ap2.cozy_campfire.fuel_added",
            styled(added, ChatFormatting.YELLOW)
        ).formatted(ChatFormatting.GREEN)

        for (player in team.players) {
            player.sendOverlayMessage(msg.translateFor(player))
            ServerPlayerAccess.playSoundToPlayer(
                player,
                SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS,
                0.2f,
                1.8f
            )
        }
    }

    private fun createCampfireFuel(team: Team) =
        CampfireFuel((startingFuel * playersFactor(team)).roundToInt())
}

private class CampfireFuel(initial: Int) {
    var count: Int = initial
    var max: Int = initial

    fun set(count: Int) {
        this.count = count
        if (count > max) max = count
    }

    fun percent(): Float {
        if (max == 0) return 0f
        return Mth.clamp(count.toFloat() / max, 0f, 1f)
    }
}
