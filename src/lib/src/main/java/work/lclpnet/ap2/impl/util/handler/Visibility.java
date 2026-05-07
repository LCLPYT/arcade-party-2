package work.lclpnet.ap2.impl.util.handler;

public enum Visibility {
    VISIBLE,
    PARTIALLY_VISIBLE,
    INVISIBLE;

    public Visibility next() {
        Visibility[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
