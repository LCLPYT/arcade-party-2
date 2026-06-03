package work.lclpnet.ap2.turf_wars

import work.lclpnet.ap2.ext.ticks
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

enum class Phase { Nothing, Build, Fight }

const val DEBUG_TURF = false
const val INITIAL_BUILDING_BLOCKS = 32
const val MAX_BUILDING_BLOCKS = 50
const val MAX_ARROWS = 2
const val MAX_ARROWS_DEFICIT = 3
const val CAMP_ELIMINATION_SECONDS = 20
const val CAMP_WARNING_SECONDS = 10
const val STUCK_REPEL_TICKS = 20

val BUILDING_BLOCK_GAIN_PERIOD = 5.seconds
val ARROW_GAIN_DELAY = 2.seconds + 10.ticks
val ARROW_GAIN_DEFICIT_DELAY = 2.seconds
val TURF_INCREASE_PERIOD = 30.seconds
val TURF_INITIAL_INCREASE_DELAY = 45.seconds

val BUILD_PHASE_DURATION = 20.seconds
val FIGHT_PHASE_DURATION = 1.minutes
