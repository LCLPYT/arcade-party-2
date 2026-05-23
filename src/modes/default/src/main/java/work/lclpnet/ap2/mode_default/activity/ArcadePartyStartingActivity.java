package work.lclpnet.ap2.mode_default.activity;

import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import work.lclpnet.activity.ComponentActivity;
import work.lclpnet.activity.component.ComponentBundle;
import work.lclpnet.activity.component.builtin.BuiltinComponents;
import work.lclpnet.ap2.api.game.MiniGame;
import work.lclpnet.game.api.start.GameStartArgs;
import work.lclpnet.game.api.start.ItemReservationManager;
import work.lclpnet.game.impl.Voting;
import work.lclpnet.game.util.GameStartUtil;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.Ticks;

public class ArcadePartyStartingActivity extends ComponentActivity {

    private final GameStartArgs args;
    private final Voting<MiniGame> miniGameVoting;

    public ArcadePartyStartingActivity(@NonNull GameStartArgs args, @NonNull Logger logger, Voting<MiniGame> miniGameVoting) {
        super(args.options().getContext().getServer(), logger);

        this.args = args;
        this.miniGameVoting = miniGameVoting;
    }

    @Override
    protected void registerComponents(@NotNull ComponentBundle components) {
        components.add(BuiltinComponents.HOOKS);
    }

    @Override
    public void start() {
        super.start();

        ItemReservationManager.Reservation votingItemSlot = args.itemManager().reserve(4);

        if (votingItemSlot != null) {
            HookRegistrar hooks = component(BuiltinComponents.HOOKS).hooks();

            GameStartUtil.setupVoting(miniGameVoting, hooks, votingItemSlot.slot(), args, Ticks.seconds(15));
        }
    }
}
