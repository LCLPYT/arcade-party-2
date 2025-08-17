package work.lclpnet.ap2.base;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Formatting;
import org.json.JSONObject;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.api.config.Ap2Config;
import work.lclpnet.ap2.api.game.MiniGame;
import work.lclpnet.ap2.impl.base.FabricMiniGameManager;
import work.lclpnet.ap2.impl.bootstrap.ApDataPacks;
import work.lclpnet.ap2.impl.util.IconMaker;
import work.lclpnet.config.json.JsonConfigFactory;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.lobby.game.api.Game;
import work.lclpnet.lobby.game.api.GameConfig;
import work.lclpnet.lobby.game.api.GameFactory;
import work.lclpnet.lobby.game.api.data.GameDataPacks;
import work.lclpnet.lobby.game.api.option.GameOptionConfig;
import work.lclpnet.lobby.game.api.option.OptionVoting;
import work.lclpnet.lobby.game.api.start.GameScope;
import work.lclpnet.lobby.game.api.start.GameStatusManager;
import work.lclpnet.lobby.game.impl.MinecraftGameConfig;

import java.nio.file.Path;
import java.util.Set;

import static net.minecraft.util.Formatting.AQUA;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class ArcadePartyDefaultGame implements Game {

    public static final String VOTING_MINI_GAMES = "mini_games";
    public static final int MIN_REQUIRED_PLAYERS = 2;

    private final Path cacheDirectory = Path.of(".cache", ApConstants.ID);

    @Override
    public GameConfig getConfig() {
        return new MinecraftGameConfig(ApConstants.ID, new ItemStack(Items.GOLD_BLOCK));
    }

    @Override
    public boolean canBePlayed(GameScope gameScope) {
        int playerCount = gameScope.playerCount();

        return (ApConstants.DEVELOPMENT && playerCount >= 1) || playerCount >= MIN_REQUIRED_PLAYERS;
    }

    @Override
    public GameFactory createFactory() {
        return new ArcadePartyFactory(CONFIG_FACTORY, ApConstants.logger);
    }

    @Override
    public GameDataPacks getBootstrapDataPacks() {
        return new ApDataPacks(cacheDirectory, CONFIG_FACTORY, ApConstants.logger);
    }

    @Override
    public void configureStatusManager(GameStatusManager manager) {
        Translations translations = manager.getContext().getTranslations();

        var msg = translations.translateText("lobby.game.not_enough_players", styled(MIN_REQUIRED_PLAYERS, Formatting.YELLOW))
                .formatted(Formatting.RED);

        manager.setCannotStartMessage(msg::translateFor);
        manager.setCannotStartBossBarValue(translations.translateText("lobby.game.waiting_for_players"));
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
                    stack.set(DataComponentTypes.ITEM_NAME, gameVotingName.translateFor(player).formatted(AQUA));
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
