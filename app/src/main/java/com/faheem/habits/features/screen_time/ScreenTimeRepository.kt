package com.faheem.habits.features.screen_time

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScreenTimeSnapshot(
    val hasAccess: Boolean,
    val apps: List<AppUsage>,
    val dailyMinutes: List<Long>,
    val yesterdayMinutes: Long
)

@Singleton
class ScreenTimeRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun load(days: Long): ScreenTimeSnapshot = withContext(Dispatchers.IO) {
        val access = hasUsageAccess(context)
        if (!access) return@withContext ScreenTimeSnapshot(false, emptyList(), emptyList(), 0L)
        val daily = loadDailyMinutes(context, 7)
        val yesterday = loadDailyMinutes(context, 2).firstOrNull() ?: 0L
        ScreenTimeSnapshot(
            hasAccess = true,
            apps = loadUsage(context, days),
            dailyMinutes = daily,
            yesterdayMinutes = yesterday
        )
    }
}
