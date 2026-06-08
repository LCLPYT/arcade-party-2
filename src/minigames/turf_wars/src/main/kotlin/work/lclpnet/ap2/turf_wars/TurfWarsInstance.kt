package work.lclpnet.ap2.turf_wars

import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.entity.EquipmentSlot
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
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.api.game.team.DyeTeamKey
import work.lclpnet.ap2.api.game.team.Team
import work.lclpnet.ap2.api.stats.CommonStats.Deaths
import work.lclpnet.ap2.api.stats.CommonStats.KillDeathRatio
import work.lclpnet.ap2.api.stats.CommonStats.Kills
import work.lclpnet.ap2.api.stats.Stat
import work.lclpnet.ap2.core.hook.ArmorAbsorbDamageCallback
import work.lclpnet.ap2.core.hook.CanShootProjectileCallback
import work.lclpnet.ap2.core.hook.ProjectileShootCallback
import work.lclpnet.ap2.ext.*
import work.lclpnet.ap2.ext.mc.*
import work.lclpnet.ap2.game.kit.KitHandler
import work.lclpnet.ap2.game.kit.hasKitEquipped
import work.lclpnet.ap2.game.team.getWoolBlock
import work.lclpnet.ap2.impl.game.TeamEliminationGameInstance
import work.lclpnet.ap2.impl.map.schema.SchemaHolder
import work.lclpnet.ap2.impl.util.ItemHelper.getLeatherArmor
import work.lclpnet.ap2.impl.util.TimeHelper
import work.lclpnet.ap2.impl.util.math.MathUtil
import work.lclpnet.ap2.turf_wars.Phase.*
import work.lclpnet.ap2.turf_wars.util.*
import work.lclpnet.combatctl.hook.SwordBlockDamageCallback
import work.lclpnet.gaco.collisions.ChunkedCollisionDetector
import work.lclpnet.gaco.collisions.movement.TickMovementObserver
import work.lclpnet.gaco.ds.BlockBox
import work.lclpnet.game.impl.prot.ProtectionTypes
import work.lclpnet.kibu.access.VelocityModifier
import work.lclpnet.kibu.hook.entity.ProjectileHooks
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks
import work.lclpnet.kibu.hook.player.PlayerSpawnLocationCallback
import work.lclpnet.kibu.title.Title
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

val TurfClaimed = Stat("turf_claimed", 0)

class TurfWarsInstance(gameHandle: MiniGameHandle) : TeamEliminationGameInstance(gameHandle) {

    val schemaHolder: SchemaHolder<TurfWarsSchema> = useSchema(TurfWarsSchema::class.java)
    val arrowEconomy = ArrowEconomy(gameHandle, teamManager)
    lateinit var turfManager: TurfManager
    lateinit var teamInfos: Map<DyeTeamKey, TurfWarsTeamInfo>
    lateinit var kitHandler: KitHandler
    lateinit var campingMonitor: CampingMonitor
    val movementObserver = TickMovementObserver(
        ChunkedCollisionDetector(),
        gameHandle.participants::isParticipating
    ).also {
        it.init(gameHandle.scheduler, gameHandle.hooks, gameHandle.server)
    }
    var phase: Phase = Nothing
    var blocksPerKill: Int = 1
    val repelTicks = mutableMapOf<UUID, Int>()
    val stats = createStats(
        /* teamStats = */ listOf(Kills, Deaths, TurfClaimed),
        /* playerStats = */ listOf(Kills, Deaths, KillDeathRatio, TurfClaimed)
    )

    init {
        useOldCombat()
        useSurvivalMode()

        teamManager.setUseColorCodes(true)
    }

    override fun prepare() {
        val teams = setupTeams()

        setupKits(teams)

        teamInfos = teams.associateBy { it.key() }

        campingMonitor = CampingMonitor(gameHandle, teamManager, teamInfos)

        turfManager = TurfManager(
            teams.map { it.initialTurf },
            teams.map { it.key() },
            commons().debugController(),
            level
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

        registerProtection()
        registerHooks()
        setupInitialState()
        scheduleTasks()
    }

    private fun registerProtection() {
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
    }

    private fun registerHooks() {
        ServerLivingEntityHooks.ALLOW_DAMAGE.registerWith(hooks) { entity, source, amount ->
            entity is ServerPlayer && onDamage(entity, source, amount)
        }

        PlayerSpawnLocationCallback.HOOK.registerWith(hooks) { data ->
            handleRespawn(data)
        }

        ProjectileShootCallback.HOOK.registerWith(hooks) { shooter, projectile ->
            if (projectile is Arrow && shooter is ServerPlayer && isParticipating(shooter)) {
                arrowEconomy.scheduleRefill(shooter)
            }
        }

        ProjectileHooks.HIT_BLOCK.registerWith(hooks) { projectile, hitResult ->
            onProjectileHitBlock(projectile, hitResult)
        }

        CanShootProjectileCallback.HOOK.registerWith(hooks) { shooter, _, _ ->
            shooter is ServerPlayer && isParticipating(shooter) && phase == Fight
        }

        ArmorAbsorbDamageCallback.HOOK.registerWith(hooks) { _, _, _ ->
            false
        }

        SwordBlockDamageCallback.HOOK.registerWith(hooks) { _, _, originalDamage, _, _ ->
            originalDamage
        }
    }

    private fun setupInitialState() {
        for (player in players()) {
            giveItems(player)
        }

        for (gate in schemaHolder.get().spawnGates) {
            level.setBlocks(gate, Blocks.AIR)
        }

        blocksPerKill = if (players().count() == 2) 2 else 1
    }

    private fun scheduleTasks() {
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
                tickRepel(player)
            }
        }

        deferEvery(period = TURF_INCREASE_PERIOD, after = TURF_INITIAL_INCREASE_DELAY) {
            advanceBlocksPerKill(blocksPerKill + 1)
        }

        deferEvery(1.seconds) {
            val toEliminate = campingMonitor.tick(phase)

            if (toEliminate.isNotEmpty()) {
                eliminateForCamping(toEliminate)
            }
        }
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

    fun setupKits(teamInfos: List<TurfWarsTeamInfo>) {
        kitHandler = KitHandler.create(gameHandle, level) { handle ->
            listOf(
                ArcherKit(handle),
                AssassinKit(handle)
            )
        }

        kitHandler.setup()

        for (info in teamInfos) {
            movementObserver.whenEntering(info.baseBounds) { player ->
                if (info.isMember(player, teamManager)) {
                    kitHandler.enableKitChanger(player)
                }
            }

            movementObserver.whenLeaving(info.baseBounds) { player ->
                if (info.isMember(player, teamManager)) {
                    kitHandler.disableKitChanger(player)
                }
            }
        }
    }

    private fun giveItems(player: ServerPlayer) {
        kitHandler.reequip(player)

        arrowEconomy.giveArrow(player)

        buildingBlock(player)?.let { block ->
            player.inventory.add(ItemStack(block, INITIAL_BUILDING_BLOCKS))
        }

        arrowEconomy.scheduleRefill(player)

        val team = teamManager.getTeam(player).orElse(null) ?: return
        val color = team.key().color()

        player.setItemSlot(EquipmentSlot.HEAD, getLeatherArmor(Items.LEATHER_HELMET, color).unbreakable())
        player.setItemSlot(EquipmentSlot.CHEST, getLeatherArmor(Items.LEATHER_CHESTPLATE, color).unbreakable())
        player.setItemSlot(EquipmentSlot.LEGS, getLeatherArmor(Items.LEATHER_LEGGINGS, color).unbreakable())
        player.setItemSlot(EquipmentSlot.FEET, getLeatherArmor(Items.LEATHER_BOOTS, color).unbreakable())
    }

    private fun handleRespawn(data: PlayerSpawnLocationCallback.LocationData) {
        if (data.isJoin) return

        val player = data.player
        val teamInfo = teamInfoOf(player) ?: return

        data.position = teamInfo.spawn.asVec3d()
        data.yaw = teamInfo.spawn.yaw
        data.pitch = teamInfo.spawn.pitch

        arrowEconomy.cancelRefill(player)
        giveItems(player)
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

        level.destroyBlock(pos, false)
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

    private fun eliminateForCamping(teams: List<Team>) {
        changePhase(Nothing)

        for (team in teams) {
            translate(
                "game.ap2.turf_wars.eliminated_for_camping",
                team.key().getDisplayName(gameHandle.translations),
                TimeHelper.formatTime(gameHandle.translations, CAMP_ELIMINATION_SECONDS)
            ).formatted(ChatFormatting.GRAY)
                .sendTo(allPlayers())
        }

        eliminateAll(teams)
    }

    private fun onKilled(victim: ServerPlayer, killer: ServerPlayer) {
        val victimTeam = teamManager.getTeam(victim).orElse(null) ?: return
        val killerTeam = teamManager.getTeam(killer).orElse(null) ?: return

        gainKill(killer, stats.players)
        stats.teams.increment(killerTeam, Kills)

        stats.players.increment(victim, Deaths)
        stats.teams.increment(victimTeam, Deaths)

        stats.players.set(
            killer,
            KillDeathRatio,
            stats.players.get(killer, Kills).toFloat() /
                    stats.players.get(killer, Deaths).coerceAtLeast(1)
        )

        stats.players.set(
            victim,
            KillDeathRatio,
            stats.players.get(victim, Kills).toFloat() /
                    stats.players.get(victim, Deaths).coerceAtLeast(1)
        )

        val key = killerTeam.key()

        if (key !is DyeTeamKey) return

        turfManager.growTurf(key, blocksPerKill)

        stats.players.increment(killer, TurfClaimed, blocksPerKill)
        stats.teams.increment(killerTeam, TurfClaimed, blocksPerKill)

        val victimTurf = turfManager.turfOf(victimTeam.key()) ?: return

        if (victimTurf.bounds == null) {
            changePhase(Nothing)
            eliminate(victimTeam)
            return
        }

        arrowEconomy.giveArrow(killer)
    }

    private fun allowDamage(player: ServerPlayer, source: DamageSource): Boolean {
        if (!isParticipating(player) || phase != Fight) return false

        val attacker = source.entity

        if (attacker !is ServerPlayer || teamManager.areTeamMates(player, attacker)) return false

        return source.isOf(DamageTypes.ARROW)
                || (source.isOf(DamageTypes.PLAYER_ATTACK) && kitHandler.manager.hasKitEquipped<AssassinKit>(attacker))
    }

    private fun onDamage(victim: ServerPlayer, source: DamageSource, amount: Float): Boolean {
        val attacker = source.entity

        if (attacker !is ServerPlayer) return false

        // damage constraints validated by allowDamage()

        if (source.isOf(DamageTypes.ARROW)) {
            if (kitHandler.manager.hasKitEquipped<ArcherKit>(attacker)) {
                // ensure arrows are one-hit for archer kit
                if (amount < victim.health) {
                    victim.hurtServer(level, source, victim.health)
                    return false
                }
            }

            if (kitHandler.manager.hasKitEquipped<AssassinKit>(attacker)) {
                // ensure arrows are two-hit for assassin kit
                if (amount < 10.0f) {
                    victim.hurtServer(level, source, 10.0f)
                    return false
                }
            }
        }

        if (amount >= victim.health) {
            onKilled(victim, attacker)
        }

        return true
    }

    private fun tickRepel(player: ServerPlayer) {
        if (!repel(player)) {
            repelTicks.remove(player.uuid)
            return
        }

        val ticks = (repelTicks[player.uuid] ?: 0) + 1

        if (ticks >= STUCK_REPEL_TICKS) {
            repelTicks.remove(player.uuid)
            teamInfoOf(player)?.let { player.teleport(it.spawn) }
        } else {
            repelTicks[player.uuid] = ticks
        }
    }

    private fun repel(player: ServerPlayer): Boolean {
        val ownTeam = teamManager.getTeam(player).orElse(null) ?: return false
        val ownTurf = turfManager.turfOf(ownTeam.key()) ?: return false
        val ownTurfBounds = ownTurf.bounds ?: return false

        val mayEnterTurf = mayEnterEnemyTurf(player) && phase == Fight

        var repelled = false

        for (team in teamManager.teams) {
            if (team == ownTeam) continue

            // enemy base is always off-limits, regardless of kit
            val baseBounds = teamInfos[team.key()]?.baseBounds

            if (baseBounds != null && intersectsPlayer(player, baseBounds)) {
                pushOut(player, ownTurfBounds, baseBounds)
                repelled = true
                continue
            }

            if (mayEnterTurf) continue

            val turf = turfManager.turfOf(team.key()) ?: continue
            val bounds = turf.bounds ?: continue

            if (!intersectsPlayer(player, bounds)) continue

            pushOut(player, ownTurfBounds, bounds)
            repelled = true
        }

        return repelled
    }

    private fun intersectsPlayer(player: ServerPlayer, box: BlockBox): Boolean =
        MathUtil.corners(player.boundingBox).any { box.contains(it) }

    private fun pushOut(player: ServerPlayer, ownTurfBounds: BlockBox, target: BlockBox) {
        val repelDir = Vec3(ownTurfBounds.min().subtract(target.min()))
            .normalize()
            .add(0.0, 0.5, 0.0)

        VelocityModifier.setVelocity(player, repelDir)
        player.playNotifySound(SoundEvents.ALLAY_HURT, SoundSource.PLAYERS, 0.5f, 2f)
    }

    private fun mayEnterEnemyTurf(player: ServerPlayer): Boolean =
        kitHandler.manager.hasKitEquipped<AssassinKit>(player)

    private fun buildingBlock(player: ServerPlayer): Block? {
        val team = teamManager.getTeam(player).orElse(null) ?: return null

        val key = team.key()

        if (key !is DyeTeamKey) return null

        return key.getWoolBlock()
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

        if (turfManager.turfOf(team.key())?.builtBlocks?.contains(pos) != true) return false

        val state = level.getBlockState(pos)
        player.inventory.add(ItemStack(state.block))

        return true
    }

    fun changePhase(phase: Phase) {
        this.phase = phase

        if (phase == Nothing) return

        val duration: Duration
        val nextPhase: Phase
        val titleKey: String
        val soundPitch: Float

        when (phase) {
            Build -> {
                duration = BUILD_PHASE_DURATION
                nextPhase = Fight
                titleKey = "game.ap2.turf_wars.phase.build"
                soundPitch = 0.5f
            }
            Fight -> {
                duration = FIGHT_PHASE_DURATION
                nextPhase = Build
                titleKey = "game.ap2.turf_wars.phase.fight.title"
                soundPitch = 2f
            }
            Nothing -> return
        }

        createTimer(
            label = translate("game.ap2.turf_wars.phase.${phase.name.lowercase()}"),
            duration = duration
        ).whenDone { changePhase(nextPhase) }

        translate(titleKey).formatted(ChatFormatting.GREEN).acceptEach(players()) { player, text ->
            Title.get(player).title(Component.empty(), text)
        }

        playSound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.5f, soundPitch)
    }

    fun teamInfoOf(player: ServerPlayer): TurfWarsTeamInfo? {
        val team = teamManager.getTeam(player).orElse(null) ?: return null

        return teamInfos[team.key()]
    }
}
