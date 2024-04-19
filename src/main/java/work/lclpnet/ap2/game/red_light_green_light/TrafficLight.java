package work.lclpnet.ap2.game.red_light_green_light;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.json.JSONObject;
import work.lclpnet.ap2.impl.map.MapUtil;

import java.util.EnumSet;

public record TrafficLight(BlockPos red, BlockPos yellow, BlockPos green) {

    public void set(EnumSet<Status> status, ServerWorld world) {
        BlockState off = Blocks.BLACK_CONCRETE.getDefaultState();

        if (status.contains(Status.RED)) {
            world.setBlockState(red, Blocks.RED_CONCRETE.getDefaultState());
        } else {
            world.setBlockState(red, off);
        }

        if (status.contains(Status.YELLOW)) {
            world.setBlockState(yellow, Blocks.YELLOW_CONCRETE.getDefaultState());
        } else {
            world.setBlockState(yellow, off);
        }

        if (status.contains(Status.GREEN)) {
            world.setBlockState(green, Blocks.LIME_CONCRETE.getDefaultState());
        } else {
            world.setBlockState(green, off);
        }
    }

    public static TrafficLight fromJson(JSONObject json) {
        BlockPos red = MapUtil.readBlockPos(json.getJSONArray("red"));
        BlockPos yellow = MapUtil.readBlockPos(json.getJSONArray("yellow"));
        BlockPos green = MapUtil.readBlockPos(json.getJSONArray("green"));

        return new TrafficLight(red, yellow, green);
    }

    public enum Status {
        RED, YELLOW, GREEN
    }
}
