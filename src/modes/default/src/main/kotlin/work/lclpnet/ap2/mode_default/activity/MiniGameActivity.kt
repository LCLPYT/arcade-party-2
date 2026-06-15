package work.lclpnet.ap2.mode_default.activity

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.minecraft.server.level.ServerPlayer
import work.lclpnet.activity.ComponentActivity
import work.lclpnet.activity.component.ComponentBundle
import work.lclpnet.activity.component.builtin.BuiltinComponents
import work.lclpnet.ap2.ext.inWholeTicks
import work.lclpnet.ap2.game.MiniGame
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.impl.activity.ArcadePartyComponents
import work.lclpnet.ap2.mode_default.cmd.DrawCommand
import work.lclpnet.ap2.mode_default.cmd.RemakeCommand
import work.lclpnet.ap2.mode_default.cmd.WinCommand
import work.lclpnet.ap2.mode_default.util.ApBaseArgs
import work.lclpnet.ap2.mode_default.util.DefaultMiniGameHandle
import work.lclpnet.ap2.util.MinecraftDispatcher
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
    private val scope = CoroutineScope(MinecraftDispatcher(args.miniGameArgs.server) + SupervisorJob())

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

        args.playerManager.startMiniGame()
        registerHooks(args.miniGameArgs.hookStack)

        val hooks = component(BuiltinComponents.HOOKS).hooks()

        PlayerAdvancementPacketCallback.HOOK.registerWith(hooks) { _, _ -> true }
        PlayerRecipeNotificationCallback.HOOK.registerWith(hooks) { _, _, _ -> true }
        EntityUsePortalCallback.HOOK.registerWith(hooks) { _, _, _ -> true }

        val factory = miniGame.createFactory()

        scope.launch {
            val instance = try {
                // suspends until the map is opened, then resumes on the server thread
                factory.createInstance(handle)
            } catch (t: Throwable) {
                args.miniGameArgs.logger.error("Failed to start mini-game {}", miniGame.id, t)
                return@launch
            }

            onInstanceReady(instance, remake)
        }
    }

    private fun onInstanceReady(instance: MiniGameInstance, remake: AtomicBoolean) {
        val listener = instance.participantListener
        args.playerManager.bind(listener)

        val commands = component(BuiltinComponents.COMMANDS).commands()

        WinCommand(handle, instance).register(commands)
        DrawCommand(handle, instance).register(commands)
        RemakeCommand(handle, remake).register(commands)

        val scheduler = component(BuiltinComponents.SCHEDULER).scheduler()
        val maxDurationTicks = instance.maxDuration

        if (maxDurationTicks.isPositive()) {
            scheduler.timeout(maxDurationTicks.inWholeTicks) { ->
                DrawCommand.dispatchDraw(instance)
            }
        }

        args.tablistManager.status = args.miniGameArgs.translations.translateText(miniGame.titleKey)
        args.tablistManager.update()

        instance.start()
    }

    override fun stop() {
        scope.cancel()

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