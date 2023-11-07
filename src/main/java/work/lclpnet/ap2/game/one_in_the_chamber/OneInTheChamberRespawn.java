package work.lclpnet.ap2.game.one_in_the_chamber;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.Random;

public class OneInTheChamberRespawn {

    public static void respawn(ArrayList<BlockPos> spawnPoints, ServerPlayerEntity player) {

        Random rand = new Random();

        int randomIndex = rand.nextInt(0, spawnPoints.size() - 1);
        BlockPos randomSpawn = spawnPoints.get(randomIndex);

        player.changeGameMode(GameMode.SPECTATOR);
        player.heal(20);
        player.teleport(randomSpawn.getX() + 0.5,randomSpawn.getY(),randomSpawn.getZ() + 0.5);
        player.changeGameMode(GameMode.ADVENTURE);
    }
}
