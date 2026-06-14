package work.lclpnet.ap2.api.stats

object CommonStats {
    @JvmField
    val Score = Stat("score", 0)

    @JvmField
    val Kills = Stat("kills", 0)

    @JvmField
    val Deaths = Stat("deaths", 0, higherIsBetter = false)

    @JvmField
    val KillDeathRatio = Stat("kd", 0f)

    @JvmField
    val DamageDealt = Stat("damage_dealt", 0f)

    @JvmField
    val DistanceMoved = Stat("distance_moved", 0.0)

    @JvmField
    val TimeSurvived = Stat("time_survived", 0, unit = StatUnits.Seconds)

    @JvmField
    val BlocksPlaced = Stat("blocks_placed", 0)

    @JvmField
    val BlocksBroken = Stat("blocks_broken", 0)
}