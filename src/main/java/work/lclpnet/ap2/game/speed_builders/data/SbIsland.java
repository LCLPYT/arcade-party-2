package work.lclpnet.ap2.game.speed_builders.data;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.StairShape;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import org.slf4j.Logger;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.math.AffineIntMatrix;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.kibu.jnbt.CompoundTag;
import work.lclpnet.kibu.mc.KibuBlockPos;
import work.lclpnet.kibu.mc.KibuBlockState;
import work.lclpnet.kibu.mc.KibuEntity;
import work.lclpnet.kibu.nbt.FabricNbtConversion;
import work.lclpnet.kibu.schematic.FabricBlockStateAdapter;
import work.lclpnet.kibu.structure.BlockStructure;
import work.lclpnet.kibu.util.BlockStateUtils;
import work.lclpnet.kibu.util.StructureWriter;
import work.lclpnet.kibu.util.math.Matrix3i;

import java.util.EnumSet;
import java.util.List;

import static net.minecraft.block.enums.StairShape.*;
import static net.minecraft.state.property.Properties.HORIZONTAL_FACING;
import static net.minecraft.state.property.Properties.STAIR_SHAPE;
import static work.lclpnet.kibu.util.StructureWriter.Option.*;

public class SbIsland {

    private final SbIslandData data;
    private final BlockPos spawnWorldPos;
    private final BlockBox buildingArea;
    private final BlockBox previewArea;
    private final BlockBox bounds;
    private final BlockBox movementBounds;
    private final Logger logger;

    /**
     * Constructor.
     *
     * @param data   The island data.
     * @param origin The origin of the schematic, used to calculate relative data.
     * @param offset The offset the island structure is placed at in world coordinates.
     * @param bounds The island bounds in world space.
     */
    public SbIsland(SbIslandData data, BlockPos origin, BlockPos offset, BlockBox bounds, Logger logger) {
        this.data = data;
        this.logger = logger;

        BlockPos relSpawn = data.spawn().subtract(origin);
        this.spawnWorldPos = offset.add(relSpawn);

        AffineIntMatrix buildingAreaTranslation = AffineIntMatrix.makeTranslation(
                offset.getX() - origin.getX(),
                offset.getY() - origin.getY(),
                offset.getZ() - origin.getZ());

        this.buildingArea = data.buildArea().transform(buildingAreaTranslation);

        Vec3i previewOffset = data.previewOffset();
        AffineIntMatrix previewAreaTranslation = AffineIntMatrix.makeTranslation(
                previewOffset.getX(),
                previewOffset.getY(),
                previewOffset.getZ());

        this.previewArea = this.buildingArea.transform(previewAreaTranslation);
        this.bounds = bounds;
        this.movementBounds = new BlockBox(bounds.min().add(-4, 0, -4), bounds.max().add(4, 10, 4));
    }

    public void teleport(ServerPlayerEntity player) {
        double x = spawnWorldPos.getX() + 0.5, y = spawnWorldPos.getY(), z = spawnWorldPos.getZ() + 0.5;
        ServerWorld world = player.getServerWorld();

        player.teleport(world, x, y, z, data.yaw(), 0);
    }

    public boolean isWithinBuildingArea(BlockPos pos) {
        return buildingArea.contains(pos);
    }

    public boolean supports(SbModule module) {
        BlockBox buildArea = data.buildArea();
        BlockStructure structure = module.structure();

        return buildArea.width() == structure.getWidth() && buildArea.length() == structure.getLength() && structure.getHeight() <= buildArea.height();
    }

    public void placeModulePreview(SbModule module, ServerWorld world, Team team, CustomScoreboardManager scoreboardManager) {
        clear(previewArea, world);

        BlockStructure structure = module.structure();

        var options = EnumSet.of(FORCE_STATE, SKIP_AIR, SKIP_DROPS, SKIP_BLOCK_ENTITIES);
        StructureWriter.placeStructure(structure, world, previewArea.min().down(), Matrix3i.IDENTITY, options);

        var entities = getPreviewEntities(world);

        for (Entity entity : entities) {
            if (entity instanceof MobEntity mob) {
                mob.setAiDisabled(true);
                mob.setPersistent();
            }

            entity.setNoGravity(true);
            entity.setSilent(true);
            entity.setInvulnerable(true);

            scoreboardManager.joinTeam(entity, team);
        }
    }

    private List<? extends Entity> getEntities(ServerWorld world, BlockBox box) {
        return world.getEntitiesByType(TypeFilter.instanceOf(Entity.class), box.toBox(), entity -> !(entity instanceof ServerPlayerEntity));
    }

    public List<? extends Entity> getPreviewEntities(ServerWorld world) {
        return getEntities(world, previewArea);
    }

    public void copyPreviewFloorToBuildArea(ServerWorld world) {
        BlockPos from = buildingArea.min().down();
        BlockPos to = buildingArea.max().down(buildingArea.height());
        Vec3i previewOffset = data.previewOffset();
        BlockPos.Mutable pointer = new BlockPos.Mutable();
        int flags = Block.FORCE_STATE | Block.SKIP_DROPS | Block.NOTIFY_LISTENERS;

        for (BlockPos pos : BlockPos.iterate(from, to)) {
            pointer.set(
                    pos.getX() + previewOffset.getX(),
                    pos.getY() + previewOffset.getY(),
                    pos.getZ() + previewOffset.getZ());

            BlockState state = world.getBlockState(pointer);
            world.setBlockState(pos, state, flags);
        }
    }

    public void clearBuildingArea(ServerWorld world) {
        clear(buildingArea, world);
    }

    private void clear(BlockBox box, ServerWorld world) {
        BlockState air = Blocks.AIR.getDefaultState();
        int flags = Block.FORCE_STATE | Block.SKIP_DROPS | Block.NOTIFY_LISTENERS;

        for (BlockPos pos : box) {
            world.setBlockState(pos, air, flags);
        }

        var entities = getEntities(world, box);

        for (Entity entity : entities) {
            entity.discard();
        }
    }

    public int evaluate(ServerWorld world, SbModule module) {
        BlockStructure structure = module.structure();

        BlockPos.Mutable pointer = new BlockPos.Mutable();

        // each block that of the structure that is present in the build area is awarded with one point
        int score = 0;

        score += evaluateBlocks(world, structure, pointer);
        score += evaluateEntities(world, structure, pointer);

        return score;
    }

    private int evaluateEntities(ServerWorld world, BlockStructure structure, BlockPos.Mutable pointer) {
        KibuBlockPos origin = structure.getOrigin();
        BlockPos min = buildingArea.min();

        final int ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();
        final int mx = min.getX(), my = min.getY(), mz = min.getZ();

        var presentEntities = getEntities(world, buildingArea);
        int score = 0;

        next: for (KibuEntity entity : structure.getEntities()) {
            int rx = (int) Math.floor(entity.getX() - ox);
            int ry = (int) Math.floor(entity.getY() - oy);
            int rz = (int) Math.floor(entity.getZ() - oz);

            pointer.set(mx + rx, my + ry - 1, mz + rz);

            Identifier identifier = Identifier.tryParse(entity.getId());

            if (identifier == null) {
                // invalid entity, treat as correct
                score++;
                continue;
            }

            // O(n^2) should be okay since the number of entities is generally really low
            for (Entity en : presentEntities) {
                if (!pointer.equals(en.getBlockPos())) continue;

                Identifier id = Registries.ENTITY_TYPE.getId(en.getType());

                if (!identifier.equals(id)) {
                    logger.info("Entity differs: ({}, {}, {}) expected {} but got {}",
                            pointer.getX(), pointer.getY(), pointer.getZ(),
                            identifier, id);
                    continue;
                }

                // the correct entity type is at the required position

                if (en instanceof ItemFrameEntity itemFrame) {
                    // item frames must contain the correct item as well
                    CompoundTag kibuNbt = entity.getExtraNbt();

                    if (!kibuNbt.contains("Item")) continue next;  // incorrect item frame

                    CompoundTag kibuItem = kibuNbt.getCompound("Item");

                    if (kibuItem == null) continue next;  // incorrect item frame

                    NbtCompound item = FabricNbtConversion.convert(kibuItem, NbtCompound.class);

                    ItemStack expected = ItemStack.fromNbtOrEmpty(world.getRegistryManager(), item);
                    ItemStack actual = itemFrame.getHeldItemStack();

                    if ((!expected.isEmpty() || !actual.isEmpty()) && !actual.isOf(expected.getItem())) {
                        logger.info("Item frame differs: Expected item {} but got {}", expected, actual);
                        continue next;  // incorrect item frame
                    }
                }

                score++;

                break;
            }
        }

        return score;
    }

    private int evaluateBlocks(ServerWorld world, BlockStructure structure, BlockPos.Mutable pointer) {
        KibuBlockPos origin = structure.getOrigin();
        BlockPos min = buildingArea.min();

        final int ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();
        final int mx = min.getX(), my = min.getY(), mz = min.getZ();

        FabricBlockStateAdapter adapter = FabricBlockStateAdapter.getInstance();

        int score = 0;

        for (KibuBlockPos pos : structure.getBlockPositions()) {
            int ry = pos.getY() - oy;

            // do not grade the floor
            if (ry == 0) continue;

            int rx = pos.getX() - ox;
            int rz = pos.getZ() - oz;

            pointer.set(mx + rx, my + ry - 1, mz + rz);

            KibuBlockState kibuState = structure.getBlockState(pos);
            BlockState expected = adapter.revert(kibuState);

            if (expected == null) {
                // unknown block state, treat as correct
                score++;
                continue;
            }

            BlockState actual = world.getBlockState(pointer);

            if (areStatesEqual(actual, expected)) {
                score++;
            } else {
                logger.info("Block differs: ({}, {}, {}) expected {} but got {}",
                        pointer.getX(), pointer.getY(), pointer.getZ(),
                        BlockStateUtils.stringify(expected), BlockStateUtils.stringify(actual));
            }
        }

        return score;
    }

    private boolean areStatesEqual(BlockState first, BlockState second) {
        // stairs have states that look the same, but are actually different block states
        // e.g. [shape=inner_right, facing=south] and [shape=inner_left, facing=west] should be equal
        if (first.contains(STAIR_SHAPE) && second.contains(STAIR_SHAPE)
            && first.contains(HORIZONTAL_FACING) && second.contains(HORIZONTAL_FACING)) {

            StairShape firstShape = first.get(STAIR_SHAPE);
            StairShape secondShape = second.get(STAIR_SHAPE);

            if (firstShape != secondShape) {
                // rotate second accordingly so that equivalences are formed
                if ((firstShape == INNER_LEFT && secondShape == INNER_RIGHT) || (firstShape == OUTER_LEFT && secondShape == OUTER_RIGHT)) {
                    second = second
                            .with(STAIR_SHAPE, firstShape)
                            .with(HORIZONTAL_FACING, second.get(HORIZONTAL_FACING).rotateYClockwise());
                } else if ((firstShape == INNER_RIGHT && secondShape == INNER_LEFT) || (firstShape == OUTER_RIGHT && secondShape == OUTER_LEFT)) {
                    second = second
                            .with(STAIR_SHAPE, firstShape)
                            .with(HORIZONTAL_FACING, second.get(HORIZONTAL_FACING).rotateYCounterclockwise());
                }
            }
        }

        // leaves distance
        first = first.withIfExists(Properties.DISTANCE_1_7, 1);
        second = second.withIfExists(Properties.DISTANCE_1_7, 1);

        // leaves persistent
        first = first.withIfExists(Properties.PERSISTENT, true);
        second = second.withIfExists(Properties.PERSISTENT, true);

        return first.equals(second);
    }

    public boolean isCompleted(ServerWorld world, SbModule module) {
        int score = evaluate(world, module);
        int maxScore = module.getMaxScore();

        logger.info("Island completion: {} / {}", score, maxScore);

        return score >= maxScore;
    }

    public Vec3d getCenter() {
        Vec3d buildingCenter = buildingArea.getCenter().withAxis(Direction.Axis.Y, buildingArea.min().getY());
        Vec3d previewCenter = previewArea.getCenter().withAxis(Direction.Axis.Y, previewArea.min().getY());

        return buildingCenter.add(previewCenter).multiply(0.5);
    }

    public BlockBox getBounds() {
        return bounds;
    }

    public BlockBox getMovementBounds() {
        return movementBounds;
    }
}
