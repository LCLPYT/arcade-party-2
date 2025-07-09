package work.lclpnet.ap2.game.paintball;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.json.JSONArray;
import org.json.JSONObject;
import work.lclpnet.ap2.api.ds.Partial;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.game.team.Team;
import work.lclpnet.ap2.api.game.team.TeamKey;
import work.lclpnet.ap2.api.map.MapBootstrapFunction;
import work.lclpnet.ap2.impl.game.TeamGameInstance;
import work.lclpnet.ap2.impl.game.data.IntScoreDataContainer;
import work.lclpnet.ap2.impl.game.data.Ordering;
import work.lclpnet.ap2.impl.game.data.type.TeamRef;
import work.lclpnet.ap2.impl.game.team.ApTeamKeys;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.StreamUtil;
import work.lclpnet.ap2.impl.util.world.ResetBlockWorldModifier;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.*;
import java.util.stream.Collectors;

import static work.lclpnet.ap2.impl.game.team.ApTeamKeys.complementary;

public class PaintballInstance extends TeamGameInstance implements MapBootstrapFunction {

    private final IntScoreDataContainer<Team, TeamRef> data = new IntScoreDataContainer<>(this::createReference, Ordering.DESCENDING, "game.ap2.paintball.blocks_painted");
    private final Random random = new Random();

    private List<TeamInstance> teams = null;
    private ResetBlockWorldModifier baseWalls = null;

    public PaintballInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    @Override
    protected DataContainer<Team, TeamRef> getData() {
        return data;
    }

    @Override
    public void bootstrapWorld(ServerWorld world, GameMap map) {
        teams = setupTeams(map);

        // TODO replace base template colors with actual colors
    }

    @Override
    protected void prepare() {
        getTeamManager()
                .partitionIntoTeams(gameHandle.getParticipants(), teams.stream()
                .map(TeamInstance::key)
                .collect(Collectors.toSet()));

        teleportTeamsToSpawns();

        closeBases();
    }

    private List<TeamInstance> setupTeams(GameMap map) {
        JSONArray array = map.getProperties().getJSONArray("teams");
        List<Partial<TeamInstance, TeamKey>> partial = new ArrayList<>(array.length());

        for (Object entry : array) {
            if (!(entry instanceof JSONObject json)) {
                gameHandle.getLogger().error("Invalid team entry: {}", entry);
                continue;
            }

            partial.add(TeamInstance.fromJson(json));
        }

        // choose random team colors, by first choosing a random base color and then choosing according complementary colors
        var keys = ApTeamKeys.teamKeys();
        TeamKey base = keys.get(random.nextInt(keys.size()));
        List<TeamKey> complementary = complementary(base, partial.size());

        return StreamUtil.zip(partial.stream(), complementary.stream(), Partial::with).toList();
    }

    @Override
    protected void ready() {
        openBases();
    }

    private void closeBases() {
        int flags = Block.NOTIFY_LISTENERS | Block.SKIP_DROPS | Block.FORCE_STATE;

        baseWalls = new ResetBlockWorldModifier(getWorld(), flags);

        ServerWorld world = getWorld();

        for (TeamInstance team : teams) {
            BlockBox bounds = team.baseBounds();

            for (BlockPos pos : bounds) {
                if (!bounds.isBorder(pos)) continue;

                BlockState state = world.getBlockState(pos);

                if (!state.getCollisionShape(world, pos).isEmpty()) continue;

                baseWalls.setBlockState(pos, Blocks.BARRIER.getDefaultState(), flags);
            }
        }
    }

    private void openBases() {
        if (baseWalls != null) {
            baseWalls.undo();
        }
    }

    @Override
    protected void teleportTeamsToSpawns() {
        ServerWorld world = getWorld();

        for (TeamInstance inst : teams) {
            Team team = getTeamManager().getTeam(inst.key()).orElse(null);

            if (team == null) continue;

            Vec3d pos = inst.spawn();

            for (ServerPlayerEntity player : team.getPlayers()) {
                player.teleport(world, pos.getX(), pos.getY(), pos.getZ(), Set.of(), inst.yaw(), 0, true);
            }
        }
    }

    private record TeamInstance(Vec3d spawn, float yaw, BlockBox baseBounds, TeamKey templateColor, TeamKey key) {

        public static Partial<TeamInstance, TeamKey> fromJson(JSONObject json) {
            Vec3d spawn = MapUtil.readCenteredVec3d(json.getJSONArray("spawn"));
            float yaw = MapUtil.readAngle(json.optFloat("yaw", 0));
            BlockBox baseBounds = MapUtil.readBox(json.getJSONArray("base-bounds"));

            String teamId = json.getString("template-color");

            TeamKey templateColor = ApTeamKeys.byId(teamId)
                    .orElseThrow(() -> new NoSuchElementException("Unknown team template-color \"%s\"".formatted(teamId)));

            return key -> new TeamInstance(spawn, yaw, baseBounds, templateColor, key);
        }
    }
}
