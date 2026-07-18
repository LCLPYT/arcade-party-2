package work.lclpnet.ap2.impl.activity

import net.minecraft.server.ServerScoreboard
import net.minecraft.server.players.PlayerList
import work.lclpnet.activity.component.Component
import work.lclpnet.activity.component.ComponentBundle
import work.lclpnet.activity.component.ComponentView
import work.lclpnet.activity.component.DependentComponent
import work.lclpnet.activity.component.builtin.BuiltinComponents
import work.lclpnet.activity.component.builtin.HookComponent
import work.lclpnet.ap2.impl.util.Lazy
import work.lclpnet.ap2.util.scoreboard.CustomScoreboardManager
import work.lclpnet.kibu.hook.HookRegistrar
import work.lclpnet.kibu.translate.Translations
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier

class ScoreboardComponent(
    scoreboard: ServerScoreboard,
    playerManager: PlayerList
) : Component, DependentComponent {
    private val scoreboardManager: Lazy<CustomScoreboardManager, Translations> = Lazy { translations ->
        CustomScoreboardManager(scoreboard, translations, playerManager)
    }
    private lateinit var hooks: HookRegistrar

    override fun declareDependencies(componentBundle: ComponentBundle) {
        componentBundle.add(BuiltinComponents.HOOKS)
    }

    override fun injectDependencies(componentView: ComponentView) {
        hooks = componentView.get(BuiltinComponents.HOOKS).hooks()
    }

    override fun mount() {
        scoreboardManager.afterEvaluate {
            it.init(hooks)
        }
    }

    override fun dismount() {
        scoreboardManager.afterEvaluate {
            it.unload()
        }
    }

    fun scoreboardManager(translationsSupplier: () -> Translations): CustomScoreboardManager =
        scoreboardManager.get(translationsSupplier)
}
