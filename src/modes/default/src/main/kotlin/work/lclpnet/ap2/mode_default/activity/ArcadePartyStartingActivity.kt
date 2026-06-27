package work.lclpnet.ap2.mode_default.activity

import org.slf4j.Logger
import work.lclpnet.activity.ComponentActivity
import work.lclpnet.activity.component.ComponentBundle
import work.lclpnet.activity.component.builtin.BuiltinComponents
import work.lclpnet.ap2.game.MiniGame
import work.lclpnet.game.api.start.GameStartArgs
import work.lclpnet.game.impl.Voting
import work.lclpnet.game.util.GameStartUtil
import work.lclpnet.kibu.scheduler.Ticks

class ArcadePartyStartingActivity(
    private val args: GameStartArgs,
    logger: Logger,
    private val miniGameVoting: Voting<MiniGame>
) : ComponentActivity(
    args.options().context.server,
    logger
) {

    override fun registerComponents(components: ComponentBundle) {
        components.add(BuiltinComponents.HOOKS)
    }

    override fun start() {
        super.start()

        val votingItemSlot = args.itemManager().reserve(4)

        if (votingItemSlot != null) {
            val hooks = component(BuiltinComponents.HOOKS).hooks()

            GameStartUtil.setupVoting(miniGameVoting, hooks, votingItemSlot.slot(), args, Ticks.seconds(15))
        }
    }
}