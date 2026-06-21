package work.lclpnet.ap2.game.paintball

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import it.unimi.dsi.fastutil.longs.LongSet
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameType
import net.minecraft.world.level.gamerules.GameRules
import work.lclpnet.ap2.api.stats.CommonStats
import work.lclpnet.ap2.api.stats.CommonStats.DamageDealt
import work.lclpnet.ap2.api.stats.CommonStats.Deaths
import work.lclpnet.ap2.api.stats.CommonStats.KillDeathRatio
import work.lclpnet.ap2.api.stats.CommonStats.Kills
import work.lclpnet.ap2.api.util.world.AdjacentBlocks
import work.lclpnet.ap2.api.util.world.BlockPredicate
import work.lclpnet.ap2.api.util.world.WorldScanner
import work.lclpnet.ap2.core.hook.SpectatePlayerCallback
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.ext.mc.resetAttribute
import work.lclpnet.ap2.ext.mc.setAttribute
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.base.TeamGameInstance
import work.lclpnet.ap2.game.data.IntScoreDataContainer
import work.lclpnet.ap2.game.data.Ordering
import work.lclpnet.ap2.game.kit.KitHandler
import work.lclpnet.ap2.game.paintball.item.InkGrenadeItem
import work.lclpnet.ap2.game.paintball.item.InkPackItem
import work.lclpnet.ap2.game.paintball.item.MedKitItem
import work.lclpnet.ap2.game.paintball.item.TripWireItem
import work.lclpnet.ap2.game.paintball.kit.RifleKit
import work.lclpnet.ap2.game.paintball.kit.ShotgunKit
import work.lclpnet.ap2.game.paintball.kit.SniperKit
import work.lclpnet.ap2.game.paintball.util.*
import work.lclpnet.ap2.game.player.Participants
import work.lclpnet.ap2.game.team.TeamManager
import work.lclpnet.ap2.game.util.GameStartSequence
import work.lclpnet.ap2.game.util.createTimer
import work.lclpnet.ap2.game.util.useAnnouncer
import work.lclpnet.ap2.game.util.useTeamStats
import work.lclpnet.ap2.impl.game.item.SpecialItems
import work.lclpnet.ap2.impl.util.ItemHelper.getLeatherArmor
import work.lclpnet.ap2.impl.util.ItemHelper.unbreakable
import work.lclpnet.ap2.impl.util.VanishManager
import work.lclpnet.ap2.impl.util.handler.VisualCooldown
import work.lclpnet.ap2.impl.util.world.BfsWorldScanner
import work.lclpnet.ap2.impl.util.world.SimpleAdjacentBlocks
import work.lclpnet.ap2.impl.util.world.WalkableBlockPredicate
import work.lclpnet.gaco.collisions.ChunkedCollisionDetector
import work.lclpnet.gaco.collisions.movement.TickMovementObserver
import work.lclpnet.gaco.ds.BlockBox
import work.lclpnet.gaco.scene.Scene
import work.lclpnet.gaco.scene.ServerWorldMountContext
import work.lclpnet.gaco.scene.physics.EntityCollisionManager
import work.lclpnet.game.impl.prot.ProtectionTypes
import work.lclpnet.game.map.GameMap
import work.lclpnet.kibu.hook.HookRegistrar
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks
import java.util.*
import kotlin.time.Duration.Companion.seconds

private val DURATION = 150.seconds

class PaintballInstance(
    gameHandle: MiniGameHandle,
    level: ServerLevel,
    map: GameMap,
    teamManager: TeamManager,
    private val random: Random,
    private val teams: PaintballTeams,
    private val paintManager: PaintManager,
) : TeamGameInstance(gameHandle, level, map, teamManager) {

    private val respawnCooldown = VisualCooldown(gameHandle.scheduler)
    private val vanishManager = VanishManager.setup(gameHandle)
    private val announcer = useAnnouncer()
    private val scene = Scene(ServerWorldMountContext(level))

    private val paintGunManager = PaintGunManager(
        level,
        scene,
        paintManager,
        teams,
        random,
        gameHandle.participants,
        gameHandle.translations,
        commons().debugController(),
        winManager::gameOver
    )

    override val data = IntScoreDataContainer(
        ::createReference,
        Ordering.DESCENDING,
        "blocks_painted"
    )

    private val stats = PaintballStats(useTeamStats(
        winManager,
        data,
        CommonStats.IntScore,
        teamStats = listOf(
            TotalBlocksPainted, BlocksRepainted, Kills, Deaths, DamageDealt, SpecialItemsUsed
        ),
        memberStats = listOf(
            TotalBlocksPainted, BlocksRepainted, Kills, Deaths, KillDeathRatio, DamageDealt, SpecialItemsUsed
        )
    ), teamManager, gameHandle.translations)

    private val movementObserver = TickMovementObserver(
        ChunkedCollisionDetector(),
        gameHandle.participants::isParticipating
    ).also {
        it.init(gameHandle.scheduler, gameHandle.hooks, gameHandle.server)
    }

    private lateinit var kitHandler: KitHandler
    private lateinit var results: PaintballResults
    private lateinit var specialItems: SpecialItems
    private var started = false

    init {
        teamManager.setUseColorCodes(true)
    }

    override fun prepare() {
        teams.setup()

        scene.animate(1, gameHandle.rootScheduler)

        paintManager.data = data
        paintManager.onPaint = stats::blockPainted

        paintGunManager.init(gameHandle.hooks)

        setupSpecialItems(level, map)

        paintManager.countBlocks()

        val resultSpot = resultSpotFromJson(map.properties.getJSONObject("result-spot"))

        results = PaintballResults(gameHandle, announcer, level, resultSpot, data, winManager) {
            teams.mapNotNull { teamManager.getTeam(it) }
                .map { createReference(it) }
        }

        teamManager.partitionIntoTeams(gameHandle.participants, teams.map { it.key }.toHashSet())

        for (team in teamManager.minecraftTeams) {
            team.setSeeFriendlyInvisibles(true)
        }

        teleportTeamsToSpawns()
        equipPlayers()
        setupKits()
        setupPlayerCollisions()
        balanceTeams()

        commons().gameRuleBuilder()
            .set(GameRules.NATURAL_HEALTH_REGENERATION, false)
            .set(GameRules.FALL_DAMAGE, false)
    }

    private fun setupSpecialItems(world: ServerLevel, map: GameMap) {
        val validSpawns: LongSet = findReachablePositions(world, map)

        for (team in teams) {
            for (pos in team.baseBounds) {
                validSpawns.remove(pos.asLong())
            }
        }

        val validSpawn = BlockPredicate { pos -> validSpawns.contains(pos.asLong()) }

        specialItems = SpecialItems.create(
            gameHandle,
            map,
            world,
            random,
            validSpawn,
            commons(map, world).debugController()
        ) { r -> r.apply {
            register(MedKitItem(stats::specialItemUsed), 0.25f)
            register(InkGrenadeItem(paintGunManager, scene, random, teams, stats::specialItemUsed), 0.5f)
            register(InkPackItem(paintGunManager, stats::specialItemUsed), 0.15f)
            register(TripWireItem(gameHandle.translations, gameHandle.participants, world, teams, paintManager, stats::specialItemUsed), 0.15f)
        }}

        specialItems.isMarkGlowing = true
        specialItems.setup()
    }

    private fun findReachablePositions(world: ServerLevel, map: GameMap): LongSet {
        val bounds: BlockBox = SpecialItems.getSpawnArea(map).bounds()
        val predicate = BlockPredicate.and(bounds::contains, WalkableBlockPredicate(world))
        val adjacent: AdjacentBlocks = SimpleAdjacentBlocks(predicate, 1)
        val scanner: WorldScanner = BfsWorldScanner(adjacent)

        val spawns: LongSet = LongOpenHashSet()

        val anyTeam = teams.first()
        val startPos = BlockPos.containing(anyTeam.spawn)

        scanner.scan(startPos).forEachRemaining { pos ->
            spawns.add(pos.asLong())
        }

        return spawns
    }

    private fun setupPlayerCollisions() {
        val entityCollisions = EntityCollisionManager(level) { gameHandle.participants }
        entityCollisions.init(gameHandle.rootScheduler)

        teams.forEach { pbt ->
            teamManager.getTeam(pbt)?.let { team ->
                val group = teams.playerGroup(pbt)

                for (player in team.players) {
                    entityCollisions.getRigidBody(player).ifPresent { rb ->
                        rb.setCollisionGroup(group)
                    }
                }
            }
        }
    }

    private fun setupKits() {
        kitHandler = KitHandler.create(gameHandle, level) { kitHandle ->
            listOf(
                RifleKit(kitHandle, paintGunManager),
                ShotgunKit(kitHandle, paintGunManager),
                SniperKit(kitHandle, paintGunManager)
            )
        }

        kitHandler.setup()

        for (pbt in teams) {
            movementObserver.whenEntering(pbt.baseBounds) { player ->
                if (teams.isMember(pbt, player)) {
                    kitHandler.enableKitChanger(player)
                }
            }

            movementObserver.whenLeaving(pbt.baseBounds) { player ->
                if (teams.isMember(pbt, player)) {
                    kitHandler.disableKitChanger(player)
                }
            }
        }

        paintGunManager.injectKitManager(kitHandler.manager)
    }

    private fun equipPlayers() {
        for (instance in teams) {
            val team = teamManager.getTeam(instance) ?: continue
            val color = instance.key.color

            for (player in team.players) {
                player.setItemSlot(EquipmentSlot.HEAD, unbreakable(getLeatherArmor(Items.LEATHER_HELMET, color)))
                player.setItemSlot(EquipmentSlot.CHEST, unbreakable(getLeatherArmor(Items.LEATHER_CHESTPLATE, color)))
                player.setItemSlot(EquipmentSlot.LEGS, unbreakable(getLeatherArmor(Items.LEATHER_LEGGINGS, color)))
                player.setItemSlot(EquipmentSlot.FEET, unbreakable(getLeatherArmor(Items.LEATHER_BOOTS, color)))
            }
        }
    }

    override fun configureStartup(sequence: GameStartSequence) {
        sequence.beforeGo { next ->
            kitHandler.startKitSelectionTimer(this, announcer) { next.run() }
        }

        super.configureStartup(sequence)
    }

    override fun go() {
        teams.openBases()

        kitHandler.closeKitChanger()
        kitHandler.selectKitItem()

        respawnCooldown.setOnCooldownOver(::respawnPlayer)
        configureHooksAndProtector()

        paintGunManager.shootingEnabled = true

        val subject = gameHandle.translations.translateText(gameHandle.gameInfo.taskKey)
        createTimer(subject, DURATION).whenDone(::beginResults)

        started = true

        val ticker = PaintballTicker(
            level, gameHandle.participants, teams, paintManager,
            paintGunManager, vanishManager, commons().debugController()
        )

        ticker.start(gameHandle.scheduler, gameHandle.hooks)

        specialItems.spawnPeriodically()
    }

    private fun beginResults() {
        paintGunManager.shootingEnabled = false
        paintManager.freeze()

        results.beginResults()
    }

    private fun configureHooksAndProtector() {
        val hooks: HookRegistrar = gameHandle.hooks
        val participants: Participants = gameHandle.participants

        SpectatePlayerCallback.HOOK.registerWith(hooks) { spectator, _ ->
            participants.isParticipating(spectator)
        }

        gameHandle.protect { config ->
            ProtectionTypes.EXPLOSION.allow(config)

            ProtectionTypes.ALLOW_DAMAGE.allow(config) { entity, source ->
                entity is ServerPlayer && participants.isParticipating(entity)
                    && (source.isOf(DamageTypes.ARROW) || source.isOf(DamageTypes.PLAYER_EXPLOSION))
            }
        }

        ServerLivingEntityHooks.ALLOW_DAMAGE.registerWith(hooks, ::onDamage)
    }

    private fun respawnPlayer(player: ServerPlayer) {
        teams.teamOf(player)?.let { teleportToTeamSpawn(player, it) }

        player.health = player.maxHealth
        player.resetAttribute(Attributes.MAX_ABSORPTION)
        player.absorptionAmount = 0f
        player.abilities.flyingSpeed = 0f
        player.onUpdateAbilities()

        paintGunManager.refillPaintGun(player)

        gameHandle.scheduler.immediate(Runnable {
            player.abilities.flyingSpeed = 0.05f
            player.onUpdateAbilities()
            player.setGameMode(gameHandle.playerUtil.defaultGameMode)
        })
    }

    override fun teleportTeamsToSpawns() {
        for (pbt in teams) {
            val team = teamManager.getTeam(pbt) ?: continue

            for (player in team.players) {
                teleportToTeamSpawn(player, pbt)
            }
        }
    }

    private fun teleportToTeamSpawn(player: ServerPlayer, pbt: PaintballTeam) {
        val pos = pbt.spawn
        player.teleportTo(level, pos.x(), pos.y(), pos.z(), emptySet(), pbt.yaw, 0f, true)
    }

    private fun onDamage(entity: LivingEntity, source: DamageSource, amount: Float): Boolean {
        if (winManager.gameOver) return false

        val player = entity as? ServerPlayer ?: return false

        if (!gameHandle.participants.isParticipating(player)) return false

        val team = teams.teamOf(player) ?: return false

        if (team.baseBounds.contains(player.position())) return false

        if (!source.isOf(DamageTypes.PLAYER_EXPLOSION) && player.hurtTime > 0) return false

        if (source.isOf(DamageTypes.PLAYER_EXPLOSION)) {
            val attacker = source.entity as? ServerPlayer

            if (attacker != null && teamManager.areTeamMates(attacker, player)) return false
        }

        if ((player.health - amount) <= 0) {
            trackDamage(player, source, player.health)
            onLethalDamage(source, player, amount)
            return false
        }

        trackDamage(player, source, amount)

        return true
    }

    private fun trackDamage(victim: ServerPlayer, source: DamageSource, applied: Float) {
        if (applied <= 0f) return

        val attacker = source.entity as? ServerPlayer ?: return

        if (attacker === victim || teamManager.areTeamMates(attacker, victim)) return

        stats.damageDealt(attacker, applied)
    }

    private fun onLethalDamage(source: DamageSource, player: ServerPlayer, amount: Float) {
        player.combatTracker.recordDamage(source, amount)

        val killer = source.entity as? ServerPlayer

        if (killer != null && killer !== player && !teamManager.areTeamMates(killer, player)) {
            stats.onKill(player, killer)
        }

        gameHandle.deathMessages.getDeathMessage(player, source)
            .sendTo(PlayerLookup.all(gameHandle.server))

        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_DEATH, SoundSource.PLAYERS, 0.8f, 0.8f)

        player.setGameMode(GameType.SPECTATOR)
        player.health = 20f

        respawnCooldown.setCooldown(player, 50)
    }

    override fun participantRemoved(player: ServerPlayer) {
        balanceTeams()

        super.participantRemoved(player)
    }

    private fun balanceTeams() {
        for (team in teams) {
            val players: Set<ServerPlayer> = team.participants(teamManager, gameHandle.participants)

            if (players.isEmpty()) continue

            val deficit = teams.playerDeficit(team)
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
}
