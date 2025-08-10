package work.lclpnet.ap2.impl.util.title;

public interface TitleAnimation {

    /**
     * Advances the animation to the next tick.
     * @return True, if the animation is done.
     */
    boolean tick();

    void begin();

    void destroy();
}
