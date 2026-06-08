package work.lclpnet.ap2.mode_default.activity

import net.minecraft.server.level.ServerPlayer
import work.lclpnet.activity.ComponentActivity
import work.lclpnet.activity.component.ComponentBundle
import work.lclpnet.activity.component.builtin.BuiltinComponents
import work.lclpnet.ap2.ext.inWholeTicks
import work.lclpnet.ap2.game.MiniGame
import work.lclpnet.ap2.impl.activity.ArcadePartyComponents
import work.lclpnet.ap2.mode_default.cmd.DrawCommand
import work.lclpnet.ap2.mode_default.cmd.RemakeCommand
import work.lclpnet.ap2.mode_default.cmd.WinCommand
import work.lclpnet.ap2.mode_default.util.ApBaseArgs
import work.lclpnet.ap2.mode_default.util.DefaultMiniGameHandle
import work.lclpnet.kibu.hook.HookRegistrar
import work.lclpnet.kibu.hook.entity.EntityUsePortalCallback
import work.lclpnet.kibu.hook.player.PlayerAdvancementPacketCallback
import work.lclpnet.kibu.hook.player.PlayerConnectionHooks
import work.lclpnet.kibu.hook.player.PlayerRecipeNotificationCallback
import java.util.concurrent.atomic.AtomicBoolean

class MiniGameActivity(
    private val miniGame: MiniGame,
    private val args: ApBaseArgs,
) : ComponentActivity(args.miniGameArgs.server, args.miniGameArgs.logger) {

    private lateinit var handle: DefaultMiniGameHandle

    override fun registerComponents(componentBundle: ComponentBundle) {
        componentBundle
            .add(BuiltinComponents.BOSS_BAR)
            .add(BuiltinComponents.COMMANDS)
            .add(BuiltinComponents.HOOKS)
            .add(BuiltinComponents.SCHEDULER)
            .add(ArcadePartyComponents.SCORE_BOARD)
    }

    override fun start() {
        super.start()

        val bossBars = component(BuiltinComponents.BOSS_BAR)

        val scoreboardComponent = component(ArcadePartyComponents.SCORE_BOARD)
        val scoreboard = scoreboardComponent.scoreboardManager(args.miniGameArgs::translations)

        val remake = AtomicBoolean(false)

        handle = DefaultMiniGameHandle(miniGame, args, bossBars, bossBars, scoreboard, remake)
        handle.init()  // hook stack is pushed and later popped by handle::unload in stop()

        val playerManager = args.playerManager

        playerManager.startMiniGame()
        registerHooks(args.miniGameArgs.hookStack)

        val instance = miniGame.createInstance(handle)
        instance.start()

        val listener = instance.participantListener
        playerManager.bind(listener)

        val commands = component(BuiltinComponents.COMMANDS).commands()

        WinCommand(handle, instance).register(commands)
        DrawCommand(handle, instance).register(commands)
        RemakeCommand(handle, remake).register(commands)

        val hooks = component(BuiltinComponents.HOOKS).hooks()

        PlayerAdvancementPacketCallback.HOOK.registerWith(hooks) { _, _ -> true }
        PlayerRecipeNotificationCallback.HOOK.registerWith(hooks) { _, _, _ -> true }
        EntityUsePortalCallback.HOOK.registerWith(hooks) { _, _, _ -> true }

        val scheduler = component(BuiltinComponents.SCHEDULER).scheduler()
        val maxDurationTicks = instance.maxDuration

        if (maxDurationTicks.isPositive()) {
            scheduler.timeout(maxDurationTicks.inWholeTicks) { ->
                DrawCommand.dispatchDraw(instance, handle)
            }
        }

        args.tablistManager.status = args.miniGameArgs.translations.translateText(miniGame.titleKey)
        args.tablistManager.update()
    }

    override fun stop() {
        handle.unload()

        args.playerManager.bind(null)

        super.stop()
    }

    private fun registerHooks(hooks: HookRegistrar) {
        PlayerConnectionHooks.JOIN.registerWith(hooks, this::onJoin)
        PlayerConnectionHooks.QUIT.registerWith(hooks, this::onQuit)
    }

    private fun onJoin(player: ServerPlayer) {
        args.miniGameArgs.playerUtil.resetPlayer(player)
    }

    private fun onQuit(player: ServerPlayer) {
        args.playerManager.remove(player)
    }
}