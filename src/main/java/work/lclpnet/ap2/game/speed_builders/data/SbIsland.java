package work.lclpnet.ap2.game.speed_builders.data;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.math.AffineIntMatrix;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.kibu.mc.KibuBlockPos;
import work.lclpnet.kibu.mc.KibuBlockState;
import work.lclpnet.kibu.schematic.FabricBlockStateAdapter;
import work.lclpnet.kibu.structure.BlockStructure;
import work.lclpnet.kibu.util.StructureWriter;
import work.lclpnet.kibu.util.math.Matrix3i;

import java.util.EnumSet;

import static work.lclpnet.kibu.util.StructureWriter.Option.*;

public class SbIsland {

    private final SbIslandData data;
    private final BlockPos spawnWorldPos;
    private final BlockBox buildingArea;
    private final BlockBox previewArea;

    /**
     * Constructor.
     * @param data The island data.
     * @param origin The origin of the schematic, used to calculate relative data.
     * @param offset The offset the island structure is placed at in world coordinates.
     */
    public SbIsland(SbIslandData data, BlockPos origin, BlockPos offset) {
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

        var entities = world.getEntitiesByType(TypeFilter.instanceOf(MobEntity.class), previewArea.toBox(), entity -> true);

        for (MobEntity entity : entities) {
            entity.setNoGravity(true);
            entity.setSilent(true);
            entity.setAiDisabled(true);
            entity.setPersistent();
            entity.setInvulnerable(true);

            scoreboardManager.joinTeam(entity, team);
        }
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

        var entities = world.getEntitiesByType(TypeFilter.instanceOf(Entity.class), box.toBox(), entity -> !(entity instanceof ServerPlayerEntity));

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

            pointer.set(mx + rx, my + ry, mz + rz);

            KibuBlockState kibuState = structure.getBlockState(pos);
            BlockState expected = adapter.revert(kibuState);
            BlockState actual = world.getBlockState(pointer);

            if (actual.equals(expected)) {
                score++;
            }
        }

        return score;
    }
}
