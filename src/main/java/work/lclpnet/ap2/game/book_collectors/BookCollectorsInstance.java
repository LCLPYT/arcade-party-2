package work.lclpnet.ap2.game.book_collectors;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.UnbreakableComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.GameRules;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.team.TeamKey;
import work.lclpnet.ap2.api.game.team.TeamManager;
import work.lclpnet.ap2.game.cozy_campfire.setup.CCBaseManager;
import work.lclpnet.ap2.game.cozy_campfire.setup.CCReader;
import work.lclpnet.ap2.impl.game.TeamEliminationGameInstance;
import work.lclpnet.ap2.impl.game.data.ScoreDataContainer;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.game.team.ApTeamKeys;
import work.lclpnet.ap2.impl.util.TextUtil;
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.util.Formatting.GOLD;

public class BookCollectorsInstance extends TeamEliminationGameInstance {

    private static final float DAY_TIME_CHANCE = 0.55f, RAIN_CHANCE = 0.6f, THUNDER_CHANCE = 0.15f;
    static final int GAME_DURATION = 180*20;
    public static final TeamKey TEAM_RED = ApTeamKeys.RED, TEAM_BLUE = ApTeamKeys.BLUE;
    private CCBaseManager baseManager;
    private TeamManager teamManager;
    private final Random random = new Random();

    public BookCollectorsInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        useOldCombat();
    }

    @Override
    protected ScoreDataContainer<ServerPlayerEntity, PlayerRef> getData() {
        return null;
    }

    public CompletableFuture<Void> createWorldBootstrap(ServerWorld world, GameMap map) {
        teamManager = getTeamManager();
        teamManager.partitionIntoTeams(gameHandle.getParticipants(), Set.of(TEAM_RED, TEAM_BLUE));

        CCReader setup = new CCReader(map, world, gameHandle.getLogger());

        return setup.readBases(teamManager.getTeams())
                .thenAccept(bases -> baseManager = new CCBaseManager(bases, teamManager))
                .thenCompose(nil -> world.getServer().submit(() -> {
                    randomizeWorldConditions(world);
                }));
    }

    @Override
    protected void prepare() {
        ServerWorld world = getWorld();

        teamManager.getMinecraftTeams().forEach(team -> {
            team.setFriendlyFireAllowed(false);
            team.setShowFriendlyInvisibles(true);
            team.setCollisionRule(AbstractTeam.CollisionRule.PUSH_OTHER_TEAMS);
        });

        commons().gameRuleBuilder()
                .set(GameRules.DO_ENTITY_DROPS, false)
                .set(GameRules.NATURAL_REGENERATION, true)
                .set(GameRules.ANNOUNCE_ADVANCEMENTS, false);

        HookRegistrar hooks = gameHandle.getHookRegistrar();

        teleportTeamsToSpawns();

    }

    @Override
    protected void ready() {
        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            giveSwordToPlayer(player);
        }
    }

    private void giveSwordToPlayer(ServerPlayerEntity player) {
        ItemStack stack = new ItemStack(Items.STONE_SWORD);

        stack.set(DataComponentTypes.CUSTOM_NAME, TextUtil.getVanillaName(stack)
                .styled(style -> style.withItalic(false).withFormatting(GOLD)));

        stack.set(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(false));

        PlayerInventory inventory = player.getInventory();
        inventory.setStack(0, stack);
        PlayerInventoryAccess.setSelectedSlot(player, 0);
    }

    private void randomizeWorldConditions(ServerWorld world) {
        if (random.nextFloat() <= DAY_TIME_CHANCE) {
            world.setTimeOfDay(6000);
        } else {
            world.setTimeOfDay(18000);
        }

        if (random.nextFloat() <= RAIN_CHANCE) {
            boolean thunder = random.nextFloat() <= (THUNDER_CHANCE / RAIN_CHANCE);  // conditional probability
            world.setWeather(0, 1000, true, thunder);
        } else {
            world.setWeather(1000, 0, false, false);
        }
    }

}
