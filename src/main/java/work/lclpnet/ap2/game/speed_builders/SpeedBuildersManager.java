package work.lclpnet.ap2.game.speed_builders;

import net.minecraft.entity.Entity;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.game.speed_builders.data.SbIsland;
import work.lclpnet.ap2.game.speed_builders.data.SbModule;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;

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
                .filter(module -> module.id().equals("sb_mine"))  // TODO remove
                .findAny()
                .orElseGet(() -> queue.remove(0));
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

    public List<? extends Entity> getPreviewEntities() {
        var it = islands.values().iterator();

        if (!it.hasNext()) {
            return List.of();
        }

        return it.next().getPreviewEntities(world);
    }

    public void setTeam(Team team) {
        this.team = team;
    }
}
