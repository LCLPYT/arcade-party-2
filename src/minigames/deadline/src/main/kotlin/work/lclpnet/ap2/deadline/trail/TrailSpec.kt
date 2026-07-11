package work.lclpnet.ap2.deadline.trail

import org.json.JSONObject
import work.lclpnet.game.map.GameMap

/**
 * Pacing of a rider's [LightTrail].
 */
data class TrailSpec(
    val initialSegments: Int = 100, // how many segments a trail keeps before its tail starts to disappear
    val maxSegments: Int = 500, // the segment limit stops growing once it reaches this
    val growthInterval: Int = 9, // ticks it takes for the segment limit to grow by one
) {
    companion object {
        /**
         * Reads the optional "trail" object from the map properties.
         * Every entry is optional and falls back to the [TrailSpec] defaults.
         */
        fun fromMap(map: GameMap): TrailSpec {
            val json = map.getProperty<Any?>("trail") as? JSONObject ?: return TrailSpec()
            val defaults = TrailSpec()

            return TrailSpec(
                initialSegments = json.optInt("initial-segments", defaults.initialSegments),
                maxSegments = json.optInt("max-segments", defaults.maxSegments),
                growthInterval = json.optInt("growth-interval", defaults.growthInterval),
            )
        }
    }
}
