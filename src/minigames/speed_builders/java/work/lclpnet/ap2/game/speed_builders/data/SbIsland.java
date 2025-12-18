package work.lclpnet.ap2.game.speed_builders.data;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import org.slf4j.Logger;
import work.lclpnet.ap2.impl.util.ItemHelper;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.gaco.ds.BlockBox;
import work.lclpnet.gaco.math.AffineIntMatrix;
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
import java.util.Set;

import static net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.STAIRS_SHAPE;
import static net.minecraft.world.level.block.state.properties.StairsShape.*;
import static work.lclpnet.kibu.util.StructureWriter.Option.*;

public class SbIsland {

    private final SbIslandData data;
    private final BlockPos spawnWorldPos;
    private final BlockBox buildingArea;
    @Getter
    private final BlockBox bounds;
    @Getter
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
        this.spawnWorldPos = offset.offset(relSpawn);

        AffineIntMatrix buildingAreaTranslation = AffineIntMatrix.makeTranslation(
                offset.getX() - origin.getX(),
                offset.getY() - origin.getY(),
                offset.getZ() - origin.getZ());

        this.buildingArea = data.buildArea().transform(buildingAreaTranslation);

        this.bounds = bounds;
        this.movementBounds = new BlockBox(bounds.min().offset(-4, 0, -4), bounds.max().offset(4, 10, 4));
    }

    public void teleport(ServerPlayer player) {
        double x = spawnWorldPos.getX() + 0.5, y = spawnWorldPos.getY(), z = spawnWorldPos.getZ() + 0.5;
        ServerLevel world = player.level();

        player.teleportTo(world, x, y, z, Set.of(), data.yaw(), 0, true);
    }

    public boolean isWithinBuildingArea(BlockPos pos) {
        return buildingArea.contains(pos);
    }

    public boolean supports(SbModule module) {
        BlockBox buildArea = data.buildArea();
        BlockStructure structure = module.structure();

        return buildArea.width() == structure.getWidth() && buildArea.length() == structure.getLength() && structure.getHeight() <= buildArea.height();
    }

    public void placeModulePreview(SbModule module, ServerLevel world, PlayerTeam team, CustomScoreboardManager scoreboardManager) {
        clear(buildingArea, world);

        BlockStructure structure = module.structure();

        var options = EnumSet.of(FORCE_STATE, SKIP_AIR, SKIP_DROPS, SKIP_BLOCK_ENTITIES);
        StructureWriter.placeStructure(structure, world, buildingArea.min().below(), Matrix3i.IDENTITY, options);

        var entities = getPreviewEntities(world);

        for (Entity entity : entities) {
            if (entity instanceof Mob mob) {
                mob.setNoAi(true);
                mob.setPersistenceRequired();
            }

            entity.setNoGravity(true);
            entity.setSilent(true);
            entity.setInvulnerable(true);

            scoreboardManager.joinTeam(entity, team);
        }
    }

    private List<? extends Entity> getEntities(ServerLevel world, BlockBox box) {
        return world.getEntities(EntityTypeTest.forClass(Entity.class), box.toBox(), entity -> !(entity instanceof ServerPlayer));
    }

    public List<? extends Entity> getPreviewEntities(ServerLevel world) {
        return getEntities(world, buildingArea);
    }

//    public void copyPreviewFloorToBuildArea(ServerWorld world) {
//        BlockPos from = buildingArea.min().down();
//        BlockPos to = buildingArea.max().down(buildingArea.height());
//        Vec3i previewOffset = data.previewOffset();
//        BlockPos.Mutable pointer = new BlockPos.Mutable();
//        int flags = Block.FORCE_STATE | Block.SKIP_DROPS | Block.NOTIFY_LISTENERS;
//
//        for (BlockPos pos : BlockPos.iterate(from, to)) {
//            pointer.set(
//                    pos.getX() + previewOffset.getX(),
//                    pos.getY() + previewOffset.getY(),
//                    pos.getZ() + previewOffset.getZ());
//
//            BlockState state = world.getBlockState(pointer);
//            world.setBlockState(pos, state, flags);
//        }
//    }

    public void clearBuildingArea(ServerLevel world) {
        clear(buildingArea, world);
    }

    private void clear(BlockBox box, ServerLevel world) {
        BlockState air = Blocks.AIR.defaultBlockState();
        int flags = Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_CLIENTS;

        for (BlockPos pos : box) {
            world.setBlock(pos, air, flags);
        }

        var entities = getEntities(world, box);

        for (Entity entity : entities) {
            entity.discard();
        }
    }

    public int evaluate(ServerLevel world, SbModule module) {
        BlockStructure structure = module.structure();

        BlockPos.MutableBlockPos pointer = new BlockPos.MutableBlockPos();

        // each block that of the structure that is present in the build area is awarded with one point
        int score = 0;

        score += evaluateBlocks(world, structure, pointer);
        score += evaluateEntities(world, structure, pointer);

        return score;
    }

    private int evaluateEntities(ServerLevel world, BlockStructure structure, BlockPos.MutableBlockPos pointer) {
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

            ResourceLocation identifier = ResourceLocation.tryParse(entity.getId());

            if (identifier == null) {
                // invalid entity, treat as correct
                score++;
                continue;
            }

            // O(n^2) should be okay since the number of entities is generally really low
            for (Entity en : presentEntities) {
                if (!pointer.equals(en.blockPosition())) continue;

                ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(en.getType());

                if (!identifier.equals(id)) {
                    logger.info("Entity differs: ({}, {}, {}) expected {} but got {}",
                            pointer.getX(), pointer.getY(), pointer.getZ(),
                            identifier, id);
                    continue;
                }

                // the correct entity type is at the required position

                if (en instanceof ItemFrame itemFrame) {
                    // item frames must contain the correct item as well
                    var kibuNbt = entity.getExtraNbt();

                    if (!kibuNbt.contains("Item")) continue next;  // incorrect item frame

                    var kibuItem = kibuNbt.getCompound("Item");

                    if (kibuItem == null) continue next;  // incorrect item frame

                    CompoundTag item = FabricNbtConversion.convert(kibuItem, CompoundTag.class);

                    ItemStack expected = ItemHelper.fromNbt(world.registryAccess(), item).orElse(ItemStack.EMPTY);
                    ItemStack actual = itemFrame.getItem();

                    if ((!expected.isEmpty() || !actual.isEmpty()) && !actual.is(expected.getItem())) {
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

    private int evaluateBlocks(ServerLevel world, BlockStructure structure, BlockPos.MutableBlockPos pointer) {
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
        if (first.hasProperty(STAIRS_SHAPE) && second.hasProperty(STAIRS_SHAPE)
            && first.hasProperty(HORIZONTAL_FACING) && second.hasProperty(HORIZONTAL_FACING)) {

            StairsShape firstShape = first.getValue(STAIRS_SHAPE);
            StairsShape secondShape = second.getValue(STAIRS_SHAPE);

            if (firstShape != secondShape) {
                // rotate second accordingly so that equivalences are formed
                if ((firstShape == INNER_LEFT && secondShape == INNER_RIGHT) || (firstShape == OUTER_LEFT && secondShape == OUTER_RIGHT)) {
                    second = second
                            .setValue(STAIRS_SHAPE, firstShape)
                            .setValue(HORIZONTAL_FACING, second.getValue(HORIZONTAL_FACING).getClockWise());
                } else if ((firstShape == INNER_RIGHT && secondShape == INNER_LEFT) || (firstShape == OUTER_RIGHT && secondShape == OUTER_LEFT)) {
                    second = second
                            .setValue(STAIRS_SHAPE, firstShape)
                            .setValue(HORIZONTAL_FACING, second.getValue(HORIZONTAL_FACING).getCounterClockWise());
                }
            }
        }

        // leaves distance
        first = first.trySetValue(BlockStateProperties.DISTANCE, 1);
        second = second.trySetValue(BlockStateProperties.DISTANCE, 1);

        // leaves persistent
        first = first.trySetValue(BlockStateProperties.PERSISTENT, true);
        second = second.trySetValue(BlockStateProperties.PERSISTENT, true);

        return first.equals(second);
    }

    public boolean isCompleted(ServerLevel world, SbModule module) {
        int score = evaluate(world, module);
        int maxScore = module.getMaxScore();

        logger.info("Island completion: {} / {}", score, maxScore);

        return score >= maxScore;
    }

    public Vec3 getCenter() {
        return buildingArea.getCenter().with(Direction.Axis.Y, buildingArea.min().getY());
    }
}
