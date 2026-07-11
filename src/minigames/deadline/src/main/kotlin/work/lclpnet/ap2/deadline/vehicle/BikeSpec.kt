package work.lclpnet.ap2.deadline.vehicle

import org.json.JSONObject
import work.lclpnet.game.map.GameMap

/**
 * Tuning of a [Motorbike].
 *
 * Speeds are in m/s, where one block is one meter (km/h = m/s * 3.6).
 *
 * @property mass The mass of bike and rider in kilograms.
 * @property engineForce The drive force at full throttle in newtons.
 * @property brakeForce The braking force in newtons.
 * @property drag The air resistance coefficient in kg/m; the resisting force grows with the speed squared.
 *   Exaggerated compared to a real bike, so the arena top speed stays playable.
 * @property roll The rolling resistance coefficient in kg/s; the resisting force grows linearly with the speed.
 * @property minSpeed The speed in m/s that the bike never falls below.
 * @property maxSpeed The speed in m/s that the bike never exceeds.
 * @property turnRate The steering rate in degrees per tick.
 */
data class BikeSpec(
    val mass: Float = 250f,
    val engineForce: Float = 3000f,
    val brakeForce: Float = 3000f,
    val drag: Float = 1.42f,
    val roll: Float = 42.6f,
    val minSpeed: Float = 12f,
    val maxSpeed: Float = 40f,
    val turnRate: Float = 6f,
) {
    companion object {
        /**
         * Reads the optional "bike" object from the map properties.
         * Every entry is optional and falls back to the [BikeSpec] defaults.
         */
        fun fromMap(map: GameMap): BikeSpec {
            val json = map.getProperty<Any?>("bike") as? JSONObject ?: return BikeSpec()
            val defaults = BikeSpec()

            return BikeSpec(
                mass = json.optFloat("mass", defaults.mass),
                engineForce = json.optFloat("engine-force", defaults.engineForce),
                brakeForce = json.optFloat("brake-force", defaults.brakeForce),
                drag = json.optFloat("drag", defaults.drag),
                roll = json.optFloat("roll", defaults.roll),
                minSpeed = json.optFloat("min-speed", defaults.minSpeed),
                maxSpeed = json.optFloat("max-speed", defaults.maxSpeed),
                turnRate = json.optFloat("turn-rate", defaults.turnRate),
            )
        }
    }
}
