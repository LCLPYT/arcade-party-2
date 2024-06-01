package work.lclpnet.ap2.game.maniac_digger;

import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.api.map.MapBootstrapFunction;
import work.lclpnet.ap2.game.maniac_digger.data.MdGenerator;
import work.lclpnet.ap2.game.maniac_digger.data.MdPipe;
import work.lclpnet.ap2.impl.game.DefaultGameInstance;
import work.lclpnet.ap2.impl.game.PlayerUtil;
import work.lclpnet.ap2.impl.game.data.OrderedDataContainer;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.map.ServerThreadMapBootstrap;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class ManiacDiggerInstance extends DefaultGameInstance implements MapBootstrapFunction {

    private final OrderedDataContainer<ServerPlayerEntity, PlayerRef> data = new OrderedDataContainer<>(PlayerRef::create);
    private final Map<UUID, MdPipe> pipes = new HashMap<>();

    public ManiacDiggerInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    @Override
    protected DataContainer<ServerPlayerEntity, PlayerRef> getData() {
        return data;
    }

    @Override
    protected MapBootstrap getMapBootstrap() {
        // run the bootstrap on the server thread
        return new ServerThreadMapBootstrap(this);
    }


    @Override
    public void bootstrapWorld(ServerWorld world, GameMap map) {
        MdGenerator generator = new MdGenerator(world, map, gameHandle.getLogger(), new Random());
        Participants participants = gameHandle.getParticipants();

        var pipes = generator.generate(participants.count());

        int i = 0;

        for (ServerPlayerEntity player : participants) {
            MdPipe pipe = pipes.get(i++);
            this.pipes.put(player.getUuid(), pipe);
        }
    }

    @Override
    protected void prepare() {
        ServerWorld world = getWorld();

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            MdPipe pipe = pipes.get(player.getUuid());

            if (pipe == null) continue;

            Vec3d spawn = pipe.spawn();
            player.teleport(world, spawn.getX(), spawn.getY(), spawn.getZ(), 0f, 0f);
            PlayerUtil.setAttribute(player, EntityAttributes.GENERIC_SCALE, 0.5);
        }
    }

    @Override
    protected void ready() {

    }
}
