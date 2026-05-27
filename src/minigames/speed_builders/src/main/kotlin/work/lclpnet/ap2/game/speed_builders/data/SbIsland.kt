package work.lclpnet.ap2.game.speed_builders.data

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING
import net.minecraft.world.level.block.state.properties.BlockStateProperties.STAIRS_SHAPE
import net.minecraft.world.level.block.state.properties.StairsShape.*
import net.minecraft.world.level.entity.EntityTypeTest
import net.minecraft.world.phys.Vec3
import net.minecraft.world.scores.PlayerTeam
import org.slf4j.Logger
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.impl.util.ItemHelper
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager
import work.lclpnet.gaco.ds.BlockBox
import work.lclpnet.gaco.math.AffineIntMatrix
import work.lclpnet.kibu.nbt.FabricNbtConversion
import work.lclpnet.kibu.schematic.FabricBlockStateAdapter
import work.lclpnet.kibu.structure.BlockStructure
import work.lclpnet.kibu.util.BlockStateUtils
import work.lclpnet.kibu.util.StructureWriter
import work.lclpnet.kibu.util.StructureWriter.Option.*
import work.lclpnet.kibu.util.math.Matrix3i
import java.util.*
import kotlin.math.floor

class SbIsland(
    private val data: SbIslandData,
    origin: BlockPos,
    offset: BlockPos,
    val bounds: BlockBox,
    private val logger: Logger
) {
    private val spawnWorldPos: BlockPos
    private val buildingArea: BlockBox
    val movementBounds: BlockBox

    init {
        val relSpawn = data.spawn.subtract(origin)
        spawnWorldPos = offset.offset(relSpawn)

        val translation = AffineIntMatrix.makeTranslation(
            offset.x - origin.x,
            offset.y - origin.y,
            offset.z - origin.z
        )

        buildingArea = data.buildArea.transform(translation)

        movementBounds = BlockBox(
            bounds.min().offset(-4, 0, -4),
            bounds.max().offset(4, 10, 4)
        )
    }

    fun teleport(player: ServerPlayer) {
        val x = spawnWorldPos.x + 0.5
        val y = spawnWorldPos.y.toDouble()
        val z = spawnWorldPos.z + 0.5

        player.teleportTo(player.level(), x, y, z, setOf(), data.yaw, 0f, true)
    }

    fun isWithinBuildingArea(pos: BlockPos): Boolean = buildingArea.contains(pos)

    fun supports(module: SbModule): Boolean {
        val buildArea = data.buildArea
        val structure = module.structure

        return buildArea.width() == structure.width && buildArea.length() == structure.length && structure.height <= buildArea.height()
    }

    fun placeModulePreview(module: SbModule, world: ServerLevel, team: PlayerTeam, scoreboardManager: CustomScoreboardManager) {
        clear(buildingArea, world)

        val options = EnumSet.of(FORCE_STATE, SKIP_AIR, SKIP_DROPS, SKIP_BLOCK_ENTITIES)

        StructureWriter.placeStructure(module.structure, world, buildingArea.min().below(), Matrix3i.IDENTITY, options)

        for (entity in getPreviewEntities(world)) {
            if (entity is Mob) {
                entity.isNoAi = true
                entity.setPersistenceRequired()
            }

            entity.isNoGravity = true
            entity.isSilent = true
            entity.isInvulnerable = true

            scoreboardManager.joinTeam(entity, team)
        }
    }

    private fun getEntities(world: ServerLevel, box: BlockBox): List<Entity> =
        world.getEntities(EntityTypeTest.forClass(Entity::class.java), box.toBox()) { it !is ServerPlayer }

    fun getPreviewEntities(world: ServerLevel): List<Entity> = getEntities(world, buildingArea)

    fun clearBuildingArea(world: ServerLevel) = clear(buildingArea, world)

    private fun clear(box: BlockBox, world: ServerLevel) {
        val air = Blocks.AIR.defaultBlockState()
        val flags = Block.UPDATE_KNOWN_SHAPE or Block.UPDATE_SUPPRESS_DROPS or Block.UPDATE_CLIENTS

        for (pos in box) {
            world.setBlock(pos, air, flags)
        }

        for (entity in getEntities(world, box)) {
            entity.discard()
        }
    }

    fun evaluate(world: ServerLevel, module: SbModule): Int {
        val structure = module.structure
        val pointer = BlockPos.MutableBlockPos()
        var score = 0

        score += evaluateBlocks(world, structure, pointer)
        score += evaluateEntities(world, structure, pointer)

        return score
    }

    private fun evaluateEntities(world: ServerLevel, structure: BlockStructure, pointer: BlockPos.MutableBlockPos): Int {
        val origin = structure.origin
        val min = buildingArea.min()

        val ox = origin.x; val oy = origin.y; val oz = origin.z
        val mx = min.x; val my = min.y; val mz = min.z

        val presentEntities = getEntities(world, buildingArea)
        var score = 0

        next@ for (entity in structure.entities) {
            val rx = floor(entity.x - ox).toInt()
            val ry = floor(entity.y - oy).toInt()
            val rz = floor(entity.z - oz).toInt()

            pointer.set(mx + rx, my + ry - 1, mz + rz)

            val identifier = Identifier.tryParse(entity.id)
            if (identifier == null) {
                score++
                continue@next
            }

            for (en in presentEntities) {
                if (pointer != en.blockPosition()) continue

                val id = BuiltInRegistries.ENTITY_TYPE.getKey(en.type)

                if (identifier != id) {
                    logger.debug("Entity differs: ({}, {}, {}) expected {} but got {}", pointer.x, pointer.y, pointer.z, identifier, id)
                    continue
                }

                if (en is ItemFrame) {
                    val kibuNbt = entity.extraNbt

                    if (!kibuNbt.contains("Item")) continue@next

                    val kibuItem = kibuNbt.getCompound("Item") ?: continue@next

                    val item = FabricNbtConversion.convert(kibuItem, CompoundTag::class.java)

                    val expected = ItemHelper.fromNbt(world.registryAccess(), item)
                        .orElse(ItemStack.EMPTY) ?: ItemStack.EMPTY

                    val actual = en.item

                    if ((!expected.isEmpty || !actual.isEmpty) && !actual.isOf(expected.item)) {
                        logger.debug("Item frame differs: Expected item {} but got {}", expected, actual)
                        continue@next
                    }
                }

                score++
                break
            }
        }

        return score
    }

    private fun evaluateBlocks(world: ServerLevel, structure: BlockStructure, pointer: BlockPos.MutableBlockPos): Int {
        val origin = structure.origin
        val min = buildingArea.min()

        val ox = origin.x; val oy = origin.y; val oz = origin.z
        val mx = min.x; val my = min.y; val mz = min.z

        val adapter = FabricBlockStateAdapter.getInstance()
        var score = 0

        for (pos in structure.blockPositions) {
            val ry = pos.y - oy
            if (ry == 0) continue

            val rx = pos.x - ox
            val rz = pos.z - oz
            pointer.set(mx + rx, my + ry - 1, mz + rz)

            val kibuState = structure.getBlockState(pos)
            val expected = adapter.revert(kibuState)

            if (expected == null) {
                score++
                continue
            }

            val actual = world.getBlockState(pointer)

            if (areStatesEqual(actual, expected)) {
                score++
            } else {
                logger.debug("Block differs: ({}, {}, {}) expected {} but got {}",
                    pointer.x, pointer.y, pointer.z,
                    BlockStateUtils.stringify(expected), BlockStateUtils.stringify(actual))
            }
        }

        return score
    }

    private fun areStatesEqual(first: BlockState, second: BlockState): Boolean {
        var a = first
        var b = second

        if (a.hasProperty(STAIRS_SHAPE) && b.hasProperty(STAIRS_SHAPE)
            && a.hasProperty(HORIZONTAL_FACING) && b.hasProperty(HORIZONTAL_FACING)) {

            val aShape = a.getValue(STAIRS_SHAPE)
            val bShape = b.getValue(STAIRS_SHAPE)

            if (aShape != bShape) {
                when {
                    (aShape == INNER_LEFT && bShape == INNER_RIGHT) || (aShape == OUTER_LEFT && bShape == OUTER_RIGHT) ->
                        b = b.setValue(STAIRS_SHAPE, aShape).setValue(HORIZONTAL_FACING, b.getValue(HORIZONTAL_FACING).clockWise)
                    (aShape == INNER_RIGHT && bShape == INNER_LEFT) || (aShape == OUTER_RIGHT && bShape == OUTER_LEFT) ->
                        b = b.setValue(STAIRS_SHAPE, aShape).setValue(HORIZONTAL_FACING, b.getValue(HORIZONTAL_FACING).counterClockWise)
                }
            }
        }

        a = a.trySetValue(BlockStateProperties.DISTANCE, 1)
        b = b.trySetValue(BlockStateProperties.DISTANCE, 1)

        a = a.trySetValue(BlockStateProperties.PERSISTENT, true)
        b = b.trySetValue(BlockStateProperties.PERSISTENT, true)

        return a == b
    }

    fun isCompleted(world: ServerLevel, module: SbModule): Boolean {
        val score = evaluate(world, module)
        val maxScore = module.getMaxScore()
        logger.debug("Island completion: {} / {}", score, maxScore)
        return score >= maxScore
    }

    fun getCenter(): Vec3 = buildingArea.getCenter().with(Direction.Axis.Y, buildingArea.min().y.toDouble())
}
