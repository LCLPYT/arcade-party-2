package work.lclpnet.ap2.game.guess_it.data;

public interface Challenge {

    String getPreparationKey();

    int getDurationTicks();

    void begin(InputInterface input);

    void evaluate(PlayerChoices choices, ChallengeResult result);

    void destroy();
}
