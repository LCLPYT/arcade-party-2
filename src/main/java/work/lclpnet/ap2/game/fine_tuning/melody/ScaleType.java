package work.lclpnet.ap2.game.fine_tuning.melody;

public enum ScaleType {

    MAJOR(3),
    MINOR(4);

    private final int thirdSteps;

    ScaleType(int thirdSteps) {
        this.thirdSteps = thirdSteps;
    }

    public int getThirdSteps() {
        return thirdSteps;
    }
}
