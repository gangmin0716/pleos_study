package com.example.pleos_study

import org.json.JSONObject

/**
 * One telemetry frame. Field names in the JSON follow Assetto Corsa's own
 * (RTCarInfo / shared memory), so the simulator and a real AC bridge are interchangeable.
 */
data class Telemetry(
    val carName: String?,
    val trackName: String?,
    val speedKmh: Float,
    val rpm: Float,
    val maxRpm: Float,
    /** AC convention: 0 = R, 1 = N, 2 = 1st gear. */
    val gear: Int,
    val gas: Float,
    val brake: Float,
    val lapTimeMs: Int,
    val lastLapMs: Int,
    val bestLapMs: Int,
    val lapCount: Int,
    val fuel: Float?,
    val maxFuel: Float?,
    /** FL, FR, RL, RR in °C. */
    val tyreTemps: List<Float>?,
    /** FL, FR, RL, RR: % of tread left, 100 = new. */
    val tyreWear: List<Float>?,
    /** FL, FR, RL, RR in psi, as AC reports them. */
    val tyrePressuresPsi: List<Float>?,
    /** Brake disc temperatures FL, FR, RL, RR in °C. */
    val brakeTemps: List<Float>?,
    /** AC's compound name, e.g. "Medium (M)". */
    val tyreCompound: String?,
    /** 0..1 around the lap, 0 at the finish line. */
    val lapPosition: Float?,
    /** World position on the ground plane (AC x and z). */
    val carX: Float?,
    val carZ: Float?,
    /** Traction control / ABS currently intervening. */
    val tcActive: Boolean,
    val absActive: Boolean,
) {
    companion object {
        fun fromJson(line: String): Telemetry {
            val o = JSONObject(line)
            val coords = o.optJSONArray("carCoordinates")?.takeIf { it.length() >= 3 }
            return Telemetry(
                carName = o.optString("carName").ifEmpty { null },
                trackName = o.optString("trackName").ifEmpty { null },
                speedKmh = o.optDouble("speedKmh", 0.0).toFloat(),
                rpm = o.optDouble("engineRPM", 0.0).toFloat(),
                maxRpm = o.optDouble("maxRpm", 8000.0).toFloat(),
                gear = o.optInt("gear", 1),
                gas = o.optDouble("gas", 0.0).toFloat(),
                brake = o.optDouble("brake", 0.0).toFloat(),
                lapTimeMs = o.optInt("lapTime", 0),
                lastLapMs = o.optInt("lastLap", 0),
                bestLapMs = o.optInt("bestLap", 0),
                lapCount = o.optInt("lapCount", 0),
                fuel = o.optFloatOrNull("fuel"),
                maxFuel = o.optFloatOrNull("maxFuel"),
                tyreTemps = o.optFloatList("tyreCoreTemp"),
                tyreWear = o.optFloatList("tyreWear"),
                tyrePressuresPsi = o.optFloatList("wheelsPressure"),
                brakeTemps = o.optFloatList("brakeTemp"),
                tyreCompound = o.optString("tyreCompound").ifEmpty { null },
                lapPosition = o.optFloatOrNull("normalizedCarPosition"),
                carX = coords?.getDouble(0)?.toFloat(),
                carZ = coords?.getDouble(2)?.toFloat(),
                tcActive = o.optBoolean("isTcInAction", false),
                absActive = o.optBoolean("isAbsInAction", false),
            )
        }

        private fun JSONObject.optFloatList(name: String): List<Float>? =
            optJSONArray(name)?.let { a -> List(a.length()) { a.getDouble(it).toFloat() } }

        private fun JSONObject.optFloatOrNull(name: String): Float? =
            if (has(name) && !isNull(name)) optDouble(name).toFloat() else null
    }
}
