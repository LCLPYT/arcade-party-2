package work.lclpnet.ap2.game.maniac_digger;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.api.map.MapBootstrapFunction;
import work.lclpnet.ap2.game.maniac_digger.data.MdGenerator;
import work.lclpnet.ap2.impl.game.DefaultGameInstance;
import work.lclpnet.ap2.impl.game.data.OrderedDataContainer;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.map.ServerThreadMapBootstrap;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.Random;

public class ManiacDiggerInstance extends DefaultGameInstance implements MapBootstrapFunction {

    private final OrderedDataContainer<ServerPlayerEntity, PlayerRef> data = new OrderedDataContainer<>(PlayerRef::create);

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
        generator.generate(gameHandle.getParticipants().count());
    }

    @Override
    protected void prepare() {

    }

    @Override
    protected void ready() {

    }
}
