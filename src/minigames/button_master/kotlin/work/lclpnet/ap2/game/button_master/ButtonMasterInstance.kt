package work.lclpnet.ap2.game.button_master

import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.block.Blocks
import net.minecraft.component.DataComponentTypes
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.item.equipment.trim.ArmorTrim
import net.minecraft.item.equipment.trim.ArmorTrimMaterials
import net.minecraft.item.equipment.trim.ArmorTrimPatterns
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.BlockTags
import net.minecraft.scoreboard.AbstractTeam
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.ActionResult
import net.minecraft.util.DyeColor
import net.minecraft.util.Formatting
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import work.lclpnet.ap2.*
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.api.map.MapBootstrap
import work.lclpnet.ap2.api.util.heads.PlayerHead
import work.lclpnet.ap2.impl.game.EliminationGameInstance
import work.lclpnet.ap2.impl.map.schema.SchemaHolder
import work.lclpnet.ap2.impl.util.ApRegistries
import work.lclpnet.ap2.util.scene.ApSceneRenderer
import work.lclpnet.ap2.util.scene.PlayerMountContext
import work.lclpnet.gaco.dynamic_entities.DynamicEntityManager
import work.lclpnet.gaco.math.BlockFace
import work.lclpnet.gaco.scene.Object3d
import work.lclpnet.gaco.scene.Scene
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks
import work.lclpnet.kibu.scheduler.Ticks
import work.lclpnet.kibu.scheduler.api.TaskHandle
import work.lclpnet.kibu.schematic.FabricBlockStateAdapter
import work.lclpnet.kibu.schematic.SchematicFormats
import work.lclpnet.kibu.translate.bossbar.TranslatedBossBar
import work.lclpnet.kibu.translate.text.FormatWrapper.styled
import work.lclpnet.lobby.game.map.GameMap
import work.lclpnet.lobby.game.util.BossBarTimer
import work.lclpnet.lobby.util.ResetWorldModifier
import java.util.*
import java.util.concurrent.CompletableFuture

const val DEBUG_VALID_POSITIONS = false
const val DEBUG_BUTTON_POSITION = false
const val EJECT_SECONDS = 15

val ASTRONAUT_HEAD: RegistryKey<PlayerHead> = RegistryKey.of(
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
    var dynamicEntityManager: DynamicEntityManager? = null
    var capsules: ButtonMasterCapsules? = null

    override fun createWorldBootstrap(world: ServerWorld, map: GameMap): CompletableFuture<Void> {
        wallBlocks = ResetWorldModifier(world, gameHandle.hooks)

        return CompletableFuture.runAsync {
            asset(assetPath("capsule.schem")).use {
                val capsuleSchematic = SchematicFormats.SPONGE_V2.reader().read(it, FabricBlockStateAdapter.getInstance())

                capsules = ButtonMasterCapsules(world, schemaHolder.get(), capsuleSchematic, commons())
            }
        }
    }

    override fun prepare() {
        val dynamicEntityManager = DynamicEntityManager(world)
        dynamicEntityManager.init(gameHandle.scheduler, gameHandle.hooks)

        this.dynamicEntityManager = dynamicEntityManager

        val positions = ButtonPositions(world, map, schemaHolder.get(), commons(), gameHandle)

        validPositions.addAll(positions.scanWorld())

        capsules?.setup()

        setupTeam()
        equipPlayers()
        closeWall()
    }

    private fun closeWall() {
        val world = this.world
        val wallBlocks = wallBlocks ?: return
        val wallState = Blocks.WHITE_STAINED_GLASS.defaultState

        for (box in schemaHolder.get().startWalls) {
            for (pos in box) {
                if (world.getBlockState(pos).isFullCube(world, pos)) continue

                wallBlocks.setBlockState(pos, wallState)
            }
        }
    }

    private fun equipPlayers() {
        val trimPatterns = world.registryManager.getOrThrow(RegistryKeys.TRIM_PATTERN)
        val trimMaterials = world.registryManager.getOrThrow(RegistryKeys.TRIM_MATERIAL)

        val silenceTrim = trimPatterns.getOrThrow(ArmorTrimPatterns.SILENCE)
        val wildTrim = trimPatterns.getOrThrow(ArmorTrimPatterns.WILD)

        val quartz = trimMaterials.getOrThrow(ArmorTrimMaterials.QUARTZ)

        val chestplate = ItemStack(Items.NETHERITE_CHESTPLATE)
        chestplate.set(DataComponentTypes.TRIM, ArmorTrim(quartz, silenceTrim))

        val leggings = ItemStack(Items.NETHERITE_LEGGINGS)
        leggings.set(DataComponentTypes.TRIM, ArmorTrim(quartz, silenceTrim))

        val boots = ItemStack(Items.NETHERITE_BOOTS)
        boots.set(DataComponentTypes.TRIM, ArmorTrim(quartz, wildTrim))

        val head = world.registryManager.getOrThrow(ApRegistries.PLAYER_HEAD)
            .getValueOrThrow(ASTRONAUT_HEAD).createStack()

        for (player in players()) {
            player.equipStack(EquipmentSlot.HEAD, head.copy())
            player.equipStack(EquipmentSlot.CHEST, chestplate.copy())
            player.equipStack(EquipmentSlot.LEGS, leggings.copy())
            player.equipStack(EquipmentSlot.FEET, boots.copy())
        }
    }

    private fun setupTeam() {
        val scoreboardManager = gameHandle.getScoreboardManager()
        val team = scoreboardManager.createTeam("team")
        team.nameTagVisibilityRule = AbstractTeam.VisibilityRule.NEVER
        scoreboardManager.joinTeam(gameHandle.getParticipants(), team)
    }



    override fun go() {
        taskBar = useTaskDisplay()

        nextRound()

        gameHandle.hooks.registerHook(PlayerInteractionHooks.USE_BLOCK, UseBlockCallback { entity, world, hand, result ->
            onUseBlock(entity, world, hand, result)
        })

        eliminateBelowCriticalHeight()
    }

    fun onUseBlock(
        entity: PlayerEntity,
        _world: World,
        hand: Hand,
        result: BlockHitResult
    ): ActionResult {
        if (entity !is ServerPlayerEntity) {
            return ActionResult.PASS
        }

        val state = world.getBlockState(result.blockPos)

        if (!state.isIn(BlockTags.BUTTONS))
            return ActionResult.PASS

        if (gameState == GameState.SEARCHING_BUTTON) {
            becomeButtonMaster(entity)
            return ActionResult.SUCCESS_SERVER
        }

        if (gameState != GameState.CHOOSE_EJECT || buttonMasterUuid != entity.uuid)
            return ActionResult.PASS

        val capsule = capsules?.buttons[result.blockPos] ?: return ActionResult.PASS

        eject(capsule)

        return ActionResult.PASS
    }

    private fun eject(capsule: BlockFace) {
        gameState = GameState.EJECTING

        val spawn = capsules?.getCapsuleSpawn(capsule) ?: return

        world.setBlockState(BlockPos.ofFloored(spawn).down(), Blocks.AIR.defaultState)

        val uuid = capsules?.players[capsule] ?: return
        val player = players().getParticipant(uuid).orElse(null) ?: return

        task = gameHandle.scheduler.timeout(Ticks.seconds(5), Runnable {
            eliminate(player)
        })
    }

    fun becomeButtonMaster(player: ServerPlayerEntity) {
        buttonMasterUuid = player.uuid
        gameState = GameState.CHOOSE_EJECT
        taskBar?.isVisible = false

        player.teleport(schemaHolder.get().buttonMasterSpawn!!)
        player.setAttribute(EntityAttributes.JUMP_STRENGTH, 0.0)

        capsules?.teleportToCapsules(players().filter { it != player })

        val ejectTimer = commons().createTimer(
            translate("game.ap2.button_master.eject"),
            EJECT_SECONDS,
        )

        ejectTimer.whenDone {
            eliminateButtonMaster()
        }

        translate(
            "game.ap2.button_master.choose_capsule",
            styled(EJECT_SECONDS, Formatting.YELLOW)
        ).formatted(Formatting.AQUA).sendTo(player)

        this.ejectTimer = ejectTimer

        val renderer = rendererFor(player)

        if (renderer != null) {
            capsules?.displayCapsuleButtons(renderer)
        }
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
        buttonMasterUuid = null
        ejectedPlayer = null

        task?.cancel()
        task = null

        ejectTimer?.stop()
        ejectTimer = null

        scene?.clear()
        scene = null

        for (player in players()) {
            gameHandle.worldFacade.teleport(player)

            player.resetAttribute(EntityAttributes.JUMP_STRENGTH)
        }

        nextRound()
    }

    fun nextRound() {
        gameState = GameState.SEARCHING_BUTTON
        taskBar?.isVisible = true

        capsules?.removeExcessCapsules(players().count() - 1)

        val lastPos = currentButtonPos

        if (lastPos != null) {
            world.setBlockState(lastPos, Blocks.AIR.defaultState)
        }

        require(validPositions.isNotEmpty()) { "No valid position found" }

        val pos = validPositions.random()

        val buttonBlock = Blocks.BAMBOO_BUTTON

        val states = buttonStates(buttonBlock).filter { it.canPlaceAt(world, pos) }

        require(states.isNotEmpty()) { "No valid button state found" }

        val state = states.random()

        currentButtonPos = pos
        world.setBlockState(pos, state)

        if (DEBUG_BUTTON_POSITION) {
            currentButtonMarker?.detach()

            commons().debugController().renderer().ifPresent {
                currentButtonMarker = it.marker(pos.toCenterPos(), Blocks.BLUE_STAINED_GLASS.defaultState, DyeColor.BLUE.entityColor)
            }
        }

        wallBlocks?.undo()
    }

    override fun onEliminated(player: ServerPlayerEntity?) {
        super.onEliminated(player)

        if (winManager.isGameOver || gameState == GameState.SEARCHING_BUTTON) return

        beginNextRound()
    }

    fun rendererFor(player: ServerPlayerEntity): ApSceneRenderer? {
        val dynamicEntityManager = dynamicEntityManager ?: return null

        val scene = Scene(PlayerMountContext(world, dynamicEntityManager, player.uuid))

        this.scene = scene;

        return ApSceneRenderer(scene)
    }
}
