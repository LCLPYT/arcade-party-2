package work.lclpnet.ap2.game.eggventure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.api.util.heads.PlayerHead;
import work.lclpnet.ap2.base.ApConstants;
import work.lclpnet.ap2.base.resource.ApResources;
import work.lclpnet.ap2.impl.game.DefaultGameInstance;
import work.lclpnet.ap2.impl.game.data.ScoreTimeDataContainer;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.tags.PlayerHeadTags;
import work.lclpnet.ap2.impl.util.ApRegistries;
import work.lclpnet.ap2.impl.util.debug.DebugController;
import work.lclpnet.ap2.impl.util.world.stage.BlockShape;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class EggventureInstance extends DefaultGameInstance implements MapBootstrap {

    private static final boolean DEBUG_EGG_POSITIONS = true;
    private static final MapCodec<Boolean> NBT_CODEC = Codec.BOOL.fieldOf("easter_egg");

    private final ScoreTimeDataContainer<ServerPlayerEntity, PlayerRef> data = new ScoreTimeDataContainer<>(PlayerRef::create);

    public EggventureInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    @Override
    protected DataContainer<ServerPlayerEntity, PlayerRef> getData() {
        return data;
    }

    @Override
    public CompletableFuture<Void> createWorldBootstrap(ServerWorld world, GameMap map) {
        List<PlayerHead> heads = eggVariants(world.getRegistryManager());

        var debugController = new DebugController();

        if (ApConstants.DEBUG) {
            debugController.init(ApResources.getInstance(), world);
        }

        List<BlockPos> positions = new ArrayList<>();
        BlockShape shape = MapUtil.readShape(map, "egg-area");

        for (BlockPos pos : shape) {
            if (!isEasterEgg(world, pos)) continue;

            positions.add(pos.toImmutable());

            if (DEBUG_EGG_POSITIONS) {
                debugController.renderer().ifPresent(renderer
                        -> renderer.marker(pos.toCenterPos(), Blocks.RED_TERRACOTTA.getDefaultState(), 0xff0000));
            }
        }

        return CompletableFuture.completedFuture(null);
    }

    private boolean isEasterEgg(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);

        if (!state.isOf(Blocks.PLAYER_HEAD) && !state.isOf(Blocks.PLAYER_WALL_HEAD)) return false;

        SkullBlockEntity skull = world.getBlockEntity(pos, BlockEntityType.SKULL).orElse(null);

        if (skull == null) return false;

        return skull.getComponents()
                .getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT)
                .get(NBT_CODEC)
                .result()
                .orElse(false);
    }

    static @NotNull List<PlayerHead> eggVariants(DynamicRegistryManager registryManager) {
        var headEntries = registryManager
                .getOrThrow(ApRegistries.PLAYER_HEAD)
                .iterateEntries(PlayerHeadTags.EASTER_EGGS);

        List<PlayerHead> heads = new ArrayList<>();

        for (var entry : headEntries) {
            heads.add(entry.value());
        }
        return heads;
    }

    @Override
    protected void prepare() {
        new DebugEggsCommand(gameHandle.getLogger()).register(gameHandle.getCommandRegistrar());
    }

    @Override
    protected void ready() {

    }
}
