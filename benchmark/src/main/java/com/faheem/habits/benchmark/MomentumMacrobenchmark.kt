package com.faheem.habits.benchmark

import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
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
        packageName = "com.faheem.habits",
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        setupBlock = { pressHome() }
    ) {
        startActivityAndWait()
    }

    @Test
    fun homeScroll() = benchmarkRule.measureRepeated(
        packageName = "com.faheem.habits",
        metrics = emptyList(),
        iterations = 5,
        setupBlock = { pressHome() }
    ) {
        startActivityAndWait()
        device.wait(Until.hasObject(By.textContains("Momentum")), 3_000)
        device.swipe(500, 1_500, 500, 300, 20)
    }

    @Test
    fun habitsTodayScroll() = benchmarkRule.measureRepeated(
        packageName = "com.faheem.habits",
        metrics = emptyList(),
        iterations = 5,
        setupBlock = { pressHome() }
    ) {
        startActivityAndWait()
        device.wait(Until.hasObject(By.text("Habits")), 3_000)
        device.findObject(By.text("Habits"))?.click()
        device.waitForIdle()
        device.swipe(500, 1_500, 500, 300, 20)
    }

    private val device: UiDevice
        get() = UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation())
}
