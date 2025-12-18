package work.lclpnet.ap2.game.speed_builders.util;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import lombok.Setter;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.scores.PlayerTeam;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.game.speed_builders.data.SbIsland;
import work.lclpnet.ap2.game.speed_builders.data.SbModule;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.kibu.access.entity.ServerPlayerAccess;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class SbManager {

    private static final int BASE_BUILD_DURATION_SECONDS = 25;
    private static final int MIN_BUILD_DURATION_SECONDS = 5;
    private static final int SUCCESSIVE_COMPLETION_REDUCTION_SECONDS = 7;

    private final Map<UUID, SbIsland> islands;
    private final List<SbModule> modules;
    private final MiniGameHandle gameHandle;
    private final ServerLevel world;
    private final Logger logger;
    private final Random random;
    private final Runnable allPlayersCompleted;
    private final List<SbModule> queue = new ArrayList<>();
    private final Object2LongMap<UUID> lastEdited = new Object2LongOpenHashMap<>();
    private final Set<UUID> edited = new HashSet<>();
    private final Set<UUID> completed = new HashSet<>();
    @Setter
    private boolean buildingPhase = false;
    private SbModule currentModule = null;
    @Setter
    private PlayerTeam team = null;
    private int successiveCompletion = 0;
    private int round = 0;

    public SbManager(Map<UUID, SbIsland> islands, List<SbModule> modules, MiniGameHandle gameHandle, ServerLevel world,
                     Random random, Runnable allPlayersCompleted) {
        this.islands = islands;
        this.modules = Collections.unmodifiableList(modules);
        this.gameHandle = gameHandle;
        this.logger = gameHandle.getLogger();
        this.world = world;
        this.random = random;
        this.allPlayersCompleted = allPlayersCompleted;
    }

    public void eachIsland(BiConsumer<SbIsland, ServerPlayer> action) {
        PlayerList playerManager = gameHandle.getServer().getPlayerList();

        islands.forEach((uuid, island) -> {
            ServerPlayer player = playerManager.getPlayer(uuid);

            if (player != null) {
                action.accept(island, player);
            }
        });
    }

    public boolean canModify(ServerPlayer player) {
        return buildingPhase && !completed.contains(player.getUUID());
    }

    public void clearIslands() {
        for (var island : activeIslands()) {
            island.getValue().clearBuildingArea(world);
        }
    }

    public boolean isWithinBuildingArea(ServerPlayer player, BlockPos pos) {
        SbIsland island = islands.get(player.getUUID());

        return island != null && island.isWithinBuildingArea(pos);
    }

    public synchronized void setModule(SbModule module) {
        CustomScoreboardManager scoreboardManager = gameHandle.getScoreboardManager();
        PlayerList playerManager = gameHandle.getServer().getPlayerList();

        for (var entry : activeIslands()) {
            ServerPlayer player = playerManager.getPlayer(entry.getKey());

            if (player == null) continue;

            SbIsland island = entry.getValue();

            if (!island.supports(module)) {
                logger.error("Module {} is incompatible with island {}", module, island);
                continue;
            }

            island.clearBuildingArea(world);
            island.placeModulePreview(module, world, team, scoreboardManager);
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

        return queue.removeFirst();
    }

    public Optional<ServerPlayer> getWorstPlayer() {
        var evaluation = evaluate();
        var minScore = evaluation.values().stream().mapToInt(Integer::intValue).min().orElse(0);

        return evaluation.entrySet().stream()
                // find players with minScore
                .filter(entry -> entry.getValue() == minScore)
                // sort by last edited
                .sorted(Comparator.<Map.Entry<ServerPlayer, Integer>>comparingLong(entry ->
                        lastEdited.getOrDefault(entry.getKey().getUUID(), Long.MAX_VALUE)).reversed())
                // map to actual player
                .map(Map.Entry::getKey)
                .findFirst();
    }

    private Set<Map.Entry<UUID, SbIsland>> activeIslands() {
        Participants participants = gameHandle.getParticipants();

        return islands.entrySet().stream()
                .filter(entry -> participants.isParticipating(entry.getKey()))
                .collect(Collectors.toSet());
    }

    private Map<ServerPlayer, Integer> evaluate() {
        if (currentModule == null) {
            return Map.of();
        }

        PlayerList playerManager = gameHandle.getServer().getPlayerList();
        Map<ServerPlayer, Integer> scores = new HashMap<>();

        for (var entry : activeIslands()) {
            ServerPlayer player = playerManager.getPlayer(entry.getKey());

            if (player == null) continue;

            SbIsland island = entry.getValue();
            int score = island.evaluate(world, currentModule);

            scores.put(player, score);
        }

        return scores;
    }

    public List<? extends Entity> getPreviewEntities() {
        var it = activeIslands().iterator();

        if (!it.hasNext()) {
            return List.of();
        }

        return it.next().getValue().getPreviewEntities(world);
    }

    public void onEdit(ServerPlayer player) {
        if (currentModule == null || completed.contains(player.getUUID())) return;

        lastEdited.put(player.getUUID(), System.currentTimeMillis());

        edited.add(player.getUUID());
    }

    public void tick() {
        checkPlayerPositions();
        processEdits();
    }

    private void processEdits() {
        if (edited.isEmpty()) return;

        Participants participants = gameHandle.getParticipants();
        PlayerList playerManager = gameHandle.getServer().getPlayerList();

        for (UUID uuid : edited) {
            if (!participants.isParticipating(uuid)) continue;

            ServerPlayer player = playerManager.getPlayer(uuid);

            if (player == null) continue;

            onEdited(player);
        }

        edited.clear();
    }

    private void checkPlayerPositions() {
        PlayerList playerManager = gameHandle.getServer().getPlayerList();

        for (var entry : activeIslands()) {
            ServerPlayer player = playerManager.getPlayer(entry.getKey());

            if (player == null || !player.getAbilities().mayfly) continue;

            SbIsland island = entry.getValue();

            if (island.getMovementBounds().contains(player.position())) continue;

            island.teleport(player);
        }
    }

    private void onEdited(ServerPlayer player) {
        // check if the player's used all the materials
        Inventory inventory = player.getInventory();

        for (int i = 0, size = inventory.getContainerSize(); i < size; i++) {
            ItemStack stack = inventory.getItem(i);

            if (stack.isEmpty() || stack.is(Items.WATER_BUCKET) || stack.is(Items.LAVA_BUCKET)) continue;

            return;
        }

        logger.info("Player {} has no items left", player.getScoreboardName());

        // the player used all the materials, check if the building is complete
        SbIsland island = islands.get(player.getUUID());

        if (island == null || !island.isCompleted(world, currentModule)) return;

        if (!completed.add(player.getUUID())) return;

        logger.info("Player {} has completed the building", player.getScoreboardName());

        ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.75f, 1.1f);

        var msg = gameHandle.getTranslations().translateText(player, "game.ap2.speed_builders.completed")
                .formatted(ChatFormatting.GREEN);

        player.sendSystemMessage(msg);

        checkOverallCompletion();
    }

    private void checkOverallCompletion() {
        if (completed.size() < gameHandle.getParticipants().count()) return;

        logger.info("All players completed their buildings");
        allPlayersCompleted.run();
    }

    public Optional<SbIsland> getIsland(ServerPlayer player) {
        return Optional.ofNullable(islands.get(player.getUUID()));
    }

    public boolean allIslandsComplete() {
        PlayerList playerManager = gameHandle.getServer().getPlayerList();

        return islands.entrySet().stream().allMatch(entry -> {
            UUID uuid = entry.getKey();
            ServerPlayer player = playerManager.getPlayer(uuid);

            if (player == null) {
                // do not count offline players
                return true;
            }

            SbIsland island = entry.getValue();

            return island.isCompleted(world, currentModule);
        });
    }

    public void incrementSuccessiveCompletion() {
        successiveCompletion++;
    }

    public void resetSuccessiveCompletion() {
        successiveCompletion = 0;
    }

    public void reset() {
        this.completed.clear();
        this.lastEdited.clear();
        this.edited.clear();
    }

    public int getBuildingDurationTicks() {
        if (currentModule == null) {
            return BASE_BUILD_DURATION_SECONDS;
        }

        int complexity = currentModule.getComplexity();
        int bonusTime = (int) Math.floor((Math.max(0, complexity - 64) * 0.4));
        int reduction = Math.max(0, successiveCompletion * SUCCESSIVE_COMPLETION_REDUCTION_SECONDS);

        return Math.max(MIN_BUILD_DURATION_SECONDS, BASE_BUILD_DURATION_SECONDS + bonusTime - reduction);
    }

    public void incrementRound() {
        round++;
    }

    public int getRoundsCompleted(ServerPlayer player, boolean winner) {
        if (completed.contains(player.getUUID()) || winner) {
            return round + 1;
        }

        return round;
    }
}
