package work.lclpnet.ap2.mode_default.util

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.server.level.ServerPlayer
import work.lclpnet.activity.ComponentActivity
import work.lclpnet.activity.component.builtin.BuiltinComponents
import work.lclpnet.ap2.game.player.PlayerManager
import work.lclpnet.ap2.impl.game.PlayerUtil
import work.lclpnet.game.util.ProtectorComponent
import work.lclpnet.game.util.ProtectorUtils
import work.lclpnet.kibu.hook.player.PlayerAdvancementPacketCallback
import work.lclpnet.kibu.hook.player.PlayerConnectionHooks
import work.lclpnet.kibu.hook.player.PlayerRecipeNotificationCallback
import work.lclpnet.kibu.hook.player.PlayerWaypointCallback

class BaseActivityConfigurator(private val activity: ComponentActivity, private val args: ApBaseArgs) {
    fun configureProtector() {
        activity.component(ProtectorComponent.KEY).configure { config ->
            config.disallowAll()
            ProtectorUtils.allowCreativeOperatorBypass(config)
        }
    }

    fun configureHooks() {
        val hooks = activity.component(BuiltinComponents.HOOKS).hooks()

        PlayerConnectionHooks.JOIN.registerWith(hooks) { player -> onJoin(player) }
        PlayerAdvancementPacketCallback.HOOK.registerWith(hooks) { _, _ -> true }
        PlayerRecipeNotificationCallback.HOOK.registerWith(hooks) { _, _, _ -> true }
        PlayerWaypointCallback.HOOK.registerWith(hooks) { _, _ -> true }
    }

    fun resetPlayers() {
        val playerUtil: PlayerUtil = args.miniGameArgs.playerUtil
        playerUtil.resetToDefaults()

        for (player in PlayerLookup.all(args.miniGameArgs.server)) {
            playerUtil.resetPlayer(player)
        }
    }

    private fun onJoin(player: ServerPlayer) {
        val playerManager: PlayerManager = args.playerManager

        val spectator = playerManager.isPermanentSpectator(player) || !playerManager.offer(player)

        val state = if (spectator) PlayerUtil.State.SPECTATOR else PlayerUtil.State.DEFAULT

        args.miniGameArgs.playerUtil.resetPlayer(player, state)
    }
}