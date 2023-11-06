package work.lclpnet.ap2.game.fine_tuning;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import work.lclpnet.kibu.translate.text.RootText;

public class FineTuningRoom {

    private final ServerWorld world;
    private final BlockPos[] noteBlocks;
    private final BlockPos spawn;
    private final float yaw;
    private final BlockPos signPos;

    public FineTuningRoom(ServerWorld world, BlockPos pos, Vec3i[] relNoteBlock, Vec3i relSpawn, float yaw, Vec3i relSign) {
        this.world = world;

        BlockPos[] noteBlocks = new BlockPos[relNoteBlock.length];

        for (int j = 0; j < relNoteBlock.length; j++) {
            noteBlocks[j] = pos.add(relNoteBlock[j]);
        }

        this.noteBlocks = noteBlocks;
        this.spawn = pos.add(relSpawn);
        this.yaw = yaw;
        this.signPos = pos.add(relSign);
    }

    public void teleport(ServerPlayerEntity player) {
        double x = spawn.getX() + 0.5, y = spawn.getY(), z = spawn.getZ() + 0.5;

        player.teleport(world, x, y, z, yaw, 0.0F);
    }

    public void setSignText(RootText rootText) {
        BlockEntity blockEntity = world.getBlockEntity(signPos);

        if (!(blockEntity instanceof SignBlockEntity sign)) return;

        var empty = Text.empty();
        var lines = new Text[] {empty, rootText, empty, empty};
        var signText = new SignText(lines, lines, DyeColor.BLACK, true);

        sign.setText(signText, true);
        sign.setText(signText, false);
    }
}
