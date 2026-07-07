package work.lclpnet.ap2.game.paintball.util

/**
 * Configuration of a paint gun's ink. A shot spawns a cluster of independently moving ink
 * blobs (spheres) that fly with momentum and splat onto the first surface they hit.
 *
 * @param speed The launch speed of the ink, in blocks per second.
 * @param gravity The downward acceleration applied to the ink, in blocks per second squared. Use a low value for a near-straight shot.
 * @param range The maximum distance a blob may travel before it despawns, in blocks.
 * @param blobCount How many independently moving ink blobs a single shot spawns.
 * @param blobRadius The collision (sphere-cast) radius of each blob, in blocks. Also used as the blob's particle size.
 * @param blobSpread The cone half-angle the blobs of a shot fan out within, in radians. Gives the cluster its volume.
 * @param splatRadius The paint radius of the splat a blob leaves when it hits a surface, in blocks.
 * @param damage The damage dealt when a blob hits an enemy.
 * @param deficitPaintBoost How much the paint radius is boosted per missing player in a team (multiplier).
 * @param trail The ink droplet trail configuration.
 */
data class InkSettings(
    val speed: Double,
    val gravity: Double,
    val range: Double,
    val blobCount: Int,
    val blobRadius: Double,
    val blobSpread: Double,
    val splatRadius: Float,
    val damage: Float,
    val deficitPaintBoost: Float,
    val trail: InkTrail
)

/**
 * When an ink blob travels through the air it drops some ink droplets along its flight path.
 *
 * @param trailTicks The ticks after which a blob leaves a droplet. Use [Int.MAX_VALUE] to disable the trail.
 * @param maxDroplets The maximum number of droplets a single blob can leave.
 * @param dropletRadius The paint radius of the droplets, in blocks.
 * @param subdivisions For very fast blobs, the droplet leaving process leaves gaps.
 * Configuring subdivisions splits the flight path between two droplets into the specified number of sections and leaves a droplet at each boundary.
 */
data class InkTrail(
    val trailTicks: Int,
    val maxDroplets: Int,
    val dropletRadius: Float,
    val subdivisions: Int
)

val NO_TRAIL = InkTrail(
    trailTicks = Int.MAX_VALUE,
    maxDroplets = 0,
    dropletRadius = 0f,
    subdivisions = 0
)
