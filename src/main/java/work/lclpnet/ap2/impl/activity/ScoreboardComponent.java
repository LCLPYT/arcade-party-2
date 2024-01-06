package work.lclpnet.ap2.impl.activity;

import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.PlayerManager;
import work.lclpnet.activity.component.Component;
import work.lclpnet.activity.component.ComponentBundle;
import work.lclpnet.activity.component.ComponentView;
import work.lclpnet.activity.component.DependentComponent;
import work.lclpnet.activity.component.builtin.BuiltinComponents;
import work.lclpnet.ap2.impl.util.Lazy;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.translate.TranslationService;

import java.util.function.Supplier;

public class ScoreboardComponent implements Component, DependentComponent {

    private final Lazy<CustomScoreboardManager, TranslationService> scoreboardManager;
    private HookRegistrar hooks;

    public ScoreboardComponent(ServerScoreboard scoreboard, PlayerManager playerManager) {
        this.scoreboardManager = new Lazy<>(translations -> new CustomScoreboardManager(scoreboard, translations, playerManager));
    }

    @Override
    public void declareDependencies(ComponentBundle componentBundle) {
        componentBundle.add(BuiltinComponents.HOOKS);
    }

    @Override
    public void injectDependencies(ComponentView componentView) {
        hooks = componentView.get(BuiltinComponents.HOOKS).hooks();
    }

    @Override
    public void mount() {
        scoreboardManager.afterEvaluate(it -> it.init(hooks));
    }

    @Override
    public void dismount() {
        scoreboardManager.afterEvaluate(CustomScoreboardManager::unload);
    }

    public CustomScoreboardManager scoreboardManager(Supplier<TranslationService> translationsSupplier) {
        return scoreboardManager.get(translationsSupplier);
    }
}
