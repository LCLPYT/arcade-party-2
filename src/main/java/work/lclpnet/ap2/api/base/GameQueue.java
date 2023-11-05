package work.lclpnet.ap2.api.base;

import work.lclpnet.ap2.api.game.MiniGame;

import java.util.List;

public interface GameQueue {

    MiniGame pollNextGame();

    List<MiniGame> preview();

    void setNextGame(MiniGame miniGame);

    void shiftGame(MiniGame miniGame);
}
