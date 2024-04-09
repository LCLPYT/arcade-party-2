package work.lclpnet.ap2.impl.util.scoreboard;

public class ScoreboardLayout {

    public static final int UNDEFINED = -1;
    public static final int TOP = 0;
    public static final int BOTTOM = 1;
    private int topId = Integer.MAX_VALUE - 1;
    private int bottomId = Integer.MIN_VALUE + 1;

    public void addTop(ScoreHandle handle) {
        handle.setScore(topId--);
    }

    public void addBottom(ScoreHandle handle) {
        handle.setScore(bottomId++);
    }
}
