package work.lclpnet.ap2.game.book_collectors;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.UnbreakableComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.team.Team;
import work.lclpnet.ap2.api.game.team.TeamKey;
import work.lclpnet.ap2.api.game.team.TeamManager;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.game.book_collectors.setup.BCBaseManager;
import work.lclpnet.ap2.game.book_collectors.setup.BCReader;
import work.lclpnet.ap2.impl.game.TeamEliminationGameInstance;
import work.lclpnet.ap2.impl.game.team.ApTeamKeys;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.TextUtil;
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.ArrayList;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.util.Formatting.GOLD;

public class BookCollectorsInstance extends TeamEliminationGameInstance implements MapBootstrap {

    public static final TeamKey TEAM_RED = ApTeamKeys.RED, TEAM_BLUE = ApTeamKeys.BLUE;
    static final int GAME_DURATION = 180 * 20;
    private static final float RAIN_CHANCE = 0.6f, THUNDER_CHANCE = 0.15f;
    private final Random random = new Random();
    private BCBaseManager baseManager;
    private TeamManager teamManager;

    public BookCollectorsInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        useOldCombat();
    }

    public CompletableFuture<Void> createWorldBootstrap(ServerWorld world, GameMap map) {
        teamManager = getTeamManager();
        teamManager.partitionIntoTeams(gameHandle.getParticipants(), Set.of(TEAM_RED, TEAM_BLUE));

        BCReader setup = new BCReader(map, gameHandle.getLogger());

        return setup.readBases(teamManager.getTeams()).thenAccept(bases -> baseManager = new BCBaseManager(bases, teamManager)).thenCompose(nil -> world.getServer().submit(() -> {
            randomizeWorldConditions(world);
        }));
    }

    @Override
    protected void prepare() {
        ServerWorld world = getWorld();
        GameMap map = getMap();

        BlockBox mapBox = MapUtil.readBox(map.requireProperty(("corners")));
        ArrayList<BlockPos> teamRedBookshelfs = new ArrayList<>();
        ArrayList<BlockPos> teamBlueBookshelfs = new ArrayList<>();
        ArrayList<BlockPos> mapBookshelfs = new ArrayList<>();

        for (BlockPos pos : mapBox) {
            BlockState state = world.getBlockState(pos);
            if (!state.isOf(Blocks.LECTERN) && !state.isOf(Blocks.CHISELED_BOOKSHELF)) {
                continue;
            }

            Team team = baseManager.blockPosInAnyBase(pos).orElse(null);
            if (team == null) {
                mapBookshelfs.add(pos.toImmutable());
                continue;
            }

            if (team.getKey() == TEAM_RED) teamRedBookshelfs.add(pos.toImmutable());
            if (team.getKey() == TEAM_BLUE) teamBlueBookshelfs.add(pos.toImmutable());
        }

        System.out.println("teamRedBookshelfs: " + teamRedBookshelfs);
        System.out.println("teamBlueBookshelfs: " + teamBlueBookshelfs);
        System.out.println("mapBookshelfs: " + mapBookshelfs);

        teamManager.getMinecraftTeams().forEach(team -> {
            team.setFriendlyFireAllowed(false);
            team.setShowFriendlyInvisibles(true);
            team.setCollisionRule(AbstractTeam.CollisionRule.PUSH_OTHER_TEAMS);
        });

        teleportTeamsToSpawns();

        commons().gameRuleBuilder().set(GameRules.DO_ENTITY_DROPS, false).set(GameRules.NATURAL_REGENERATION, true).set(GameRules.ANNOUNCE_ADVANCEMENTS, false);

        HookRegistrar hooks = gameHandle.getHookRegistrar();
    }

    @Override
    protected void ready() {
        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            giveSwordToPlayer(player);
        }
    }

    private void giveSwordToPlayer(ServerPlayerEntity player) {
        ItemStack stack = new ItemStack(Items.STONE_SWORD);

        stack.set(DataComponentTypes.CUSTOM_NAME, TextUtil.getVanillaName(stack).styled(style -> style.withItalic(false).withFormatting(GOLD)));

        stack.set(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(false));

        PlayerInventory inventory = player.getInventory();
        inventory.setStack(0, stack);
        PlayerInventoryAccess.setSelectedSlot(player, 0);
    }

    private void randomizeWorldConditions(ServerWorld world) {
        if (random.nextFloat() <= RAIN_CHANCE) {
            boolean thunder = random.nextFloat() <= (THUNDER_CHANCE / RAIN_CHANCE);  // conditional probability
            world.setWeather(0, 1000, true, thunder);
        } else {
            world.setWeather(1000, 0, false, false);
        }
    }

}
