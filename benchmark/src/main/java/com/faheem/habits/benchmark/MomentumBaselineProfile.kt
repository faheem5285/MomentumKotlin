package com.faheem.habits.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
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
        device.findObject(By.text("Habits"))?.click()
        device.waitForIdle()
        device.pressBack()
    }
}
