package work.lclpnet.ap2.game.vehicle

import org.json.JSONObject
import work.lclpnet.game.map.GameMap

/**
 * Tuning of a [Motorbike]. Speeds are in m/s (1 block = 1 meter), forces in newtons.
 */
data class BikeSpec(
    val mass: Float = 250f, // bike + rider mass (kg)
    val engineForce: Float = 3000f, // throttle drive force
    val brakeForce: Float = 3000f, // braking force
    // resistance forces. drag is exaggerated compared to a real bike so the arena top speed stays playable
    val drag: Float = 1.42f, // air resistance, grows with speed squared. balances the engine at ~120km/h
    val roll: Float = 42.6f, // rolling resistance, grows linearly with speed. ~30x drag
    val minSpeed: Float = 12f, // ~43 km/h
    val maxSpeed: Float = 40f, // ~144 km/h, engine max speed through drag is 120km/h
    val turnRate: Float = 6f, // steering rate in degrees per tick
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
