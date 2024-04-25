package work.lclpnet.ap2.game.speed_builders;

import net.minecraft.block.BlockState;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.game.speed_builders.data.SbIsland;
import work.lclpnet.ap2.game.speed_builders.data.SbModule;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess;
import work.lclpnet.kibu.mc.KibuBlockPos;
import work.lclpnet.kibu.mc.KibuBlockState;
import work.lclpnet.kibu.schematic.FabricBlockStateAdapter;
import work.lclpnet.kibu.structure.BlockStructure;

import java.util.*;
import java.util.function.BiConsumer;

public class SpeedBuildersManager {

    private final Map<UUID, SbIsland> islands;
    private final List<SbModule> modules;
    private final MiniGameHandle gameHandle;
    private final ServerWorld world;
    private final Logger logger;
    private final Random random;
    private final List<SbModule> queue = new ArrayList<>();
    private boolean buildingPhase = false;
    private SbModule currentModule = null;
    private Team team = null;

    public SpeedBuildersManager(Map<UUID, SbIsland> islands, List<SbModule> modules, MiniGameHandle gameHandle, ServerWorld world, Random random) {
        this.islands = islands;
        this.modules = Collections.unmodifiableList(modules);
        this.gameHandle = gameHandle;
        this.logger = gameHandle.getLogger();
        this.world = world;
        this.random = random;
    }

    public void eachIsland(BiConsumer<SbIsland, ServerPlayerEntity> action) {
        PlayerManager playerManager = gameHandle.getServer().getPlayerManager();

        islands.forEach((uuid, island) -> {
            ServerPlayerEntity player = playerManager.getPlayer(uuid);

            if (player != null) {
                action.accept(island, player);
            }
        });
    }

    public boolean isBuildingPhase() {
        return buildingPhase;
    }

    public void setBuildingPhase(boolean buildingPhase) {
        this.buildingPhase = buildingPhase;
    }

    public boolean isWithinBuildingArea(ServerPlayerEntity player, BlockPos pos) {
        SbIsland island = islands.get(player.getUuid());

        return island != null && island.isWithinBuildingArea(pos);
    }

    public synchronized void setModule(SbModule module) {
        CustomScoreboardManager scoreboardManager = gameHandle.getScoreboardManager();

        for (SbIsland island : islands.values()) {
            if (!island.supports(module)) {
                logger.error("Module {} is incompatible with island {}", module, island);
                continue;
            }

            island.clearBuildingArea(world);
            island.placeModulePreview(module, world, team, scoreboardManager);
            island.copyPreviewFloorToBuildArea(world);
        }

        currentModule = module;
    }

    @NotNull
    public SbModule nextModule() {
        if (queue.isEmpty()) {
            queue.addAll(modules);
            Collections.shuffle(queue, random);
        }

        if (queue.isEmpty()) {
            throw new IllegalStateException("There are no modules defined");
        }

        return queue.stream()
                .filter(module -> module.id().equals("sb_copper"))  // TODO remove
                .findAny()
                .orElseGet(() -> queue.remove(0));
    }

    private List<ItemStack> getCraftingMaterials() {
        if (currentModule == null) {
            return List.of();
        }

        BlockStructure structure = currentModule.structure();
        FabricBlockStateAdapter adapter = FabricBlockStateAdapter.getInstance();
        int originY = structure.getOrigin().getY();

        Map<BlockState, Integer> states = new HashMap<>();
        boolean waterRequired = false;

        for (KibuBlockPos pos : structure.getBlockPositions()) {
            if (pos.getY() - originY == 0) continue;

            KibuBlockState kibuState = structure.getBlockState(pos);
            BlockState state = adapter.revert(kibuState);

            if (state == null) continue;

            if (!waterRequired && state.getProperties().contains(Properties.WATERLOGGED)) {
                if (state.get(Properties.WATERLOGGED)) {
                    waterRequired = true;
                }
            }

            states.compute(state, (_state, prev) -> prev == null ? 1 : prev + 1);
        }

        List<ItemStack> stacks = new ArrayList<>();

        states.forEach((state, count) -> {
            ItemStack stack = getMaterialStack(state);

            if (stack.isEmpty()) return;

            count *= stack.getCount();
            stack.setCount(1);

            int maxCount = stack.getMaxCount();

            while (count > 0) {
                int decrement = Math.min(count, maxCount);

                stacks.add(stack.copyWithCount(decrement));

                count -= decrement;
            }
        });

        if (waterRequired) {
            stacks.add(new ItemStack(Items.WATER_BUCKET));
        }

        return stacks;
    }

    public void giveBuildingMaterials(Iterable<? extends ServerPlayerEntity> players) {
        List<ItemStack> stacks = getCraftingMaterials();

        for (ServerPlayerEntity player : players) {
            for (ItemStack stack : stacks) {
                player.getInventory().insertStack(stack.copy());
            }

            PlayerInventoryAccess.setSelectedSlot(player, 0);
        }
    }

    public Map<ServerPlayerEntity, Integer> evaluate() {
        if (currentModule == null) {
            return Map.of();
        }

        PlayerManager playerManager = gameHandle.getServer().getPlayerManager();
        Map<ServerPlayerEntity, Integer> scores = new HashMap<>();

        for (var entry : islands.entrySet()) {
            UUID uuid = entry.getKey();
            ServerPlayerEntity player = playerManager.getPlayer(uuid);

            if (player == null) continue;

            SbIsland island = entry.getValue();
            int score = island.evaluate(world, currentModule);

            scores.put(player, score);
        }

        return scores;
    }

    public ItemStack getSourceStack(BlockState state) {
        var properties = state.getProperties();

        if (properties.contains(Properties.SLAB_TYPE)) {
            SlabType slabType = state.get(Properties.SLAB_TYPE);

            if (slabType == SlabType.DOUBLE) {
                return new ItemStack(state.getBlock().asItem(), 2);
            }
        }

        return new ItemStack(state.getBlock().asItem());
    }

    public ItemStack getMaterialStack(BlockState state) {
        var properties = state.getProperties();

        if (properties.contains(Properties.DOUBLE_BLOCK_HALF)) {
            DoubleBlockHalf half = state.get(Properties.DOUBLE_BLOCK_HALF);

            if (half == DoubleBlockHalf.UPPER) {
                return ItemStack.EMPTY;
            }
        }

        if (properties.contains(Properties.BED_PART)) {
            BedPart part = state.get(Properties.BED_PART);

            if (part == BedPart.HEAD) {
                return ItemStack.EMPTY;
            }
        }

        return getSourceStack(state);
    }

    public void setTeam(Team team) {
        this.team = team;
    }
}
