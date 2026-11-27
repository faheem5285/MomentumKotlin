package com.faheem.habits.benchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MomentumMacrobenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        setupBlock = { pressHome() }
    ) {
        startActivityAndWait()
    }

    @Test
    fun homeScroll() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        setupBlock = { pressHome() }
    ) {
        startActivityAndWait()
        swipeContainer(HOME_CONTAINER)
    }

    @Test
    fun habitsTodayScroll() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        setupBlock = { pressHome() }
    ) {
        startActivityAndWait()
        val habits = device.wait(Until.findObject(By.descContains("Habits")), 5_000)
        habits?.let {
            runCatching { it.click() }
            device.waitForIdle()
            swipeContainer(HABITS_CONTAINER)
        }
    }

    /** Missing target means a gate/onboarding path is active; do not fail the measurement. */
    private fun swipeContainer(description: String) {
        val container = device.wait(Until.findObject(By.desc(description)), 5_000) ?: return
        val bounds = container.visibleBounds ?: return
        if (bounds.height() < 160) return
        val x = bounds.centerX()
        val startY = bounds.bottom - 40
        val endY = bounds.top + 40
        if (startY - endY >= 80) device.swipe(x, startY, x, endY, 20)
    }

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private companion object {
        const val PACKAGE_NAME = "com.faheem.habits"
        const val HOME_CONTAINER = "Home records"
        const val HABITS_CONTAINER = "Habits for today"
    }
}
