package work.lclpnet.ap2.game.speed_builders.data;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
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
import work.lclpnet.kibu.util.StructureWriter;
import work.lclpnet.kibu.util.math.Matrix3i;

import java.util.EnumSet;
import java.util.List;

import static work.lclpnet.kibu.util.StructureWriter.Option.*;

public class SbIsland {

    private final SbIslandData data;
    private final BlockPos spawnWorldPos;
    private final BlockBox buildingArea;
    private final BlockBox previewArea;
    private final BlockBox bounds;

    /**
     * Constructor.
     *
     * @param data   The island data.
     * @param origin The origin of the schematic, used to calculate relative data.
     * @param offset The offset the island structure is placed at in world coordinates.
     * @param bounds The island bounds in world space.
     */
    public SbIsland(SbIslandData data, BlockPos origin, BlockPos offset, BlockBox bounds) {
        this.data = data;

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

        return buildArea.getWidth() == structure.getWidth() && buildArea.getLength() == structure.getLength() && structure.getHeight() <= buildArea.getHeight();
    }

    public void placeModulePreview(SbModule module, ServerWorld world, Team team, CustomScoreboardManager scoreboardManager) {
        clear(previewArea, world);

        BlockStructure structure = module.structure();

        var options = EnumSet.of(FORCE_STATE, SKIP_AIR, SKIP_DROPS, SKIP_BLOCK_ENTITIES);
        StructureWriter.placeStructure(structure, world, previewArea.getMin().down(), Matrix3i.IDENTITY, options);

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
        BlockPos from = buildingArea.getMin().down();
        BlockPos to = buildingArea.getMax().down(buildingArea.getHeight());
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
        KibuBlockPos origin = structure.getOrigin();
        final int ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();

        BlockPos min = buildingArea.getMin();
        final int mx = min.getX(), my = min.getY(), mz = min.getZ();

        BlockPos.Mutable pointer = new BlockPos.Mutable();
        FabricBlockStateAdapter adapter = FabricBlockStateAdapter.getInstance();

        // each block that of the structure that is present in the build area is awarded with one point
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
            BlockState actual = world.getBlockState(pointer);

            if (actual.equals(expected)) {
                score++;
            }
        }

        var presentEntities = getEntities(world, buildingArea);

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

                if (!identifier.equals(id)) continue;

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
                        continue next;  // incorrect item frame
                    }
                }

                score++;

                break;
            }
        }

        return score;
    }

    public boolean isCompleted(ServerWorld world, SbModule module) {
        int score = evaluate(world, module);

        return score >= module.getMaxScore();
    }

    public Vec3d getCenter() {
        Vec3d buildingCenter = buildingArea.getCenter().withAxis(Direction.Axis.Y, buildingArea.getMin().getY());
        Vec3d previewCenter = previewArea.getCenter().withAxis(Direction.Axis.Y, previewArea.getMin().getY());

        return buildingCenter.add(previewCenter).multiply(0.5);
    }

    public boolean isWithinBounds(BlockPos pos) {
        return bounds.contains(pos);
    }
}
