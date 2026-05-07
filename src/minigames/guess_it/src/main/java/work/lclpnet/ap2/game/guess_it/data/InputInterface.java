package work.lclpnet.ap2.game.guess_it.data;

import net.minecraft.network.chat.Component;

public interface InputInterface {

    InputValue expectInput();

    void expectSelection(Component... options);
}
