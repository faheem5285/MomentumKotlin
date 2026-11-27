package com.faheem.habits.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MomentumBaselineProfile {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startupAndCoreFlows() = baselineProfileRule.collect(
        packageName = "com.faheem.habits",
        maxIterations = 3,
        stableIterations = 1
    ) {
        device.pressHome()
        startActivityAndWait()
        device.waitForIdle()
        val habits = device.wait(Until.findObject(By.descContains("Habits")), 5_000)
        habits?.let {
            runCatching { it.click() }
            device.wait(Until.findObject(By.desc("Habits for today")), 5_000)
            device.waitForIdle()
            device.pressBack()
        }
    }
}
