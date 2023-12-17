package work.lclpnet.ap2.base.activity;

import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.activity.ComponentActivity;
import work.lclpnet.activity.component.ComponentBundle;
import work.lclpnet.activity.component.builtin.BuiltinComponents;
import work.lclpnet.ap2.api.base.ParticipantListener;
import work.lclpnet.ap2.api.base.PlayerManager;
import work.lclpnet.ap2.api.game.MiniGame;
import work.lclpnet.ap2.api.game.MiniGameInstance;
import work.lclpnet.ap2.base.cmd.DrawCommand;
import work.lclpnet.ap2.base.cmd.WinCommand;
import work.lclpnet.ap2.impl.game.DefaultMiniGameHandle;
import work.lclpnet.kibu.hook.player.PlayerAdvancementPacketCallback;
import work.lclpnet.kibu.hook.player.PlayerConnectionHooks;
import work.lclpnet.kibu.plugin.cmd.CommandRegistrar;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.translate.bossbar.BossBarProvider;

public class MiniGameActivity extends ComponentActivity {

    private final MiniGame miniGame;
    private final PreparationActivity.Args args;
    private DefaultMiniGameHandle handle;

    public MiniGameActivity(MiniGame miniGame, PreparationActivity.Args args) {
        super(args.pluginContext());
        this.miniGame = miniGame;
        this.args = args;
    }

    @Override
    protected void registerComponents(ComponentBundle componentBundle) {
        componentBundle
                .add(BuiltinComponents.BOSS_BAR)
                .add(BuiltinComponents.COMMANDS)
                .add(BuiltinComponents.HOOKS);
    }

    @Override
    public void start() {
        BossBarProvider bossBarProvider = component(BuiltinComponents.BOSS_BAR);

        handle = new DefaultMiniGameHandle(miniGame, args, bossBarProvider);
        handle.init();

        PlayerManager playerManager = args.playerManager();

        playerManager.startMiniGame();
        registerHooks(args.container().hookStack());

        MiniGameInstance instance = miniGame.createInstance(handle);
        instance.start();

        ParticipantListener listener = instance.getParticipantListener();
        playerManager.bind(listener);

        CommandRegistrar commands = component(BuiltinComponents.COMMANDS).commands();

        new WinCommand(handle, instance).register(commands);
        new DrawCommand(handle, instance).register(commands);

        HookRegistrar hooks = component(BuiltinComponents.HOOKS).hooks();
        hooks.registerHook(PlayerAdvancementPacketCallback.HOOK, (player, packet) -> true);
    }

    @Override
    public void stop() {
        if (handle != null) {
            handle.unload();
        }

        args.playerManager().bind(null);

        handle.getWorldBorderManager().resetWorldBorder();

        super.stop();
    }

    private void registerHooks(HookRegistrar registrar) {
        registrar.registerHook(PlayerConnectionHooks.JOIN, this::onJoin);
        registrar.registerHook(PlayerConnectionHooks.QUIT, this::onQuit);
    }

    private void onJoin(ServerPlayerEntity player) {
        args.container().playerUtil().resetPlayer(player);
    }

    private void onQuit(ServerPlayerEntity player) {
        args.playerManager().remove(player);
    }
}
