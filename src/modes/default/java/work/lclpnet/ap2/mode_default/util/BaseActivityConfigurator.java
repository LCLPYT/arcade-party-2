package work.lclpnet.ap2.mode_default.util;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.activity.ComponentActivity;
import work.lclpnet.activity.component.builtin.BuiltinComponents;
import work.lclpnet.ap2.api.base.PlayerManager;
import work.lclpnet.ap2.impl.game.PlayerUtil;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.player.PlayerAdvancementPacketCallback;
import work.lclpnet.kibu.hook.player.PlayerConnectionHooks;
import work.lclpnet.kibu.hook.player.PlayerRecipeNotificationCallback;
import work.lclpnet.kibu.hook.player.PlayerWaypointCallback;
import work.lclpnet.lobby.game.util.ProtectorComponent;
import work.lclpnet.lobby.game.util.ProtectorUtils;

public class BaseActivityConfigurator {

    private final ComponentActivity activity;
    private final ApBaseArgs args;

    public BaseActivityConfigurator(ComponentActivity activity, ApBaseArgs args) {
        this.args = args;
        this.activity = activity;
    }

    public void configureProtector() {
        activity.component(ProtectorComponent.KEY).configure(config -> {
            config.disallowAll();

            ProtectorUtils.allowCreativeOperatorBypass(config);
        });
    }

    public void configureHooks() {
        HookRegistrar hooks = activity.component(BuiltinComponents.HOOKS).hooks();
        hooks.registerHook(PlayerConnectionHooks.JOIN, this::onJoin);
        hooks.registerHook(PlayerAdvancementPacketCallback.HOOK, (player, packet) -> true);
        hooks.registerHook(PlayerRecipeNotificationCallback.HOOK, (player, recipeEntry, displayEntry) -> true);
        hooks.registerHook(PlayerWaypointCallback.HOOK, (player, waypoint) -> true);
    }

    public void resetPlayers() {
        PlayerUtil playerUtil = args.miniGameArgs().playerUtil();
        playerUtil.resetToDefaults();

        PlayerLookup.all(args.miniGameArgs().server())
                .forEach(playerUtil::resetPlayer);
    }

    private void onJoin(ServerPlayerEntity player) {
        PlayerManager playerManager = args.playerManager();

        boolean spectator = playerManager.isPermanentSpectator(player) || !playerManager.offer(player);

        PlayerUtil.State state = spectator ? PlayerUtil.State.SPECTATOR : PlayerUtil.State.DEFAULT;
        args.miniGameArgs().playerUtil().resetPlayer(player, state);
    }
}
