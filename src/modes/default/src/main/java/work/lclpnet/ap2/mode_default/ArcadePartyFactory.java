package work.lclpnet.ap2.mode_default;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import work.lclpnet.activity.Activity;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.api.config.Ap2Config;
import work.lclpnet.ap2.api.game.MiniGame;
import work.lclpnet.ap2.impl.base.FabricMiniGameManager;
import work.lclpnet.ap2.impl.i18n.VanillaTranslations;
import work.lclpnet.ap2.impl.util.IconMaker;
import work.lclpnet.ap2.mode_default.activity.ArcadePartyStartingActivity;
import work.lclpnet.config.json.JsonConfigFactory;
import work.lclpnet.game.api.GameEnvironment;
import work.lclpnet.game.api.GameFactory;
import work.lclpnet.game.api.GameInstance;
import work.lclpnet.game.api.option.OptionVoting;
import work.lclpnet.game.api.option.VoteResult;
import work.lclpnet.game.api.start.GameStartArgs;
import work.lclpnet.game.impl.Voting;
import work.lclpnet.kibu.assets.AssetManager;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.util.ModTranslations;
import work.lclpnet.translations.loader.MultiTranslationLoader;
import work.lclpnet.translations.loader.TranslationLoader;
import work.lclpnet.translations.loader.UrlArchiveTranslationLoader;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static net.minecraft.ChatFormatting.AQUA;

public class ArcadePartyFactory implements GameFactory {

    private final JsonConfigFactory<Ap2Config> configFactory;
    private final Logger logger;

    private @Nullable VanillaTranslations vanillaTranslations = null;
    private @Nullable Voting<MiniGame> miniGameVoting = null;

    public ArcadePartyFactory(JsonConfigFactory<Ap2Config> configFactory, Logger logger) {
        this.configFactory = configFactory;
        this.logger = logger;
    }

    @Override
    public TranslationLoader createTranslationLoader() {
        MultiTranslationLoader loader = new MultiTranslationLoader();

        // load translations from assets
        var assetLoader = ModTranslations.assetTranslationLoader(ApConstants.LIB_ID, ApConstants.ID, logger);
        loader.addLoader(assetLoader);

        // also load vanilla death messages (unavailable until initialized)
        var assetManager = AssetManager.getShared(SharedConstants.getCurrentVersion().name());
        vanillaTranslations = new VanillaTranslations(assetManager, logger, translationKey -> translationKey.startsWith("death."));
        loader.addLoader(vanillaTranslations.getTranslationLoader());

        for (var source : new FabricMiniGameManager(logger).getGameSources()) {
            var urls = source.rootPaths().stream()
                    .map(path -> {
                        try {
                            return path.toUri().toURL();
                        } catch (MalformedURLException e) {
                            logger.error("Failed to convert path {} to url", path, e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .toArray(URL[]::new);

            var miniGameTranslations = UrlArchiveTranslationLoader.ofJson(urls, List.of("lang/"), logger);

            loader.addLoader(miniGameTranslations);
        }

        return loader;
    }

    @Override
    public @Nullable Activity createGameSelectedActivity(@NotNull GameStartArgs args) {
        Translations translations = args.options().getContext().getTranslations();

        var gameVotingName = translations.translateText("ap2.game_voting");

        var miniGameManager = new FabricMiniGameManager(ApConstants.logger);
        Set<MiniGame> miniGames = miniGameManager.getGames();

        miniGameVoting = new Voting<>(
                "mini_games",
                new OptionVoting<>(
                        player -> {
                            var stack = new ItemStack(Items.PAPER);
                            stack.set(DataComponents.ITEM_NAME, gameVotingName.translateFor(player).formatted(AQUA));
                            return stack;
                        },
                        gameVotingName::translateFor,
                        MiniGame.class,
                        miniGames,
                        (player, miniGame) -> IconMaker.createIcon(miniGame, player, translations)
                ),
                translations
        );

        return new ArcadePartyStartingActivity(args, logger, miniGameVoting);
    }

    @Override
    public @NonNull GameInstance createInstance(@NonNull GameEnvironment gameEnvironment) {
        VoteResult<MiniGame> miniGameVoteResult = miniGameVoting != null
                ? miniGameVoting.getCurrentResult()
                : VoteResult.empty();

        return new ArcadePartyInstance(gameEnvironment, vanillaTranslations, configFactory, miniGameVoteResult, logger);
    }
}
