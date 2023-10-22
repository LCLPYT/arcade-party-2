package work.lclpnet.ap2.base.activity;

import work.lclpnet.activity.Activity;
import work.lclpnet.ap2.api.game.MiniGame;
import work.lclpnet.ap2.api.game.MiniGameInstance;
import work.lclpnet.ap2.impl.game.DefaultMiniGameHandle;

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

        MiniGameInstance instance = miniGame.createInstance(handle);
        instance.start();
    }

    @Override
    public void stop() {
        if (handle != null) {
            handle.unload();
        }
    }
}
