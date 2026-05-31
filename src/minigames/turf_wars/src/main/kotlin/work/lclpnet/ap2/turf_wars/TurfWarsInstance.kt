package work.lclpnet.ap2.turf_wars

import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.arrow.Arrow
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.gamerules.GameRules
import net.minecraft.world.phys.Vec3
import net.minecraft.world.scores.Team.CollisionRule
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.api.game.team.DyeTeamKey
import work.lclpnet.ap2.core.hook.ProjectileShootCallback
import work.lclpnet.ap2.ext.*
import work.lclpnet.ap2.ext.mc.*
import work.lclpnet.ap2.game.team.getWoolBlock
import work.lclpnet.ap2.impl.game.TeamEliminationGameInstance
import work.lclpnet.ap2.impl.map.schema.SchemaHolder
import work.lclpnet.ap2.impl.util.math.MathUtil
import work.lclpnet.ap2.turf_wars.Phase.*
import work.lclpnet.ap2.turf_wars.util.TurfManager
import work.lclpnet.ap2.turf_wars.util.TurfWarsTeamInfo
import work.lclpnet.game.impl.prot.ProtectionTypes
import work.lclpnet.kibu.access.VelocityModifier
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks
import work.lclpnet.kibu.hook.entity.ProjectileHooks
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks
import work.lclpnet.kibu.hook.player.PlayerSpawnLocationCallback
import work.lclpnet.kibu.scheduler.api.TaskHandle
import work.lclpnet.kibu.title.Title
import java.util.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

enum class Phase { Nothing, Build, Fight }

const val DEBUG_TURF = false
const val INITIAL_BUILDING_BLOCKS = 32
const val MAX_BUILDING_BLOCKS = 50
const val MAX_ARROWS = 2
val BUILDING_BLOCK_GAIN_PERIOD = 5.seconds
val ARROW_GAIN_DELAY = 2.seconds + 10.ticks

class TurfWarsInstance(gameHandle: MiniGameHandle) : TeamEliminationGameInstance(gameHandle) {

    val schemaHolder: SchemaHolder<TurfWarsSchema> = useSchema(TurfWarsSchema::class.java)
    val arrowTasks = mutableMapOf<UUID, TaskHandle>()
    lateinit var turfManager: TurfManager
    lateinit var teamInfos: Map<DyeTeamKey, TurfWarsTeamInfo>
    var phase: Phase = Nothing
    var blocksPerKill: Int = 1
    val turfAbsenceSeconds = mutableMapOf<DyeTeamKey, Int>()

    init {
        useOldCombat()
        useSurvivalMode()

        teamManager.setUseColorCodes(true)
    }

    fun setupTeams(): List<TurfWarsTeamInfo> {
        val team1Key = map.properties.optString("team1Color")?.let { DyeTeamKey.byId(it) } ?: DyeTeamKey.RED
        val team2Key = map.properties.optString("team2Color")?.let { DyeTeamKey.byId(it) } ?: DyeTeamKey.LIGHT_BLUE

        require(team1Key != team2Key) { "Team colors cannot be the same" }

        val schema = schemaHolder.get()

        val team1Info = TurfWarsTeamInfo(
            spawn = schema.team1Spawn!!,
            baseBounds = schema.team1Base!!,
            initialTurf = schema.team1Turf!!,
            teamKey = team1Key
        )

        val team2Info = TurfWarsTeamInfo(
            spawn = schema.team2Spawn!!,
            baseBounds = schema.team2Base!!,
            initialTurf = schema.team2Turf!!,
            teamKey = team2Key
        )

        teamManager.partitionIntoTeams(players(), setOf(team1Key, team2Key))

        teamManager.minecraftTeams.forEach { team ->
            team.isAllowFriendlyFire = false
            team.setSeeFriendlyInvisibles(true)
            team.collisionRule = CollisionRule.PUSH_OTHER_TEAMS
        }

        return listOf(team1Info, team2Info)
    }

    override fun prepare() {
        val teams = setupTeams()

        teamInfos = teams.associateBy { it.key() }

        turfManager = TurfManager(
            teams.map { it.initialTurf },
            teams.map { it.key() },
            commons().debugController(),
            world
        )

        turfManager.updateVisualizer()

        for (info in teams) {
            val team = teamManager.getTeam(info).orElse(null) ?: continue

            for (player in team.players) {
                player.teleport(info.spawn)
            }
        }

        commons().gameRuleBuilder()
            .set(GameRules.NATURAL_HEALTH_REGENERATION, false)
    }

    override fun go() {
        changePhase(Build)

        translate("game.ap2.turf_wars.phase.build.hint")
            .formatted(ChatFormatting.AQUA)
            .sendTo(players())

        gameHandle.protect { config ->
            ProtectionTypes.USE_ITEM_ON_BLOCK.allow(config)

            ProtectionTypes.PLACE_BLOCKS.allow(config) { entity, pos ->
                entity is ServerPlayer && placeBlock(entity, pos)
            }

            ProtectionTypes.BREAK_BLOCKS.allow(config) { entity, pos ->
                entity is ServerPlayer && breakBlock(entity, pos)
            }

            ProtectionTypes.ALLOW_DAMAGE.allow(config) { entity, source ->
                entity is ServerPlayer && allowDamage(entity, source)
            }
        }

        ServerLivingEntityHooks.ALLOW_DAMAGE.registerWith(hooks) { entity, source, amount ->
            entity is ServerPlayer && onDamage(entity, source, amount)
        }

        PlayerSpawnLocationCallback.HOOK.registerWith(hooks) { data ->
            handleRespawn(data)
        }

        ProjectileShootCallback.HOOK.registerWith(hooks) { shooter, projectile ->
            if (projectile is Arrow && shooter is ServerPlayer && isParticipating(shooter)) {
                scheduleNewArrow(shooter)
            }
        }

        ProjectileHooks.HIT_BLOCK.registerWith(hooks) { projectile, _ ->
            projectile.discard()
        }

        PlayerInteractionHooks.USE_ITEM.registerWith(hooks) { player, _, hand ->
            onUseItem(player, hand)
        }

        for (player in players()) {
            giveItems(player)
        }

        for (gate in schemaHolder.get().spawnGates) {
            world.setBlocks(gate, Blocks.AIR)
        }

        runAfter(5.seconds) {
            runEvery(BUILDING_BLOCK_GAIN_PERIOD) {
                for (player in players()) {
                    val block = buildingBlock(player) ?: continue

                    val total = player.inventory.countItem(block.asItem())

                    if (total < MAX_BUILDING_BLOCKS) {
                        player.inventory.add(ItemStack(block))
                    }
                }
            }
        }

        runEveryTick {
            for (player in players()) {
                repel(player)
            }
        }

        if (players().count() == 2) {
            blocksPerKill = 2
            runAfter(90.seconds) { blocksPerKill = 3 }
        } else {
            blocksPerKill = 1
            runAfter(90.seconds) { blocksPerKill = 2 }
            runAfter(150.seconds) { blocksPerKill = 3 }
        }

        runEvery(1.seconds) {
            checkTurfCamping()
        }
    }

    private fun checkTurfCamping() {
        if (phase != Fight) {
            turfAbsenceSeconds.clear()
            return
        }

        for (team in teamManager.teams.toList()) {
            val key = team.key()

            if (key !is DyeTeamKey) continue

            val bounds = turfManager.turfOf(key)?.bounds ?: continue
            val base = teamInfos[key]?.baseBounds

            val present = team.players.any { player ->
                isParticipating(player)
                        && bounds.contains(player.position())
                        && (base == null || !base.contains(player.position()))
            }

            if (present) {
                turfAbsenceSeconds[key] = 0
                continue
            }

            val seconds = (turfAbsenceSeconds[key] ?: 0) + 1
            turfAbsenceSeconds[key] = seconds

            if (seconds >= 20) {
                turfAbsenceSeconds.remove(key)
                changePhase(Nothing)
                eliminate(team)
                return
            }
        }
    }

    private fun onKilled(victim: ServerPlayer, killer: ServerPlayer) {
        val victimTeam = teamManager.getTeam(victim).orElse(null) ?: return
        val killerTeam = teamManager.getTeam(killer).orElse(null) ?: return

        val key = killerTeam.key()

        if (key !is DyeTeamKey) return

        turfManager.growTurf(key, blocksPerKill)

        val victimTurf = turfManager.turfOf(victimTeam.key()) ?: return

        if (victimTurf.bounds == null) {
            changePhase(Nothing)
            eliminate(victimTeam)
            return
        }

        giveArrow(killer)
    }

    private fun giveItems(player: ServerPlayer) {
        player.inventory.setItem(0, ItemStack(Items.BOW).unbreakable())
        giveArrow(player)

        buildingBlock(player)?.let { block ->
            player.inventory.setItem(1, ItemStack(block, INITIAL_BUILDING_BLOCKS))
        }

        scheduleNewArrow(player)
    }

    fun repel(player: ServerPlayer) {
        val ownTeam = teamManager.getTeam(player).orElse(null) ?: return
        val ownTurf = turfManager.turfOf(ownTeam.key()) ?: return
        val ownTurfBounds = ownTurf.bounds ?: return

        for (team in teamManager.teams) {
            if (team == ownTeam) continue

            val turf = turfManager.turfOf(team.key()) ?: continue
            val bounds = turf.bounds ?: continue

            if (MathUtil.corners(player.boundingBox).none { bounds.contains(it) }) continue

            // player intersects with opponent turf
            val repelDir = Vec3(ownTurfBounds.min().subtract(bounds.min()))
                .normalize()
                .add(0.0, 0.5, 0.0)

            VelocityModifier.setVelocity(player, repelDir)
            player.playNotifySound(SoundEvents.ALLAY_HURT, SoundSource.PLAYERS, 0.5f, 2f)
        }
    }

    private fun onUseItem(player: Player, hand: InteractionHand): InteractionResult {
        if (player !is ServerPlayer || !isParticipating(player)) return InteractionResult.PASS

        val stack = player.getItemInHand(hand)

        if (stack.isOf(Items.BOW) && phase != Fight) {
            return InteractionResult.FAIL
        }

        return InteractionResult.PASS
    }

    private fun scheduleNewArrow(player: ServerPlayer) {
        if (player.inventory.countItem(Items.ARROW) >= MAX_ARROWS) return

        if (arrowTasks[player.uuid] != null) return
        
        arrowTasks[player.uuid] = runAfter(ARROW_GAIN_DELAY) {
            giveArrow(player)
            arrowTasks.remove(player.uuid)
            scheduleNewArrow(player)
        }
    }

    fun giveArrow(player: ServerPlayer) {
        val count = player.inventory.countItem(Items.ARROW)

        if (count >= MAX_ARROWS) return

        val stack = ItemStack(Items.ARROW)

        if (count == 0) {
            player.inventory.setItem(8, stack)
        } else {
            player.inventory.add(stack)
        }

        player.playNotifySound(SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.3f, 2f)
    }

    private fun buildingBlock(player: ServerPlayer): Block? {
        val team = teamManager.getTeam(player).orElse(null) ?: return null

        val key = team.key()

        if (key !is DyeTeamKey) return null

        return key.getWoolBlock()
    }

    private fun handleRespawn(data: PlayerSpawnLocationCallback.LocationData) {
        if (data.isJoin) return

        val player = data.player
        val teamInfo = teamInfoOf(player) ?: return

        data.position = teamInfo.spawn.asVec3d()
        data.yaw = teamInfo.spawn.yaw
        data.pitch = teamInfo.spawn.pitch

        arrowTasks[player.uuid]?.cancel()
        giveItems(player)
    }

    private fun allowDamage(player: ServerPlayer, source: DamageSource): Boolean {
        if (!isParticipating(player) || phase != Fight) return false

        val attacker = source.entity

        if (attacker !is ServerPlayer || teamManager.areTeamMates(player, attacker)) return false

        val team = teamManager.getTeam(player).orElse(null) ?: return false
        val info = teamInfos[team.key()] ?: return false

        if (info.baseBounds.contains(player.position())) return false

        return source.isOf(DamageTypes.ARROW)
    }

    private fun onDamage(victim: ServerPlayer, source: DamageSource, amount: Float): Boolean {
        val attacker = source.entity

        if (attacker !is ServerPlayer) return false

        if (source.isOf(DamageTypes.ARROW)) {
            // ensure arrows are one-hit
            if (victim.health > amount) {
                victim.hurtServer(world, source, victim.health)
                return false
            }

            onKilled(victim, attacker)

            return true
        }

        return false
    }

    fun placeBlock(player: ServerPlayer, pos: BlockPos): Boolean {
        if (phase != Build || !isParticipating(player)) return false

        val team = teamManager.getTeam(player).orElse(null) ?: return false

        if (!turfManager.isTurf(pos, team.key())) return false

        turfManager.turfOf(team.key())?.builtBlocks?.add(pos)

        return true
    }

    fun breakBlock(player: ServerPlayer, pos: BlockPos): Boolean {
        if (phase != Build || !isParticipating(player)) return false

        val team = teamManager.getTeam(player).orElse(null) ?: return false

        if (!(turfManager.turfOf(team.key())?.builtBlocks?.contains(pos) ?: false)) return false

        val state = world.getBlockState(pos)
        player.inventory.add(ItemStack(state.block))

        return true
    }

    fun changePhase(phase: Phase) {
        this.phase = phase

        if (phase == Nothing) return

        val duration = when (phase) {
            Build -> 20.seconds
            Fight -> 1.minutes
        }

        val nextPhase = when (phase) {
            Build -> Fight
            Fight -> Build
        }

        createTimer(
            label = translate("game.ap2.turf_wars.phase.${phase.name.lowercase()}"),
            duration = duration
        ).whenDone { changePhase(nextPhase) }

        when (phase) {
            Build -> {
                playSound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.5f, 0.5f)
            }
            Fight -> {
                playSound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.5f, 2f)

                translate("game.ap2.turf_wars.phase.fight.title")
                    .formatted(ChatFormatting.GREEN)
                    .acceptEach(players()) { player, text ->
                        Title.get(player).title(Component.empty(), text)
                    }
            }
        }
    }

    fun teamInfoOf(player: ServerPlayer): TurfWarsTeamInfo? {
        val team = teamManager.getTeam(player).orElse(null) ?: return null

        return teamInfos[team.key()]
    }
}