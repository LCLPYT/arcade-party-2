package work.lclpnet.ap2.mode_default;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import org.json.JSONObject;
import org.jspecify.annotations.NonNull;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.api.config.Ap2Config;
import work.lclpnet.ap2.api.game.MiniGame;
import work.lclpnet.ap2.impl.base.FabricMiniGameManager;
import work.lclpnet.ap2.impl.bootstrap.ApDataPacks;
import work.lclpnet.ap2.impl.util.IconMaker;
import work.lclpnet.config.json.JsonConfigFactory;
import work.lclpnet.game.api.Game;
import work.lclpnet.game.api.GameConfig;
import work.lclpnet.game.api.GameFactory;
import work.lclpnet.game.api.data.GameDataPacks;
import work.lclpnet.game.api.option.GameOptionConfig;
import work.lclpnet.game.api.option.OptionVoting;
import work.lclpnet.game.api.start.GameScope;
import work.lclpnet.game.api.start.GameStatusManager;
import work.lclpnet.game.impl.MinecraftGameConfig;
import work.lclpnet.game.util.GameStartUtil;
import work.lclpnet.kibu.translate.Translations;

import java.nio.file.Path;
import java.util.Set;

import static net.minecraft.ChatFormatting.AQUA;

public class ArcadePartyDefaultGame implements Game {

    public static final String VOTING_MINI_GAMES = "mini_games";
    public static final int MIN_REQUIRED_PLAYERS = 2;

    private final Path cacheDirectory = Path.of(".cache", ApConstants.ID);

    @Override
    public @NonNull GameConfig getConfig() {
        return new MinecraftGameConfig(ApConstants.ID, new ItemStackTemplate(Items.GOLD_BLOCK));
    }

    @Override
    public boolean canBePlayed(GameScope gameScope) {
        int playerCount = gameScope.playerCount();

        return (ApConstants.DEVELOPMENT && playerCount >= 1) || playerCount >= MIN_REQUIRED_PLAYERS;
    }

    @Override
    public @NonNull GameFactory createFactory() {
        return new ArcadePartyFactory(CONFIG_FACTORY, ApConstants.logger);
    }

    @Override
    public @NonNull GameDataPacks getBootstrapDataPacks() {
        return new ApDataPacks(cacheDirectory, CONFIG_FACTORY, ApConstants.logger);
    }

    @Override
    public void configureStatusManager(@NonNull GameStatusManager manager) {
        GameStartUtil.configureNotEnoughPlayersMessage(manager, MIN_REQUIRED_PLAYERS);
        GameStartUtil.configureWaitingForPlayersBossBar(manager);
    }

    @Override
    public void configureOptions(GameOptionConfig config) {
        Translations translations = config.getContext().getTranslations();
        var gameVotingName = translations.translateText("ap2.game_voting");

        var miniGameManager = new FabricMiniGameManager(ApConstants.logger);
        Set<MiniGame> miniGames = miniGameManager.getGames();

        config.registerVoting(VOTING_MINI_GAMES, new OptionVoting<>(
                player -> {
                    var stack = new ItemStack(Items.PAPER);
                    stack.set(DataComponents.ITEM_NAME, gameVotingName.translateFor(player).formatted(AQUA));
                    return stack;
                },
                gameVotingName::translateFor,
                MiniGame.class,
                miniGames,
                (player, miniGame) -> IconMaker.createIcon(miniGame, player, translations)
        ));
    }

    public static final JsonConfigFactory<Ap2Config> CONFIG_FACTORY = new JsonConfigFactory<>() {
        @Override
        public Ap2Config createDefaultConfig() {
            return new Ap2Config();
        }

        @Override
        public Ap2Config createConfig(JSONObject json) {
            return new Ap2Config(json);
        }
    };
}
