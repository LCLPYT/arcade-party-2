package work.lclpnet.ap2.base.activity;

import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.activity.Activity;
import work.lclpnet.ap2.api.base.ParticipantListener;
import work.lclpnet.ap2.api.base.PlayerManager;
import work.lclpnet.ap2.api.game.MiniGame;
import work.lclpnet.ap2.api.game.MiniGameInstance;
import work.lclpnet.ap2.impl.game.DefaultMiniGameHandle;
import work.lclpnet.kibu.hook.player.PlayerConnectionHooks;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;

public class MiniGameActivity implements Activity {

    private final MiniGame miniGame;
    private final PreparationActivity.Args args;
    private DefaultMiniGameHandle handle;

    public MiniGameActivity(MiniGame miniGame, PreparationActivity.Args args) {
        this.miniGame = miniGame;
        this.args = args;
    }

    @Override
    public void start() {
        handle = new DefaultMiniGameHandle(miniGame, args);
        handle.init();

        PlayerManager playerManager = args.playerManager();

        playerManager.reset();
        registerHooks(args.container().hookStack());

        MiniGameInstance instance = miniGame.createInstance(handle);
        instance.start();

        ParticipantListener listener = instance.getParticipantListener();
        playerManager.bind(listener);
    }

    @Override
    public void stop() {
        if (handle != null) {
            handle.unload();
        }

        args.playerManager().bind(null);
    }

    private void registerHooks(HookRegistrar registrar) {
        registrar.registerHook(PlayerConnectionHooks.QUIT, this::onQuit);
    }

    private void onQuit(ServerPlayerEntity player) {
        args.playerManager().removeParticipant(player);
    }
}
