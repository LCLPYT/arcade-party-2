package work.lclpnet.ap2.mode_default

import net.minecraft.ChatFormatting
import net.minecraft.SharedConstants
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.slf4j.Logger
import work.lclpnet.activity.Activity
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.config.Ap2Config
import work.lclpnet.ap2.game.MiniGame
import work.lclpnet.ap2.impl.base.FabricMiniGameManager
import work.lclpnet.ap2.impl.i18n.PrefixTranslationLoader
import work.lclpnet.ap2.impl.i18n.VanillaTranslations
import work.lclpnet.ap2.impl.util.IconMaker
import work.lclpnet.ap2.mode_default.activity.ArcadePartyStartingActivity
import work.lclpnet.config.json.JsonConfigFactory
import work.lclpnet.game.api.GameEnvironment
import work.lclpnet.game.api.GameFactory
import work.lclpnet.game.api.GameInstance
import work.lclpnet.game.api.option.OptionVoting
import work.lclpnet.game.api.option.VoteResult
import work.lclpnet.game.api.start.GameStartArgs
import work.lclpnet.game.impl.Voting
import work.lclpnet.kibu.assets.AssetManager
import work.lclpnet.kibu.translate.Translations
import work.lclpnet.kibu.translate.text.TranslatedText
import work.lclpnet.kibu.translate.util.ModTranslations
import work.lclpnet.translations.loader.MultiTranslationLoader
import work.lclpnet.translations.loader.TranslationLoader
import work.lclpnet.translations.loader.UrlArchiveTranslationLoader
import java.net.MalformedURLException
import java.nio.file.Path

class ArcadePartyFactory(
    private val configFactory: JsonConfigFactory<Ap2Config>,
    private val logger: Logger
) : GameFactory {

    private val vanillaTranslations by lazy {
        val assetManager = AssetManager.getShared(SharedConstants.getCurrentVersion().name())

        VanillaTranslations(assetManager, logger) { translationKey ->
            translationKey.startsWith("death.")
        }
    }

    private var miniGameVoting: Voting<MiniGame>? = null

    override fun createTranslationLoader(): TranslationLoader {
        val loader = MultiTranslationLoader()

        // load translations from assets
        val assetLoader = ModTranslations.assetTranslationLoader(ApConstants.LIB_ID, ApConstants.ID, logger)
        loader.addLoader(assetLoader)

        // also load vanilla death messages (unavailable until initialized)
        loader.addLoader(vanillaTranslations.translationLoader)

        for (source in FabricMiniGameManager(logger).gameSources) {
            val urls = source.rootPaths
                .mapNotNull { path: Path ->
                    try {
                        path.toUri().toURL()
                    } catch (e: MalformedURLException) {
                        logger.error("Failed to convert path {} to url", path, e)
                        null
                    }
                }
                .toTypedArray()

            val miniGameTranslations = UrlArchiveTranslationLoader.ofJson(urls, listOf("lang/"), logger)

            // the lang files omit the common prefix; re-add it programmatically
            val prefix = source.game.titleKey
            loader.addLoader(PrefixTranslationLoader(miniGameTranslations, prefix))
        }

        return loader
    }

    override fun createGameSelectedActivity(args: GameStartArgs): Activity {
        val translations = args.options().context.translations

        val gameVotingName = translations.translateText("ap2.game_voting")

        val miniGameManager = FabricMiniGameManager(ApConstants.logger)
        val votingData = createOptionVoting(miniGameManager, gameVotingName, translations)

        val miniGameVoting = Voting(
            "mini_games",
            votingData,
            translations,
            true,
            true,
            true
        )

        this.miniGameVoting = miniGameVoting

        return ArcadePartyStartingActivity(args, logger, miniGameVoting)
    }

    private fun createOptionVoting(
        miniGameManager: FabricMiniGameManager,
        gameVotingName: TranslatedText,
        translations: Translations
    ): OptionVoting<MiniGame> {
        val miniGames = miniGameManager.games

        return OptionVoting(
            { player ->
                ItemStack(Items.PAPER).apply {
                    val name = gameVotingName.translateFor(player).withStyle(ChatFormatting.AQUA)
                    set(DataComponents.ITEM_NAME, name)
                }
            },
            { player -> gameVotingName.translateFor(player) },
            MiniGame::class.java,
            miniGames,
            { player, miniGame ->
                IconMaker.createIcon(miniGame, player, translations)
            }
        )
    }

    override fun createInstance(gameEnvironment: GameEnvironment): GameInstance {
        val miniGameVoteResult = if (miniGameVoting != null)
            miniGameVoting!!.getCurrentResult()
        else
            VoteResult.empty()

        return ArcadePartyInstance(gameEnvironment, vanillaTranslations, configFactory, miniGameVoteResult, logger)
    }
}