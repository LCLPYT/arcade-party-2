package work.lclpnet.ap2.game.eggventure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.ColorUtil;
import work.lclpnet.ap2.impl.util.debug.DebugController;
import work.lclpnet.ap2.impl.util.world.stage.BlockShape;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

public class EggventureInstance extends DefaultGameInstance implements MapBootstrap {

    private static final boolean DEBUG_EGG_POSITIONS = true;
    private static final MapCodec<Boolean> NBT_CODEC = Codec.BOOL.fieldOf("easter_egg");

    private final ScoreTimeDataContainer<ServerPlayerEntity, PlayerRef> data = new ScoreTimeDataContainer<>(PlayerRef::create);
    private final Random random = new Random();

    public EggventureInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    @Override
    protected DataContainer<ServerPlayerEntity, PlayerRef> getData() {
        return data;
    }

    @Override
    public CompletableFuture<Void> createWorldBootstrap(ServerWorld world, GameMap map) {
        BlockShape shape = MapUtil.readShape(map, "egg-area");
        List<BlockPos> positions = new ArrayList<>();

        for (BlockPos pos : shape) {
            if (isEasterEgg(world, pos)) {
                positions.add(pos.toImmutable());
            }
        }

        int minEggs = map.requireProperty("min-eggs");
        int maxEggs = map.requireProperty("max-eggs");
        int eggs = minEggs + random.nextInt(maxEggs - minEggs + 1);

        List<PlayerHead> variants = eggVariants(world.getRegistryManager());

        if (variants.isEmpty()) {
            throw new IllegalStateException("There are no egg variants defined");
        }

        var debugController = new DebugController();

        if (ApConstants.DEBUG) {
            debugController.init(ApResources.getInstance(), world);

            gameHandle.getLogger().info("There are {} possible egg positions and {} should be placed", positions.size(), eggs);
        }

        for (int i = 0; i < eggs && !positions.isEmpty(); i++) {
            BlockPos pos = positions.remove(random.nextInt(positions.size()));

            if (DEBUG_EGG_POSITIONS) {
                debugController.renderer().ifPresent(renderer
                        -> renderer.marker(pos.toCenterPos(), Blocks.GREEN_TERRACOTTA.getDefaultState(), 0x00ff00));
            }

            PlayerHead variant = variants.get(random.nextInt(variants.size()));

            world.getBlockEntity(pos, BlockEntityType.SKULL).ifPresent(variant::apply);
        }

        for (BlockPos pos : positions) {
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.SKIP_DROPS | Block.FORCE_STATE);

            if (DEBUG_EGG_POSITIONS) {
                debugController.renderer().ifPresent(renderer
                        -> renderer.marker(pos.toCenterPos(), Blocks.BLUE_TERRACOTTA.getDefaultState(), 0x0000ff));
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

        var variants = eggVariants(getWorld().getRegistryManager());

        if (variants.isEmpty()) {
            throw new IllegalStateException("There are no egg variants defined");
        }

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            PlayerHead variant = variants.get(random.nextInt(variants.size()));
            player.equipStack(EquipmentSlot.HEAD, variant.createStack());

            int color = ColorUtil.getRandomHsvColor(random);

            ItemStack chestPlate = new ItemStack(Items.LEATHER_CHESTPLATE);
            chestPlate.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(color, false));
            player.equipStack(EquipmentSlot.CHEST, chestPlate);

            ItemStack leggings = new ItemStack(Items.LEATHER_LEGGINGS);
            leggings.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(color, false));
            player.equipStack(EquipmentSlot.LEGS, leggings);

            ItemStack boots = new ItemStack(Items.LEATHER_BOOTS);
            boots.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(color, false));
            player.equipStack(EquipmentSlot.FEET, boots);
        }
    }

    @Override
    protected void ready() {
        BlockBox gate = MapUtil.readBox(getMap().requireProperty("gate"));
        ServerWorld world = getWorld();

        for (BlockPos pos : gate) {
            world.setBlockState(pos, Blocks.AIR.getDefaultState());
        }
    }
}
