package work.lclpnet.ap2.impl.game.kit;

public abstract class BaseKit implements Kit {

    protected final KitHandle handle;
    protected final String id;

    protected BaseKit(KitHandle handle, String id) {
        this.id = id;
        this.handle = handle;
    }

    @Override
    public String id() {
        return id;
    }
}
