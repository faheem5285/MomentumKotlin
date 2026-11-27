package com.faheem.habits.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

/** Clickable modifier with a TalkBack label and an explicit role. */
fun Modifier.momentumClickable(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    role: Role = Role.Button
): Modifier = this
    .semantics(mergeDescendants = true) {
        contentDescription = label
        this.role = role
    }
    .clickable(
        enabled = enabled,
        onClickLabel = label,
        role = role,
        onClick = onClick
    )

/** Adds a spoken description to the existing progress range information. */
fun Modifier.progressSemantics(label: String, fraction: Float): Modifier = this.semantics {
    contentDescription = label
    progressBarRangeInfo = ProgressBarRangeInfo(fraction.coerceIn(0f, 1f), 0f..1f)
}

/** Concise chart summary for TalkBack instead of reading every plotted value. */
fun chartSummaryDescription(label: String, values: List<Float>): String {
    if (values.isEmpty()) return "$label, no data"
    val minimum = values.minOrNull() ?: 0f
    val maximum = values.maxOrNull() ?: 0f
    return "$label, ${values.size} values, from ${minimum.toInt()} to ${maximum.toInt()}"
}
