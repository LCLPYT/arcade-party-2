package work.lclpnet.ap2.game.guess_it.data;

import net.minecraft.text.Text;

public interface InputInterface {

    InputValue expectInput();

    void expectSelection(Text... options);
}
