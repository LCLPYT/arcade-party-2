package work.lclpnet.ap2.game.paintball.util;

import net.minecraft.sound.SoundEvent;

/**
 * Configuration for a paint gun.
 * @param id An identifier string.
 * @param cooldownTicks Ticks to cooldown in between shots.
 * @param bulletCount The amount of bullets to fire at once.
 * @param bulletSpread The maximum random bullet spread angle in degrees.
 * @param ammo The ammo count. Each shot cost one ammo.
 * @param reloadTicks The amount of ticks it takes to reload the next ink.
 * @param reloadAmount The amount of ink to reload at once.
 * @param fireSound The sound to be played when firing.
 * @param bullet Bullet settings
 */
public record PaintGun(
        String id, int cooldownTicks, int bulletCount, double bulletSpread, int ammo, int reloadTicks, int reloadAmount,
        SoundCfg fireSound, BulletSettings bullet
) {

    /**
     * Settings for bullets of a paint gun
     * @param size The scale / sidelength of the bullet cube.
     * @param power The magnitude of the bullet velocity.
     * @param maxHits Maximum number of times a bullet can paint blocks. Compares against a counter that is incremented with each block painted.
     * @param despawnSeconds Time after which a bullet starts fading away. Timer is started when a block is hit.
     * @param mass The physical mass of the bullet object.
     * @param damage How much damage the bullet deals when hitting an entity.
     * @param maxImpactPower Maximum magnitude of a bullet when hitting a block. If the bullet velocity magnitude is above this value, the magnitude is clamped.
     * @param paintRadius The sphere radius in which blocks are painted when hitting a block.
     * @param deficitPaintBoost A paint radius multiplier per missing player in the team.
     * @param split Bullet splitting settings.
     */
    public record BulletSettings(
            double size, double power, double maxHits, double despawnSeconds, float mass, float damage,
            float maxImpactPower, float paintRadius, float deficitPaintBoost, BulletSplit split
    ) {}

    /**
     * Settings for bullet splitting.
     * @param splitTicks The amount of ticks after which a bullet may split.
     * @param maxSplits The maximum number of splits a bullet can do.
     * @param splitPaintRadius The sphere radius in which blocks are painted when a split bullet hits a block.
     * @param splitSpread The maximum random spread angle of split bullets, in degrees.
     */
    public record BulletSplit(
            int splitTicks, int maxSplits, float splitPaintRadius, float splitSpread
    ) {
        public static final BulletSplit NO_SPLIT = new BulletSplit(Integer.MAX_VALUE, 0, 0, 0);
    }

    public record SoundCfg(SoundEvent sound, float volume, float pitch) {}
}
