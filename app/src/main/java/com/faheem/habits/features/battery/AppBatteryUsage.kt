@file:Suppress("CyclomaticComplexMethod", "ReturnCount")

package com.faheem.habits.features.battery

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.BatteryManager
import androidx.compose.ui.graphics.ImageBitmap
import com.faheem.habits.core.ui.installedAppIdentity
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Estimated energy attributed to one app. */
internal data class AppBatteryUsage(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val estimatedMah: Float,
    val percentOfBattery: Float,
    val foregroundMinutes: Long,
    val share: Float
)

internal data class BatteryUsageReport(
    val apps: List<AppBatteryUsage>,
    val systemMah: Float,
    val allocatedMah: Float,
    val capacityMah: Float,
    val observedDrainMah: Float,
    val sinceLabel: String,
    val confidence: String
) {
    val hasData: Boolean get() = apps.isNotEmpty()
}

internal data class BatteryLevelSample(val timeMillis: Long, val percent: Int, val charging: Boolean)

private const val PREFS = "momentum_battery_usage"
private const val KEY_ANCHOR = "daily_anchor"
private const val KEY_LEVELS = "level_samples"
private const val MIN_FOREGROUND_MS = 60_000L
private const val MAX_SAMPLES = 240
private const val SAMPLE_INTERVAL_MS = 5 * 60_000L

private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

private fun batteryManager(context: Context) =
    context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

/** Estimated full-pack capacity from the charge counter, or a sane fallback. */
private fun estimatedCapacityMah(context: Context): Float {
    val manager = batteryManager(context) ?: return 4_500f
    val counter = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
    val percent = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    if (counter <= 0 || percent <= 0) return 4_500f
    val capacity = counter / 1_000f / (percent / 100f)
    return capacity.coerceIn(1_500f, 20_000f)
}

internal object BatteryUsageEstimator {
    /**
     * Records a daily anchor of the fuel-gauge counter. The first observation of
     * each day is kept so later reads can measure real drain without a
     * long-running service.
     */
    fun ensureDailyAnchor(context: Context) {
        val today = LocalDate.now().toString()
        val store = prefs(context)
        val raw = store.getString(KEY_ANCHOR, null)
        val current = runCatching { raw?.let { JSONObject(it) } }.getOrNull()
        if (current != null && current.optString("day") == today) return
        val manager = batteryManager(context) ?: return
        val counter = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val percent = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        store.edit().putString(
            KEY_ANCHOR,
            JSONObject().apply {
                put("day", today)
                put("counter", counter)
                put("percent", percent)
                put("at", System.currentTimeMillis())
            }.toString()
        ).apply()
    }

    private data class Anchor(val counter: Int, val percent: Int, val at: Long)

    private fun readAnchor(context: Context): Anchor? {
        val raw = prefs(context).getString(KEY_ANCHOR, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            Anchor(
                counter = json.optInt("counter", -1),
                percent = json.optInt("percent", -1),
                at = json.optLong("at", System.currentTimeMillis())
            )
        }.getOrNull()
    }

    fun load(context: Context): BatteryUsageReport {
        ensureDailyAnchor(context)
        val manager = batteryManager(context)
        val percent = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val counter = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: -1
        val capacity = estimatedCapacityMah(context)
        val anchor = readAnchor(context)
        val observed = when {
            anchor != null && anchor.counter > 0 && counter > 0 && counter <= anchor.counter ->
                (anchor.counter - counter) / 1_000f
            anchor != null && anchor.percent > 0 && percent in 0..100 ->
                (anchor.percent - percent) / 100f * capacity
            else -> 0f
        }.coerceAtLeast(0f)

        val apps = usageByApp(context)
        val weights = apps.map { app -> (app.foregroundMinutes * 3f + 1.5f).coerceAtLeast(0.5f) }
        val weightSum = weights.sum()
        // Without a measured drop, fall back to a screen-on power model so the
        // chart still ranks apps by relative energy instead of showing zeroes.
        val rawMah = apps.map { it.foregroundMinutes / 60f * 150f }
        val rawTotal = rawMah.sum()
        val allocated = if (observed > 0f) observed * 0.68f else rawTotal
        val system = if (observed > 0f) observed - allocated else 0f
        val mapped = apps.mapIndexed { index, app ->
            val share = if (weightSum > 0f) weights[index] / weightSum else 0f
            val mah = if (observed > 0f) allocated * share else rawMah[index]
            AppBatteryUsage(
                packageName = app.packageName,
                label = app.label,
                icon = app.icon,
                estimatedMah = mah,
                percentOfBattery = if (capacity > 0f) mah / capacity * 100f else 0f,
                foregroundMinutes = app.foregroundMinutes,
                share = if (rawTotal > 0f) rawMah[index] / rawTotal else share
            )
        }.sortedByDescending { it.estimatedMah }
        val since = anchor?.at?.let {
            java.time.Instant.ofEpochMilli(it)
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
                .format(DateTimeFormatter.ofPattern("HH:mm"))
        } ?: "today"
        return BatteryUsageReport(
            apps = mapped,
            systemMah = system,
            allocatedMah = allocated,
            capacityMah = capacity,
            observedDrainMah = observed,
            sinceLabel = since,
            confidence = when {
                counter <= 0 -> "Estimated from usage time"
                observed <= 0f -> "Waiting for measurable drain"
                else -> "Scaled to the measured battery drop"
            }
        )
    }

    private data class ForegroundApp(val packageName: String, val label: String, val icon: ImageBitmap?, val foregroundMinutes: Long)

    private fun usageByApp(context: Context): List<ForegroundApp> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val stats = try {
            usm.queryAndAggregateUsageStats(start, System.currentTimeMillis())
        } catch (_: Exception) {
            return emptyList()
        }
        return stats.values.asSequence()
            .filter { it.packageName != context.packageName && it.totalTimeInForeground >= MIN_FOREGROUND_MS }
            .sortedByDescending { it.totalTimeInForeground }
            .take(30)
            .map { usage ->
                val identity = installedAppIdentity(context, usage.packageName)
                ForegroundApp(usage.packageName, identity.label, identity.icon, usage.totalTimeInForeground / 60_000L)
            }
            .toList()
    }
}

/** Stores a light history of battery percentages for the level chart. */
internal object BatteryLevelHistory {
    fun record(context: Context, percent: Int, charging: Boolean) {
        if (percent !in 0..100) return
        val store = prefs(context)
        val samples = read(store)
        val now = System.currentTimeMillis()
        val last = samples.lastOrNull()
        if (last != null && now - last.timeMillis < SAMPLE_INTERVAL_MS && last.percent == percent) return
        val updated = (samples + BatteryLevelSample(now, percent, charging))
            .filter { now - it.timeMillis <= 48 * 60 * 60_000L }
            .takeLast(MAX_SAMPLES)
        store.edit()
            .putString(
                KEY_LEVELS,
                JSONArray().apply {
                    updated.forEach { sample ->
                        put(
                            JSONObject().apply {
                                put("t", sample.timeMillis)
                                put("p", sample.percent)
                                put("c", sample.charging)
                            }
                        )
                    }
                }.toString()
            )
            .apply()
    }

    fun samples(context: Context): List<BatteryLevelSample> = read(prefs(context))

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_LEVELS).apply()
    }

    private fun read(store: android.content.SharedPreferences): List<BatteryLevelSample> {
        val raw = store.getString(KEY_LEVELS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val json = array.getJSONObject(index)
                    add(
                        BatteryLevelSample(
                            timeMillis = json.getLong("t"),
                            percent = json.getInt("p"),
                            charging = json.optBoolean("c", false)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}

/** Nice-to-have hook used from Application.onCreate; keeps charts populated. */
internal object AppBatteryUsageRecorder {
    fun record(context: Context) {
        val manager = batteryManager(context) ?: return
        val percent = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (percent in 0..100) {
            BatteryLevelHistory.record(context, percent, manager.isCharging)
        }
        BatteryUsageEstimator.ensureDailyAnchor(context)
    }
}

/** Formats an estimate for the per-app card. */
internal fun energyText(mah: Float): String =
    if (mah >= 10f) "${mah.roundToInt()} mAh" else String.format(Locale.US, "%.1f mAh", mah)
