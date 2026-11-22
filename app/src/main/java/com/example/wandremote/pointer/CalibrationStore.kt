package com.example.wandremote.pointer

import android.content.Context
import com.example.wandremote.sensors.Aim
import com.example.wandremote.sensors.ScreenCalibration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** A saved corner calibration and when it was captured (epoch millis). */
data class CalibrationProfile(val calibration: ScreenCalibration, val savedAtMs: Long)

/**
 * Corner calibrations, one profile per PC, in SharedPreferences. Key = the connected Wi-Fi host (or [DEFAULT_KEY]).
 * Each profile is one JSON string: {"refYaw":f,"time":ms,"corners":[[yaw,pitch] x4 in TL,TR,BR,BL order]}.
 */
class CalibrationStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val _profiles = MutableStateFlow(loadAll())
    val profiles: StateFlow<Map<String, CalibrationProfile>> = _profiles.asStateFlow()

    @Synchronized
    fun save(key: String, profile: CalibrationProfile) {
        prefs.edit().putString(PREFIX + key, encode(profile)).apply()
        _profiles.value = _profiles.value + (key to profile)
    }

    @Synchronized
    fun delete(key: String) {
        prefs.edit().remove(PREFIX + key).apply()
        _profiles.value = _profiles.value - key
    }

    private fun loadAll(): Map<String, CalibrationProfile> {
        val result = mutableMapOf<String, CalibrationProfile>()
        for ((k, v) in prefs.all) {
            if (!k.startsWith(PREFIX) || v !is String) continue
            decode(v)?.let { result[k.removePrefix(PREFIX)] = it }
        }
        return result
    }

    companion object {
        const val DEFAULT_KEY = "default"
        private const val FILE = "calibration_profiles"
        private const val PREFIX = "profile_"

        internal fun encode(p: CalibrationProfile): String {
            val corners = JSONArray()
            for (c in p.calibration.corners) corners.put(JSONArray().put(c.yaw.toDouble()).put(c.pitch.toDouble()))
            return JSONObject()
                .put("refYaw", p.calibration.refYaw.toDouble())
                .put("time", p.savedAtMs)
                .put("corners", corners)
                .toString()
        }

        internal fun decode(text: String): CalibrationProfile? = runCatching {
            val o = JSONObject(text)
            val arr = o.getJSONArray("corners")
            val corners = (0 until arr.length()).map {
                val c = arr.getJSONArray(it)
                Aim(c.getDouble(0).toFloat(), c.getDouble(1).toFloat())
            }
            val cal = ScreenCalibration(o.getDouble("refYaw").toFloat(), corners)
            if (cal.isValid) CalibrationProfile(cal, o.optLong("time", 0L)) else null
        }.getOrNull()
    }
}
