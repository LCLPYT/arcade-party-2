package work.lclpnet.ap2.game.paintball.util;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.game.team.DyeTeamKey;
import work.lclpnet.ap2.api.game.team.Team;
import work.lclpnet.ap2.api.game.team.TeamManager;
import work.lclpnet.ap2.impl.game.data.IntScoreDataContainer;
import work.lclpnet.ap2.impl.game.data.type.TeamRef;
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Math.max;
import static net.minecraft.block.Blocks.*;

public class PaintManager {

    private final ServerWorld world;
    private final PaintballTeams teams;
    private final TeamManager teamManager;
    private final IntScoreDataContainer<Team, TeamRef> data;
    private final BlockShape bounds;
    private final Paintable concrete;
    private final Map<Block, Paintable> paintableBlocks = new HashMap<>();
    private final Map<Block, DyeTeamKey> blockTeamMap = new HashMap<>();
    private final Object2IntMap<DyeTeamKey> count = new Object2IntOpenHashMap<>();

    public PaintManager(ServerWorld world, PaintballTeams teams, TeamManager teamManager,
                        IntScoreDataContainer<Team, TeamRef> data, BlockShape bounds) {
        this.world = world;
        this.teams = teams;
        this.teamManager = teamManager;
        this.data = data;
        this.bounds = bounds;

        List<Paintable> paintables = new ArrayList<>();

        // wool
        paintables.add(team -> switch (team) {
            case WHITE -> WHITE_WOOL;
            case LIGHT_GRAY -> LIGHT_GRAY_WOOL;
            case DARK_GRAY -> GRAY_WOOL;
            case BLACK -> BLACK_WOOL;
            case BROWN -> BROWN_WOOL;
            case RED -> RED_WOOL;
            case BLUE -> BLUE_WOOL;
            case ORANGE -> ORANGE_WOOL;
            case YELLOW -> YELLOW_WOOL;
            case PURPLE -> PURPLE_WOOL;
            case LIME -> LIME_WOOL;
            case DARK_GREEN -> GREEN_WOOL;
            case CYAN -> CYAN_WOOL;
            case LIGHT_BLUE -> LIGHT_BLUE_WOOL;
            case MAGENTA -> MAGENTA_WOOL;
            case PINK -> PINK_WOOL;
        });

        // carpet
        paintables.add(team -> switch (team) {
            case WHITE -> WHITE_CARPET;
            case LIGHT_GRAY -> LIGHT_GRAY_CARPET;
            case DARK_GRAY -> GRAY_CARPET;
            case BLACK -> BLACK_CARPET;
            case BROWN -> BROWN_CARPET;
            case RED -> RED_CARPET;
            case BLUE -> BLUE_CARPET;
            case ORANGE -> ORANGE_CARPET;
            case YELLOW -> YELLOW_CARPET;
            case PURPLE -> PURPLE_CARPET;
            case LIME -> LIME_CARPET;
            case DARK_GREEN -> GREEN_CARPET;
            case CYAN -> CYAN_CARPET;
            case LIGHT_BLUE -> LIGHT_BLUE_CARPET;
            case MAGENTA -> MAGENTA_CARPET;
            case PINK -> PINK_CARPET;
        });

        // concrete
        concrete = team -> switch (team) {
            case WHITE -> WHITE_CONCRETE;
            case LIGHT_GRAY -> LIGHT_GRAY_CONCRETE;
            case DARK_GRAY -> GRAY_CONCRETE;
            case BLACK -> BLACK_CONCRETE;
            case BROWN -> BROWN_CONCRETE;
            case RED -> RED_CONCRETE;
            case BLUE -> BLUE_CONCRETE;
            case ORANGE -> ORANGE_CONCRETE;
            case YELLOW -> YELLOW_CONCRETE;
            case PURPLE -> PURPLE_CONCRETE;
            case LIME -> LIME_CONCRETE;
            case DARK_GREEN -> GREEN_CONCRETE;
            case CYAN -> CYAN_CONCRETE;
            case LIGHT_BLUE -> LIGHT_BLUE_CONCRETE;
            case MAGENTA -> MAGENTA_CONCRETE;
            case PINK -> PINK_CONCRETE;
        };

        paintables.add(concrete);

        // concrete powder
        paintables.add(team -> switch (team) {
            case WHITE -> WHITE_CONCRETE_POWDER;
            case LIGHT_GRAY -> LIGHT_GRAY_CONCRETE_POWDER;
            case DARK_GRAY -> GRAY_CONCRETE_POWDER;
            case BLACK -> BLACK_CONCRETE_POWDER;
            case BROWN -> BROWN_CONCRETE_POWDER;
            case RED -> RED_CONCRETE_POWDER;
            case BLUE -> BLUE_CONCRETE_POWDER;
            case ORANGE -> ORANGE_CONCRETE_POWDER;
            case YELLOW -> YELLOW_CONCRETE_POWDER;
            case PURPLE -> PURPLE_CONCRETE_POWDER;
            case LIME -> LIME_CONCRETE_POWDER;
            case DARK_GREEN -> GREEN_CONCRETE_POWDER;
            case CYAN -> CYAN_CONCRETE_POWDER;
            case LIGHT_BLUE -> LIGHT_BLUE_CONCRETE_POWDER;
            case MAGENTA -> MAGENTA_CONCRETE_POWDER;
            case PINK -> PINK_CONCRETE_POWDER;
        });

        // terracotta
        paintables.add(team -> switch (team) {
            case WHITE -> WHITE_TERRACOTTA;
            case LIGHT_GRAY -> LIGHT_GRAY_TERRACOTTA;
            case DARK_GRAY -> GRAY_TERRACOTTA;
            case BLACK -> BLACK_TERRACOTTA;
            case BROWN -> BROWN_TERRACOTTA;
            case RED -> RED_TERRACOTTA;
            case BLUE -> BLUE_TERRACOTTA;
            case ORANGE -> ORANGE_TERRACOTTA;
            case YELLOW -> YELLOW_TERRACOTTA;
            case PURPLE -> PURPLE_TERRACOTTA;
            case LIME -> LIME_TERRACOTTA;
            case DARK_GREEN -> GREEN_TERRACOTTA;
            case CYAN -> CYAN_TERRACOTTA;
            case LIGHT_BLUE -> LIGHT_BLUE_TERRACOTTA;
            case MAGENTA -> MAGENTA_TERRACOTTA;
            case PINK -> PINK_TERRACOTTA;
        });

        // glazed terracotta
        paintables.add(team -> switch (team) {
            case WHITE -> WHITE_GLAZED_TERRACOTTA;
            case LIGHT_GRAY -> LIGHT_GRAY_GLAZED_TERRACOTTA;
            case DARK_GRAY -> GRAY_GLAZED_TERRACOTTA;
            case BLACK -> BLACK_GLAZED_TERRACOTTA;
            case BROWN -> BROWN_GLAZED_TERRACOTTA;
            case RED -> RED_GLAZED_TERRACOTTA;
            case BLUE -> BLUE_GLAZED_TERRACOTTA;
            case ORANGE -> ORANGE_GLAZED_TERRACOTTA;
            case YELLOW -> YELLOW_GLAZED_TERRACOTTA;
            case PURPLE -> PURPLE_GLAZED_TERRACOTTA;
            case LIME -> LIME_GLAZED_TERRACOTTA;
            case DARK_GREEN -> GREEN_GLAZED_TERRACOTTA;
            case CYAN -> CYAN_GLAZED_TERRACOTTA;
            case LIGHT_BLUE -> LIGHT_BLUE_GLAZED_TERRACOTTA;
            case MAGENTA -> MAGENTA_GLAZED_TERRACOTTA;
            case PINK -> PINK_GLAZED_TERRACOTTA;
        });

        // stained-glass
        paintables.add(team -> switch (team) {
            case WHITE -> WHITE_STAINED_GLASS;
            case LIGHT_GRAY -> LIGHT_GRAY_STAINED_GLASS;
            case DARK_GRAY -> GRAY_STAINED_GLASS;
            case BLACK -> BLACK_STAINED_GLASS;
            case BROWN -> BROWN_STAINED_GLASS;
            case RED -> RED_STAINED_GLASS;
            case BLUE -> BLUE_STAINED_GLASS;
            case ORANGE -> ORANGE_STAINED_GLASS;
            case YELLOW -> YELLOW_STAINED_GLASS;
            case PURPLE -> PURPLE_STAINED_GLASS;
            case LIME -> LIME_STAINED_GLASS;
            case DARK_GREEN -> GREEN_STAINED_GLASS;
            case CYAN -> CYAN_STAINED_GLASS;
            case LIGHT_BLUE -> LIGHT_BLUE_STAINED_GLASS;
            case MAGENTA -> MAGENTA_STAINED_GLASS;
            case PINK -> PINK_STAINED_GLASS;
        });

        // stained-glass pane
        paintables.add(team -> switch (team) {
            case WHITE -> WHITE_STAINED_GLASS_PANE;
            case LIGHT_GRAY -> LIGHT_GRAY_STAINED_GLASS_PANE;
            case DARK_GRAY -> GRAY_STAINED_GLASS_PANE;
            case BLACK -> BLACK_STAINED_GLASS_PANE;
            case BROWN -> BROWN_STAINED_GLASS_PANE;
            case RED -> RED_STAINED_GLASS_PANE;
            case BLUE -> BLUE_STAINED_GLASS_PANE;
            case ORANGE -> ORANGE_STAINED_GLASS_PANE;
            case YELLOW -> YELLOW_STAINED_GLASS_PANE;
            case PURPLE -> PURPLE_STAINED_GLASS_PANE;
            case LIME -> LIME_STAINED_GLASS_PANE;
            case DARK_GREEN -> GREEN_STAINED_GLASS_PANE;
            case CYAN -> CYAN_STAINED_GLASS_PANE;
            case LIGHT_BLUE -> LIGHT_BLUE_STAINED_GLASS_PANE;
            case MAGENTA -> MAGENTA_STAINED_GLASS_PANE;
            case PINK -> PINK_STAINED_GLASS_PANE;
        });

        // beds
        paintables.add(team -> switch (team) {
            case WHITE -> WHITE_BED;
            case LIGHT_GRAY -> LIGHT_GRAY_BED;
            case DARK_GRAY -> GRAY_BED;
            case BLACK -> BLACK_BED;
            case BROWN -> BROWN_BED;
            case RED -> RED_BED;
            case BLUE -> BLUE_BED;
            case ORANGE -> ORANGE_BED;
            case YELLOW -> YELLOW_BED;
            case PURPLE -> PURPLE_BED;
            case LIME -> LIME_BED;
            case DARK_GREEN -> GREEN_BED;
            case CYAN -> CYAN_BED;
            case LIGHT_BLUE -> LIGHT_BLUE_BED;
            case MAGENTA -> MAGENTA_BED;
            case PINK -> PINK_BED;
        });

        // shulker boxes
        paintables.add(team -> switch (team) {
            case WHITE -> WHITE_SHULKER_BOX;
            case LIGHT_GRAY -> LIGHT_GRAY_SHULKER_BOX;
            case DARK_GRAY -> GRAY_SHULKER_BOX;
            case BLACK -> BLACK_SHULKER_BOX;
            case BROWN -> BROWN_SHULKER_BOX;
            case RED -> RED_SHULKER_BOX;
            case BLUE -> BLUE_SHULKER_BOX;
            case ORANGE -> ORANGE_SHULKER_BOX;
            case YELLOW -> YELLOW_SHULKER_BOX;
            case PURPLE -> PURPLE_SHULKER_BOX;
            case LIME -> LIME_SHULKER_BOX;
            case DARK_GREEN -> GREEN_SHULKER_BOX;
            case CYAN -> CYAN_SHULKER_BOX;
            case LIGHT_BLUE -> LIGHT_BLUE_SHULKER_BOX;
            case MAGENTA -> MAGENTA_SHULKER_BOX;
            case PINK -> PINK_SHULKER_BOX;
        });

        // candles
        paintables.add(team -> switch (team) {
            case WHITE -> WHITE_CANDLE;
            case LIGHT_GRAY -> LIGHT_GRAY_CANDLE;
            case DARK_GRAY -> GRAY_CANDLE;
            case BLACK -> BLACK_CANDLE;
            case BROWN -> BROWN_CANDLE;
            case RED -> RED_CANDLE;
            case BLUE -> BLUE_CANDLE;
            case ORANGE -> ORANGE_CANDLE;
            case YELLOW -> YELLOW_CANDLE;
            case PURPLE -> PURPLE_CANDLE;
            case LIME -> LIME_CANDLE;
            case DARK_GREEN -> GREEN_CANDLE;
            case CYAN -> CYAN_CANDLE;
            case LIGHT_BLUE -> LIGHT_BLUE_CANDLE;
            case MAGENTA -> MAGENTA_CANDLE;
            case PINK -> PINK_CANDLE;
        });

        // candle cake
        paintables.add(team -> switch (team) {
            case WHITE -> WHITE_CANDLE_CAKE;
            case LIGHT_GRAY -> LIGHT_GRAY_CANDLE_CAKE;
            case DARK_GRAY -> GRAY_CANDLE_CAKE;
            case BLACK -> BLACK_CANDLE_CAKE;
            case BROWN -> BROWN_CANDLE_CAKE;
            case RED -> RED_CANDLE_CAKE;
            case BLUE -> BLUE_CANDLE_CAKE;
            case ORANGE -> ORANGE_CANDLE_CAKE;
            case YELLOW -> YELLOW_CANDLE_CAKE;
            case PURPLE -> PURPLE_CANDLE_CAKE;
            case LIME -> LIME_CANDLE_CAKE;
            case DARK_GREEN -> GREEN_CANDLE_CAKE;
            case CYAN -> CYAN_CANDLE_CAKE;
            case LIGHT_BLUE -> LIGHT_BLUE_CANDLE_CAKE;
            case MAGENTA -> MAGENTA_CANDLE_CAKE;
            case PINK -> PINK_CANDLE_CAKE;
        });

        // banner
        paintables.add(team -> switch (team) {
            case WHITE -> WHITE_BANNER;
            case LIGHT_GRAY -> LIGHT_GRAY_BANNER;
            case DARK_GRAY -> GRAY_BANNER;
            case BLACK -> BLACK_BANNER;
            case BROWN -> BROWN_BANNER;
            case RED -> RED_BANNER;
            case BLUE -> BLUE_BANNER;
            case ORANGE -> ORANGE_BANNER;
            case YELLOW -> YELLOW_BANNER;
            case PURPLE -> PURPLE_BANNER;
            case LIME -> LIME_BANNER;
            case DARK_GREEN -> GREEN_BANNER;
            case CYAN -> CYAN_BANNER;
            case LIGHT_BLUE -> LIGHT_BLUE_BANNER;
            case MAGENTA -> MAGENTA_BANNER;
            case PINK -> PINK_BANNER;
        });

        // wall banner
        paintables.add(team -> switch (team) {
            case WHITE -> WHITE_WALL_BANNER;
            case LIGHT_GRAY -> LIGHT_GRAY_WALL_BANNER;
            case DARK_GRAY -> GRAY_WALL_BANNER;
            case BLACK -> BLACK_WALL_BANNER;
            case BROWN -> BROWN_WALL_BANNER;
            case RED -> RED_WALL_BANNER;
            case BLUE -> BLUE_WALL_BANNER;
            case ORANGE -> ORANGE_WALL_BANNER;
            case YELLOW -> YELLOW_WALL_BANNER;
            case PURPLE -> PURPLE_WALL_BANNER;
            case LIME -> LIME_WALL_BANNER;
            case DARK_GREEN -> GREEN_WALL_BANNER;
            case CYAN -> CYAN_WALL_BANNER;
            case LIGHT_BLUE -> LIGHT_BLUE_WALL_BANNER;
            case MAGENTA -> MAGENTA_WALL_BANNER;
            case PINK -> PINK_WALL_BANNER;
        });

        for (Paintable paintable : paintables) {
            for (DyeTeamKey team : DyeTeamKey.values()) {
                Block block = paintable.blockFor(team);
                paintableBlocks.put(block, paintable);
            }

            for (PaintballTeam team : teams) {
                Block block = paintable.blockFor(team.key());
                blockTeamMap.put(block, team.key());
            }
        }
    }

    public @Nullable Paintable paintable(Block block) {
        return paintableBlocks.get(block);
    }

    public BlockState getPaintBulletState(DyeTeamKey team) {
        return concrete.blockFor(team).getDefaultState();
    }

    public boolean replace(BlockPos pos, DyeTeamKey target) {
        BlockState current = world.getBlockState(pos);
        Paintable paintable = paintable(current.getBlock());

        if (paintable == null) {
            return false;
        }

        return replace(pos, current, paintable, target);
    }

    public boolean replace(BlockPos pos, BlockState current, Paintable paintable, DyeTeamKey targetTeam) {
        // prevent painting bases of other teams
        if (teams.teamBaseAt(pos)
                .map(team -> team.key() != targetTeam)
                .orElse(false)) return false;

        BlockState baseState = paintable.blockFor(targetTeam).getDefaultState();
        BlockState targetState = copyProperties(current, baseState);

        if (current == targetState) return false;

        DyeTeamKey prevTeam = getTeam(current.getBlock());

        if (prevTeam != null) {
            addCount(prevTeam, -1);
        }

        addCount(targetTeam, 1);

        return world.setBlockState(pos, targetState, Block.FORCE_STATE | Block.NOTIFY_LISTENERS | Block.SKIP_DROPS);
    }

    private @Nullable DyeTeamKey getTeam(Block block) {
        return blockTeamMap.get(block);
    }

    private BlockState copyProperties(BlockState reference, BlockState state) {
        for (var property : reference.getProperties()) {
            var value = reference.get(property);

            state = withProperty(state, property, value);
        }

        return state;
    }

    private void addCount(DyeTeamKey key, int amount) {
        Team team = teamManager.getTeam(key).orElse(null);

        if (team == null) return;

        synchronized (this) {
            int score = data.getScore(team);
            data.setScore(team, max(0, score + amount));
        }
    }

    public void countBlocks() {
        Object2IntMap<DyeTeamKey> count = new Object2IntOpenHashMap<>();

        for (DyeTeamKey value : DyeTeamKey.values()) {
            count.put(value, 0);
        }

        for (BlockPos pos : bounds) {
            BlockState state = world.getBlockState(pos);
            DyeTeamKey key = getTeam(state.getBlock());

            if (key == null) continue;

            count.computeInt(key, (_k, prev) -> prev + 1);
        }

        for (var entry : count.object2IntEntrySet()) {
            teamManager.getTeam(entry.getKey()).ifPresent(team -> {
                synchronized (this) {
                    data.setScore(team, entry.getIntValue());
                }
            });
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Comparable<T>, V extends T> BlockState withProperty(BlockState state, Property<T> property, Object value) {
        return state.withIfExists(property, (V) value);
    }
}