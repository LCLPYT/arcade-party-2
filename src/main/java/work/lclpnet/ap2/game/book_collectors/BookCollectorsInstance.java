package work.lclpnet.ap2.game.book_collectors;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LecternBlock;
import net.minecraft.block.entity.ChiseledBookshelfBlockEntity;
import net.minecraft.block.entity.LecternBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.UnbreakableComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.game.team.Team;
import work.lclpnet.ap2.api.game.team.TeamKey;
import work.lclpnet.ap2.api.game.team.TeamManager;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.core.hook.ChiseledBookshelfModifyCallback;
import work.lclpnet.ap2.game.book_collectors.setup.BCBaseManager;
import work.lclpnet.ap2.game.book_collectors.setup.BCReader;
import work.lclpnet.ap2.impl.game.DefaultTeamGameInstance;
import work.lclpnet.ap2.impl.game.data.ScoreDataContainer;
import work.lclpnet.ap2.impl.game.data.type.TeamRef;
import work.lclpnet.ap2.impl.game.team.ApTeamKeys;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.TextUtil;
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.ArrayList;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.util.Formatting.GOLD;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class BookCollectorsInstance extends DefaultTeamGameInstance implements MapBootstrap {

    public static final TeamKey TEAM_RED = ApTeamKeys.RED, TEAM_BLUE = ApTeamKeys.BLUE;
    static final int GAME_DURATION = 180;
    private static final float RAIN_CHANCE = 0.6f, THUNDER_CHANCE = 0.15f;
    private final Random random = new Random();
    private final ScoreDataContainer<Team, TeamRef> data = new ScoreDataContainer<>(this::createReference);
    private final Translations translations = gameHandle.getTranslations();
    private final Participants participants = gameHandle.getParticipants();
    private BCBaseManager baseManager;
    private TeamManager teamManager;

    public BookCollectorsInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
        useOldCombat();
    }

    @Override
    protected DataContainer<Team, TeamRef> getData() {
        return data;
    }

    public CompletableFuture<Void> createWorldBootstrap(ServerWorld world, GameMap map) {
        teamManager = getTeamManager();
        teamManager.partitionIntoTeams(participants, Set.of(TEAM_RED, TEAM_BLUE));

        BCReader setup = new BCReader(map, gameHandle.getLogger());

        return setup.readBases(teamManager.getTeams()).thenAccept(bases -> baseManager = new BCBaseManager(bases, teamManager)).thenCompose(nil -> world.getServer().submit(() -> randomizeWorldConditions(world)));
    }

    @Override
    protected void prepare() {

        ServerWorld serverWorld = getWorld();
        GameMap map = getMap();

        teamManager.getMinecraftTeams().forEach(team -> {
            team.setFriendlyFireAllowed(false);
            team.setShowFriendlyInvisibles(true);
            team.setCollisionRule(AbstractTeam.CollisionRule.PUSH_OTHER_TEAMS);
        });

        teleportTeamsToSpawns();

        var bookOptions = new BookOptions();
        ArrayList<ItemStack> books = bookOptions.getBookOptions();
        BlockBox mapBox = MapUtil.readBox(map.requireProperty(("corners")));
        ArrayList<BlockPos> mapBookshelves = new ArrayList<>();

        for (BlockPos pos : mapBox) {
            BlockState state = serverWorld.getBlockState(pos);

            if (!state.isOf(Blocks.LECTERN) && !state.isOf(Blocks.CHISELED_BOOKSHELF)) continue;

            Team team = baseManager.blockPosInAnyBase(pos).orElse(null);
            if (team != null) continue;

            mapBookshelves.add(pos.toImmutable());
        }

        fillBookshelves(serverWorld, mapBookshelves, books);

        commons().gameRuleBuilder().set(GameRules.DO_ENTITY_DROPS, false).set(GameRules.NATURAL_REGENERATION, true).set(GameRules.ANNOUNCE_ADVANCEMENTS, false);

        HookRegistrar hooks = gameHandle.getHookRegistrar();

        hooks.registerHook(ChiseledBookshelfModifyCallback.ADD, (player, pos) -> {
            Team team = baseManager.blockPosInAnyBase(pos).orElse(null);
            if (team == null) return false;

            double x = pos.getX() + 0.5;
            double y = pos.getY() + 0.5;
            double z = pos.getZ() + 0.5;

            if (team.getPlayers().contains(player))
                serverWorld.spawnParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 12, 0.5, 0.1, 0.5, 0.2);
            else serverWorld.spawnParticles(ParticleTypes.ANGRY_VILLAGER, x, y, z, 4, 0.5, 0.1, 0.5, 0.2);

            addScore(team);
            return false;
        });

        hooks.registerHook(ChiseledBookshelfModifyCallback.REMOVE, (player, pos) -> {
            if (checkBookLimit(player)) return true;
            baseManager.blockPosInAnyBase(pos).ifPresent(this::removeScore);
            return false;
        });

        gameHandle.protect(config -> config.allow(ProtectionTypes.USE_BLOCK, (entity, pos) -> {
            BlockState state = entity.getWorld().getBlockState(pos);

            if (winManager.isGameOver()) return false;

            if (state.isOf(Blocks.CHISELED_BOOKSHELF)) return true;

            if (serverWorld.getBlockEntity(pos) instanceof LecternBlockEntity lecternBlockEntity && entity instanceof ServerPlayerEntity player) {
                var lecternBook = lecternBlockEntity.getBook().copy();
                if ((lecternBook != ItemStack.EMPTY) && checkBookLimit(player)) return false;

                player.getInventory().insertStack(lecternBook);
                lecternBlockEntity.clear();
                serverWorld.setBlockState(pos, state.with(LecternBlock.HAS_BOOK, false));
            }
            return false;
        }));

        gameHandle.protect(config -> config.allow(ProtectionTypes.TAKE_LECTERN_BOOK, (entity, blockPos) -> !winManager.isGameOver()));
    }

    @Override
    protected void ready() {
        for (ServerPlayerEntity player : participants) {
            giveSwordToPlayer(player);
        }

        var subject = translations.translateText("game.ap2.book_collectors.task");
        commons().createTimer(subject, GAME_DURATION).whenDone(this::onTimerDone);

        gameHandle.protect(config -> config.allow(ProtectionTypes.ALLOW_DAMAGE, (entity, blockPos) -> !winManager.isGameOver()));
    }

    private void fillBookshelves(ServerWorld world, ArrayList<BlockPos> bookshelves, ArrayList<ItemStack> books) {

        for (ItemStack book : books) {

            int r = random.nextInt(bookshelves.size());
            BlockPos bsPos = bookshelves.get(r);
            BlockState bsState = world.getBlockState(bsPos);

            if (world.getBlockEntity(bsPos) instanceof ChiseledBookshelfBlockEntity chiseled) {

                ArrayList<Integer> freeSlots = new ArrayList<>();
                for (int i = 0; i < 6; i++) {
                    if (chiseled.getStack(i).isEmpty()) {
                        freeSlots.add(i);
                    }
                }
                int slot = random.nextInt(freeSlots.size());
                chiseled.setStack(slot, book);
            }

            if (bsState.isOf(Blocks.LECTERN)) {
                LecternBlock.putBookIfAbsent(null, world, bsPos, bsState, book);

                bookshelves.remove(r);
            }
        }
    }

    private boolean checkBookLimit(ServerPlayerEntity player) {

        Inventory inventory = player.getInventory();
        int bookCounter = 0;
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isOf(Items.WRITTEN_BOOK) || stack.isOf(Items.ENCHANTED_BOOK) || stack.isOf(Items.KNOWLEDGE_BOOK)) {
                bookCounter += stack.getCount();
            }
        }

        if (bookCounter >= 3) {
            var msg = translations.translateText(player, "game.ap2.book_collectors.max_book_warning").formatted(Formatting.RED);
            player.sendMessage(msg, true);
            return true;
        }
        return false;
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

    private void addScore(Team team) {

        data.addScore(team, 1);

        for (ServerPlayerEntity player : team.getPlayers()) {
            var msg = gameHandle.getTranslations().translateText(player, "ap2.gain_point", styled(1, Formatting.YELLOW), styled(data.getScore(team), Formatting.AQUA)).formatted(Formatting.GREEN);

            player.sendMessage(msg, true);
        }
    }

    private void removeScore(Team team) {

        data.setScore(team, data.getScore(team) - 1);

        for (ServerPlayerEntity player : team.getPlayers()) {
            var msg = gameHandle.getTranslations().translateText(player, "ap2.lose_point", styled(1, Formatting.YELLOW), styled(data.getScore(team), Formatting.AQUA)).formatted(Formatting.RED);

            player.sendMessage(msg, true);
        }
    }

    private void onTimerDone() {
        winManager.win(data.getBestSubject(getResolver()).orElse(null));
    }

}
