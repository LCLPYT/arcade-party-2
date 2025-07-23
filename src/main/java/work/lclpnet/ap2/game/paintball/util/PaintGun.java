package work.lclpnet.ap2.game.paintball.util;

public record PaintGun(int cooldownTicks, double bulletSize, double bulletPower, int bulletCount, double bulletSpread,
                       int maxBulletHits, double bulletDespawnSeconds, float bulletMass, float bulletDamage) {
}
