package work.lclpnet.ap2.impl.util.math;

public class Vec2i {

    protected int x, z;

    public Vec2i(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public int x() {
        return x;
    }

    public int z() {
        return z;
    }

    public static class Mutable extends Vec2i {

        public Mutable(int x, int z) {
            super(x, z);
        }

        public void set(int x, int z) {
            this.x = x;
            this.z = z;
        }
    }
}
