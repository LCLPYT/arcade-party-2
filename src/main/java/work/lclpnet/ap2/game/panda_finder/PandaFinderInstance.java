package work.lclpnet.ap2.game.panda_finder;

import net.minecraft.entity.EntityType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.util.world.AdjacentBlocks;
import work.lclpnet.ap2.api.util.world.BlockPredicate;
import work.lclpnet.ap2.api.util.world.WorldScanner;
import work.lclpnet.ap2.impl.game.DefaultGameInstance;
import work.lclpnet.ap2.impl.game.data.ScoreDataContainer;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.world.BfsWorldScanner;
import work.lclpnet.ap2.impl.util.world.NotOccupiedBlockPredicate;
import work.lclpnet.ap2.impl.util.world.SimpleAdjacentBlocks;
import work.lclpnet.ap2.impl.util.world.SizedSpaceFinder;

import java.util.Random;

public class PandaFinderInstance extends DefaultGameInstance {

    public static final int MAX_SCORE = 3;
    private final ScoreDataContainer data = new ScoreDataContainer();
    private final Random random = new Random();
    private PandaManager pandaManager;

    public PandaFinderInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    @Override
    protected DataContainer getData() {
        return data;
    }

    @Override
    protected void prepare() {
        scanWorld();
    }

    @Override
    protected void ready() {
        pandaManager.next();
    }

    private void scanWorld() {
        BlockPos start = MapUtil.readBlockPos(getMap().requireProperty("search-start"));
        BlockBox bounds = MapUtil.readBox(getMap().requireProperty("bounds"));
        BlockBox exclude = MapUtil.readBox(getMap().requireProperty("search-exclude"));

        ServerWorld world = getWorld();

        BlockPredicate predicate = BlockPredicate.and(new NotOccupiedBlockPredicate(world), pos -> {
            int x = pos.getX(), y = pos.getY(), z = pos.getZ();
            return bounds.contains(x, y, z) && !exclude.contains(x, y, z);
        });

        AdjacentBlocks adjacent = new SimpleAdjacentBlocks(predicate);
        WorldScanner scanner = new BfsWorldScanner(adjacent);

        SizedSpaceFinder spaceFinder = SizedSpaceFinder.create(world, EntityType.PANDA);
        var spaces = spaceFinder.findSpaces(scanner.scan(start));

        pandaManager = new PandaManager(spaces, random, world);
    }
}
