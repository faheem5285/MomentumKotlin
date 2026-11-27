package com.faheem.habits.core.notifications

/** Pure policy for bounded, collapsed network usage alerts. */

internal const val MAX_NETWORK_ALERTS_PER_CHECK = 3
internal const val NETWORK_ALERT_MULTIPLIER = 1.5
private const val MAX_THRESHOLDS_TO_EVALUATE = 256

internal data class NetworkAlert(val index: Int, val thresholdBytes: Long)

internal data class NetworkAlertPlan(
    val alerts: List<NetworkAlert>,
    val collapsedOlderAlerts: Int,
    val lastAlertIndex: Int
)

/**
 * Returns at most [maxAlerts] unposted thresholds. Older crossed thresholds are counted rather than
 * posted one-by-one, and every crossed threshold is marked handled so a later check cannot replay a
 * burst after a long gap or a period change.
 */
internal fun planNetworkAlerts(
    bytes: Long?,
    limitMb: Long,
    lastAlertIndex: Int,
    maxAlerts: Int = MAX_NETWORK_ALERTS_PER_CHECK,
    multiplier: Double = NETWORK_ALERT_MULTIPLIER
): NetworkAlertPlan {
    if (bytes == null || limitMb <= 0L) {
        return NetworkAlertPlan(emptyList(), 0, lastAlertIndex)
    }
    val cap = maxAlerts.coerceIn(1, 8)
    val crossed = mutableListOf<NetworkAlert>()
    var index = (lastAlertIndex + 1).coerceAtLeast(0)
    while (crossed.size < MAX_THRESHOLDS_TO_EVALUATE) {
        val threshold = networkAlertThreshold(limitMb, index, multiplier) ?: break
        if (bytes < threshold) break
        crossed += NetworkAlert(index, threshold)
        index++
    }
    val visible = crossed.takeLast(cap)
    return NetworkAlertPlan(
        alerts = visible,
        collapsedOlderAlerts = crossed.size - visible.size,
        lastAlertIndex = crossed.lastOrNull()?.index ?: lastAlertIndex
    )
}

internal fun networkAlertThreshold(limitMb: Long, index: Int, multiplier: Double = NETWORK_ALERT_MULTIPLIER): Long? {
    if (index < 0 || multiplier <= 1.0 || !multiplier.isFinite()) return null
    val value = limitMb.toDouble() * 1_000_000.0 * Math.pow(multiplier, index.toDouble())
    if (!value.isFinite() || value >= Long.MAX_VALUE.toDouble()) return null
    return kotlin.math.ceil(value).toLong()
}

internal fun networkAlertTitle(label: String, period: String, alertNumber: Int): String =
    "$label $period usage alert $alertNumber"

internal fun networkAlertBody(
    bytes: Long,
    alertNumber: Int,
    thresholdBytes: Long,
    period: String,
    collapsedOlderAlerts: Int
): String = buildString {
    append(formatDataBytes(bytes))
    append(" used; alert ")
    append(alertNumber)
    append(" was triggered at ")
    append(formatDataBytes(thresholdBytes))
    append(". Monitoring continues.")
    if (collapsedOlderAlerts > 0) {
        append(" ")
        append(collapsedOlderAlerts)
        append(" earlier threshold")
        append(if (collapsedOlderAlerts == 1) "" else "s")
        append(" were passed and combined into this update.")
    }
}

internal fun formatDataBytes(bytes: Long): String =
    if (bytes >= 1_000_000_000L) "%.2f GB".format(bytes / 1_000_000_000.0)
    else "%.1f MB".format(bytes / 1_000_000.0)

internal fun networkLimitHelperCopy(periodLabel: String): String =
    "Limits reset ${periodLabel.lowercase()}. Use 0 to disable an alarm. Alerts are silent, need Usage Access and monitoring, and are combined when several thresholds are passed at once."

internal fun screenTimeAlertCopy(minutes: Int, limitMinutes: Int, period: String): Pair<String, String> {
    val duration = { value: Int -> if (value >= 60) "${value / 60}h ${value % 60}m" else "${value}m" }
    return "$period screen time limit reached" to
        "You used ${duration(minutes)} of screen time; your $period limit is ${duration(limitMinutes)}."
}
