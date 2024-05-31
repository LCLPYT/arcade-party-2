package work.lclpnet.ap2.base.activity;

import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.activity.ComponentActivity;
import work.lclpnet.activity.component.ComponentBundle;
import work.lclpnet.activity.component.builtin.BossBarComponent;
import work.lclpnet.activity.component.builtin.BuiltinComponents;
import work.lclpnet.ap2.api.base.ParticipantListener;
import work.lclpnet.ap2.api.base.PlayerManager;
import work.lclpnet.ap2.api.game.MiniGame;
import work.lclpnet.ap2.api.game.MiniGameInstance;
import work.lclpnet.ap2.base.cmd.DrawCommand;
import work.lclpnet.ap2.base.cmd.WinCommand;
import work.lclpnet.ap2.impl.activity.ArcadePartyComponents;
import work.lclpnet.ap2.impl.activity.ScoreboardComponent;
import work.lclpnet.ap2.impl.game.DefaultMiniGameHandle;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.kibu.hook.player.PlayerAdvancementPacketCallback;
import work.lclpnet.kibu.hook.player.PlayerConnectionHooks;
import work.lclpnet.kibu.hook.player.PlayerRecipePacketCallback;
import work.lclpnet.kibu.plugin.cmd.CommandRegistrar;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;

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
                .add(BuiltinComponents.HOOKS)
                .add(ArcadePartyComponents.SCORE_BOARD);
    }

    @Override
    public void start() {
        super.start();

        BossBarComponent bossBars = component(BuiltinComponents.BOSS_BAR);

        ScoreboardComponent scoreboardComponent = component(ArcadePartyComponents.SCORE_BOARD);
        CustomScoreboardManager scoreboard = scoreboardComponent.scoreboardManager(args.container()::translations);

        handle = new DefaultMiniGameHandle(miniGame, args, bossBars, bossBars, scoreboard);
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
        hooks.registerHook(PlayerRecipePacketCallback.HOOK, (player, packet) -> true);
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
