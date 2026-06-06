package work.lclpnet.ap2.game.button_master

import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.BlockTags
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.equipment.trim.ArmorTrim
import net.minecraft.world.item.equipment.trim.TrimMaterials
import net.minecraft.world.item.equipment.trim.TrimPatterns
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.scores.Team
import work.lclpnet.ap2.*
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.api.map.MapBootstrap
import work.lclpnet.ap2.api.stats.CommonStats
import work.lclpnet.ap2.api.util.heads.PlayerHead
import work.lclpnet.ap2.ext.*
import work.lclpnet.ap2.ext.mc.isIn
import work.lclpnet.ap2.ext.mc.resetAttribute
import work.lclpnet.ap2.ext.mc.setAttribute
import work.lclpnet.ap2.ext.mc.teleport
import work.lclpnet.ap2.impl.game.EliminationGameInstance
import work.lclpnet.ap2.impl.map.schema.SchemaHolder
import work.lclpnet.ap2.impl.util.ApRegistries
import work.lclpnet.ap2.impl.util.SoundHelper
import work.lclpnet.ap2.impl.util.movement.SimpleMovementBlocker
import work.lclpnet.ap2.util.scene.ApSceneRenderer
import work.lclpnet.ap2.util.scene.PlayerMountContext
import work.lclpnet.gaco.dynamic_entities.DynamicEntityManager
import work.lclpnet.gaco.math.BlockFace
import work.lclpnet.gaco.scene.MountContext
import work.lclpnet.gaco.scene.Object3d
import work.lclpnet.gaco.scene.Scene
import work.lclpnet.gaco.scene.ServerWorldMountContext
import work.lclpnet.game.map.GameMap
import work.lclpnet.game.util.BossBarTimer
import work.lclpnet.game.util.ResetWorldModifier
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks
import work.lclpnet.kibu.hook.player.PlayerMoveCallback
import work.lclpnet.kibu.scheduler.Ticks
import work.lclpnet.kibu.scheduler.api.TaskHandle
import work.lclpnet.kibu.structure.BlockStructure
import work.lclpnet.kibu.translate.bossbar.TranslatedBossBar
import work.lclpnet.kibu.translate.text.FormatWrapper.styled
import java.util.*
import java.util.concurrent.CompletableFuture

const val DEBUG_VALID_POSITIONS = false
const val DEBUG_BUTTON_POSITION = false
const val EJECT_SECONDS = 15
const val BUTTON_REVEAL_SECONDS = 45

val ASTRONAUT_HEAD: ResourceKey<PlayerHead> = ResourceKey.create(
    ApRegistries.PLAYER_HEAD,
    ApConstants.identifier("astronaut")
)

enum class GameState {
    SEARCHING_BUTTON,
    CHOOSE_EJECT,
    EJECTING,
    IDLE
}

class ButtonMasterInstance(gameHandle: MiniGameHandle) : EliminationGameInstance(gameHandle), MapBootstrap {

    val schemaHolder: SchemaHolder<ButtonMasterSchema> = useSchema(ButtonMasterSchema::class.java)
    val validPositions = mutableListOf<BlockPos>()

    private val stats = createStats(ButtonsFound, Escapes, CommonStats.DistanceMoved, ButtonsMissed)
    private val bmStats = ButtonMasterStats(stats)
    private lateinit var missDetector: ButtonMissDetector
    private var escapesRecorded = false

    val movementBlocker = SimpleMovementBlocker(gameHandle.scheduler).also {
        it.setModifySpeedAttribute(false)
    }

    var currentButtonMarker: Object3d? = null
    var currentButtonPos: BlockPos? = null
    var gameState = GameState.IDLE
    var ejectTimer: BossBarTimer? = null
    var buttonMasterUuid: UUID? = null
    var ejectedPlayer: UUID? = null
    var task: TaskHandle? = null
    var taskBar: TranslatedBossBar? = null
    var wallBlocks: ResetWorldModifier? = null
    var scene: Scene? = null
    lateinit var dynamicEntityManager: DynamicEntityManager
    lateinit var capsules: ButtonMasterCapsules
    lateinit var buttonPositions: ButtonPositions
    lateinit var capsuleSchematic: BlockStructure

    override fun createWorldBootstrap(world: ServerLevel, map: GameMap): CompletableFuture<Void> {
        wallBlocks = ResetWorldModifier(world, gameHandle.hooks)

        return schematic(assetPath("capsule.schem")).thenAccept {
            capsuleSchematic = it
        }
    }


    override fun prepare() {
        capsules = ButtonMasterCapsules(level, schemaHolder.get(), capsuleSchematic, commons())
        missDetector = ButtonMissDetector(level, bmStats)

        dynamicEntityManager = DynamicEntityManager(level)
        dynamicEntityManager.init(gameHandle.scheduler, gameHandle.hooks)

        buttonPositions = ButtonPositions(level, map, schemaHolder.get(), commons(), gameHandle)

        validPositions.addAll(buttonPositions.scanWorld())

        capsules.setup()
        movementBlocker.init(gameHandle.hooks)

        setupTeam()
        equipPlayers()
        closeWall()
    }

    private fun closeWall() {
        val world = this.level
        val wallBlocks = wallBlocks ?: return
        val wallState = Blocks.WHITE_STAINED_GLASS.defaultBlockState()

        for (box in schemaHolder.get().startWalls) {
            for (pos in box) {
                if (world.getBlockState(pos).isCollisionShapeFullBlock(world, pos)) continue

                wallBlocks.setBlockState(pos, wallState)
            }
        }
    }

    private fun equipPlayers() {
        val trimPatterns = level.registryAccess().lookupOrThrow(Registries.TRIM_PATTERN)
        val trimMaterials = level.registryAccess().lookupOrThrow(Registries.TRIM_MATERIAL)

        val silenceTrim = trimPatterns.getOrThrow(TrimPatterns.SILENCE)
        val wildTrim = trimPatterns.getOrThrow(TrimPatterns.WILD)

        val quartz = trimMaterials.getOrThrow(TrimMaterials.QUARTZ)

        val chestplate = ItemStack(Items.NETHERITE_CHESTPLATE)
        chestplate.set(DataComponents.TRIM, ArmorTrim(quartz, silenceTrim))

        val leggings = ItemStack(Items.NETHERITE_LEGGINGS)
        leggings.set(DataComponents.TRIM, ArmorTrim(quartz, silenceTrim))

        val boots = ItemStack(Items.NETHERITE_BOOTS)
        boots.set(DataComponents.TRIM, ArmorTrim(quartz, wildTrim))

        val head = level.registryAccess().lookupOrThrow(ApRegistries.PLAYER_HEAD)
            .getValueOrThrow(ASTRONAUT_HEAD).createStack()

        for (player in players()) {
            player.setItemSlot(EquipmentSlot.HEAD, head.copy())
            player.setItemSlot(EquipmentSlot.CHEST, chestplate.copy())
            player.setItemSlot(EquipmentSlot.LEGS, leggings.copy())
            player.setItemSlot(EquipmentSlot.FEET, boots.copy())
        }
    }

    private fun setupTeam() {
        val scoreboardManager = gameHandle.getScoreboardManager()
        val team = scoreboardManager.createTeam("team")
        team.nameTagVisibility = Team.Visibility.NEVER
        scoreboardManager.joinTeam(gameHandle.getParticipants(), team)
    }



    override fun go() {
        buttonPositions.filterNoEntityCollision(validPositions)

        if (DEBUG_VALID_POSITIONS) {
            buttonPositions.debugValidPositions(validPositions)
        }

        taskBar = useTaskDisplay()

        nextRound()

        PlayerInteractionHooks.USE_BLOCK.registerWith(gameHandle.hooks) { entity, _, _, result ->
            onUseBlock(entity, result)
        }

        PlayerMoveCallback.HOOK.registerWith(gameHandle.hooks) { player, from, to ->
            updateDistanceMoved(stats, player, from, to)
            false
        }

        runEvery(BUTTON_SIGHT_CHECK_INTERVAL.ticks) {
            checkButtonVisibility()
        }

        eliminateBelowCriticalHeight()
    }

    fun onUseBlock(
        entity: Player,
        result: BlockHitResult
    ): InteractionResult {
        if (entity !is ServerPlayer) {
            return InteractionResult.PASS
        }

        val state = level.getBlockState(result.blockPos)

        if (!state.isIn(BlockTags.BUTTONS))
            return InteractionResult.PASS

        if (gameState == GameState.SEARCHING_BUTTON) {
            becomeButtonMaster(entity)
            return InteractionResult.SUCCESS_SERVER
        }

        if (gameState != GameState.CHOOSE_EJECT || buttonMasterUuid != entity.uuid)
            return InteractionResult.PASS

        val capsule = capsules.buttons[result.blockPos] ?: return InteractionResult.PASS

        eject(capsule)

        return InteractionResult.PASS
    }

    private fun eject(capsule: BlockFace) {
        gameState = GameState.EJECTING

        val spawn = capsules.getCapsuleSpawn(capsule)

        level.setBlockAndUpdate(BlockPos.containing(spawn).below(), Blocks.AIR.defaultBlockState())

        val uuid = capsules.players[capsule] ?: return
        val player = players().getParticipant(uuid).orElse(null) ?: return

        movementBlocker.enableMovement(player)
        player.resetAttribute(Attributes.GRAVITY)

        task = gameHandle.scheduler.timeout(Ticks.seconds(5), Runnable {
            eliminate(player)
        })
    }

    fun becomeButtonMaster(player: ServerPlayer) {
        bmStats.buttonFound(player)
        escapesRecorded = false

        buttonMasterUuid = player.uuid
        gameState = GameState.CHOOSE_EJECT
        taskBar?.isVisible = false
        task?.cancel()
        scene?.clear()

        player.teleport(schemaHolder.get().buttonMasterSpawn!!)
        player.setAttribute(Attributes.JUMP_STRENGTH, 0.0)

        val otherPlayers = players().filter { it != player }

        capsules.teleportToCapsules(otherPlayers)

        otherPlayers.forEach {
            movementBlocker.disableMovement(it)
            it.setAttribute(Attributes.GRAVITY, 0.0)
        }

        val ejectTimer = commons().createTimer(
            translate("game.ap2.button_master.eject"),
            EJECT_SECONDS,
        )

        ejectTimer.whenDone {
            eliminateButtonMaster()
        }

        translate(
            "game.ap2.button_master.choose_capsule",
            styled(EJECT_SECONDS, ChatFormatting.YELLOW)
        ).formatted(ChatFormatting.AQUA).sendTo(player)

        this.ejectTimer = ejectTimer

        val renderer = rendererFor(player)

        capsules.displayCapsuleButtons(renderer)
    }

    private fun eliminateButtonMaster() {
        val uuid = buttonMasterUuid ?: return
        val buttonMaster = players().getParticipant(uuid).orElse(null) ?: return

        eliminate(buttonMaster)

        if (!winManager.isGameOver) {
            beginNextRound()
        }
    }


    fun beginNextRound() {
        recordEscapes()

        buttonMasterUuid = null
        ejectedPlayer = null

        task?.cancel()
        task = null

        ejectTimer?.stop()
        ejectTimer = null

        scene?.clear()
        scene = null

        for (player in players()) {
            movementBlocker.enableMovement(player)
            gameHandle.worldFacade.teleport(player)

            player.resetAttribute(Attributes.JUMP_STRENGTH)
            player.resetAttribute(Attributes.GRAVITY)
        }

        nextRound()
    }

    fun nextRound() {
        gameState = GameState.SEARCHING_BUTTON
        taskBar?.isVisible = true

        missDetector.reset()

        capsules.removeExcessCapsules(players().count() - 1)

        val lastPos = currentButtonPos

        if (lastPos != null) {
            level.setBlockAndUpdate(lastPos, Blocks.AIR.defaultBlockState())
        }

        require(validPositions.isNotEmpty()) { "No valid position found" }

        val pos = validPositions.random()

        val buttonBlock = Blocks.BAMBOO_BUTTON

        val states = buttonStates(buttonBlock).filter { it.canSurvive(level, pos) }

        require(states.isNotEmpty()) { "No valid button state found" }

        val state = states.random()

        currentButtonPos = pos
        level.setBlockAndUpdate(pos, state)

        if (DEBUG_BUTTON_POSITION) {
            currentButtonMarker?.detach()

            commons().debugController().renderer().ifPresent {
                currentButtonMarker = it.marker(pos.center, Blocks.BLUE_STAINED_GLASS.defaultBlockState(), DyeColor.BLUE.textureDiffuseColor)
            }
        }

        wallBlocks?.undo()

        task = gameHandle.scheduler.timeout(BUTTON_REVEAL_SECONDS * 20, Runnable {
            markButton()
        })
    }

    private fun markButton() {
        val pos = currentButtonPos ?: return
        val renderer = renderer(ServerWorldMountContext(level))

        renderer.markBlock(pos, level.getBlockState(pos), 0x00ff00)

        SoundHelper.playSound(level, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1f, 1.7f)

        translate("game.ap2.button_master.revealed").formatted(ChatFormatting.AQUA).sendTo(allPlayers())
    }

    private fun checkButtonVisibility() {
        if (gameState != GameState.SEARCHING_BUTTON) return

        val buttonPos = currentButtonPos ?: return

        missDetector.update(buttonPos, players())
    }

    private fun recordEscapes() {
        if (escapesRecorded) return
        escapesRecorded = true

        for (uuid in capsules.players.values) {
            players().getParticipant(uuid).ifPresent(bmStats::escaped)
        }
    }

    override fun onEliminated(player: ServerPlayer) {
        super.onEliminated(player)

        if (winManager.isGameOver || gameState == GameState.SEARCHING_BUTTON) return

        beginNextRound()
    }

    fun rendererFor(player: ServerPlayer): ApSceneRenderer {
        val dynamicEntityManager = dynamicEntityManager

        val mountContext = PlayerMountContext(level, dynamicEntityManager, player.uuid)

        return renderer(mountContext)
    }

    private fun renderer(mountContext: MountContext): ApSceneRenderer {
        val scene = Scene(mountContext)

        this.scene = scene

        return ApSceneRenderer(scene)
    }
}
