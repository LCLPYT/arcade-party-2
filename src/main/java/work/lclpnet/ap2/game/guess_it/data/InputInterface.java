package work.lclpnet.ap2.game.guess_it.data;

public interface InputInterface {

    InputValue expectInput();

    void expectSelection(String... options);
}
