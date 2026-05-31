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
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.entity.projectile.arrow.Arrow
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.gamerules.GameRules
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.scores.Team.CollisionRule
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.api.game.team.DyeTeamKey
import work.lclpnet.ap2.api.game.team.Team
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

enum class Phase { Nothing, Build, Fight }

const val DEBUG_TURF = false
const val INITIAL_BUILDING_BLOCKS = 32
const val MAX_BUILDING_BLOCKS = 50
const val MAX_ARROWS = 2
const val MAX_ARROWS_DEFICIT = 3
const val CAMP_ELIMINATION_SECONDS = 20
const val CAMP_WARNING_SECONDS = 10
val BUILDING_BLOCK_GAIN_PERIOD = 5.seconds
val ARROW_GAIN_DELAY = 2.seconds + 10.ticks
val ARROW_GAIN_DEFICIT_DELAY = 2.seconds
val TURF_INCREASE_PERIOD = 30.seconds
val TURF_INITIAL_INCREASE_DELAY = 45.seconds

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

        ProjectileHooks.HIT_BLOCK.registerWith(hooks) { projectile, hitResult ->
            onProjectileHitBlock(projectile, hitResult)
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

        deferEvery(BUILDING_BLOCK_GAIN_PERIOD) {
            for (player in players()) {
                val block = buildingBlock(player) ?: continue

                val total = player.inventory.countItem(block.asItem())

                if (total < MAX_BUILDING_BLOCKS) {
                    player.inventory.add(ItemStack(block))
                }
            }
        }

        runEveryTick {
            for (player in players()) {
                repel(player)
            }
        }

        blocksPerKill = if (players().count() == 2) 2 else 1

        deferEvery(period = TURF_INCREASE_PERIOD, after = TURF_INITIAL_INCREASE_DELAY) {
            advanceBlocksPerKill(blocksPerKill + 1)
        }

        deferEvery(1.seconds) {
            checkTurfCamping()
        }
    }

    private fun onProjectileHitBlock(projectile: Projectile, hitResult: BlockHitResult) {
        projectile.discard()

        if (projectile !is Arrow) return

        val player = projectile.owner

        if (player !is ServerPlayer) return

        val opponentTeam = opponentTeam(player) ?: return
        val turf = turfManager.turfOf(opponentTeam.key()) ?: return

        val pos = hitResult.blockPos

        if (!turf.builtBlocks.contains(pos)) return

        world.destroyBlock(pos, false)
    }

    private fun opponentTeam(player: ServerPlayer): Team? {
        val ownTeam = teamManager.getTeam(player).orElse(null) ?: return null

        return teamManager.teams.firstOrNull { it != ownTeam }
    }

    private fun advanceBlocksPerKill(blocks: Int) {
        blocksPerKill = blocks

        translate("game.ap2.turf_wars.speed_up", blocks)
            .formatted(ChatFormatting.GOLD)
            .sendTo(players())

        playSound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.5f, 1.5f)
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

            if (seconds >= CAMP_ELIMINATION_SECONDS) {
                turfAbsenceSeconds.remove(key)
                changePhase(Nothing)
                eliminate(team)
                return
            }

            val remaining = CAMP_ELIMINATION_SECONDS - seconds

            if (remaining == CAMP_WARNING_SECONDS) {
                translate("game.ap2.turf_wars.camp_warning", remaining)
                    .formatted(ChatFormatting.RED)
                    .sendTo(team.players)
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
        if (player.inventory.countItem(Items.ARROW) >= maxArrows(player)) return

        if (arrowTasks[player.uuid] != null) return

        arrowTasks[player.uuid] = runAfter(arrowGainDelay(player)) {
            giveArrow(player)
            arrowTasks.remove(player.uuid)
            scheduleNewArrow(player)
        }
    }

    private fun isOutnumbered(player: ServerPlayer): Boolean {
        val team = teamManager.getTeam(player).orElse(null) ?: return false
        val ownSize = team.playerCount
        val maxSize = teamManager.teams.maxOfOrNull { it.playerCount } ?: return false

        return ownSize < maxSize
    }

    private fun maxArrows(player: ServerPlayer): Int =
        if (isOutnumbered(player)) MAX_ARROWS_DEFICIT else MAX_ARROWS

    private fun arrowGainDelay(player: ServerPlayer): Duration =
        if (isOutnumbered(player)) ARROW_GAIN_DEFICIT_DELAY else ARROW_GAIN_DELAY

    fun giveArrow(player: ServerPlayer) {
        val count = player.inventory.countItem(Items.ARROW)

        if (count >= maxArrows(player)) return

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
        arrowTasks.remove(player.uuid)
        giveItems(player)
    }

    private fun allowDamage(player: ServerPlayer, source: DamageSource): Boolean {
        if (!isParticipating(player) || phase != Fight) return false

        val attacker = source.entity

        if (attacker !is ServerPlayer || teamManager.areTeamMates(player, attacker)) return false

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