package work.lclpnet.ap2.assassins

import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Style
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.BossEvent
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.border.WorldBorder
import net.minecraft.world.level.gamerules.GameRules
import net.minecraft.world.phys.Vec3
import net.minecraft.world.scores.TeamColor
import work.lclpnet.ap2.api.stats.CommonStats
import work.lclpnet.ap2.api.stats.Stat
import work.lclpnet.ap2.ext.*
import work.lclpnet.ap2.ext.mc.playNotifySound
import work.lclpnet.ap2.ext.mc.setSelectedSlot
import work.lclpnet.ap2.ext.mc.unbreakable
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.base.EliminationGameInstance
import work.lclpnet.ap2.game.util.createTimer
import work.lclpnet.ap2.game.util.useFFAStats
import work.lclpnet.ap2.game.util.useOldCombat
import work.lclpnet.ap2.game.util.whenBelowY
import work.lclpnet.ap2.impl.game.GameCommons
import work.lclpnet.ap2.impl.util.ItemHelper.getLeatherArmor
import work.lclpnet.ap2.impl.util.ParticleHelper
import work.lclpnet.ap2.impl.util.SoundHelper
import work.lclpnet.ap2.impl.util.VanishManager
import work.lclpnet.ap2.impl.util.movement.SimpleMovementBlocker
import work.lclpnet.ap2.impl.util.world.*
import work.lclpnet.gaco.ds.BlockBox
import work.lclpnet.gaco.ds.StructureMask
import work.lclpnet.game.impl.prot.ProtectionTypes
import work.lclpnet.game.map.GameMap
import work.lclpnet.game.util.BossBarTimer
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks
import work.lclpnet.kibu.scheduler.api.TaskHandle
import work.lclpnet.kibu.util.math.Matrix3i
import work.lclpnet.pal.PalApi
import java.util.*
import kotlin.random.Random
import kotlin.random.asJavaRandom
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

private val TARGET_COLOR = TeamColor.RED
private val ASSASSIN_COLOR = TeamColor.BLUE
private val PREPARE_DURATION = 10.seconds
private val INVISIBILITY_DURATION = 6.seconds
private val JUMP_BOOST_DURATION = 8.seconds
private val REVEAL_DURATION = 6.seconds
private val WORLD_BORDER_SHRINK_START_DELAY = 45.seconds
private const val ITEM_COOLDOWN_TICKS = 20
private const val WORLD_BORDER_SHRINK_PER_SECOND = 1.5
private const val SPAWN_SPACING_DEFAULT = 10.0
const val DEBUG_ALWAYS_GIVE_ITEM = false
const val DEBUG_SPAWN_POSITIONS = false
const val DEBUG_SCANNED_POSITIONS = false

val Kills = CommonStats.Kills
val DamageDealt = CommonStats.DamageDealt
val DistanceMoved = CommonStats.DistanceMoved
val DamageReceived = Stat("damage_received", 0f, higherIsBetter = false)
val ItemsUsed = Stat("items_used", 0)

class AssassinsInstance(
    gameHandle: MiniGameHandle,
    level: ServerLevel,
    map: GameMap,
    private val schema: AssassinsMapSchema
) : EliminationGameInstance(gameHandle, level, map) {

    private val random = Random(Clock.System.now().toEpochMilliseconds())
    private val spawnSpacing = map.properties.optNumber("spawn-spacing", SPAWN_SPACING_DEFAULT).toDouble()
    private val targets = AssassinTargets()
    private val glow = AssassinGlowHandler(gameHandle.server, gameHandle.scoreboardManager)
    private val vanishManager = VanishManager.setup(gameHandle)
    private val movementBlocker = SimpleMovementBlocker(gameHandle.rootScheduler).also {
        it.setModifySpeedAttribute(false)
    }
    private val colors = HashMap<UUID, DyeColor>()
    private val rewarded = HashSet<UUID>()
    private val revealTasks = mutableListOf<TaskHandle>()
    private val stats = useFFAStats(winManager, listOf(
        Kills, DamageDealt, DamageReceived, ItemsUsed, DistanceMoved
    ))

    private var spawns: List<Vec3> = emptyList()
    private var wbConfig: GameCommons.WorldBorderConfig? = null
    private var roundActive = false
    private var prepTimer: BossBarTimer? = null
    private var nextRoundTask: TaskHandle? = null
    private var worldBorderDelayTask: TaskHandle? = null

    init {
        useOldCombat()
    }

    override fun teleportPlayers() {
        computeSpawns()

        teleportToSpacedSpawns(allPlayers().filter { isParticipating(it) })

        for (player in allPlayers()) {
            if (!isParticipating(player)) {
                gameHandle.worldFacade.teleport(player)
            }
        }
    }

    override fun prepare() {
        level.gameRules.apply {
            set(GameRules.FALL_DAMAGE, false, server)
            set(GameRules.NATURAL_HEALTH_REGENERATION, false, server)
            set(GameRules.ENTITY_DROPS, false, server)
        }

        wbConfig = runCatching { commons().readWorldBorderConfig() }.getOrNull()

        assignColors()

        glow.init(gameHandle.hooks, players())
        movementBlocker.init(gameHandle.hooks)

        for (player in players()) {
            movementBlocker.disableMovement(player)
        }

        ServerLivingEntityHooks.ALLOW_DAMAGE.registerWith(gameHandle.hooks, ::onDamage)

        trackDistanceMoved(stats)

        disableTeleportEliminated()
        useSmoothDeath()
        useRemainingPlayersDisplay()

        registerSpecialItemUse()

        setupLevitation()
    }

    private fun setupLevitation() {
        val floatHeight = map.properties.optNumber("float-height") ?: return

        whenBelowY(floatHeight.toDouble()) { player ->
            if (player.hasEffect(MobEffects.LEVITATION)) return@whenBelowY

            player.addEffect(
                MobEffectInstance(
                    MobEffects.LEVITATION,
                    35,
                    15,
                    false,
                    false,
                    false
                )
            )

            SoundHelper.playSoundAt(player, SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, SoundSource.PLAYERS, 0.2f, 1f)
            ParticleHelper.spawnParticleAt(player, ParticleTypes.END_ROD, 50, 0.5, 0.5, 0.5, 0.1)
        }
    }

    override fun go() {
        gameHandle.protect { config ->
            ProtectionTypes.ALLOW_DAMAGE.allow(config, ::isDamageAllowed)
        }

        startRound(initial = true)
    }

    private fun startRound(initial: Boolean) {
        if (winManager.gameOver) return

        roundActive = false
        prepTimer?.stop()
        prepTimer = null
        revealTasks.forEach(TaskHandle::cancel)
        revealTasks.clear()
        glow.clearAll()

        resetWorldBorder()

        val alive = players().toList()

        if (!initial) teleportToSpacedSpawns(alive)

        for (player in alive) {
            movementBlocker.disableMovement(player)
            resetPlayer(player)
            equip(player)

            if (DEBUG_ALWAYS_GIVE_ITEM || player.uuid in rewarded) {
                giveSpecialItem(player, chooseRandomItem())
            }
        }

        rewarded.clear()

        targets.assign(alive, random)

        for (player in alive) {
            val target = targets.targetOf(player) ?: continue
            glow.setGlow(player, target, TARGET_COLOR)
            sendTargetMessage(player, target)
        }

        if (initial) {
            beginCombat()
        } else {
            val label = translate("prepare")
            prepTimer = createTimer(label, PREPARE_DURATION, BossEvent.BossBarColor.YELLOW)
            prepTimer!!.whenDone { beginCombat() }
        }
    }

    private fun chooseRandomItem(): AssassinsSpecialItem {
        return if (players().count() >= 3) {
            AssassinsSpecialItem.random(random)
        } else {
            AssassinsSpecialItem.entries.toMutableList().also {
                it.remove(AssassinsSpecialItem.REVEAL_ASSASSIN)
            }.random()
        }
    }

    private fun resetWorldBorder() {
        val worldBorder = gameHandle.worldBorderManager.getWorldBorder()
        worldBorder.setCenter(0.0, 0.0)
        worldBorder.size = WorldBorder.MAX_SIZE
    }

    private fun beginCombat() {
        if (winManager.gameOver) return

        roundActive = true

        for (player in players()) {
            movementBlocker.enableMovement(player)
        }

        wbConfig?.let { config ->
            worldBorderDelayTask = runAfter(WORLD_BORDER_SHRINK_START_DELAY) {
                val durationTicks = (config.maxRadius() / WORLD_BORDER_SHRINK_PER_SECOND * 20).toLong()
                commons().startWorldBorderShrink(config, durationTicks, random.asJavaRandom())
            }
        }
    }

    override fun onDeath(player: ServerPlayer, attacker: Entity?) {
        // the round ends as soon as a player dies
        roundActive = false

        if (attacker is ServerPlayer && targets.targetOf(attacker) === player) {
            gainKill(attacker, stats)
            rewarded.add(attacker.uuid)
        }

        // intentionally not calling super.onDeath -> no equipment/experience drops
    }

    override fun onEliminated(player: ServerPlayer) {
        super.onEliminated(player)

        targets.remove(player)
        glow.removePlayer(player)

        nextRoundTask?.cancel()
        nextRoundTask = null

        worldBorderDelayTask?.cancel()
        worldBorderDelayTask = null

        if (!winManager.gameOver) {
            startRound(initial = false)
        }
    }

    private fun isDamageAllowed(entity: Entity, source: DamageSource): Boolean {
        if (!roundActive) return false

        val attacker = source.entity

        if (attacker is ServerPlayer) {
            return entity is ServerPlayer && targets.targetOf(attacker) === entity
        }

        // environmental damage (e.g. world border); fall damage is disabled via game rule
        return true
    }

    private fun onDamage(entity: LivingEntity, source: DamageSource, amount: Float): Boolean {
        if (entity !is ServerPlayer) return true

        val attacker = source.entity

        if (attacker is ServerPlayer && attacker !== entity) {
            if (!roundActive || targets.targetOf(attacker) !== entity) {
                if (roundActive) sendOffTargetFeedback(attacker)
                return false
            }

            val dealt = amount.coerceAtMost(entity.health)
            stats.modify(attacker, DamageDealt) { it + dealt }
            stats.modify(entity, DamageReceived) { it + dealt }
            return true
        }

        return isDamageAllowed(entity, source)
    }

    private fun registerSpecialItemUse() {
        PlayerInteractionHooks.USE_ITEM.registerWith(gameHandle.hooks) { player, _, hand ->
            if (player !is ServerPlayer || !isParticipating(player) || !roundActive) {
                return@registerWith InteractionResult.PASS
            }

            val stack = player.getItemInHand(hand)
            val type = AssassinsSpecialItem.byStack(stack) ?: return@registerWith InteractionResult.PASS

            val cooldowns = player.cooldowns

            if (cooldowns.isOnCooldown(stack)) {
                return@registerWith InteractionResult.FAIL
            }

            cooldowns.addCooldown(stack, ITEM_COOLDOWN_TICKS)

            useSpecialItem(player, type)
            stack.shrink(1)
            stats.increment(player, ItemsUsed)

            InteractionResult.FAIL
        }
    }

    private fun useSpecialItem(player: ServerPlayer, type: AssassinsSpecialItem) {
        when (type) {
            AssassinsSpecialItem.INVISIBILITY -> {
                vanishManager.vanish(player)
                player.addEffect(MobEffectInstance(MobEffects.INVISIBILITY, INVISIBILITY_DURATION.inWholeTicks.toInt(), 0, false, false, true))
                player.playNotifySound(SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 1.2f)
                runAfter(INVISIBILITY_DURATION) {
                    vanishManager.show(player)
                    player.playNotifySound(SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.5f, 0.5f)
                }
            }
            AssassinsSpecialItem.JUMP_BOOST -> {
                player.addEffect(MobEffectInstance(MobEffects.JUMP_BOOST, JUMP_BOOST_DURATION.inWholeTicks.toInt(), 4, false, false, true))
                player.playNotifySound(SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 1.6f)
            }
            AssassinsSpecialItem.REVEAL_ASSASSIN -> revealAssassin(player)
        }
    }

    private fun revealAssassin(viewer: ServerPlayer) {
        val hunter = targets.hunterOf(viewer) ?: return

        glow.setGlow(viewer, hunter, ASSASSIN_COLOR)
        viewer.playNotifySound(SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 0.8f)

        val task = runAfter(REVEAL_DURATION) {
            // restore the target glow if the hunter also happens to be this viewer's target (two players left)
            if (targets.targetOf(viewer) === hunter) {
                glow.setGlow(viewer, hunter, TARGET_COLOR)
            } else {
                glow.clearGlow(viewer, hunter)
            }
        }

        revealTasks.add(task)
    }

    private fun sendOffTargetFeedback(attacker: ServerPlayer) {
        val text = translate( "off_target")
            .withStyle(ChatFormatting.RED)
            .translateFor(attacker)

        attacker.sendOverlayMessage(text)
        attacker.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.5f, 1.0f)
    }

    private fun sendTargetMessage(player: ServerPlayer, target: ServerPlayer) {
        val text = translate("target", target.name)
            .withStyle(ChatFormatting.RED)
            .translateFor(player)

        player.sendSystemMessage(text)
    }

    private fun assignColors() {
        colors.clear()

        val palette = DyeColor.entries.shuffled(random)

        for ((i, player) in players().withIndex()) {
            colors[player.uuid] = palette[i % palette.size]
        }
    }

    private fun resetPlayer(player: ServerPlayer) {
        player.inventory.clearContent()
        player.removeAllEffects()
        vanishManager.show(player)
        player.health = player.maxHealth
        player.foodData.foodLevel = 20
    }

    private fun equip(player: ServerPlayer) {
        val colorInt = (colors[player.uuid] ?: DyeColor.WHITE).textureDiffuseColor

        player.setItemSlot(EquipmentSlot.HEAD, getLeatherArmor(Items.LEATHER_HELMET, colorInt).unbreakable())
        player.setItemSlot(EquipmentSlot.CHEST, getLeatherArmor(Items.LEATHER_CHESTPLATE, colorInt).unbreakable())
        player.setItemSlot(EquipmentSlot.LEGS, getLeatherArmor(Items.LEATHER_LEGGINGS, colorInt).unbreakable())
        player.setItemSlot(EquipmentSlot.FEET, getLeatherArmor(Items.LEATHER_BOOTS, colorInt).unbreakable())

        val sword = ItemStack(Items.STONE_SWORD).unbreakable()
        player.inventory.setItem(0, sword)
        player.setSelectedSlot(0)
    }

    private fun giveSpecialItem(player: ServerPlayer, type: AssassinsSpecialItem) {
        val stack = ItemStack(type.item)

        stack.set(DataComponents.CUSTOM_NAME, gameHandle.translations.translateText(player, type.translationKey)
            .setStyle(Style.EMPTY.withItalic(false).withColor(ChatFormatting.LIGHT_PURPLE)))

        player.inventory.setItem(4, stack)
    }

    private fun teleportToSpacedSpawns(players: List<ServerPlayer>) {
        if (players.isEmpty()) return

        if (spawns.isEmpty()) computeSpawns()

        // pick a spread-out subset so players keep at least spawnSpacing blocks apart where possible
        val finder = SpawnFinder(spawnSpacing, commons().debugController())
        val spaced = finder.generateSpacedSpawns(spawns, players.size, random.asJavaRandom())

        for ((i, player) in players.withIndex()) {
            val pos = spaced[i]
            player.teleportTo(level, pos.x, pos.y, pos.z, emptySet(), player.yRot, player.xRot, true)
        }
    }

    private fun computeSpawns() {
        if (spawns.isNotEmpty()) return

        val box = schema.spawnBox
            ?: throw IllegalStateException("Map property \"Spawn box\" is not set in the assassins schema")

        val starts = schema.scanStarts

        check(starts.isNotEmpty()) {
            "Map property \"Spawn scanner starts\" is not set in the assassins schema"
        }

        val contraptionService = PalApi.getInstance().contraptionService

        val walkable = WalkableBlockPredicate(level)

        // 3D flood fill from the scan starts through open space (air / slim collision),
        // bounded by the spawn box -> the reachable play area, excluding sealed cavities.
        val adjacent = CardinalAdjacentBlocks { pos ->
            box.contains(pos) && WalkableBlockPredicate.isPassable(level, pos)
        }

        val scanned = BfsWorldScanner(adjacent).scan(starts.toSet())

        val reachable: Iterator<BlockPos> = if (DEBUG_SCANNED_POSITIONS) {
            scanned.asSequence().toList()
                .also { visualizeScannedPositions(box, it) }
                .iterator()
        } else {
            scanned
        }

        val ground = iterator {
            for (pos in reachable) {
                if (!walkable.test(pos)) continue

                if (contraptionService.isBoosterPlate(level, pos)) continue

                yield(pos.immutable())
            }
        }

        val finder = SizedSpaceFinder.create(level, EntityTypes.PLAYER)
        spawns = finder.findSpaces(ground.iterator())

        if (spawns.isEmpty()) {
            throw IllegalStateException("No valid floor spawns found within the assassins spawn box")
        }

        if (DEBUG_SPAWN_POSITIONS) {
            commons().debugController().renderer().ifPresent { renderer ->
                for (pos in spawns) {
                    renderer.marker(pos, Blocks.CONCRETE.green.defaultBlockState(), 0x00ff00)
                }
            }
        }
    }

    private fun visualizeScannedPositions(box: BlockBox, positions: List<BlockPos>) {
        val mask = StructureMask.createEmpty(box)
        val min = box.min()

        for (pos in positions) {
            if (!box.contains(pos)) continue

            mask.setVoxelAt(pos.x - min.x, pos.y - min.y, pos.z - min.z, true)
        }

        commons().debugController().visualizeStructureMask(
            mask, min, Matrix3i.IDENTITY, Blocks.STAINED_GLASS.lightBlue.defaultBlockState()
        )
    }
}
