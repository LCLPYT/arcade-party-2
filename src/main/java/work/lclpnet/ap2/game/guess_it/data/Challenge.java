package work.lclpnet.ap2.game.guess_it.data;

public interface Challenge {

    String getPreparationKey();

    int getDurationTicks();

    void begin(InputInterface input, ChallengeMessenger messenger);

    void evaluate(PlayerChoices choices, ChallengeResult result);

    default void destroy() {}

    default void prepare() {}

    default boolean shouldPlayBeginSound() {
        return true;
    }
}
