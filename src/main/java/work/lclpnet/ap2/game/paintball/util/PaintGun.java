package work.lclpnet.ap2.game.paintball.util;

public record PaintGun(String id, int cooldownTicks, int bulletCount, double bulletSpread, BulletSettings bullet) {

    public record BulletSettings(
            double size, double power, double maxHits, double despawnSeconds, float mass, float damage,
            float maxImpactPower
    ) {}
}
