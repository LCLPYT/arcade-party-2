package work.lclpnet.ap2.game.killeporter

import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.Container
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.gamerules.GameRules
import net.minecraft.world.level.material.Fluids
import work.lclpnet.ap2.ext.*
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.ext.mc.setDayTime
import work.lclpnet.ap2.ext.mc.teleport
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.base.EliminationGameInstance
import work.lclpnet.ap2.game.kit.KitHandle
import work.lclpnet.ap2.game.kit.KitHandler
import work.lclpnet.ap2.game.kit.PrefabKitLoader
import work.lclpnet.ap2.game.util.GameStartSequence
import work.lclpnet.ap2.game.util.useAnnouncer
import work.lclpnet.ap2.game.util.useSurvivalMode
import work.lclpnet.ap2.impl.util.SoundHelper
import work.lclpnet.ap2.util.loot.LazyLootContainerManager
import work.lclpnet.ap2.util.loot.LootEntry
import work.lclpnet.ap2.util.loot.LootFiller
import work.lclpnet.gaco.ds.WeightedList
import work.lclpnet.game.impl.prot.ProtectionTypes
import work.lclpnet.game.map.GameMap
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks
import work.lclpnet.kibu.hook.level.BlockModificationHooks
import work.lclpnet.kibu.hook.util.PlayerUtils
import work.lclpnet.kibu.hook.util.PositionRotation
import work.lclpnet.kibu.scheduler.Ticks
import work.lclpnet.kibu.translate.text.FormatWrapper
import java.lang.Math.floorMod
import kotlin.random.Random
import kotlin.random.asJavaRandom
import kotlin.time.Duration.Companion.seconds

val MIN_DURATION_TICKS = Ticks.seconds(18)
val MAX_DURATION_TICKS = Ticks.seconds(32)
val GAME_DURATION_TICKS = Ticks.minutes(6)
const val TIME_TO_NIGHTFALL_DAYTIME_TICKS = 3600

class KilleporterInstance(
    gameHandle: MiniGameHandle,
    level: ServerLevel,
    map: GameMap,
    private val kitLoader: PrefabKitLoader,
    private val loot: WeightedList<LootEntry>,
) : EliminationGameInstance(gameHandle, level, map) {

    val announcer = useAnnouncer()
    var kitHandler: KitHandler? = null
    var itemUseAllowed = false
    lateinit var lootContainerManager: LazyLootContainerManager

    init {
        useSurvivalMode()
    }

    override fun prepare() {
        lootContainerManager = LazyLootContainerManager(
            players(),
            level,
            KilleporterLootFiller(loot),
        ).also { it.setup(gameHandle.hooks) }

        level.setDayTime(13000 - TIME_TO_NIGHTFALL_DAYTIME_TICKS)

        commons().gameRuleBuilder()
            .set(GameRules.FALL_DAMAGE, true)
            .set(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0)
            .set(GameRules.SPAWN_PHANTOMS, false)
            .set(GameRules.NATURAL_HEALTH_REGENERATION, true)
            .set(GameRules.KEEP_INVENTORY, false)
            .set(GameRules.ADVANCE_TIME, false)
            .set(GameRules.SPAWN_MOBS, true)
            .set(GameRules.MOB_DROPS, true)
            .set(GameRules.MOB_GRIEFING, true)
            .set(GameRules.ENTITY_DROPS, true)
            .set(GameRules.SHOW_ADVANCEMENT_MESSAGES, false)

        useRemainingPlayersDisplay()
        useSmoothDeath()
        setupKits()

        ServerLivingEntityHooks.ALLOW_DAMAGE.registerWith(hooks) { entity, _, _ ->
            if (entity is ServerPlayer && entity.foodData.foodLevel >= 20) {
                entity.foodData.addExhaustion(8f)
                entity.foodData.setSaturation(2f)
            }

            true
        }
    }

    override fun configureStartup(sequence: GameStartSequence) {
        sequence.beforeGo { next ->
            kitHandler?.startKitSelectionTimer(this, announcer, 15.seconds) { next.run() }
        }

        super.configureStartup(sequence)
    }

    override fun go() {
        kitHandler?.disableKitChanger()

        itemUseAllowed = true

        commons().gameRuleBuilder().set(GameRules.ADVANCE_TIME, true)

        gameHandle.protect { config ->
            config.allowAll()
            ProtectionTypes.ALLOW_DAMAGE.disallow(config) { entity, source ->
                entity is ServerPlayer && source.entity is ServerPlayer
                        && !source.isOf(DamageTypes.PLAYER_EXPLOSION)
                        && !source.isOf(DamageTypes.INDIRECT_MAGIC)
                        && !source.isOf(DamageTypes.MAGIC)
            }
        }

        BlockModificationHooks.PLACE_FLUID.registerWith(hooks) { _, pos, entity, fluid ->
            val minDistSq = 6.0 * 6.0
            entity is ServerPlayer && fluid.isSame(Fluids.LAVA) && players().any {
                it != entity && it.distanceToSqr(pos.center) < minDistSq
            }
        }

        BlockModificationHooks.BREAK_BLOCK.registerWith(hooks) { world, pos, entity ->
            if (entity is ServerPlayer && world.getBlockState(pos).isOf(Blocks.DECORATED_POT)) {
                lootContainerManager.touch(pos)
            }

            false
        }

        switchTimeout()

        gameHandle.scheduler.interval(20*60*3, 20*60*3, Runnable {
            SoundHelper.playSound(level, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.8f, 0.5f)
            translate("game.ap2.killeporter.chest_refill").formatted(ChatFormatting.AQUA).sendTo(allPlayers())
            lootContainerManager.reset()
        })

        timeout(GAME_DURATION_TICKS) {
            winManager.forceWin(players().toSet())
        }
    }

    fun switchTimeout() {

        val maxDelaySeconds = 7
        val switchTime =  Random.nextInt(MIN_DURATION_TICKS, MAX_DURATION_TICKS+1)
        val messageTime = Random.nextInt(Ticks.seconds(1), Ticks.seconds(maxDelaySeconds)+1)

        timeout(switchTime - messageTime) {
            translate("game.ap2.killeporter.switch_announcement", FormatWrapper.styled(maxDelaySeconds, ChatFormatting.YELLOW))
            .formatted(ChatFormatting.GREEN)
            .sendTo(players(), true)}

        timeout(switchTime) {
            playerSwitcher()
            switchTimeout()
        }
    }

    fun switchAnnouncement() {
        timeout(MIN_DURATION_TICKS) {
            switchAnnouncement()
        }
    }

    fun playerSwitcher() {
        val shuffledPlayers = players().shuffled()
        val playerCount = shuffledPlayers.count()
        val positionRotations = shuffledPlayers.map { player ->
            PositionRotation(player.x, player.y, player.z, player.yRot, player.xRot)
        }

        for (p in (0 ..< playerCount)) {
            val previousIndex = floorMod(p-1, playerCount)
            shuffledPlayers[p].teleport(positionRotations[previousIndex])
            translate("game.ap2.killeporter.switch_message", shuffledPlayers[previousIndex].scoreboardName)
                .formatted(ChatFormatting.GREEN)
                .sendTo(shuffledPlayers[p], true)
        }
    }

    private fun setupKits() {
        kitHandler = KitHandler.create(gameHandle, level) { kitHandle: KitHandle -> kitLoader.createKits(kitHandle) }

        kitHandler?.manager?.modifyOptions {
            it.withKitSelectorSlot(8)
        }

        PlayerInteractionHooks.USE_ITEM.registerWith(hooks) { player: Player, _: Level, hand: InteractionHand ->
            if (player !is ServerPlayer) return@registerWith InteractionResult.PASS

            val stack = player.getItemInHand(hand)

            if (itemUseAllowed || kitHandler!!.isKitSelector(stack)) {
                return@registerWith InteractionResult.PASS
            }

            if (stack.has(DataComponents.USE_COOLDOWN)) {
                player.cooldowns.addCooldown(stack, 0)
            }

            PlayerUtils.syncPlayerItems(player)
            InteractionResult.FAIL
        }

        kitHandler?.setup()
    }
}

class KilleporterLootFiller(
    val loot: WeightedList<LootEntry>,
) : LootFiller {

    override fun fill(
        pos: BlockPos,
        level: ServerLevel,
        container: Container
    ) {
        val state = level.getBlockState(pos)

        container.clearContent()

        val invSize = container.containerSize
        val availableSlots = (0..<invSize).toMutableList()
        val maxSlotsToFill = 5.coerceAtMost(invSize)

        val slotsToFill = if (state.block == Blocks.DECORATED_POT) {
            Random.nextInt(0, maxSlotsToFill + 1)
        } else { Random.nextInt(1, maxSlotsToFill + 1) }

        repeat(slotsToFill) {
            val slot = availableSlots.removeAt(Random.nextInt(availableSlots.size))
            val entry = loot.getRandomElement(Random.asJavaRandom())
            container.setItem(slot, entry!!.generateItemStack())
        }
    }
}