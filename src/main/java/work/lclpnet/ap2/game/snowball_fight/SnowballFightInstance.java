package work.lclpnet.ap2.game.snowball_fight;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;

import java.util.List;
import java.util.Random;

public class SnowballFightInstance extends EliminationGameInstance {

    public SnowballFightInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
        useSurvivalMode();
    }

    @Override
    protected void prepare() {
        ServerWorld world = getWorld();
        Participants participants = gameHandle.getParticipants();

        Random random = new Random();
        SnowballFightSpawns spawns = new SnowballFightSpawns(16);
        List<Vec3d> available = spawns.findSpawns(world, getMap());
        List<Vec3d> spacedSpawns = spawns.generateSpacedSpawns(available, participants.count(), random);

        int i = 0;

        for (ServerPlayerEntity player : participants) {
            Vec3d spawn = spacedSpawns.get(i++);

            float yaw = random.nextFloat(360) - 180;

            player.teleport(world, spawn.getX(), spawn.getY(), spawn.getZ(), yaw, 0);
        }
    }

    @Override
    protected void ready() {

    }
}
