package work.lclpnet.ap2.game.eggventure

import com.mojang.math.Transformation
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.RegistryAccess
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.numbers.StyledFormat
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.Items
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.SkullBlock
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.criteria.ObjectiveCriteria
import org.joml.Matrix4f
import work.lclpnet.ap2.api.stats.Stat
import work.lclpnet.ap2.api.util.heads.PlayerHead
import work.lclpnet.ap2.ext.hooks
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.ext.players
import work.lclpnet.ap2.ext.scheduler
import work.lclpnet.ap2.ext.translations
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.base.FFAGameInstance
import work.lclpnet.ap2.game.util.GameStartSequence
import work.lclpnet.ap2.game.util.createTimer
import work.lclpnet.ap2.game.util.finaleCompatibleScoreContainer
import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.impl.tags.PlayerHeadTags
import work.lclpnet.ap2.impl.util.ApRegistries
import work.lclpnet.ap2.impl.util.ColorUtil
import work.lclpnet.ap2.impl.util.ItemHelper
import work.lclpnet.ap2.impl.util.RayCastUtil
import work.lclpnet.ap2.impl.util.checkpoint.CheckpointHelper
import work.lclpnet.gaco.ds.BlockBox
import work.lclpnet.gaco.dynamic_entities.DynamicEntityManager
import work.lclpnet.game.map.GameMap
import work.lclpnet.kibu.access.entity.ServerPlayerAccess
import work.lclpnet.kibu.access.misc.CustomNbt
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks
import work.lclpnet.kibu.hook.player.PlayerSwingHandHook
import work.lclpnet.kibu.scheduler.Ticks
import work.lclpnet.kibu.translate.text.FormatWrapper.styled
import java.util.*
import kotlin.math.PI
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

const val DEBUG_EGG_POSITIONS = false
private const val STEAL_RANGE = 10.0
private val NBT_CODEC: MapCodec<Boolean> = Codec.BOOL.fieldOf("easter_egg")

private val DURATION = 1.minutes + 30.seconds

private val EggsStolen = Stat("eggs_stolen", 0)
private val EggsLost = Stat("eggs_lost", 0)

fun eggVariants(registryManager: RegistryAccess): List<PlayerHead> {
    val headEntries = registryManager
        .lookupOrThrow(ApRegistries.PLAYER_HEAD)
        .getTagOrEmpty(PlayerHeadTags.EASTER_EGGS)

    return headEntries.map { it.value() }
}

fun isEasterEgg(world: ServerLevel, pos: BlockPos): Boolean {
    val state = world.getBlockState(pos)

    if (!state.isOf(Blocks.PLAYER_HEAD) && !state.isOf(Blocks.PLAYER_WALL_HEAD)) return false

    val skull = world.getBlockEntity(pos, BlockEntityType.SKULL).orElse(null) ?: return false

    return CustomNbt.get(skull.components(), NBT_CODEC).orElse(false) ?: false
}

class EggventureInstance(
    gameHandle: MiniGameHandle,
    level: ServerLevel,
    map: GameMap,
    private val remainingPositions: MutableSet<BlockPos>,
) : FFAGameInstance(gameHandle, level, map) {

    override val data = finaleCompatibleScoreContainer(gameHandle, PlayerRef::create)
    private val stats = createStats(data, EggsStolen, EggsLost)
    private val random = Random()

    override fun prepare() {
        DebugEggsCommand(gameHandle.logger).register(gameHandle.commands)

        val variants = eggVariants(level.registryAccess())

        if (variants.isEmpty()) {
            throw IllegalStateException("There are no egg variants defined")
        }

        for (player in gameHandle.participants) {
            val variant = variants[random.nextInt(variants.size)]
            player.setItemSlot(EquipmentSlot.HEAD, variant.createStack())

            val color = ColorUtil.getRandomHsvColor(random)

            player.setItemSlot(EquipmentSlot.CHEST, ItemHelper.getLeatherArmor(Items.LEATHER_CHESTPLATE, color))
            player.setItemSlot(EquipmentSlot.LEGS, ItemHelper.getLeatherArmor(Items.LEATHER_LEGGINGS, color))
            player.setItemSlot(EquipmentSlot.FEET, ItemHelper.getLeatherArmor(Items.LEATHER_BOOTS, color))
        }

        val scoreboardManager = gameHandle.scoreboardManager

        val objective = scoreboardManager.createObjective(
            "points", ObjectiveCriteria.DUMMY,
            Component.literal("Points").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
            ObjectiveCriteria.RenderType.INTEGER,
            StyledFormat.PLAYER_LIST_DEFAULT
        )

        useScoreboardStatsSync(data, objective)

        scoreboardManager.setDisplay(DisplaySlot.LIST, objective)
    }

    override fun configureStartup(sequence: GameStartSequence) {
        sequence.beforeGo { next ->
            val dynamicEntityManager = DynamicEntityManager(level)
            val tutorial = EggventureTutorial(level, dynamicEntityManager, random, translations)

            dynamicEntityManager.init(scheduler, hooks)
            tutorial.start(scheduler, players()).thenRun { next.run() }
        }

        super.configureStartup(sequence)
    }

    override fun go() {
        val gate: BlockBox = MapUtil.readBox(map.requireProperty("gate"))

        for (pos in gate) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState())
        }

        val hooks = gameHandle.hooks

        PlayerInteractionHooks.USE_BLOCK.registerWith(hooks) { player, _, hand, hitResult ->
            val pos = hitResult.blockPos

            if (player is ServerPlayer
                    && gameHandle.participants.isParticipating(player)
                    && hand == InteractionHand.MAIN_HAND
                    && isEasterEgg(level, pos)) {
                onFindEasterEgg(player, pos)
            }

            InteractionResult.PASS
        }

        PlayerSwingHandHook.HOOK.registerWith(hooks) { player, hand ->
            if (hand != InteractionHand.MAIN_HAND || !gameHandle.participants.isParticipating(player)) return@registerWith

            val range = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE)

            val hit = RayCastUtil.raycast(
                level, player.eyePosition, player.lookAngle, range,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.empty()
            ) { !it.isSpectator }

            if (hit !is BlockHitResult) return@registerWith

            val pos = hit.blockPos

            if (isEasterEgg(level, pos)) {
                onFindEasterEgg(player, pos)
            }
        }

        val subject = gameHandle.translations.translateText(gameHandle.gameInfo.taskKey)

        createTimer(subject, DURATION.inWholeSeconds.toInt()).whenDone {
            completeAndShowRemaining()
        }

        gameHandle.scheduler.interval(
            20,
            Ticks.seconds(10),
            Runnable(::checkNearbyEggs)
        )

        CheckpointHelper.setupResetItem(hooks, { winManager.isGameOver }) {
            gameHandle.participants.isParticipating(it)
        }.then(::reset)

        CheckpointHelper.giveResetItem(gameHandle.participants, level, gameHandle.translations, 4)
    }

    private fun reset(player: ServerPlayer) {
        gameHandle.worldFacade.teleport(player)
    }

    private fun checkNearbyEggs() {
        val checkDistSq = 20.0 * 20.0

        for (player in gameHandle.participants) {
            if (remainingPositions.any { pos ->
                player.distanceToSqr(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5) < checkDistSq
            }) continue

            gameHandle.translations.translateText("game.ap2.eggventure.no_eggs_nearby")
                .formatted(ChatFormatting.RED)
                .sendTo(player, true)
        }
    }

    private fun completeAndShowRemaining() {
        if (winManager.isGameOver) return

        winManager.complete()

        for (pos in remainingPositions) {
            val state = level.getBlockState(pos)
            val stack = ItemHelper.getStackWithData(level, pos)

            if (!state.isOf(Blocks.PLAYER_HEAD)) {
                gameHandle.logger.warn("Unexpected block: {}", state)
                continue
            }

            val rotation = state.getValueOrElse(SkullBlock.ROTATION, 0)

            val display = Display.ItemDisplay(EntityType.ITEM_DISPLAY, level)
            display.itemStack = stack
            display.setPos(pos.center)
            display.setTransformation(Transformation(Matrix4f().rotateY((rotation / -8f * PI).toFloat())))
            display.setGlowingTag(true)

            level.addFreshEntity(display)
        }

        gameHandle.translations.translateText("game.ap2.eggventure.eggs_left", styled(remainingPositions.size, ChatFormatting.YELLOW))
            .formatted(ChatFormatting.GREEN)
            .sendTo(gameHandle.participants, true)
    }

    private fun onFindEasterEgg(player: ServerPlayer, pos: BlockPos) {
        if (winManager.isGameOver) return

        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_SUPPRESS_DROPS or Block.UPDATE_KNOWN_SHAPE or Block.UPDATE_CLIENTS)

        commons().addScore(player, 1, data)
        trackSteal(player)

        val x = pos.x + 0.5
        val y = pos.y.toDouble()
        val z = pos.z + 0.5

        level.playSound(null, x, y, z, SoundEvents.ALLAY_THROW, SoundSource.PLAYERS, 1f, 1f)
        ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 0.75f, 1.2f)

        level.sendParticles(ParticleTypes.CRIMSON_SPORE, x, y, z, 75, 0.25, 0.25, 0.25, 0.0)
        level.sendParticles(ParticleTypes.WARPED_SPORE, x, y, z, 75, 0.25, 0.25, 0.25, 0.0)
        level.sendParticles(ParticleTypes.GLOW, x, y, z, 25, 0.5, 0.5, 0.5, 0.0)

        remainingPositions.remove(pos)
    }

    private fun trackSteal(finder: ServerPlayer) {
        val rangeSq = STEAL_RANGE * STEAL_RANGE
        var stole = false

        for (other in gameHandle.participants) {
            if (other === finder || other.distanceToSqr(finder) > rangeSq) continue

            stats.increment(other, EggsLost)
            stole = true
        }

        if (stole) stats.increment(finder, EggsStolen)
    }
}
