package work.lclpnet.ap2.base.activity;

import work.lclpnet.activity.ComponentActivity;
import work.lclpnet.activity.component.ComponentBundle;
import work.lclpnet.activity.component.builtin.BuiltinComponents;
import work.lclpnet.ap2.base.util.ApBaseArgs;
import work.lclpnet.ap2.base.util.BaseActivityConfigurator;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.lobby.game.util.ProtectorComponent;

public class WinActivity extends ComponentActivity {

    private final ApBaseArgs args;
    private final BaseActivityConfigurator activityConfigurator;

    public WinActivity(ApBaseArgs args) {
        super(args.miniGameArgs().server(), args.miniGameArgs().logger());
        this.args = args;
        this.activityConfigurator = new BaseActivityConfigurator(this, args);
    }

    @Override
    protected void registerComponents(ComponentBundle components) {
        components.add(BuiltinComponents.HOOKS).add(ProtectorComponent.KEY);
    }

    @Override
    public void start() {
        super.start();

        activityConfigurator.configureProtector();
        activityConfigurator.configureHooks();

        args.playerManager().leaveFinale();

        activityConfigurator.resetPlayers();

        PlayerRef winner = args.scoreManager().getFinalWinner().orElseThrow();

        // TODO implement
    }
}
